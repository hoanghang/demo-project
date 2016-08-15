package com.fpt.jira.worklog.service;

import com.atlassian.activeobjects.external.ActiveObjects;
import com.atlassian.cache.*;
import com.atlassian.cache.CacheManager;
import com.atlassian.core.util.InvalidDurationException;
import com.atlassian.crowd.embedded.api.User;
import com.atlassian.event.api.EventPublisher;
import com.atlassian.jira.ComponentManager;
import com.atlassian.jira.avatar.Avatar;
import com.atlassian.jira.avatar.AvatarService;
import com.atlassian.jira.bc.EntityNotFoundException;
import com.atlassian.jira.bc.issue.worklog.TimeTrackingConfiguration;
import com.atlassian.jira.bc.project.component.ProjectComponent;
import com.atlassian.jira.bc.project.component.ProjectComponentManager;
import com.atlassian.jira.bc.projectroles.ProjectRoleService;
import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.config.properties.ApplicationProperties;
import com.atlassian.jira.event.issue.IssueEvent;
import com.atlassian.jira.issue.CustomFieldManager;
import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.issue.IssueManager;
import com.atlassian.jira.issue.MutableIssue;
import com.atlassian.jira.issue.changehistory.ChangeHistoryItem;
import com.atlassian.jira.issue.changehistory.ChangeHistoryManager;
import com.atlassian.jira.issue.fields.CustomField;
import com.atlassian.jira.issue.fields.layout.field.FieldLayoutManager;
import com.atlassian.jira.issue.issuetype.IssueType;
import com.atlassian.jira.project.Project;
import com.atlassian.jira.project.ProjectManager;
import com.atlassian.jira.project.version.Version;
import com.atlassian.jira.project.version.VersionManager;
import com.atlassian.jira.security.JiraAuthenticationContext;
import com.atlassian.jira.security.groups.GroupManager;
import com.atlassian.jira.security.roles.*;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.util.I18nHelper.BeanFactory;
import com.atlassian.jira.util.JiraDurationUtils;
import com.atlassian.jira.util.ObjectUtils;
import com.atlassian.jira.util.SimpleErrorCollection;
import com.atlassian.mail.Email;
import com.atlassian.mail.MailFactory;
import com.atlassian.mail.queue.SingleMailQueueItem;
import com.atlassian.mail.server.SMTPMailServer;
import com.atlassian.sal.api.pluginsettings.PluginSettings;
import com.atlassian.sal.api.pluginsettings.PluginSettingsFactory;
import com.fpt.jira.core.util.constant.CustomFieldName;
import com.fpt.jira.core.util.constant.IssueTypeAndStatus;
import com.fpt.jira.core.util.constant.WorkLogConstant;
import com.fpt.jira.project.projectconfig.ProjectConfigInfo;
import com.fpt.jira.project.projectconfig.ProjectConfigInfoService;
import com.fpt.jira.project.projectconfig.group.ProjectGroup;
import com.fpt.jira.project.projectconfig.team.ObjectSearch;
import com.fpt.jira.project.projectconfig.team.ProjectTeamService;
import com.fpt.jira.project.projectconfig.unit.ProjectUnit;
import com.fpt.jira.project.rest.bean.ProjectConfigInfoBean;
import com.fpt.jira.project.rest.bean.ProjectUnitBean;
import com.fpt.jira.worklog.WorkLog;
import com.fpt.jira.worklog.bean.*;
import com.fpt.jira.worklog.token.StaticTokenUtil;
import com.fpt.jira.worklog.util.CommonUtil;
import com.fpt.jira.worklog.util.CustomFieldUtil;
import com.fpt.jira.worklog.util.DateUtils;
import com.google.common.base.Function;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;

import net.java.ao.EntityStreamCallback;
import net.java.ao.Query;

import org.codehaus.jackson.JsonGenerationException;
import org.codehaus.jackson.map.JsonMappingException;
import org.codehaus.jackson.map.ObjectMapper;
import org.joda.time.DateTimeConstants;
import org.joda.time.Days;
import org.joda.time.LocalDate;
import org.ofbiz.core.entity.GenericEntityException;

import webwork.action.ActionContext;

import java.io.IOException;
import java.sql.Timestamp;
import java.util.*;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.Lists.newArrayList;

public class WorkLogServiceImp implements WorkLogService {

    private static final String SELECT_ALL = "All";
    private static final int YEAR_LOWER_RANGE = 1900;
    private static final int YEAR_UPPER_RANGE = 2100;
    private static final String TOTAL_RECORD = "totalRecord";
    private static final String TOTAL_HOUR = "totalHour";
    private static final String ACTUAL_START_DATE = CustomFieldName.ACTUAL_START_DATE;
    private static final String ACTUAL_END_DATE = CustomFieldName.ACTUAL_END_DATE;
    private static final String APPROVED = WorkLogConstant.STATUS_APPROVED;
    private static final String SUM = "Sum";
    private static final String EMPTY = "";
    private static final String TOTAL_WORK_LOGS = "TotalWorkLogs";
    private static final String TOTAL_ISSUE_WORK_LOGS = "TotalIssueWorkLogs";
    private static final String FIS_TOP_USER = "FIS-TOP_USER";
    private static final String CREATE_WORKLOG = "Created WorkLog";
    private static final String DELETE_WORKLOG = "Deleted WorkLog";
    private static final String UPDATE_WORKLOG = "Edited WorkLog";
    private static final String APPROVE_WORKLOG = "Approved WorkLog";
    private static final String REJECT_WORKLOG = "Reject WorkLog";
    private final ActiveObjects ao;
    private final IssueManager issueManager;
    private ProjectConfigInfoService projectConfigInfoService;
    private ProjectComponentManager projectComponentManager;
    private VersionManager versionManager;
    private ApplicationProperties applicationProperties;
    private TimeTrackingConfiguration timeTrackingConfiguration;
    private JiraAuthenticationContext jiraAuthenticationContext;
    private EventPublisher eventPublisher;
    private BeanFactory beanFactory;
    private CacheManager cacheManager;
    private CustomFieldManager customFieldManager;
    private com.atlassian.sal.api.user.UserManager userManagerSAL;
    public static final String BASE_URL = ComponentAccessor
            .getApplicationProperties().getString("jira.baseurl");
    String dateFormat;
    protected Float tempFloatValue = (float) 0;
    private final PluginSettingsFactory pluginSettingsFactory;
    private PluginSettings pluginSettings;
    private ProjectRoleManager projectRoleManager;
    private ProjectTeamService projectTeamServiceImpl;
    private ProjectManager projectManager;
    private FieldLayoutManager fieldLayoutManager;
    private final ChangeHistoryManager changeHistoryManager;
    private final AvatarService avatarService;
    private final GroupManager groupManager;
    private final WorkLogHistoryService workLogHistoryService;
    private final ProjectRoleService projectRoleService;
    
    public WorkLogServiceImp(PluginSettingsFactory pluginSettingsFactory,
                             ActiveObjects ao, IssueManager issueManager,
                             ProjectComponentManager projectComponentManager,
                             ProjectConfigInfoService projectConfigInfoService,
                             VersionManager versionManager,
                             ApplicationProperties applicationProperties,
                             TimeTrackingConfiguration timeTrackingConfiguration,
                             JiraAuthenticationContext jiraAuthenticationContext,
                             EventPublisher eventPublisher, BeanFactory beanFactory,
                             CacheManager cacheManager, ProjectManager projectManager,
                             ProjectTeamService projectTeamServiceImpl,
                             com.atlassian.sal.api.user.UserManager userManagerSAL,
                             CustomFieldManager customFieldManager,
                             ProjectRoleManager projectRoleManager,
                             FieldLayoutManager fieldLayoutManager,
                             ChangeHistoryManager changeHistoryManager,
                             AvatarService avatarService, GroupManager groupManager,
                             WorkLogHistoryService workLogHistoryService,
                             ProjectRoleService projectRoleService) {
        this.pluginSettingsFactory = pluginSettingsFactory;
        this.pluginSettings = this.pluginSettingsFactory.createGlobalSettings();
        this.ao = checkNotNull(ao);
        this.issueManager = checkNotNull(issueManager);
        this.projectRoleManager = projectRoleManager;
        this.projectComponentManager = checkNotNull(projectComponentManager);
        this.projectConfigInfoService = checkNotNull(projectConfigInfoService);
        this.projectComponentManager = checkNotNull(projectComponentManager);
        this.versionManager = versionManager;
        this.customFieldManager = customFieldManager;
        this.applicationProperties = applicationProperties;
        this.timeTrackingConfiguration = timeTrackingConfiguration;
        this.jiraAuthenticationContext = jiraAuthenticationContext;
        this.eventPublisher = eventPublisher;
        this.beanFactory = beanFactory;
        this.cacheManager = cacheManager;
        this.userManagerSAL = userManagerSAL;
        dateFormat = DateUtils.getJiraDateFormat(applicationProperties);
        this.projectTeamServiceImpl = checkNotNull(projectTeamServiceImpl);
        this.projectManager = projectManager;
        this.fieldLayoutManager = fieldLayoutManager;
        this.changeHistoryManager = changeHistoryManager;
        this.avatarService = avatarService;
        this.groupManager = groupManager;
        this.workLogHistoryService = checkNotNull(workLogHistoryService);
        this.projectRoleService = projectRoleService;
    }

    @Override
    public WorkLog addWorkLog(String userKey, String userName, Long projectId,
                              String projectName, String issueKey, String issueSummary,
                              Date startDate, Date endDate, String workPerDay, String desc,
                              String typeOfWork, String status, String comment) {
        float workPerDayFloat = Float.valueOf(workPerDay);
        workPerDayFloat = CommonUtil.roundFloat(workPerDayFloat);
        workPerDay = CommonUtil.convertFloatToStringRound(workPerDayFloat);
        Calendar calendar = Calendar.getInstance();
        Date currentDate = new Date();
        ApplicationUser appuser = jiraAuthenticationContext.getUser();
        WorkLog newWorkLog;
        if (endDate != null) {
            for (long i = startDate.getTime(); i <= endDate.getTime(); i += DateUtils.MILI_SEC_PER_DAY) {
                calendar.setTimeInMillis(i);
                if (calendar.get(Calendar.DAY_OF_WEEK) != Calendar.SUNDAY
                        && calendar.get(Calendar.DAY_OF_WEEK) != Calendar.SATURDAY) {
                    newWorkLog = createNewWorkLog(userKey, userName, projectId,
                            projectName, issueKey, issueSummary,
                            calendar.getTime(), null, workPerDay, desc,
                            typeOfWork, status, comment, currentDate);
                    workLogHistoryService.createWorkLogHistory(
                            appuser.getDisplayName(), issueKey, CREATE_WORKLOG,
                            newWorkLog);
                }
            }
        } else {
            newWorkLog = createNewWorkLog(userKey, userName, projectId,
                    projectName, issueKey, issueSummary, startDate, null,
                    workPerDay, desc, typeOfWork, status, comment, currentDate);
            workLogHistoryService.createWorkLogHistory(
                    appuser.getDisplayName(), issueKey, CREATE_WORKLOG,
                    newWorkLog);
        }
        try {
            MutableIssue issue = issueManager.getIssueObject(issueKey);
            List<WorkLog> workLogs = getWorkLogByIssueKey(issueKey);
            List<Date> actualStartDateList = new ArrayList<Date>();
            for (WorkLog workLog : workLogs) {
                if (!WorkLogConstant.STATUS_REJECTED.equalsIgnoreCase(workLog
                        .getStatus())) {
                    actualStartDateList.add(workLog.getStartDate());
                }
            }
            Timestamp actualStartDate = new Timestamp(Collections.min(
                    actualStartDateList).getTime());
            CustomFieldUtil.updateDateCustomfielValue(issue,
                    getCustomFieldByName(ACTUAL_START_DATE), actualStartDate,
                    fieldLayoutManager);
        } catch (Exception e) {
        }
        return null;
    }

    @Override
    public WorkLog getWorkLogById(int id) {
        return ao.get(WorkLog.class, id);
    }

    @Override
    public List<WorkLog> getWorkLogByIssue(String issueId, String userKey) {
        WorkLog[] listWorkLog = ao.find(
                WorkLog.class,
                Query.select()
                        .order("START_DATE DESC")
                        .where("ISSUE_KEY = ? AND USER_KEY = ? ", issueId,
                                userKey));
        return newArrayList(listWorkLog);
    }

    @Override
    public List<WorkLog> getWorkLogByIssue(Issue issue) {
        WorkLog[] listWorkLog = ao.find(
                WorkLog.class,
                Query.select().order("START_DATE DESC")
                        .where("ISSUE_KEY = ?", issue.getKey()));

        return newArrayList(listWorkLog);
    }

    @Override
    public List<WorkLog> getWorkLogByIssueKey(String issueKey) {
        WorkLog[] listWorkLog = ao.find(
                WorkLog.class,
                Query.select().order("START_DATE DESC")
                        .where("ISSUE_KEY = ?", issueKey));

        return newArrayList(listWorkLog);
    }

    @Override
    public List<WorkLog> getWorkLogByIssueKeys(List<Issue> issues) {
        List<WorkLog> worklogs = new ArrayList<WorkLog>();
        String issueQuerry;
        issueQuerry = "(";
        int i = 0;
        for (Issue issue : issues) {
            i++;
            if (issues != null) {
                if (issues.size() == i) {
                    issueQuerry += "'" + issue.getKey() + "'";
                } else {
                    issueQuerry += "'" + issue.getKey() + "'" + ',';
                }
            }
        }
        issueQuerry += ")";
        if (!"()".equals(issueQuerry)) {
            WorkLog[] listWorkLog = ao.find(
                    WorkLog.class,
                    Query.select().order("START_DATE DESC")
                            .where("ISSUE_KEY IN " + issueQuerry));
            worklogs = newArrayList(listWorkLog);
        }
        return worklogs;
    }

    @Override
    public List<WorkLog> getWorkLogApprovedByIssueKeys(List<Issue> issues) {
        List<WorkLog> worklogs = new ArrayList<WorkLog>();
        String issueQuerry;
        issueQuerry = "(";
        int i = 0;
        for (Issue issue : issues) {
            i++;
            if (issues != null) {
                if (issues.size() == i) {
                    issueQuerry += "'" + issue.getKey() + "'";
                } else {
                    issueQuerry += "'" + issue.getKey() + "'" + ',';
                }
            }
        }
        issueQuerry += ")";
        if (!"()".equals(issueQuerry)) {
            WorkLog[] listWorkLog = ao.find(
                    WorkLog.class,
                    Query.select()
                            .order("START_DATE DESC")
                            .where("STATUS = '" + StaticTokenUtil.APPROVE
                                    + "' AND ISSUE_KEY IN " + issueQuerry));
            worklogs = newArrayList(listWorkLog);
        }
        return worklogs;
    }

    @Override
    public boolean deleteWorkLog(String idWorkLog) {
        final WorkLog worklogObj = ao.get(WorkLog.class,
                Integer.parseInt(idWorkLog));
        ApplicationUser appuser = jiraAuthenticationContext.getUser();
        workLogHistoryService.createWorkLogHistory(appuser.getDisplayName(),
                worklogObj.getIssueKey(), DELETE_WORKLOG, worklogObj);
        ao.delete(worklogObj);
        try {
            MutableIssue issue = issueManager.getIssueByCurrentKey(worklogObj
                    .getIssueKey());
            CustomField actualStartCustomField = getCustomFieldByName(ACTUAL_START_DATE);
            List<WorkLog> workLogs = getWorkLogNotRejectByIssueKeyOrderByDate(worklogObj
                    .getIssueKey());
            Timestamp dueDate = new Timestamp(workLogs.get(0).getStartDate()
                    .getTime());
            CustomFieldUtil.updateDateCustomfielValue(issue,
                    actualStartCustomField, dueDate, fieldLayoutManager);
            dueDate = new Timestamp(workLogs.get(workLogs.size() - 1)
                    .getStartDate().getTime());
            CustomField actualEndCustomField = getCustomFieldByName(ACTUAL_END_DATE);
            CustomFieldUtil.updateDateCustomfielValue(issue,
                    actualEndCustomField, dueDate, fieldLayoutManager);
        } catch (Exception e) {
        }
        return true;
    }

    @Override
    public List<WorkLog> searchWorkLogNoneIssue() {
        final List<WorkLog> listWorkLog = new ArrayList<WorkLog>();
        Query query = Query.select("*").order("ISSUE_KEY");
        ao.stream(WorkLog.class, query,
                new EntityStreamCallback<WorkLog, Integer>() {
                    @Override
                    public void onRowRead(WorkLog workLog) {
                        if (workLog.getIssueKey() != null
                                && !workLog.getIssueKey().isEmpty()) {
                            try {
                                Issue issue = issueManager
                                        .getIssueByCurrentKey(workLog
                                                .getIssueKey());
                                if (issue == null) {
                                    listWorkLog.add(workLog);
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    }
                });
        return listWorkLog;
    }

    @Override
    public boolean deleteWorkLogNoneIssue(List<WorkLog> listWorkLog) {
        try {
            for (WorkLog workLog : listWorkLog) {
                deleteWorkLog(String.valueOf(workLog.getID()));
            }
            return true;
        } catch (Exception e) {
            return false;
        }

    }

    @Override
    public WorkLog editWorkLog(String id, Date startDate, Date endDate,
                               String workPerDay, String desc, String typeOfWork) {
        final WorkLog worklogObj = ao.get(WorkLog.class, Integer.parseInt(id));
        float workPerDayFloat = Float.valueOf(workPerDay);
        workPerDayFloat = CommonUtil.roundFloat(workPerDayFloat);
        workPerDay = CommonUtil.convertFloatToStringRound(workPerDayFloat);
        try {
            MutableIssue issue = issueManager.getIssueByCurrentKey(worklogObj
                    .getIssueKey());
            CustomField actualStartCustomField = customFieldManager
                    .getCustomFieldObjectByName(ACTUAL_START_DATE);
            Timestamp dueDate = new Timestamp(startDate.getTime());
            Timestamp temp = (Timestamp) actualStartCustomField.getValue(issue);
            if (actualStartCustomField.getValue(issue) == null
                    || dueDate.getTime() < temp.getTime()) {
                CustomFieldUtil.updateDateCustomfielValue(issue,
                        actualStartCustomField, dueDate, fieldLayoutManager);
            }
        } catch (Exception e) {
        }
        try {
            if (worklogObj != null) {
                worklogObj.setStatus(StaticTokenUtil.PENDING);
                worklogObj.setStartDate(startDate);
                worklogObj.setEndDate(endDate);
                worklogObj.setWorkPerDay(workPerDay);
                worklogObj.setTypeOfWork(typeOfWork);
                worklogObj.setDesc(desc);
                worklogObj.setComment("");
                worklogObj.save();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        ApplicationUser appuser = jiraAuthenticationContext.getUser();
        workLogHistoryService.createWorkLogHistory(appuser.getDisplayName(),
                worklogObj.getIssueKey(), UPDATE_WORKLOG, worklogObj);
        return worklogObj;
    }

    @Override
    public int getWorkLogForCurrentUserTotal(String userKey, Long projectId,
                                             Date startDate, Date endDate, String status) {
        if (startDate == null) {
            startDate = DateUtils.INVALID_START_DATE;
        }
        if (endDate == null) {
            endDate = DateUtils.INVALID_END_DATE;
        }
        startDate = DateUtils.standardizeDate(startDate, 0, 0, 0, 0);
        endDate = DateUtils.standardizeDate(endDate, 23, 59, 59, 999);
        if (!(SELECT_ALL.equals(status) || status == null)) {
            Query query = Query
                    .select()
                    .where("PROJECT_ID = ? AND USER_KEY = ? AND START_DATE >= ? AND START_DATE <= ? AND (END_DATE <= ? OR END_DATE IS NULL) AND STATUS = ?",
                            projectId, userKey, startDate, endDate, endDate,
                            status);
            return ao.count(WorkLog.class, query);
        }
        if ((SELECT_ALL.equals(status) || status == null)) {
            Query query = Query
                    .select()
                    .where("PROJECT_ID = ? AND USER_KEY = ? AND START_DATE >= ? AND START_DATE <= ? AND (END_DATE <= ? OR END_DATE IS NULL)",
                            projectId, userKey, startDate, endDate, endDate);
            return ao.count(WorkLog.class, query);
        }
        return 0;
    }

    @Override
    public int getWorkLogForCurrentUserTotal(Long projectId, String userKey,
                                             Date startDate, Date endDate, String type, String componentId,
                                             String versionId, String status, String issueType, String issueStatus) {
        if (startDate == null) {
            startDate = DateUtils.INVALID_START_DATE;
        }
        if (endDate == null) {
            endDate = DateUtils.INVALID_END_DATE;
        }
        startDate = DateUtils.standardizeDate(startDate, 0, 0, 0, 0);
        endDate = DateUtils.standardizeDate(endDate, 23, 59, 59, 999);
        Query query = queryWorkLogForCurrentUserTotal(projectId, userKey, startDate, endDate, type, componentId, versionId, status, issueType, issueStatus);
        return ao.count(WorkLog.class, query);
    }

    @Override
    public List<WorkLog> getWorkLogForCurrentUser(int currentPageNumber,
                                                  int recordInPage, String userKey, Long projectId, Date startDate,
                                                  Date endDate, String status) {
        WorkLog[] listWorkLog = null;
        if (startDate == null) {
            startDate = DateUtils.INVALID_START_DATE;
        }
        if (endDate == null) {
            endDate = DateUtils.INVALID_END_DATE;
        }

        if (!(SELECT_ALL.equals(status) || status == null)) {
            Query query = Query
                    .select()
                    .limit(recordInPage)
                    .offset((currentPageNumber - 1) * recordInPage)
                    .order("START_DATE DESC")
                    .where("PROJECT_ID = ? AND USER_KEY = ? AND START_DATE >= ? AND START_DATE <= ? AND (END_DATE <= ? OR END_DATE IS NULL) AND STATUS = ?",
                            projectId, userKey, startDate, endDate, endDate,
                            status);
            listWorkLog = ao.find(WorkLog.class, query);
        }
        if ((SELECT_ALL.equals(status) || status == null)) {
            Query query = Query
                    .select()
                    .limit(recordInPage)
                    .offset((currentPageNumber - 1) * recordInPage)
                    .order("START_DATE DESC")
                    .where("PROJECT_ID = ? AND USER_KEY = ? AND START_DATE >= ? AND START_DATE <= ? AND (END_DATE <= ? OR END_DATE IS NULL)",
                            projectId, userKey, startDate, endDate, endDate);
            listWorkLog = ao.find(WorkLog.class, query);
        }
        return newArrayList(listWorkLog);
    }

    @Override
    public List<WorkLog> getWorkLogForCurrentUser(int currentPageNumber,
                                                  int recordInPage, String userKey, Long projectId, Date startDate,
                                                  Date endDate, String type, String componentId,
                                                  String versionId, String status, String issueType, String issueStatus) {
        WorkLog[] listWorkLog = null;
        if (startDate == null) {
            startDate = DateUtils.INVALID_START_DATE;
        }
        if (endDate == null) {
            endDate = DateUtils.INVALID_END_DATE;
        }
        Query query = queryWorkLogForUser(currentPageNumber, recordInPage, userKey, projectId, startDate,
                endDate, type, componentId, versionId, status, issueType, issueStatus);
        listWorkLog = ao.find(WorkLog.class, query);
        return newArrayList(listWorkLog);
    }

    @Override
    public float getTotalHourWorkLogForCurrentUser(String userKey,
                                                   Long projectId, Date startDate,
                                                   Date endDate, String status) {
        WorkLog[] workLogs = null;
        Query query = null;
        if (startDate == null) {
            startDate = DateUtils.INVALID_START_DATE;
        }
        if (endDate == null) {
            endDate = DateUtils.INVALID_END_DATE;
        }
        startDate = DateUtils.standardizeDate(startDate, 0, 0, 0, 0);
        endDate = DateUtils.standardizeDate(endDate, 23, 59, 59, 999);
        if ((SELECT_ALL.equals(status) || status == null)) {
            query = Query
                    .select("ID, WORK_PER_DAY, START_DATE")
                    .where("PROJECT_ID = ? AND USER_KEY = ? AND START_DATE >= ? AND START_DATE <= ? AND (END_DATE <= ? OR END_DATE IS NULL)",
                            projectId, userKey, startDate, endDate, endDate);
        } else {
            query = Query
                    .select("ID, WORK_PER_DAY, START_DATE")
                    .where("PROJECT_ID = ? AND USER_KEY = ? AND START_DATE >= ? AND START_DATE <= ? AND (END_DATE <= ? OR END_DATE IS NULL) AND STATUS = ?",
                            projectId, userKey, startDate, endDate, endDate,
                            status);
        }
        Float totalHour = 0F;
        workLogs = ao.find(WorkLog.class, query);
        List<WorkLog> listWorkLog = newArrayList(workLogs);
        for (WorkLog workLog : listWorkLog) {
            totalHour += Float.valueOf(workLog.getWorkPerDay());
        }
        return totalHour;
    }

    @Override
    public float getTotalHourWorkLogForCurrentUser(Long projectId, String userKey, Date startDate,
                                                   Date endDate, String type, String componentId,
                                                   String versionId, String status, String issueType, String issueStatus) {
        WorkLog[] workLogs = null;

        if (startDate == null) {
            startDate = DateUtils.INVALID_START_DATE;
        }
        if (endDate == null) {
            endDate = DateUtils.INVALID_END_DATE;
        }
        startDate = DateUtils.standardizeDate(startDate, 0, 0, 0, 0);
        endDate = DateUtils.standardizeDate(endDate, 23, 59, 59, 999);
        Query query = queryTotalHourWorkLogForCurrentUser(projectId, userKey, startDate,
                endDate, type, componentId, versionId, status, issueType, issueStatus);
        Float totalHour = 0F;
        workLogs = ao.find(WorkLog.class, query);
        List<WorkLog> listWorkLog = newArrayList(workLogs);
        for (WorkLog workLog : listWorkLog) {
            totalHour += Float.valueOf(workLog.getWorkPerDay());
        }
        return totalHour;
    }

    @Override
    public WorkLog updateWorkLogStatus(long id, String status, String reason) {
        WorkLog worklogObj = null;
        try {
            worklogObj = ao.get(WorkLog.class, (int) id);
            worklogObj.setStatus(status);
            worklogObj.setComment(reason);
            ApplicationUser appuser = jiraAuthenticationContext.getUser();
            if (StaticTokenUtil.REJECT.equalsIgnoreCase(status)) {
                if (ObjectUtils.isNotEmpty(worklogObj.getApprovalDate())) {
                    worklogObj.setApprovalDate(null);
                    worklogObj.setApproved(EMPTY);
                }
                workLogHistoryService.createWorkLogHistory(
                        appuser.getDisplayName(), worklogObj.getIssueKey(),
                        REJECT_WORKLOG, worklogObj);
            }
            if (StaticTokenUtil.APPROVE.equalsIgnoreCase(status)) {
                worklogObj.setApproved(jiraAuthenticationContext.getUser()
                        .getKey());
                worklogObj.setApprovalDate(new Date());
                workLogHistoryService.createWorkLogHistory(
                        appuser.getDisplayName(), worklogObj.getIssueKey(),
                        APPROVE_WORKLOG, worklogObj);
            }
            worklogObj.save();
            if (StaticTokenUtil.REJECT.equalsIgnoreCase(status)) {
                MutableIssue issue = issueManager
                        .getIssueByCurrentKey(worklogObj.getIssueKey());
                List<WorkLog> workLogs = getWorkLogNotRejectByIssueKeyOrderByDate(worklogObj
                        .getIssueKey());
                Timestamp dueDate = new Timestamp(workLogs.get(0)
                        .getStartDate().getTime());
                CustomField actualStartCustomField = customFieldManager
                        .getCustomFieldObjectByName(ACTUAL_START_DATE);
                CustomFieldUtil.updateDateCustomfielValue(issue,
                        actualStartCustomField, dueDate, fieldLayoutManager);
                dueDate = new Timestamp(workLogs.get(workLogs.size() - 1)
                        .getStartDate().getTime());
                CustomField actualEndCustomField = customFieldManager
                        .getCustomFieldObjectByName(ACTUAL_END_DATE);
                CustomFieldUtil.updateDateCustomfielValue(issue,
                        actualEndCustomField, dueDate, fieldLayoutManager);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return worklogObj;
    }

    @Override
    public List<WorkLog> getAllWorkLogForAdmin(Long projectId,
                                               int currentPageNumber, int recordInPage) {
        return newArrayList(ao.find(
                WorkLog.class,
                Query.select().limit(recordInPage)
                        .offset((currentPageNumber - 1) * recordInPage)));
    }

    @Override
    public List<WorkLog> getAllPendingWorkLogForAdmin(Long projectId,
                                                      int currentPageNumber, int recordInPage) {
        final WorkLog[] worklog = (WorkLog[]) ao.find(
                (WorkLog.class),
                Query.select()
                        .limit(recordInPage)
                        .offset((currentPageNumber - 1) * recordInPage)
                        .where("PROJECT_ID = ? AND STATUS = ?", projectId,
                                "Pending"));

        return newArrayList(worklog);
    }

    @Override
    public List<WorkLog> getWorkLogPendingForAdmin(Long projectId,
                                                   int currentPageNumber, int recordInPage, String userKey,
                                                   Date startDate, Date endDate, String typeOfWork, String component) {
        Query query = queryWorkLogPendingForAdmin(projectId, userKey,
                startDate, endDate, typeOfWork, component);
        if (query == null) {
            return new ArrayList<WorkLog>();
        } else {
            query.limit(recordInPage);
            query.offset((currentPageNumber - 1) * recordInPage);
            query.order("STATUS DESC, START_DATE DESC");
            WorkLog[] listWorkLog = ao.find(WorkLog.class, query);
            return newArrayList(listWorkLog);
        }
    }

    @Override
    public int getWorkLogPendingForAdminTotal(Long projectId, String userKey,
                                              Date startDate, Date endDate, String typeOfWork, String component) {
        Query query = queryWorkLogPendingForAdmin(projectId, userKey,
                startDate, endDate, typeOfWork, component);
        if (query == null) {
            return 0;
        } else {
            return ao.count(WorkLog.class, query);
        }

    }

    @Override
    public float totalHourWorkLogPendingForAdminTotal(Long projectId,
                                                      String userKey, Date startDate, Date endDate, String typeOfWork,
                                                      String component) {
        float totalHour = 0;
        Query query = queryWorkLogPendingForAdmin(projectId, userKey,
                startDate, endDate, typeOfWork, component);
        if (query == null) {
            return totalHour;
        } else {
            WorkLog[] workLogs = ao.find(WorkLog.class, query);
            List<WorkLog> listWorkLog = newArrayList(workLogs);

            for (WorkLog workLog : listWorkLog) {
                totalHour += Float.valueOf(workLog.getWorkPerDay());
            }
            return totalHour;
        }

    }

    private Query queryWorkLogPendingForAdmin(Long projectId, String userKey,
                                              Date startDate, Date endDate, String typeOfWork, String component) {
        if (startDate == null) {
            startDate = DateUtils.INVALID_START_DATE;
        }
        if (endDate == null) {
            endDate = DateUtils.INVALID_END_DATE;
        }
        startDate = DateUtils.standardizeDate(startDate, 0, 0, 0, 0);
        endDate = DateUtils.standardizeDate(endDate, 23, 59, 59, 999);
        List<Object> listParam = new ArrayList<Object>();
        listParam.add(projectId);
        listParam.add(startDate);
        listParam.add(endDate);
        listParam.add(endDate);
        listParam.add(StaticTokenUtil.REJECT);
        String querryString = "PROJECT_ID = ? AND START_DATE >= ? AND START_DATE <= ? AND (END_DATE <= ? OR END_DATE IS NULL) AND STATUS != ?";
        if (!(userKey == null || userKey == "")) {
            querryString += " AND USER_KEY = ?";
            listParam.add(userKey);
        }
        if (!(SELECT_ALL.equals(typeOfWork) || typeOfWork == null)) {
            querryString += " AND TYPE_OF_WORK = ?";
            listParam.add(typeOfWork);
        }
        if (!(SELECT_ALL.equals(component) || component == null)) {
            try {
                ProjectComponent projectComponent = projectComponentManager
                        .findByComponentName(projectId, component);
                List<Issue> issues = getIssuesWithComponent(projectComponent);
                List<String> listIssueKey = new ArrayList<String>();
                for (Issue issue : issues) {
                    listIssueKey.add(issue.getKey());
                }
                if (listIssueKey.size() > 0) {
                    querryString += "AND (";
                    for (String issueKey : listIssueKey) {
                        if (listIssueKey.indexOf(issueKey) == 0) {
                            querryString += "ISSUE_KEY = ?";
                        } else {
                            querryString += "OR ISSUE_KEY = ?";
                        }
                    }
                    querryString += ")";
                    listParam.addAll(listIssueKey);
                } else {
                    return null;
                }
            } catch (NullPointerException e) {
            }
        }
        Query query = Query.select().where(querryString, listParam.toArray());
        return query;
    }

    @Override
    public int getAllWorkLogForAdminTotal(Long projectId, String userKey,
                                          Date startDate, Date endDate, String status, String typeOfWork,
                                          String component) {
        Query query = queryAllWorkLogForAdmin(projectId, userKey, startDate,
                endDate, status, typeOfWork, component);
        if (query == null) {
            return 0;
        } else {
            return ao.count(WorkLog.class, query);
        }
    }

    @Override
    public List<WorkLog> getAllWorkLogForAdmin(Long projectId,
                                               int currentPageNumber, int recordInPage, String userKey,
                                               Date startDate, Date endDate, String status, String typeOfWork,
                                               String component) {

        Query query = queryAllWorkLogForAdmin(projectId, userKey, startDate,
                endDate, status, typeOfWork, component);
        if (query == null) {
            return new ArrayList<WorkLog>();
        } else {
            query.limit(recordInPage);
            query.offset((currentPageNumber - 1) * recordInPage);
            query.order("START_DATE DESC");
            WorkLog[] listWorkLog = ao.find(WorkLog.class, query);
            return newArrayList(listWorkLog);
        }
    }

    @Override
    public float totalHourAllWorkLogForAdmin(Long projectId, String userKey,
                                             Date startDate, Date endDate, String status, String typeOfWork,
                                             String component) {
        float totalHour = 0;
        Query query = queryAllWorkLogForAdmin(projectId, userKey, startDate,
                endDate, status, typeOfWork, component);
        if (query == null) {
            return totalHour;
        } else {
            query.order("START_DATE DESC");
            WorkLog[] workLogs = ao.find(WorkLog.class, query);
            List<WorkLog> listWorkLog = newArrayList(workLogs);
            for (WorkLog workLog : listWorkLog) {
                totalHour += Float.valueOf(workLog.getWorkPerDay());
            }
            return totalHour;
        }

    }

    public Query queryAllWorkLogForAdmin(Long projectId, String userKey,
                                         Date startDate, Date endDate, String status, String typeOfWork,
                                         String component) {
        if (startDate == null) {
            startDate = DateUtils.INVALID_START_DATE;
        }
        if (endDate == null) {
            endDate = DateUtils.INVALID_END_DATE;
        }
        List<Object> listParam = new ArrayList<Object>();
        listParam.add(projectId);
        listParam.add(startDate);
        listParam.add(endDate);
        listParam.add(endDate);
        String querryString = "PROJECT_ID = ? AND START_DATE >= ? AND START_DATE <= ? AND (END_DATE <= ? OR END_DATE IS NULL)";

        if (!(SELECT_ALL.equals(status) || status == null)) {
            if ("A&P".equals(status)) {
                querryString += " AND STATUS IN ('Pending','Approved')";
            } else {
                querryString += " AND STATUS = ?";
                listParam.add(status);
            }
        }
        if (!(userKey == null || userKey == "")) {
            querryString += " AND USER_KEY = ?";
            listParam.add(userKey);
        }
        if (!(SELECT_ALL.equals(typeOfWork) || typeOfWork == null)) {
            querryString += " AND TYPE_OF_WORK = ?";
            listParam.add(typeOfWork);
        }

        if (!(SELECT_ALL.equals(component) || component == null)) {
            try {
                List<Issue> issues = getIssuesWithComponent(projectComponentManager
                        .findByComponentName(projectId, component));
                List<String> listIssueKey = new ArrayList<String>();
                for (Issue issue : issues) {
                    listIssueKey.add(issue.getKey());
                }
                if (listIssueKey.size() > 0) {
                    querryString += "AND (";
                    for (String issueKey : listIssueKey) {
                        if (listIssueKey.indexOf(issueKey) == 0) {
                            querryString += "ISSUE_KEY = ?";
                        } else {
                            querryString += "OR ISSUE_KEY = ?";
                        }
                    }
                    querryString += ")";
                    listParam.addAll(listIssueKey);
                } else {
                    return null;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        Query query = Query.select().where(querryString, listParam.toArray());

        return query;
    }

    @Override
    public List<WorkLog> getWorkLogByProjectId(Long projectId) {
        List<WorkLog> listWorkLog;
        WorkLog[] workLogs = ao.find(WorkLog.class,
                Query.select().where("PROJECT_ID = ? ", projectId));
        if (workLogs == null) {
            listWorkLog = new ArrayList<WorkLog>();
        } else {
            listWorkLog = newArrayList(workLogs);
        }
        return listWorkLog;
    }

    @Override
    public List<WorkLog> getWorkLogByUsers(String[] userKeys, Date startDate,
                                           Date endDate) {
        if (startDate == null) {
            startDate = DateUtils.INVALID_START_DATE;
        }
        if (endDate == null) {
            endDate = DateUtils.INVALID_END_DATE;
        }
        startDate = DateUtils.standardizeDate(startDate, 0, 0, 0, 0);
        endDate = DateUtils.standardizeDate(endDate, 23, 59, 59, 999);
        StringBuilder query = new StringBuilder();
        if (!"null".equals(userKeys[0])) {
            String userQuerry = "(";
            int i = 0;
            for (String user : userKeys) {
                i++;
                if (userKeys.length == i) {
                    userQuerry += "'" + user + "'";
                } else {
                    userQuerry += "'" + user + "'" + ',';
                }

            }
            userQuerry += ")";
            query.append("USER_KEY IN ");
            query.append(userQuerry);
            query.append(" AND (START_DATE BETWEEN ? AND ?)");
        } else {
            query.append(" (START_DATE BETWEEN ? AND ?)");
        }
        WorkLog[] listWorkLog = ao.find(
                WorkLog.class,
                Query.select().order("USER_KEY ASC")
                        .where(query.toString(), startDate, endDate));
        return newArrayList(listWorkLog);
    }

    @Override
    public List<WorkLog> getWorkLogByUsers(String[] userKeys, int month,
                                           int year) {
        StringBuilder query = new StringBuilder();

        if (!"null".equals(userKeys[0])) {
            String userQuerry = "(";
            int i = 0;
            for (String user : userKeys) {
                i++;
                if (userKeys.length == i) {
                    userQuerry += "'" + user + "'";
                } else {
                    userQuerry += "'" + user + "'" + ',';
                }

            }
            userQuerry += ")";
            query.append("USER_KEY IN ");
            query.append(userQuerry);
            query.append(" AND MONTH(START_DATE) = ? AND YEAR(START_DATE) = ?");
        } else {
            query.append(" MONTH(START_DATE) = ? AND YEAR(START_DATE) = ?");
        }
        WorkLog[] listWorkLog = ao.find(
                WorkLog.class,
                Query.select().order("USER_KEY ASC")
                        .where(query.toString(), month, year));
        return newArrayList(listWorkLog);
    }

    @Override
    public List<WorkLog> getWorkLogByProjectId(Long projectId, Date startDate,
                                               Date endDate) {
        if (startDate == null) {
            startDate = DateUtils.INVALID_START_DATE;
        }
        if (endDate == null) {
            endDate = DateUtils.INVALID_END_DATE;
        }
        startDate = DateUtils.standardizeDate(startDate, 0, 0, 0, 0);
        endDate = DateUtils.standardizeDate(endDate, 23, 59, 59, 999);
        WorkLog[] listWorkLog = ao
                .find(WorkLog.class,
                        Query.select()
                                .order("USER_KEY ASC")
                                .where("PROJECT_ID = ? AND (START_DATE BETWEEN ? AND ?)",
                                        projectId, startDate, endDate));
        return newArrayList(listWorkLog);
    }

    @Override
    public List<WorkLog> getWorkLogApprovalAndPendingByProjectId(
            Long projectId, Date startDate, Date endDate) {
        if (startDate == null) {
            startDate = DateUtils.INVALID_START_DATE;
        }
        if (endDate == null) {
            endDate = DateUtils.INVALID_END_DATE;
        }
        startDate = DateUtils.standardizeDate(startDate, 0, 0, 0, 0);
        endDate = DateUtils.standardizeDate(endDate, 23, 59, 59, 999);
        WorkLog[] listWorkLog = ao
                .find(WorkLog.class,
                        Query.select()
                                .order("USER_KEY ASC")
                                .where("PROJECT_ID = ? AND (START_DATE BETWEEN ? AND ?) AND STATUS IN ( 'Pending','Approved' )",
                                        projectId, startDate, endDate));
        return newArrayList(listWorkLog);
    }

    public List<WorkLog> getWorkLogApprovedByProjectId(Long projectId,
                                                       Date startDate, Date endDate) {
        if (startDate == null) {
            startDate = DateUtils.INVALID_START_DATE;
        }
        if (endDate == null) {
            endDate = DateUtils.INVALID_END_DATE;
        }
        startDate = DateUtils.standardizeDate(startDate, 0, 0, 0, 0);
        endDate = DateUtils.standardizeDate(endDate, 23, 59, 59, 999);
        WorkLog[] listWorkLog = ao
                .find(WorkLog.class,
                        Query.select()
                                .order("USER_KEY ASC")
                                .where("STATUS = '"
                                                + StaticTokenUtil.APPROVE
                                                + "' AND PROJECT_ID = ? AND (START_DATE BETWEEN ? AND ?)",
                                        projectId, startDate, endDate));
        return newArrayList(listWorkLog);
    }

    @Override
    public List<WorkLog> getWorkLogByUserKey(String userKey) {
        WorkLog[] listWorkLog = ao.find(
                WorkLog.class,
                Query.select().order("ISSUE_KEY ASC")
                        .where("USER_KEY = ?", userKey));
        return newArrayList(listWorkLog);
    }

    @Override
    public float getCorrectionCost(Long projectId, List<Long> listComponentId) {
        List<Long> issueIds = new ArrayList<Long>();
        if (!listComponentId.isEmpty()) {
            for (Long componentId : listComponentId) {
                try {
                    issueIds.addAll(projectComponentManager
                            .getIssueIdsWithComponent(projectComponentManager
                                    .getProjectComponent(componentId)));
                } catch (NullPointerException e) {
                }
            }
        }
        List<Issue> issues = issueManager.getIssueObjects(issueIds);
        if (!issues.isEmpty()) {
            List<WorkLog> worklogDelivers = new ArrayList<WorkLog>();
            worklogDelivers = getWorkLogByIssueKeys(issues);
            float totalDelivers = 0;
            float totalCorrect = 0;
            for (WorkLog workLog : worklogDelivers) {
                totalDelivers += Float.valueOf(workLog.getWorkPerDay());
                if ("Correct".equals(workLog.getTypeOfWork())) {
                    totalCorrect += Float.valueOf(workLog.getWorkPerDay());
                }
            }
            if (totalDelivers == 0) {
                return 0;
            } else {
                return totalCorrect / totalDelivers;
            }
        } else {
            return 0;
        }
    }

    @Override
    public float getTotalWorkLogByProjectId(Long projectId) {
        List<WorkLog> worklogs = getWorkLogByProjectId(projectId);
        float total = 0;
        for (WorkLog workLogView : worklogs) {
            try {
                total += Float.valueOf(workLogView.getWorkPerDay());
            } catch (NumberFormatException e) {
            }
        }
        return total / DateUtils.HOUR_PER_DAY;
    }

    @Override
    public double getActualWorkLogByProject(long projectId) {
        double actualWorkLog = 0;
        List<WorkLog> list = getWorkLogByProjectId(projectId);
        for (WorkLog workLog : list) {
            actualWorkLog += Double.valueOf(workLog.getWorkPerDay());
        }
        return actualWorkLog;
    }

    @Override
    public double getActualWorkLogByVersion(Version version) {
        Collection<Long> keyIssues;
        keyIssues = versionManager.getIssueIdsWithAffectsVersion(version);
        List<Issue> issues = issueManager.getIssueObjects(keyIssues);
        List<Issue> issueResults = new ArrayList<Issue>();
        for (Issue issue : issues) {
            issueResults.add(issue);
        }
        return getActualForWorklogList(getWorkLogByIssueKeys(issueResults));
    }

    @Override
    public double getActualWorkLogByComponent(ProjectComponent component,
                                              Date startDate, Date endDate) {
        // FIXME: Invalid Date from componentDetailsService!
        List<WorkLog> list = getWorkLogByProjectId(component.getProjectId(),
                startDate, endDate);
        return getActualForWorklogList(list);
    }

    @Override
    public double getActualWorkLogByProject(long projectId, Date startDate,
                                            Date endDate) {
        List<WorkLog> list = getWorkLogApprovedByProjectId(projectId,
                startDate, endDate);
        return getActualForWorklogList(list);
    }

    @Override
    public double getActualWorkLogByVersion(Version version, Date startDate,
                                            Date endDate) {
        List<WorkLog> list = getWorkLogByProjectId(version.getProjectId(),
                version.getStartDate(), new Date());
        return getActualForWorklogList(list);

    }

    @Override
    public double getActualForWorklogList(List<WorkLog> list) {
        double sum = 0;
        for (WorkLog w : list) {
            sum += Double.valueOf(w.getWorkPerDay());
        }
        return sum;
    }

    @Override
    public double getActual(WorkLog w) {
        return getDaysBetween(w.getStartDate(), w.getEndDate(), false)
                * Float.parseFloat(w.getWorkPerDay());
    }

    /**
     * Get days between to date, exclude weekends.
     *
     * @param startDate
     * @param endDate
     * @return
     */
    public int getDaysBetween(Date startDate, Date endDate,
                              boolean ignoreWeekend) {
        int count = 0;
        LocalDate start = new LocalDate(startDate);
        LocalDate end = new LocalDate(endDate);
        if (ignoreWeekend) {
            LocalDate weekday = start;
            if (start.getDayOfWeek() == DateTimeConstants.SATURDAY
                    || start.getDayOfWeek() == DateTimeConstants.SUNDAY) {
                weekday = weekday.plusWeeks(1).withDayOfWeek(
                        DateTimeConstants.MONDAY);
            }
            while (weekday.isBefore(end)) {
                count++;

                if (weekday.getDayOfWeek() == DateTimeConstants.FRIDAY)
                    weekday = weekday.plusDays(3);
                else
                    weekday = weekday.plusDays(1);
            }
        } else {
            count = Days.daysBetween(start, end).getDays();
        }
        return count;
    }

    /**
     * Convert WorkLog to WorkLogRestModel for rest api
     *
     * @param worklog
     * @return
     */
    @Override
    public WorkLogRestModel convertModel(WorkLog worklog) {
        WorkLogRestModel workLogRestModel = new WorkLogRestModel();
        workLogRestModel.setId((worklog.getID()));
        workLogRestModel.setUserKey((worklog.getUserKey()));
        workLogRestModel.setUserName((worklog.getUserName()));
        workLogRestModel.setProjectId(worklog.getProjectId());
        workLogRestModel.setProjectName(worklog.getProjectName());
        workLogRestModel.setIssueKey(worklog.getIssueKey());
        workLogRestModel.setIssueName(worklog.getIssueName());
        workLogRestModel.setStartDate(worklog.getStartDate());
        workLogRestModel.setEndDate(worklog.getEndDate());
        workLogRestModel.setWorkPerDay(worklog.getWorkPerDay());
        workLogRestModel.setDesc(worklog.getDesc());
        workLogRestModel.setTypeOfWork(worklog.getTypeOfWork());
        workLogRestModel.setStatus(worklog.getStatus());
        workLogRestModel.setComment(worklog.getComment());
        workLogRestModel.setApprovalDate(worklog.getApprovalDate());
        workLogRestModel.setCreateDate(worklog.getCreateDate());
        return workLogRestModel;
    }

    @Override
    public List<WorkLogView> convertWorkLogList(List<WorkLog> collectionWorkLog) {
        String dateFormat = DateUtils.getLFDateFormat(applicationProperties);
        List<WorkLogView> listWorkLog = new ArrayList<WorkLogView>();
        for (WorkLog workLog : collectionWorkLog) {
            WorkLogView workLogView = new WorkLogView();
            workLogView.setId(Long.toString(workLog.getID()));
            if (workLog.getIssueKey() == null) {
                workLogView.setIssueKey(EMPTY);
            } else {
                workLogView.setIssueKey(workLog.getIssueKey());
            }

            if (workLog.getIssueName() == null) {
                workLogView.setIssueName(EMPTY);
            } else {
                workLogView.setIssueName(workLog.getIssueName());
            }

            if (workLog.getProjectId() == null) {
                workLogView.setProjectId(EMPTY);
            } else {
                workLogView.setProjectId(Long.toString(workLog.getProjectId()));
            }

            if (workLog.getProjectName() == null) {
                workLogView.setProjectName(EMPTY);
            } else {
                workLogView.setProjectName(workLog.getProjectName());
            }

            if (workLog.getUserKey() == null) {
                workLogView.setUserKey(EMPTY);
            } else {
                workLogView.setUserKey(workLog.getUserKey());
            }

            if (workLog.getUserName() == null) {
                workLogView.setUserName(EMPTY);
            } else {
                workLogView.setUserName(workLog.getUserName());
            }

            if (workLog.getDesc() == null) {
                workLogView.setDesc(EMPTY);
            } else {
                workLogView.setDesc(workLog.getDesc());
            }

            if (workLog.getComment() == null) {
                workLogView.setComment(EMPTY);
            } else {
                workLogView.setComment(workLog.getComment());
            }

            if (workLog.getStartDate() == null) {
                workLogView.setStartDate(EMPTY);
            } else {
                workLogView.setStartDate(DateUtils.convertDateToString(
                        workLog.getStartDate(), dateFormat));
            }
            if (workLog.getCreateDate() == null) {
                workLogView.setCreateDate(EMPTY);
            } else {
                workLogView.setCreateDate(DateUtils.convertDateToString(
                        workLog.getCreateDate(), dateFormat));
            }
            if (workLog.getApprovalDate() == null) {
                workLogView.setApprovalDate(EMPTY);
            } else {
                workLogView.setApprovalDate(DateUtils.convertDateToString(
                        workLog.getApprovalDate(), dateFormat));
            }

            if (workLog.getTypeOfWork() == null) {
                workLogView.setTypeOfWork(EMPTY);
            } else {
                workLogView.setTypeOfWork(workLog.getTypeOfWork());
            }
            if (workLog.getStatus() == null) {
                workLogView.setStatus(EMPTY);
            } else {
                workLogView.setStatus(workLog.getStatus());
            }
            if (workLog.getWorkPerDay() == null) {
                workLogView.setWorkPerDay("0");
            } else {
                float workPerDayFloat = CommonUtil.removeFraction(
                        Float.valueOf(workLog.getWorkPerDay()), 2);
                workLogView.setWorkPerDay(CommonUtil
                        .convertFloatToStringRound(workPerDayFloat));
            }
            // FIXME
            // calculate time == total hours in worklog
            float workPerDayFloat = CommonUtil.removeFraction(
                    Float.valueOf(workLog.getWorkPerDay()), 2);
            workLogView.setTime(workPerDayFloat);

            // calculate logged
            Float logged = (float) 0;
            for (WorkLogView w : listWorkLog) {
                logged += w.getTime();
            }
            workLogView.setLogged(logged);

            listWorkLog.add(workLogView);
        }

        return listWorkLog;
    }

    @Override
    public double getActualWorkLogByIssue(Issue issue) {
        return getActualForWorklogList(getWorkLogByIssue(issue));
    }

    /**
     * Get list worklog for check workperday limit 24h
     */
    @Override
    public List<WorkLog> getWorkLogCheckWorkPerDay(Date startDate,
                                                   Date endDate, String userKey) {
        List<Object> listParam = new ArrayList<Object>();
        startDate = DateUtils.standardizeDate(startDate, 0, 0, 0, 0);
        endDate = DateUtils.standardizeDate(endDate, 23, 59, 59, 999);
        String querryString = "USER_KEY = ? AND (START_DATE BETWEEN ? AND ?)";
        listParam.add(userKey);
        listParam.add(startDate);
        listParam.add(endDate);
        Query query = Query.select().where(querryString, listParam.toArray());
        return newArrayList(ao.find(WorkLog.class, query));
    }

    @Override
    public List<WorkLog> getWorkLogByIssue(String issueKey) {
        WorkLog[] listWorkLog = ao.find(WorkLog.class,
                Query.select().where("ISSUE_KEY = ?", issueKey));
        return newArrayList(listWorkLog);
    }

    @Override
    public List<WorkLog> getWorkLogByUserKey(long projectId, String userKey,
                                             int month, int year) {
        WorkLog[] listWorkLog = ao
                .find(WorkLog.class,
                        Query.select()
                                .order("ISSUE_KEY ASC")
                                .where("USER_KEY = ? AND PROJECT_ID = ? AND MONTH(START_DATE) = ? AND YEAR(START_DATE) = ?",
                                        userKey, projectId, month, year));

        return newArrayList(listWorkLog);
    }

    @Override
    public List<WorkLog> getWorkLogByUserKey(long projectId, String userKey,
                                             int month, int year, String type,
                                             String componentId, String versionId, String status,
                                             String issueType, String issueStatus) {
        try {
            WorkLog[] listWorkLog = ao
                    .find(WorkLog.class, queryWorkLogForUser(projectId, userKey, month, year, type, componentId, versionId, status, issueType, issueStatus));
            return newArrayList(listWorkLog);
        } catch (Exception e) {
            return newArrayList(new ArrayList<WorkLog>());
        }
    }

    @Override
    public List<WorkLog> getWorkLogByUserKey(Long projectId, String userKey,
                                             Date startDate, Date endDate) {
        startDate = DateUtils.standardizeDate(startDate, 0, 0, 0, 0);
        endDate = DateUtils.standardizeDate(endDate, 23, 59, 59, 999);
        WorkLog[] listWorkLog = ao
                .find(WorkLog.class,
                        Query.select()
                                .order("ISSUE_KEY ASC")
                                .where("USER_KEY = ? AND PROJECT_ID = ? AND START_DATE >= ? AND START_DATE <= ?",
                                        userKey, projectId, startDate, endDate));

        return newArrayList(listWorkLog);
    }

    @Override
    public List<WorkLog> getWorkLogByProject(long projectId, int month, int year) {
        WorkLog[] listWorkLog = ao
                .find(WorkLog.class,
                        Query.select()
                                .order("USER_KEY ASC")
                                .where("PROJECT_ID = ? AND MONTH(START_DATE) = ? AND YEAR(START_DATE) = ?",
                                        projectId, month, year));

        return newArrayList(listWorkLog);
    }

    @Override
    public List<WorkLog> getWorkLogNotRejectByProject(long projectId,
                                                      int month, int year) {
        WorkLog[] listWorkLog = ao
                .find(WorkLog.class,
                        Query.select()
                                .order("USER_KEY ASC")
                                .where("PROJECT_ID = ? AND MONTH(START_DATE) = ? AND YEAR(START_DATE) = ?  AND STATUS IN ('Pending','Approved')",
                                        projectId, month, year));

        return newArrayList(listWorkLog);
    }

    Function<WorkLog, DateUserKeyGroup> sameDayGroupFunc = new Function<WorkLog, DateUserKeyGroup>() {
        @Override
        public DateUserKeyGroup apply(WorkLog w) {
            return new DateUserKeyGroup(w);
        }
    };

    Function<WorkLog, IssueKeyGroup> sameIssueKeyFunc = new Function<WorkLog, IssueKeyGroup>() {
        @Override
        public IssueKeyGroup apply(WorkLog w) {
            return new IssueKeyGroup(w);
        }
    };

    Function<WorkLog, String> sameDayGroupStrFunc = new Function<WorkLog, String>() {
        @Override
        public String apply(WorkLog w) {
            return w.getUserKey()
                    + DateUtils.convertDateToString(w.getStartDate());
        }
    };

    Function<WorkLog, String> sameIssueKeyStrFunc = new Function<WorkLog, String>() {
        @Override
        public String apply(WorkLog w) {
            return w.getIssueKey()
                    + DateUtils.convertDateToString(w.getStartDate());
        }
    };
    Function<WorkLogGantt, String> sameIssueKey = new Function<WorkLogGantt, String>() {
        @Override
        public String apply(WorkLogGantt w) {
            return w.getIssueKey();
        }
    };

    @Override
    public List<WorkLogGantt> convertWorkLogListToGantt(
            List<WorkLog> listWorkLog) {
        List<WorkLogGantt> listWorkLogGantt = new ArrayList<WorkLogGantt>();
        ObjectMapper objectMapper = new ObjectMapper();

        Multimap<String, WorkLog> groupedWorklogs = Multimaps.index(
                listWorkLog, sameDayGroupStrFunc);
        Map<String, Collection<WorkLog>> mappedWorklogs = groupedWorklogs
                .asMap();
        for (String group : mappedWorklogs.keySet()) {
            WorkLogGantt gantt = new WorkLogGantt();
            List<WorkLog> workLogOfGantt = newArrayList(mappedWorklogs
                    .get(group));
            List<WorkLogView> listWorkLogView = convertWorkLogList(workLogOfGantt);
            gantt.setUserKey(workLogOfGantt.get(0).getUserKey());
            gantt.setStartDate(workLogOfGantt.get(0).getStartDate());
            float totalWorkPerDay = 0;

            for (WorkLog w : workLogOfGantt) {
                if (!StaticTokenUtil.REJECT.equals(w.getStatus())) {
                    totalWorkPerDay += Float.valueOf(w.getWorkPerDay());
                }
            }

            totalWorkPerDay = CommonUtil.roundFloat(totalWorkPerDay);
            gantt.setWorkPerDay(CommonUtil
                    .convertFloatToStringRound(totalWorkPerDay));
            try {
                String json = objectMapper.writeValueAsString(listWorkLogView);
                gantt.setJson(json);
            } catch (JsonGenerationException e) {
            } catch (JsonMappingException e) {
            } catch (IOException e) {
            }
            listWorkLogGantt.add(gantt);
        }
        return listWorkLogGantt;
    }

    @Override
    public List<WorkLogGantt> convertWorkLogListToGanttMyWorklog(
            List<WorkLog> listWorkLog) {
        List<WorkLogGantt> listWorkLogGantt = new ArrayList<WorkLogGantt>();

        ObjectMapper objectMapper = new ObjectMapper();

        Multimap<String, WorkLog> groupedWorklogs = Multimaps.index(
                listWorkLog, sameIssueKeyStrFunc);
        Map<String, Collection<WorkLog>> mappedWorklogs = groupedWorklogs
                .asMap();
        for (String group : mappedWorklogs.keySet()) {
            WorkLogGantt gantt = new WorkLogGantt();
            List<WorkLog> workLogOfGantt = newArrayList(mappedWorklogs
                    .get(group));
            List<WorkLogView> listWorkLogView = convertWorkLogList(workLogOfGantt);
            gantt.setIssueKey(workLogOfGantt.get(0).getIssueKey());
            gantt.setStartDate(workLogOfGantt.get(0).getStartDate());
            gantt.setIssueName(workLogOfGantt.get(0).getIssueName());
            float totalWorkPerDay = 0;
            for (WorkLog w : workLogOfGantt) {
                if (!WorkLogConstant.STATUS_REJECTED.equalsIgnoreCase(w
                        .getStatus())) {
                    totalWorkPerDay += Float.valueOf(w.getWorkPerDay());
                }
            }
            totalWorkPerDay = CommonUtil.roundFloat(totalWorkPerDay);
            gantt.setWorkPerDay(CommonUtil
                    .convertFloatToStringRound(totalWorkPerDay));
            try {
                String json = objectMapper.writeValueAsString(listWorkLogView);
                gantt.setJson(json);
            } catch (JsonGenerationException e) {
            } catch (JsonMappingException e) {
            } catch (IOException e) {
            }
            listWorkLogGantt.add(gantt);
        }
        return listWorkLogGantt;
    }

    @Override
    public List<WorkLogGantt> getIssueNoneWorkLogForGantt(
            List<WorkLogGantt> listWorkLogGantt, Long projectId) {
        List<String> listIssueKeyNoneWorkLog = new ArrayList<String>();
        List<WorkLogGantt> listWorkLogGanttResult = new ArrayList<WorkLogGantt>();
        try {
            List<Issue> listIssue = issueManager.getIssueObjects(issueManager
                    .getIssueIdsForProject(projectId));
            for (Issue issue : listIssue) {
                listIssueKeyNoneWorkLog.add(issue.getKey());
            }
            Multimap<String, WorkLogGantt> groupedWorklogGantts = Multimaps
                    .index(listWorkLogGantt, sameIssueKey);
            Map<String, Collection<WorkLogGantt>> mappedWorklogGantts = groupedWorklogGantts
                    .asMap();
            List<String> listIssueKeyWorkLog = new ArrayList<String>();
            for (String group : mappedWorklogGantts.keySet()) {
                listIssueKeyWorkLog.add(group);
                if (listIssueKeyNoneWorkLog.contains(group)) {
                    listIssueKeyNoneWorkLog.remove(group);
                }
            }
            for (Issue issue : listIssue) {
                if (listIssueKeyNoneWorkLog.contains(issue.getKey())
                        && (isAssigee(userManagerSAL.getRemoteUsername(), issue) || isWatcher(
                        userManagerSAL.getRemoteUsername(),
                        issueManager, issue))) {
                    WorkLogGantt workLogGantt = new WorkLogGantt();
                    workLogGantt.setIssueKey(issue.getKey());
                    workLogGantt.setIssueName(issue.getSummary());
                    listWorkLogGanttResult.add(workLogGantt);
                }
            }
        } catch (GenericEntityException e) {
        }
        return listWorkLogGanttResult;
    }

    @Override
    public List<WorkLogGantt> getIssueNoneWorkLogForGantt(
            List<WorkLogGantt> listWorkLogGantt, Long projectId, String issueStatus) {
        List<String> listIssueKeyNoneWorkLog = new ArrayList<String>();
        List<WorkLogGantt> listWorkLogGanttResult = new ArrayList<WorkLogGantt>();
        try {
            List<Issue> listIssue = issueManager.getIssueObjects(issueManager
                    .getIssueIdsForProject(projectId));
            if (issueStatus.equals("false")) {
                for (int i = 0; i < listIssue.size(); i++) {
                    Issue issue = listIssue.get(i);
                    if (issue.getStatusObject().getName().equals("Closed")
                            || issue.getStatusObject().getName().equals("Cancelled")) {
                        listIssue.remove(i);
                        i--;
                    } else {
                        listIssueKeyNoneWorkLog.add(issue.getKey());
                    }
                }
            } else {
                for (Issue issue : listIssue) {
                    listIssueKeyNoneWorkLog.add(issue.getKey());
                }
            }
            Multimap<String, WorkLogGantt> groupedWorklogGantts = Multimaps
                    .index(listWorkLogGantt, sameIssueKey);
            Map<String, Collection<WorkLogGantt>> mappedWorklogGantts = groupedWorklogGantts
                    .asMap();
            List<String> listIssueKeyWorkLog = new ArrayList<String>();
            for (String group : mappedWorklogGantts.keySet()) {
                listIssueKeyWorkLog.add(group);
                if (listIssueKeyNoneWorkLog.contains(group)) {
                    listIssueKeyNoneWorkLog.remove(group);
                }
            }
            for (Issue issue : listIssue) {
                if (listIssueKeyNoneWorkLog.contains(issue.getKey())
                        && (isAssigee(userManagerSAL.getRemoteUsername(), issue) || isWatcher(
                        userManagerSAL.getRemoteUsername(),
                        issueManager, issue))) {
                    WorkLogGantt workLogGantt = new WorkLogGantt();
                    workLogGantt.setIssueKey(issue.getKey());
                    workLogGantt.setIssueName(issue.getSummary());
                    listWorkLogGanttResult.add(workLogGantt);
                }
            }
        } catch (GenericEntityException e) {
        }
        return listWorkLogGanttResult;
    }

    @Override
    public double getActualEffortByVersion(long versionId, long projectId) {
        double actualVersion = 0;
        try {
            List<Issue> listIssueByVersion = issueManager
                    .getIssueObjects(versionManager
                            .getIssueIdsWithAffectsVersion(versionManager
                                    .getVersion(versionId)));
            List<WorkLog> listWorkLog = getWorkLogApprovedByIssueKeys(listIssueByVersion);
            for (WorkLog workLog : listWorkLog) {
                actualVersion += Double.valueOf(workLog.getWorkPerDay());
            }
        } catch (NullPointerException e) {
        }
        return actualVersion / DateUtils.HOUR_PER_DAY;
    }

    @Override
    public float convertJiraDurationToHours(String durarionStr) {
        Locale locale = ActionContext.getLocale();
        float hour = 0;
        JiraDurationUtils durationUtils = new JiraDurationUtils(
                applicationProperties, jiraAuthenticationContext,
                timeTrackingConfiguration, eventPublisher, beanFactory,
                cacheManager);

        try {
            hour = durationUtils.parseDuration(durarionStr, locale)
                    / DateUtils.SEC_PER_HOUR_FLOAT;
        } catch (InvalidDurationException e) {
            hour = 0;
        }
        return hour;
    }

    @Override
    public List<WorkLogGantt> getGanttForQA(String[] userKeys, Date startDate,
                                            Date endDate, List<ProjectConfigInfo> projects) {
        List<WorkLog> listWorkLog = getWorkLogNotRejectedByUsersAndProject(
                userKeys, startDate, endDate, projects);
        return convertWorkLogListToGantt(listWorkLog);
    }

    @Override
    public List<WorkLogGantt> getGanttForQA(String[] userKeys, int month,
                                            int year, List<ProjectConfigInfo> projects) {
        List<WorkLog> listWorkLog = getWorkLogNotRejectedByUsersAndProject(
                userKeys, month, year, projects);
        return convertWorkLogListToGantt(listWorkLog);
    }

    @Override
    public List<WorkLogGantt> getGanttForQAByBGAndOu(String[] userKeys,
                                                     int month, int year) {
        List<WorkLog> listWorkLog = getWorkLogNotRejectedByUsersIgnoreCase(
                userKeys, month, year);
        return convertWorkLogListToGantt(listWorkLog);
    }

    @Override
    public List<WorkLogGantt> getGanttForQAByBGAndOuInDuration(
            String[] userKeys, Date startDate, Date endDate) {
        List<WorkLog> listWorkLog = getWorkLogNotRejectedByUsersInDuration(
                userKeys, startDate, endDate);
        return convertWorkLogListToGantt(listWorkLog);
    }

    @Override
    public List<WorkLogGantt> getGanttForProject(Long projectId,
                                                 Date startDate, Date endDate) {
        List<WorkLog> listWorkLog = getWorkLogApprovalAndPendingByProjectId(
                projectId, startDate, endDate);
        List<WorkLog> listWorkLogReturn = new ArrayList<WorkLog>();
        Project project = projectManager.getProjectObj(projectId);
        ApplicationUser currentUser = jiraAuthenticationContext.getUser();
        boolean checkPm = checkPM(project, currentUser);
        boolean checkQA = checkQA(project, currentUser);
        boolean checkTl = checkTeamLead(project, currentUser);
        if (checkPm || checkQA) {
            for (WorkLog workLog : listWorkLog) {
                listWorkLogReturn.add(workLog);
            }
        } else if (checkTl) {
            List<String> userKeys = new ArrayList<String>();
            userKeys = projectTeamServiceImpl.getUserByTeamLead(
                    project.getKey(), currentUser.getKey());
            for (WorkLog workLog : listWorkLog) {
                if (userKeys.contains(workLog.getUserKey().toLowerCase())) {
                    listWorkLogReturn.add(workLog);
                }
            }
        }
        return convertWorkLogListToGantt(listWorkLogReturn);
    }

    @Override
    public List<WorkLogGantt> getGanttForUser(String userKey, Long projectId,
                                              Date startDate, Date endDate) {
        List<WorkLog> listWorkLog = getWorkLogByUserKey(projectId, userKey,
                startDate, endDate);
        return convertWorkLogListToGanttMyWorklog(listWorkLog);
    }

    @Override
    public List<WorkLogGantt> getGanttForProject(Long projectId, Integer month,
                                                 Integer year) {
        List<WorkLog> listWorkLog = getWorkLogNotRejectByProject(projectId,
                month, year);
        List<WorkLog> listWorkLogReturn = new ArrayList<WorkLog>();
        ApplicationUser currentUser = jiraAuthenticationContext.getUser();
        Project project = projectManager.getProjectObj(projectId);
        boolean checkPm = checkPM(project, currentUser);
        boolean checkTl = checkTeamLead(project, currentUser);
        boolean checkQA = checkQA(project, currentUser);
        if (checkPm || checkQA) {
            for (WorkLog workLog : listWorkLog) {
                listWorkLogReturn.add(workLog);
            }
        } else if (checkTl) {
            List<String> userKeys = new ArrayList<String>();
            userKeys = projectTeamServiceImpl.getUserByTeamLead(
                    project.getKey(), currentUser.getKey());
            for (WorkLog workLog : listWorkLog) {
                if (userKeys.contains(workLog.getUserKey().toLowerCase())) {
                    listWorkLogReturn.add(workLog);
                }
            }
        }
        return convertWorkLogListToGantt(listWorkLogReturn);
    }

    @Override
    public List<WorkLogGantt> getGanttForUser(String userKey, Long projectId,
                                              Integer month, Integer year) {
        List<WorkLog> listWorkLog = getWorkLogByUserKey(projectId, userKey,
                month, year);
        List<WorkLogGantt> listWorkLogGantt = convertWorkLogListToGanttMyWorklog(listWorkLog);
        return listWorkLogGantt;
    }

    @Override
    public List<WorkLogGantt> getGanttForUser(String userKey, Long projectId,
                                              Integer month, Integer year, String type,
                                              String componentId, String versionId, String status,
                                              String issueType, String issueStatus) {
        List<WorkLog> listWorkLog = getWorkLogByUserKey(projectId, userKey,
                month, year, type, componentId, versionId, status, issueType, issueStatus);
        List<WorkLogGantt> listWorkLogGantt = convertWorkLogListToGanttMyWorklog(listWorkLog);
        return listWorkLogGantt;
    }

    @Override
    public Map<Long, Double> getActualWorkLogByProject(
            List<Long> listProjectId, Date startDate, Date endDate) {
        Map<Long, Double> mapResult = new HashMap<Long, Double>();
        for (Long projectId : listProjectId) {
            mapResult.put(projectId,
                    getActualWorkLogByProject(projectId, startDate, endDate));
        }
        return mapResult;
    }

    @Override
    public boolean deleteWorkLogByIssue(String issueKey) {
        List<WorkLog> listWorkLog = getWorkLogByIssue(issueKey);
        for (WorkLog workLog : listWorkLog) {
            deleteWorkLog(String.valueOf(workLog.getID()));
        }
        return true;
    }

    @Override
    public boolean checkDurationProject(long projectId, Date startDate,
                                        Date endDate) {
        ProjectConfigInfo projectConfigInfo = projectConfigInfoService
                .getProjectInformationById(projectId);
        Date startDateProject = projectConfigInfo.getStartDate();
        Date endDateProject = projectConfigInfo.getEndDate();
        if (startDate == null || endDate == null || startDateProject == null
                || endDateProject == null) {
            return true;
        } else {
            if (DateUtils.betweenDay(startDate, startDateProject,
                    endDateProject)
                    && DateUtils.betweenDay(endDate, startDateProject,
                    endDateProject)) {
                return true;
            } else {
                return false;
            }
        }
    }

    @Override
    public String getMinMaxWorkLogDateByComponent(long componentId,
                                                  long projectId) {
        List<Issue> issues = new ArrayList<Issue>();
        try {
            Collection<Long> issueIdsOfComponent = projectComponentManager
                    .getIssueIdsWithComponent(projectComponentManager
                            .getProjectComponent(componentId));
            for (Long id : issueIdsOfComponent) {
                issues.add(issueManager.getIssueObject(id));
            }
        } catch (Exception e) {
        }
        List<WorkLog> listWorkLog = getWorkLogByIssueKeys(issues);
        if (listWorkLog.size() > 0) {
            String result = listWorkLog.get(listWorkLog.size() - 1)
                    .getStartDate().getTime()
                    + "," + listWorkLog.get(0).getStartDate().getTime();
            return result;
        } else {
            return "null,null";
        }
    }

    @SuppressWarnings("deprecation")
    @Override
    public boolean isManager(Project currentProject) {
        boolean isPM = false;
        boolean isQA = false;
        boolean isTeamlead = false;
        JiraAuthenticationContext jiraAuthenticationContext = ComponentManager
                .getComponentInstanceOfType(JiraAuthenticationContext.class);
        ProjectRoleManager projectRoleManager = ComponentManager
                .getComponentInstanceOfType(ProjectRoleManager.class);
        ApplicationUser user = jiraAuthenticationContext.getUser();
        try {
            ProjectRole PMProjectRole = projectRoleManager
                    .getProjectRole(StaticTokenUtil.PROJECT_MANAGER);
            isPM = projectRoleManager.isUserInProjectRole(user, PMProjectRole,
                    currentProject);
        } catch (NullPointerException e) {
            isPM = false;
        }
        try {
            ProjectRole QAProjectRole = projectRoleManager
                    .getProjectRole(StaticTokenUtil.QUALITY_ASSURANCE);
            isQA = projectRoleManager.isUserInProjectRole(user, QAProjectRole,
                    currentProject);
        } catch (NullPointerException e) {
            isQA = false;
        }
        try {
            ProjectRole TLProjectRole = projectRoleManager
                    .getProjectRole(StaticTokenUtil.TEAM_LEADERS);
            isTeamlead = projectRoleManager.isUserInProjectRole(user,
                    TLProjectRole, currentProject);
        } catch (NullPointerException e) {
            isTeamlead = false;
        }
        return (isPM || isQA || isTeamlead);
    }

    @SuppressWarnings("deprecation")
    @Override
    public boolean isProjectManagerOrTeamLeader(Project currentProject) {
        boolean checkPM = false;
        boolean checkTL = false;

        JiraAuthenticationContext jiraAuthenticationContext = ComponentManager
                .getComponentInstanceOfType(JiraAuthenticationContext.class);
        ProjectRoleManager projectRoleManager = ComponentManager
                .getComponentInstanceOfType(ProjectRoleManager.class);
        ApplicationUser user = jiraAuthenticationContext.getUser();
        try {
            ProjectRole PMProjectRole = projectRoleManager
                    .getProjectRole(StaticTokenUtil.PROJECT_MANAGER);
            checkPM = projectRoleManager.isUserInProjectRole(user,
                    PMProjectRole, currentProject);
        } catch (Exception e) {
            checkPM = false;
        }
        try {
            ProjectRole TLProjectRole = projectRoleManager
                    .getProjectRole(StaticTokenUtil.TEAM_LEADERS);

            checkTL = projectRoleManager.isUserInProjectRole(user,
                    TLProjectRole, currentProject);
        } catch (Exception e) {
            checkTL = false;
        }
        return (checkPM || checkTL);
    }

    @SuppressWarnings("deprecation")
    @Override
    public boolean isProjectManager(Project currentProject) {
        try {
            JiraAuthenticationContext jiraAuthenticationContext = ComponentManager
                    .getComponentInstanceOfType(JiraAuthenticationContext.class);
            ProjectRoleManager projectRoleManager = ComponentManager
                    .getComponentInstanceOfType(ProjectRoleManager.class);
            ApplicationUser user = jiraAuthenticationContext.getUser();
            ProjectRole PMProjectRole = projectRoleManager
                    .getProjectRole(StaticTokenUtil.PROJECT_MANAGER);
            boolean checkPM = projectRoleManager.isUserInProjectRole(user,
                    PMProjectRole, currentProject);
            return checkPM;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public boolean isAssigee(String userKey, Issue issue) {
        if (!userKey.isEmpty()) {
            if (issue.getAssignee() != null) {
                if (issue.getAssignee().getName() != null) {
                    return userKey.equals(issue.getAssignee().getName());
                }
            }
            return false;
        } else {
            return false;
        }
    }

    @Override
    public boolean isWatcher(String userKey, IssueManager issueManager,
                             Issue issue) {
        if (!userKey.isEmpty()) {
            List<ApplicationUser> listUser = issueManager.getWatchersFor(issue);
            if (listUser != null) {
                for (ApplicationUser user : listUser) {
                    if (userKey.equals(user.getName())) {
                        return true;
                    }
                }
            }
            return false;
        } else {
            return false;
        }
    }

    @Override
    public boolean isIssueWorkLog(Issue issue) {
        String issueType = issue.getIssueTypeObject().getName();
        String issueStatus = issue.getStatusObject().getName();
        if (issueType.equals(IssueTypeAndStatus.TYPE_LESSON_LEARNT)
                || issueType
                .equals(IssueTypeAndStatus.TYPE_REQUIREMENT_MANAGEMENT)
                || issueType
                .equals(IssueTypeAndStatus.TYPE_TAILORING_DEVIATION)) {
            return true;
        }

        if (issueStatus.equals(IssueTypeAndStatus.STATUS_CANCELED)
                || issueStatus.equals(IssueTypeAndStatus.STATUS_ACCEPTED)
                || issueStatus.equals(IssueTypeAndStatus.STATUS_CLOSED)) {
            return true;
        }
        return false;
    }

    @Override
    public boolean checkProjectPending(long projectId) {
        ProjectConfigInfo projectInfor = projectConfigInfoService
                .getProjectInformationById(projectId);
        if ("Pending".equalsIgnoreCase(projectInfor.getProjectStatus())) {
            return true;
        }
        return false;
    }

    @Override
    public void sendEmail(String to, String subject, String body) {
        SMTPMailServer mailServer = MailFactory.getServerManager()
                .getDefaultSMTPMailServer();
        Email email = new Email(to);
        email.setFrom(mailServer.getDefaultFrom());
        email.setSubject(subject);
        email.setMimeType("text/html");
        email.setBody(body);
        SingleMailQueueItem item = new SingleMailQueueItem(email);
        ComponentAccessor.getMailQueue().addItem(item);
    }

    @Override
    public void updateIssueWorklogByChange(IssueEvent issueEvent) {
        // TODO Auto-generated method stub
    }

    public EventPublisher getEventPublisher() {
        return eventPublisher;
    }

    public void setEventPublisher(EventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    public Float getTempFloatValue() {
        return tempFloatValue;
    }

    public void setTempFloatValue(Float tempFloatValue) {
        this.tempFloatValue = tempFloatValue;
    }

    public Map<Long, List<WorkLog>> createMapIssueToComponentByProjectId(
            Long projectId) {
        Map<Long, List<Issue>> issueForComponentMapping = new HashMap<Long, List<Issue>>();
        Map<Long, List<WorkLog>> worklogForComponentMapping = new HashMap<Long, List<WorkLog>>();
        List<ProjectComponent> projectComponentList = (List<ProjectComponent>) projectComponentManager
                .findAllForProject(projectId);
        Long keyComponentId;
        Collection<Long> issueIdsOfComponent;
        for (ProjectComponent projectComponent : projectComponentList) {
            keyComponentId = projectComponent.getId();
            issueForComponentMapping
                    .put(keyComponentId, new ArrayList<Issue>());
            issueIdsOfComponent = projectComponentManager
                    .getIssueIdsWithComponent(projectComponent);
            for (Long id : issueIdsOfComponent) {
                issueForComponentMapping.get(keyComponentId).add(
                        issueManager.getIssueObject(id));
            }
            worklogForComponentMapping.put(keyComponentId,
                    new ArrayList<WorkLog>());
        }
        try {
            for (Long key : worklogForComponentMapping.keySet()) {
                List<Issue> issues = issueForComponentMapping.get(key);
                List<WorkLog> worklogForIssue = getWorkLogApprovedByIssueKeys(issues);
                worklogForComponentMapping.get(key).addAll(worklogForIssue);
            }
        } catch (Exception e) {
        }
        return worklogForComponentMapping;
    }

    @Override
    public Map<Long, List<Date>> createMapMinMaxDateWorkLogToComponentByProjectId(
            long projectId) {
        Map<Long, List<WorkLog>> worklogForComponentMapping = createMapIssueToComponentByProjectId(projectId);
        Map<Long, List<Date>> minMaxDateWorkLogForComponentMapping = new HashMap<Long, List<Date>>();
        List<ProjectComponent> projectComponentList = (List<ProjectComponent>) projectComponentManager
                .findAllForProject(projectId);
        for (ProjectComponent projectComponent : projectComponentList) {
            minMaxDateWorkLogForComponentMapping.put(projectComponent.getId(),
                    new ArrayList<Date>());
        }
        for (Long key : worklogForComponentMapping.keySet()) {
            Date minDate = null;
            Date maxDate = null;
            List<WorkLog> listWorkLog = worklogForComponentMapping.get(key);
            if (!listWorkLog.isEmpty()) {
                minDate = listWorkLog.get(0).getStartDate();
                maxDate = listWorkLog.get(0).getStartDate();
                for (WorkLog workLog : listWorkLog) {
                    if (minDate.after(workLog.getStartDate())) {
                        minDate = workLog.getStartDate();
                    }

                    if (maxDate.before(workLog.getStartDate())) {
                        maxDate = workLog.getStartDate();
                    }
                }
            }

            minMaxDateWorkLogForComponentMapping.get(key).add(minDate);
            minMaxDateWorkLogForComponentMapping.get(key).add(maxDate);
        }
        return minMaxDateWorkLogForComponentMapping;
    }

    public Map<Long, List<WorkLog>> createMapIssueToVersionByProjectId(
            Long projectId) {
        Map<Long, List<Issue>> issueForVersionMapping = new HashMap<Long, List<Issue>>();
        Map<Long, List<WorkLog>> worklogForVersionMapping = new HashMap<Long, List<WorkLog>>();
        List<Version> projectVersionList = (List<Version>) versionManager
                .getVersions(projectId);
        Long versionIdKey;
        Collection<Long> issueIdsOfVersion;
        for (Version version : projectVersionList) {
            versionIdKey = version.getId();
            issueForVersionMapping.put(versionIdKey, new ArrayList<Issue>());
            issueIdsOfVersion = versionManager
                    .getIssueIdsWithAffectsVersion(version);
            for (Long id : issueIdsOfVersion) {
                issueForVersionMapping.get(versionIdKey).add(
                        issueManager.getIssueObject(id));
            }
            worklogForVersionMapping
                    .put(versionIdKey, new ArrayList<WorkLog>());
        }
        try {
            for (Long key : worklogForVersionMapping.keySet()) {
                List<Issue> issues = issueForVersionMapping.get(key);
                List<WorkLog> worklogForIssue = getWorkLogApprovedByIssueKeys(issues);
                worklogForVersionMapping.get(key).addAll(worklogForIssue);
            }
        } catch (Exception e) {
        }

        return worklogForVersionMapping;
    }

    @Override
    public Map<Long, List<Date>> createMapMinMaxDateWorkLogToVersionByProjectId(
            long projectId) {
        Map<Long, List<WorkLog>> worklogForVersionMapping = createMapIssueToVersionByProjectId(projectId);
        Map<Long, List<Date>> minMaxDateWorkLogForVersionMapping = new HashMap<Long, List<Date>>();
        List<Version> projectVersionList = (List<Version>) versionManager
                .getVersions(projectId);
        for (Version projectVersion : projectVersionList) {
            minMaxDateWorkLogForVersionMapping.put(projectVersion.getId(),
                    new ArrayList<Date>());
        }
        for (Long key : worklogForVersionMapping.keySet()) {
            Date minDate = null;
            Date maxDate = null;
            List<WorkLog> listWorkLog = worklogForVersionMapping.get(key);
            if (!listWorkLog.isEmpty()) {
                minDate = listWorkLog.get(0).getStartDate();
                maxDate = listWorkLog.get(0).getStartDate();
                for (WorkLog workLog : listWorkLog) {
                    if (minDate.after(workLog.getStartDate())) {
                        minDate = workLog.getStartDate();
                    }

                    if (maxDate.before(workLog.getStartDate())) {
                        maxDate = workLog.getStartDate();
                    }
                }
            }

            minMaxDateWorkLogForVersionMapping.get(key).add(minDate);
            minMaxDateWorkLogForVersionMapping.get(key).add(maxDate);
        }
        return minMaxDateWorkLogForVersionMapping;
    }

    @Override
    public List<WorkLog> getWorkLogForWorkLogDetails(List<Long> listProjectId,
                                                     List<String> listIssueKey, List<String> listStatus, Date startDate,
                                                     Date endDate) {

        // get querry for issue
        String issueQuerry;
        issueQuerry = "(";
        int i = 0;
        for (String issueKey : listIssueKey) {
            i++;
            if (listIssueKey.size() == i) {
                issueQuerry += "'" + issueKey + "'";
            } else {
                issueQuerry += "'" + issueKey + "'" + ',';
            }
        }
        issueQuerry += ")";

        // get querry for project
        String projectQuerry;
        projectQuerry = "(";
        i = 0;
        for (long projectId : listProjectId) {
            i++;
            if (listProjectId.size() == i) {
                projectQuerry += "'" + projectId + "'";
            } else {
                projectQuerry += "'" + projectId + "'" + ',';
            }
        }
        projectQuerry += ")";

        // get querry for status
        String statusQuerry;
        statusQuerry = "(";
        i = 0;
        for (String status : listStatus) {
            i++;
            if (ObjectUtils.isNotEmpty(status)
                    && !SELECT_ALL.equalsIgnoreCase(status)) {
                if (listStatus.size() == i) {
                    statusQuerry += "'" + status + "'";
                } else {
                    statusQuerry += "'" + status + "'" + ',';
                }
            }
        }
        statusQuerry += ")";

        List<Object> listParam = new ArrayList<Object>();
        String querry = "START_DATE >= ? AND START_DATE <= ?";
        listParam.add(startDate);
        listParam.add(endDate);
        if (!"()".equals(issueQuerry)) {
            querry += "AND ISSUE_KEY IN " + issueQuerry;
        }
        if (!"()".equals(projectQuerry)) {
            querry += "AND PROJECT_ID IN " + projectQuerry;
        }
        if (!"()".equals(statusQuerry)) {
            querry += "AND STATUS IN " + statusQuerry;
        }
        WorkLog[] listWorkLog = ao.find(WorkLog.class,
                Query.select().where(querry, listParam.toArray()));
        return newArrayList(listWorkLog);
    }

    @Override
    public double getOriginalEstimateOfIssue(Issue issue) {
        try {
            CustomField originalEstimateField = getCustomFieldByName(StaticTokenUtil.Original);
            String originalText = originalEstimateField.getValue(issue)
                    .toString();
            return CustomFieldUtil.convertOriginalEstimateStringToHours(
                    originalText, pluginSettings);
        } catch (NullPointerException e) {
            return 0;
        }
    }

    @Override
    public Date getStartDateOfIssue(Issue issue) {
        try {
            CustomField v = getCustomFieldByName(StaticTokenUtil.StartDate);
            Date startDateText = (Date) v.getValue(issue);
            return startDateText;
        } catch (NullPointerException e) {
            return null;
        }
    }

    public CustomField getCustomFieldByName(String name) {
        try {
            return customFieldManager.getCustomFieldObjectByName(name);
        } catch (NullPointerException e) {
            return null;
        }

    }

    @Override
    public boolean isProjectManagerOrTeamLeader(Project currentProject,
                                                String userKey) {
        ApplicationUser currentUser = jiraAuthenticationContext.getUser();
        if (isProjectManager(currentProject, currentUser)) {
            return true;
        } else {
            return checkTeamLead(currentProject, currentUser);
        }

    }

    @Override
    public double getActualWorkLogByIssueAndWorkLog(Issue issue, WorkLog worklog) {
        List<WorkLog> list = getWorkLogByIssue(issue);
        double sum = 0;
        for (WorkLog w : list) {
            if (!w.getStartDate().after(worklog.getStartDate())) {
                sum += Double.valueOf(w.getWorkPerDay());
            }
        }
        return sum;
    }

    @Override
    public List<WorkLog> getResultForResourceTotalReport(
            ProjectConfigInfoBean project, Date fromDate, Date toDate,
            List<String> users) {
        Calendar cal = Calendar.getInstance();
        cal.set(YEAR_LOWER_RANGE, 1, 1);
        Date minDate = cal.getTime();
        cal.set(YEAR_UPPER_RANGE, 1, 1);
        Date maxDate = cal.getTime();
        if (fromDate == null) {
            fromDate = minDate;
        }
        if (toDate == null) {
            toDate = maxDate;
        }
        fromDate = DateUtils.standardizeDate(fromDate, 0, 0, 0, 0);
        toDate = DateUtils.standardizeDate(toDate, 23, 59, 59, 999);
        String userQuery = "(";
        int i = 0;
        if (users != null && users.size() > 0) {
            for (String user : users) {
                i++;
                if (users.size() == i) {
                    userQuery += "'" + user.toLowerCase() + "'";
                } else {
                    userQuery += "'" + user.toLowerCase() + "'" + ',';
                }
            }
        }
        userQuery += ")";

        List<Object> listParam = new ArrayList<Object>();

        String querry = "START_DATE >= ? AND START_DATE <= ? AND PROJECT_ID = ?";
        if (!"()".equals(userQuery)) {
            querry += " AND LOWER(USER_KEY) IN " + userQuery;
        }
        listParam.add(fromDate);
        listParam.add(toDate);
        listParam.add(project.getId());
        List<WorkLog> worklogs = newArrayList(ao.find(WorkLog.class, Query
                .select().where(querry, listParam.toArray())));
        return worklogs;
    }

    @Override
    public boolean checkIssueClose(String issueKey) {
        Issue issue = issueManager.getIssueByCurrentKey(issueKey);
        String issueStatus = issue.getStatusObject().getName();
        if (IssueTypeAndStatus.STATUS_CANCELED.equalsIgnoreCase(issueStatus)
                || IssueTypeAndStatus.STATUS_CLOSED
                .equalsIgnoreCase(issueStatus)
                || IssueTypeAndStatus.STATUS_ACCEPTED
                .equalsIgnoreCase(issueStatus)) {
            return true;
        }
        return false;
    }

    @Override
    public boolean checkRequiredDefectResolved(String issueKey,
                                               String[] customFieldNames) {
        Issue issue = issueManager.getIssueObject(issueKey);
        for (String customFieldName : customFieldNames) {
            try {
                CustomField customField = getCustomFieldByName(customFieldName);
                if (!ObjectUtils.isNotEmpty(customField.getValue(issue)
                        .toString())) {
                    return false;
                }
            } catch (NullPointerException e) {
                e.printStackTrace();
                return false;
            }
        }
        return true;
    }

    @Override
    public void deleteWorkLogByProjectId(long projectId) {
        WorkLog[] workLogs = ao.find(WorkLog.class,
                Query.select().where("PROJECT_ID = ?", projectId));
        ApplicationUser currentUser = jiraAuthenticationContext.getUser();
        for (WorkLog workLog : workLogs) {
            try {
                workLogHistoryService.createWorkLogHistory(
                        currentUser.getUsername(), workLog.getIssueKey(),
                        DELETE_WORKLOG, workLog);
                ao.delete(workLog);
            } catch (Exception e) {
            }
        }

    }

    @Deprecated
    @Override
    public boolean checkPM(Project currentProject) {
        ApplicationUser currentUser = jiraAuthenticationContext.getUser();
        ProjectRole pmProjectRole = projectRoleManager
                .getProjectRole(StaticTokenUtil.PROJECT_MANAGER);
        return projectRoleManager.isUserInProjectRole(currentUser,
                pmProjectRole, currentProject);
    }

    @Override
    public boolean checkPM(Project currentProject, ApplicationUser currentUser) {
        ProjectRole pmProjectRole = projectRoleManager
                .getProjectRole(StaticTokenUtil.PROJECT_MANAGER);
        return projectRoleManager.isUserInProjectRole(currentUser,
                pmProjectRole, currentProject);
    }

    @Override
    public boolean isProjectManager(Project project, ApplicationUser user) {
        return isUserInRoleExcludingDefaultGroup(project, user,
                StaticTokenUtil.PROJECT_MANAGER);
    }

    @Deprecated
    @Override
    public boolean checkTL(Project currentProject) {
        ApplicationUser currentUser = jiraAuthenticationContext.getUser();
        try {
            ProjectRole teamLeadRole = projectRoleManager
                    .getProjectRole(StaticTokenUtil.TEAM_LEADERS);
            return projectRoleManager.isUserInProjectRole(currentUser,
                    teamLeadRole, currentProject);
        } catch (NullPointerException e) {
            return false;
        }
    }

    @Override
    public boolean checkTeamLead(Project currentProject,
                                 ApplicationUser currentUser) {
        try {
            ProjectRole teamLeadRole = projectRoleManager
                    .getProjectRole(StaticTokenUtil.TEAM_LEADERS);
            return projectRoleManager.isUserInProjectRole(currentUser,
                    teamLeadRole, currentProject);
        } catch (NullPointerException e) {
        }
        return false;
    }

    public boolean isTeamLead(Project project, ApplicationUser user) {
        // TODO
        return false;
    }

    @Deprecated
    @Override
    public boolean checkQAandTopUser(Project currentProject) {
        boolean checkRole = false;
        ApplicationUser currentUser = jiraAuthenticationContext.getUser();
        return isQaOrTopUser(currentProject, currentUser);
    }

    @Override
    public boolean isQaOrTopUser(Project project, ApplicationUser user) {
        boolean checkRole = false;
        try {
            checkRole = groupManager.isUserInGroup(user.getDirectoryUser(),
                    FIS_TOP_USER);
        } catch (NullPointerException e) {
            // user haven't permission Team Leaders
            checkRole = false;
        }
        if (!checkRole) {
            return isUserInRoleExcludingDefaultGroup(project, user,
                    StaticTokenUtil.QUALITY_ASSURANCE);
        }
        return checkRole;
    }

    @Override
    public boolean checkCurrentUserIsLeadOfUser(String projectKey,
                                                String userKey) {
        ApplicationUser currentUser = jiraAuthenticationContext.getUser();
        List<String> userKeys = new ArrayList<String>();
        userKeys = projectTeamServiceImpl.getUserByTeamLead(projectKey,
                currentUser.getKey());
        try {
            return userKeys.contains(userKey);
        } catch (NullPointerException e) {
            return false;
        }
    }

    @Override
    public Map<String, Float> totalHourAndRecordAllWorkLogForAdmin(
            Long projectId, String userKey, Date startDate, Date endDate,
            String status, String typeOfWork, List<String> components,
            List<String> versions, boolean checkPm, boolean checkTeamLead,
            boolean checkQA, List<String> userKeys) {
        Map<String, Float> maps = new HashMap<String, Float>();
        float totalHour = 0;
        float totalRecord = 0;
        Query query = null;
        if (!checkPm && !checkQA && checkTeamLead) {
            if (userKeys.size() > 0) {
                query = queryAllWorkLogWithListComponentForAdmin(projectId,
                        userKey, startDate, endDate, status, typeOfWork,
                        components, versions, userKeys);
            }
        } else {
            query = queryAllWorkLogWithListComponentForAdmin(projectId,
                    userKey, startDate, endDate, status, typeOfWork,
                    components, versions);
        }
        if (query == null) {
            maps.put(TOTAL_HOUR, totalHour);
            maps.put(TOTAL_RECORD, totalRecord);
            return maps;
        } else {
            query.order("START_DATE DESC");
            WorkLog[] workLogs = ao.find(WorkLog.class, query);
            List<WorkLog> listWorkLog = newArrayList(workLogs);
            for (WorkLog workLog : listWorkLog) {
                totalHour += Float.valueOf(workLog.getWorkPerDay());
                totalRecord++;
            }
            maps.put(TOTAL_HOUR, totalHour);
            maps.put(TOTAL_RECORD, totalRecord);
            return maps;
        }

    }

    @Override
    public Map<String, Float> totalHourWorkLogPendingForAdminTotal(
            Long projectId, String userKey, Date startDate, Date endDate,
            String typeOfWork, List<String> components, List<String> versions,
            boolean checkPm, boolean checkTeamLead, List<String> userKeys) {
        Map<String, Float> maps = new HashMap<String, Float>();
        float totalHour = 0;
        float totalRecord = 0;
        Query query = null;
        if (!checkPm && checkTeamLead) {
            if (userKeys.size() > 0) {
                query = queryWorkLogPendingForAdmin(projectId, userKey,
                        startDate, endDate, typeOfWork, components, versions,
                        userKeys);
            }
        } else {
            query = queryWorkLogPendingForAdmin(projectId, userKey, startDate,
                    endDate, typeOfWork, components, versions);
        }
        if (query == null) {
            maps.put(TOTAL_HOUR, totalHour);
            maps.put(TOTAL_RECORD, totalRecord);
            return maps;
        } else {
            query.order("START_DATE DESC");
            WorkLog[] workLogs = ao.find(WorkLog.class, query);
            List<WorkLog> listWorkLog = newArrayList(workLogs);
            for (WorkLog workLog : listWorkLog) {
                totalHour += Float.valueOf(workLog.getWorkPerDay());
                totalRecord++;

            }
            maps.put(TOTAL_HOUR, totalHour);
            maps.put(TOTAL_RECORD, totalRecord);
            return maps;
        }
    }

    @Override
    public List<WorkLog> getWorkLogApprovedByProjectIdExcludeReject(
            Long projectId, Date startDate, Date endDate) {
        if (startDate == null) {
            startDate = DateUtils.INVALID_START_DATE;
        }
        if (endDate == null) {
            endDate = DateUtils.INVALID_END_DATE;
        }
        startDate = DateUtils.standardizeDate(startDate, 0, 0, 0, 0);
        endDate = DateUtils.standardizeDate(endDate, 23, 59, 59, 999);
        WorkLog[] listWorkLog = ao
                .find(WorkLog.class,
                        Query.select()
                                .order("USER_KEY ASC")
                                .where("STATUS IN( '"
                                                + StaticTokenUtil.APPROVE
                                                + "','"
                                                + StaticTokenUtil.PENDING
                                                + "') AND PROJECT_ID = ? AND (START_DATE BETWEEN ? AND ?)",
                                        projectId, startDate, endDate));
        return newArrayList(listWorkLog);
    }

    @Override
    public double getActualWorkLogByProjectExcludeReject(long projectId,
                                                         Date startDate, Date endDate) {
        List<WorkLog> list = getWorkLogApprovedByProjectIdExcludeReject(
                projectId, startDate, endDate);
        return getActualForWorklogList(list);
    }

    @Override
    public void addActualStartAndEndDateAllIssue()
            throws GenericEntityException {
        List<Project> projects = projectManager.getProjectObjects();
        Collection<Long> isssueIds = null;
        MutableIssue mutableIssue = null;
        Timestamp dueDate = null;
        List<WorkLog> workLogs = new ArrayList<WorkLog>();
        CustomField actualStartCustomField = customFieldManager
                .getCustomFieldObjectByName(ACTUAL_START_DATE);
        CustomField actualEndCustomField = customFieldManager
                .getCustomFieldObjectByName(ACTUAL_END_DATE);
        for (Project project : projects) {
            isssueIds = issueManager.getIssueIdsForProject(project.getId());
            if (isssueIds != null) {
                for (Long issueid : isssueIds) {
                    mutableIssue = issueManager.getIssueObject(issueid);
                    if (mutableIssue != null) {
                        workLogs = getWorkLogNotRejectByIssueKeyOrderByDate(mutableIssue
                                .getKey());
                        if (workLogs.size() > 0) {
                            if (actualStartCustomField.getValue(mutableIssue) == null) {
                                dueDate = new Timestamp(workLogs.get(0)
                                        .getStartDate().getTime());
                                CustomFieldUtil.updateDateCustomfielValue(
                                        mutableIssue, actualStartCustomField,
                                        dueDate, fieldLayoutManager);
                            }
                            if (actualEndCustomField.getValue(mutableIssue) == null) {
                                dueDate = new Timestamp(workLogs
                                        .get(workLogs.size() - 1)
                                        .getStartDate().getTime());
                                CustomFieldUtil.updateDateCustomfielValue(
                                        mutableIssue, actualEndCustomField,
                                        dueDate, fieldLayoutManager);
                            }
                        } else {
                            if (IssueTypeAndStatus.STATUS_CLOSED
                                    .equalsIgnoreCase(mutableIssue
                                            .getStatusObject().getName())) {
                                try {
                                    List<ChangeHistoryItem> changeHistories = orderHistoryList(changeHistoryManager
                                            .getAllChangeItems(mutableIssue));
                                    for (ChangeHistoryItem changeHistory : changeHistories) {
                                        if ("status"
                                                .equalsIgnoreCase(changeHistory
                                                        .getField())) {
                                            if (actualEndCustomField
                                                    .getValue(mutableIssue) == null) {
                                                dueDate = changeHistory
                                                        .getCreated();
                                                CustomFieldUtil
                                                        .updateDateCustomfielValue(
                                                                mutableIssue,
                                                                actualEndCustomField,
                                                                dueDate,
                                                                fieldLayoutManager);
                                                break;
                                            } else {
                                                break;
                                            }
                                        }
                                    }
                                } catch (Exception e) {
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private List<ChangeHistoryItem> orderHistoryList(
            List<ChangeHistoryItem> changeHistoryItems) {
        ArrayList<ChangeHistoryItem> outputList = new ArrayList<ChangeHistoryItem>(
                changeHistoryItems);
        Collections.sort(outputList, new Comparator<ChangeHistoryItem>() {
            @Override
            public int compare(ChangeHistoryItem c1, ChangeHistoryItem c2) {
                return (int) (c2.getCreated().getTime() - c1.getCreated()
                        .getTime());
            }
        });
        return outputList;
    }

    private List<WorkLog> getWorkLogNotRejectByIssueKeyOrderByDate(
            String issueKey) {
        WorkLog[] listWorkLog = ao
                .find(WorkLog.class,
                        Query.select()
                                .where("ISSUE_KEY = ? AND STATUS IN ( 'Pending','Approved' )",
                                        issueKey).order("START_DATE"));
        return newArrayList(listWorkLog);
    }

    @Override
    public List<WorkLog> getWorkLogByProjectIdOrderByDate(Long projectId) {
        List<WorkLog> listWorkLog;
        WorkLog[] workLogs = ao.find(
                WorkLog.class,
                Query.select()
                        .where("PROJECT_ID = ? AND STATUS = ?", projectId,
                                StaticTokenUtil.APPROVE).order("START_DATE"));
        if (workLogs == null) {
            listWorkLog = new ArrayList<WorkLog>();
        } else {
            listWorkLog = newArrayList(workLogs);
        }
        return listWorkLog;
    }

    @Deprecated
    @Override
    public boolean checkQA(Project currentProject) {
        boolean checkRole = false;
        ApplicationUser currentUser = jiraAuthenticationContext.getUser();
        try {
            ProjectRole PMProjectRole = projectRoleManager
                    .getProjectRole(StaticTokenUtil.QUALITY_ASSURANCE);
            checkRole = projectRoleManager.isUserInProjectRole(currentUser,
                    PMProjectRole, currentProject);
        } catch (Exception e) {
            // user haven't permission PM
            checkRole = false;
        }
        return (checkRole);
    }

    @Override
    public boolean checkQA(Project currentProject, ApplicationUser currentUser) {
        boolean checkRole = false;
        try {
            ProjectRole PMProjectRole = projectRoleManager
                    .getProjectRole(StaticTokenUtil.QUALITY_ASSURANCE);
            checkRole = projectRoleManager.isUserInProjectRole(currentUser,
                    PMProjectRole, currentProject);
        } catch (Exception e) {
            // user haven't permission PM
            checkRole = false;
        }
        return (checkRole);
    }

    @Override
    public boolean isQualityAssurance(Project project, ApplicationUser user) {
        return isUserInRoleExcludingDefaultGroup(project, user,
                StaticTokenUtil.QUALITY_ASSURANCE);
    }

    private List<WorkLog> getWorkLogApprovalByIssueKeyOrderByDate(
            String issueKey) {
        WorkLog[] listWorkLog = ao.find(
                WorkLog.class,
                Query.select()
                        .where("ISSUE_KEY = ? AND STATUS = 'Approved'",
                                issueKey).order("START_DATE"));
        return newArrayList(listWorkLog);
    }

    @Override
    public double resourceOfVersion(List<Long> versionIds, String projectKey) {
        List<Long> ids = new ArrayList<Long>();
        for (Long id : versionIds) {
            try {
                ids.addAll(versionManager
                        .getIssueIdsWithAffectsVersion(versionManager
                                .getVersion(id)));
            } catch (NullPointerException e) {
            }
        }
        List<MutableIssue> mutableIssues = new ArrayList<MutableIssue>();
        if (ids != null) {
            for (Long issueId : ids) {
                mutableIssues.add(issueManager.getIssueObject(issueId));
            }
        }
        double result = 0;
        List<WorkLog> workLogApproveds = new ArrayList<WorkLog>();
        for (MutableIssue issue : mutableIssues) {
            workLogApproveds
                    .addAll(getWorkLogApprovalByIssueKeyOrderByDate(issue
                            .getKey()));
        }
        for (WorkLog workLog : workLogApproveds) {
            try {
                result += Double.parseDouble(workLog.getWorkPerDay());
            } catch (Exception e) {
            }
        }
        return result / 8;
    }

    private Query queryAllWorkLogWithListComponentForAdmin(Long projectId,
                                                           String userKey, Date startDate, Date endDate, String status,
                                                           String typeOfWork, List<String> componentIds,
                                                           List<String> versionIds) {
        if (startDate == null) {
            startDate = DateUtils.INVALID_START_DATE;
        }
        if (endDate == null) {
            endDate = DateUtils.INVALID_END_DATE;
        }
        List<Object> listParam = new ArrayList<Object>();
        listParam.add(projectId);
        listParam.add(startDate);
        listParam.add(endDate);
        listParam.add(endDate);
        String querryString = "PROJECT_ID = ? AND START_DATE >= ? AND START_DATE <= ? AND (END_DATE <= ? OR END_DATE IS NULL)";

        if (!(SELECT_ALL.equals(status) || status == null)) {
            if ("A&P".equals(status)) {
                querryString += " AND STATUS IN ('Pending','Approved')";
            } else {
                querryString += " AND STATUS = ?";
                listParam.add(status);
            }
        }
        if (!(userKey == null || userKey == "")) {
            querryString += " AND USER_KEY = ?";
            listParam.add(userKey);
        }
        if (!(SELECT_ALL.equals(typeOfWork) || typeOfWork == null)) {
            querryString += " AND TYPE_OF_WORK = ?";
            listParam.add(typeOfWork);
        }
        if (!(componentIds == null || componentIds.size() == 0)) {
            if (!(versionIds == null || versionIds.size() == 0)) {
                List<Version> versions = new ArrayList<Version>();
                List<Issue> issues = new ArrayList<Issue>();
                for (String versionId : versionIds) {
                    try {
                        versions.add(versionManager.getVersion(Long
                                .parseLong(versionId)));
                    } catch (NullPointerException e) {
                    }
                }
                for (Version version : versions) {
                    issues.addAll(versionManager
                            .getIssuesWithAffectsVersion(version));
                }
                List<String> listIssueKey = new ArrayList<String>();
                for (Issue issue : issues) {
                    List<ProjectComponent> listComponent = (List<ProjectComponent>) issue
                            .getComponentObjects();
                    if (listComponent.size() > 0) {
                        if (componentIds.contains(listComponent.get(0).getId()
                                .toString())) {
                            listIssueKey.add(issue.getKey());
                        }
                    }
                }
                if (listIssueKey.size() > 0) {
                    querryString += "AND (";
                    for (String issueKey : listIssueKey) {
                        if (listIssueKey.indexOf(issueKey) == 0) {
                            querryString += "ISSUE_KEY = ?";
                        } else {
                            querryString += "OR ISSUE_KEY = ?";
                        }
                    }
                    querryString += ")";
                    listParam.addAll(listIssueKey);
                } else {
                    return null;
                }
            } else {
                List<Long> issueIds = new ArrayList<Long>();
                List<ProjectComponent> components = new ArrayList<ProjectComponent>();
                for (String componentId : componentIds) {
                    try {
                        components.add(projectComponentManager
                                .getProjectComponent(Long
                                        .parseLong(componentId)));
                    } catch (NullPointerException e) {
                    }
                }
                for (ProjectComponent component : components) {
                    issueIds.addAll(projectComponentManager
                            .getIssueIdsWithComponent(component));
                }
                List<Issue> issues = issueManager.getIssueObjects(issueIds);
                List<String> listIssueKey = new ArrayList<String>();
                for (Issue issue : issues) {
                    listIssueKey.add(issue.getKey());
                }
                if (listIssueKey.size() > 0) {
                    querryString += "AND (";
                    for (String issueKey : listIssueKey) {
                        if (listIssueKey.indexOf(issueKey) == 0) {
                            querryString += "ISSUE_KEY = ?";
                        } else {
                            querryString += "OR ISSUE_KEY = ?";
                        }
                    }
                    querryString += ")";
                    listParam.addAll(listIssueKey);
                } else {
                    return null;
                }
            }
        } else {
            if (!(versionIds == null || versionIds.size() == 0)) {
                List<Version> versions = new ArrayList<Version>();
                List<Issue> issues = new ArrayList<Issue>();
                for (String versionId : versionIds) {
                    try {
                        versions.add(versionManager.getVersion(Long
                                .parseLong(versionId)));
                    } catch (NullPointerException e) {
                    }
                }
                for (Version version : versions) {
                    issues.addAll(versionManager
                            .getIssuesWithAffectsVersion(version));
                }
                List<String> listIssueKey = new ArrayList<String>();
                for (Issue issue : issues) {
                    listIssueKey.add(issue.getKey());
                }
                if (listIssueKey.size() > 0) {
                    querryString += "AND (";
                    for (String issueKey : listIssueKey) {
                        if (listIssueKey.indexOf(issueKey) == 0) {
                            querryString += "ISSUE_KEY = ?";
                        } else {
                            querryString += "OR ISSUE_KEY = ?";
                        }
                    }
                    querryString += ")";
                    listParam.addAll(listIssueKey);
                } else {
                    return null;
                }
            }
        }
        Query query = Query.select().where(querryString, listParam.toArray());
        return query;
    }

    private Query queryWorkLogPendingForAdmin(Long projectId, String userKey,
                                              Date startDate, Date endDate, String typeOfWork,
                                              List<String> componentIds, List<String> versionIds) {
        if (startDate == null) {
            startDate = DateUtils.INVALID_START_DATE;
        }
        if (endDate == null) {
            endDate = DateUtils.INVALID_END_DATE;
        }
        List<Object> listParam = new ArrayList<Object>();
        listParam.add(projectId);
        listParam.add(startDate);
        listParam.add(endDate);
        listParam.add(endDate);
        listParam.add(StaticTokenUtil.REJECT);
        String querryString = "PROJECT_ID = ? AND START_DATE >= ? AND START_DATE <= ? AND (END_DATE <= ? OR END_DATE IS NULL) AND STATUS != ? ";

        if (!(userKey == null || userKey == "")) {
            querryString += " AND USER_KEY = ? ";
            listParam.add(userKey);
        }
        if (!(SELECT_ALL.equals(typeOfWork) || typeOfWork == null)) {
            querryString += " AND TYPE_OF_WORK = ? ";
            listParam.add(typeOfWork);
        }
        if (!(componentIds == null || componentIds.size() == 0)) {
            if (!(versionIds == null || versionIds.size() == 0)) {
                List<Issue> issues = getIssuesWithVersionList(versionIds);
                List<String> listIssueKey = new ArrayList<String>();
                for (Issue issue : issues) {
                    List<ProjectComponent> listComponent = (List<ProjectComponent>) issue
                            .getComponentObjects();
                    if (listComponent.size() > 0) {
                        if (componentIds.contains(listComponent.get(0).getId()
                                .toString())) {
                            listIssueKey.add(issue.getKey());
                        }
                    }
                }
                if (listIssueKey.size() > 0) {
                    querryString += " AND (";
                    for (String issueKey : listIssueKey) {
                        if (listIssueKey.indexOf(issueKey) == 0) {
                            querryString += "ISSUE_KEY = ? ";
                        } else {
                            querryString += "OR ISSUE_KEY = ? ";
                        }
                    }
                    querryString += ") ";
                    listParam.addAll(listIssueKey);
                } else {
                    return null;
                }
            } else {
                List<Issue> issues = getIssuesWithComponentList(componentIds);
                List<String> listIssueKey = new ArrayList<String>();
                for (Issue issue : issues) {
                    listIssueKey.add(issue.getKey());
                }
                if (listIssueKey.size() > 0) {
                    querryString += " AND (";
                    for (String issueKey : listIssueKey) {
                        if (listIssueKey.indexOf(issueKey) == 0) {
                            querryString += "ISSUE_KEY = ? ";
                        } else {
                            querryString += "OR ISSUE_KEY = ? ";
                        }
                    }
                    querryString += ") ";
                    listParam.addAll(listIssueKey);
                } else {
                    return null;
                }
            }
        } else {
            if (!(versionIds == null || versionIds.size() == 0)) {
                List<Issue> issues = getIssuesWithVersionList(versionIds);
                List<String> listIssueKey = new ArrayList<String>();
                for (Issue issue : issues) {
                    listIssueKey.add(issue.getKey());
                }
                if (listIssueKey.size() > 0) {
                    querryString += " AND (";
                    for (String issueKey : listIssueKey) {
                        if (listIssueKey.indexOf(issueKey) == 0) {
                            querryString += "ISSUE_KEY = ?";
                        } else {
                            querryString += "OR ISSUE_KEY = ? ";
                        }
                    }
                    querryString += ") ";
                    listParam.addAll(listIssueKey);
                } else {
                    return null;
                }
            }
        }
        Query query = Query.select().where(querryString, listParam.toArray());
        return query;
    }

    private Query queryWorkLogForUser(Long projectId, String userKey,
                                      int month, int year, String typeOfWork,
                                      String componentId, String versionId, String status, String issueType, String issueStatus) {
        List<Object> listParam = new ArrayList<Object>();
        listParam.add(projectId);
        listParam.add(userKey);
        listParam.add(month);
        listParam.add(year);
        String queryString = "PROJECT_ID = ? AND USER_KEY = ? AND MONTH(START_DATE) = ? AND YEAR(START_DATE) = ? ";
        if (!(SELECT_ALL.equals(typeOfWork) || typeOfWork == null)) {
            queryString += " AND TYPE_OF_WORK = ? ";
            listParam.add(typeOfWork);
        }
        if (!(SELECT_ALL.equals(status) || status == null)) {
            queryString += " AND STATUS = ? ";
            listParam.add(status);
        }
        if (!SELECT_ALL.equals(componentId)) {
            if (!SELECT_ALL.equals(versionId)) {
                List<Issue> issues = getIssuesWithVersionTypeStatus(versionId, issueType, issueStatus);
                List<String> listIssueKey = new ArrayList<String>();
                for (Issue issue : issues) {
                    List<ProjectComponent> listComponent = (List<ProjectComponent>) issue
                            .getComponentObjects();
                    for (ProjectComponent projectComponent : listComponent) {
                        if (projectComponent.getId() == Long.parseLong(componentId)) {
                            listIssueKey.add(issue.getKey());
                        }
                    }
                }
                if (listIssueKey.size() > 0) {
                    queryString += " AND ISSUE_KEY IN(";
                    for (String issueKey : listIssueKey) {
                        if (listIssueKey.indexOf(issueKey) != listIssueKey.size() - 1) {
                            queryString += "?, ";
                        } else {
                            queryString += "?)";
                        }
                    }
                    listParam.addAll(listIssueKey);
                } else {
                    return null;
                }
            } else {
                List<Issue> issues = getIssuesWithComponent(componentId);
                List<String> listIssueKey = new ArrayList<String>();
                for (Issue issue : issues) {
                    listIssueKey.add(issue.getKey());
                }
                if (listIssueKey.size() > 0) {
                    queryString += " AND ISSUE_KEY IN(";
                    for (String issueKey : listIssueKey) {
                        if (listIssueKey.indexOf(issueKey) != listIssueKey.size() - 1) {
                            queryString += "?, ";
                        } else {
                            queryString += "?)";
                        }
                    }
                    listParam.addAll(listIssueKey);
                } else {
                    return null;
                }
            }
        } else {
            if (!SELECT_ALL.equals(versionId)) {
                List<Issue> issues = getIssuesWithVersionTypeStatus(versionId, issueType, issueStatus);
                List<String> listIssueKey = new ArrayList<String>();
                for (Issue issue : issues) {
                    listIssueKey.add(issue.getKey());
                }
                if (listIssueKey.size() > 0) {
                    queryString += " AND ISSUE_KEY IN(";
                    for (String issueKey : listIssueKey) {
                        if (listIssueKey.indexOf(issueKey) != listIssueKey.size() - 1) {
                            queryString += "?, ";
                        } else {
                            queryString += "?)";
                        }
                    }
                    listParam.addAll(listIssueKey);
                } else {
                    return null;
                }
            } else {
                try {
                    List<Issue> listIssue = issueManager.getIssueObjects(issueManager
                            .getIssueIdsForProject(projectId));
                    if (listIssue.size() > 0) {
                        boolean checkExistsIssueType = false;
                        queryString += " AND ISSUE_KEY IN(";
                        if (!SELECT_ALL.equals(issueType)) {
                            if (issueStatus.equals("true")) {
                                for (Issue issue : listIssue) {
                                    if (issue.getIssueTypeObject().getName().equals(issueType)) {
                                        queryString += "?, ";
                                        checkExistsIssueType = true;
                                        listParam.add(issue.getKey());
                                    }
                                }
                            } else {
                                for (Issue issue : listIssue) {
                                    if (issue.getIssueTypeObject().getName().equals(issueType)
                                            && !issue.getStatusObject().getName().equals("Closed")
                                            && !issue.getStatusObject().getName().equals("Cancelled")) {
                                        queryString += "?, ";
                                        checkExistsIssueType = true;
                                        listParam.add(issue.getKey());
                                    }
                                }
                            }
                            if (checkExistsIssueType) {
                                queryString = queryString.substring(0, queryString.length() - 2) + ") ";
                            } else {
                                queryString = queryString.substring(0, queryString.length() - 10);
                            }
                        } else {
                            if (issueStatus.equals("true")) {
                                for (Issue issue : listIssue) {
                                    queryString += "?, ";
                                    checkExistsIssueType = true;
                                    listParam.add(issue.getKey());
                                }
                            } else {
                                for (Issue issue : listIssue) {
                                    if (!issue.getStatusObject().getName().equals("Closed")
                                            && !issue.getStatusObject().getName().equals("Cancelled")) {
                                        queryString += "?, ";
                                        checkExistsIssueType = true;
                                        listParam.add(issue.getKey());
                                    }
                                }
                            }
                            if (checkExistsIssueType) {
                                queryString = queryString.substring(0, queryString.length() - 2) + ") ";
                            } else {
                                queryString = queryString.substring(0, queryString.length() - 10);
                            }
                        }
                    } else {
                        return null;
                    }
                } catch (GenericEntityException e) {
                    e.printStackTrace();
                }
            }
        }
        Query query = Query.select().order("ISSUE_KEY ASC").where(queryString, listParam.toArray());
        return query;
    }

    private Query queryWorkLogForUser(int currentPageNumber,
                                      int recordInPage, String userKey, Long projectId, Date startDate,
                                      Date endDate, String type, String componentId,
                                      String versionId, String status, String issueType, String issueStatus) {
        List<Object> listParam = new ArrayList<Object>();
        listParam.add(projectId);
        listParam.add(userKey);
        listParam.add(startDate);
        listParam.add(endDate);
        listParam.add(endDate);
        String queryString = "PROJECT_ID = ? AND USER_KEY = ? AND START_DATE >= ? AND START_DATE <= ? AND (END_DATE <= ? OR END_DATE IS NULL) ";
        if (!(SELECT_ALL.equals(type) || type == null)) {
            queryString += " AND TYPE_OF_WORK = ? ";
            listParam.add(type);
        }
        if (!(SELECT_ALL.equals(status) || status == null)) {
            queryString += " AND STATUS = ? ";
            listParam.add(status);
        }
        if (!SELECT_ALL.equals(componentId)) {
            if (!SELECT_ALL.equals(versionId)) {
                List<Issue> issues = getIssuesWithVersionTypeStatus(versionId, issueType, issueStatus);
                List<String> listIssueKey = new ArrayList<String>();
                for (Issue issue : issues) {
                    List<ProjectComponent> listComponent = (List<ProjectComponent>) issue
                            .getComponentObjects();
                    if (listComponent.size() > 0) {
                        listIssueKey.add(issue.getKey());
                    }
                }
                if (listIssueKey.size() > 0) {
                    queryString += " AND ISSUE_KEY IN(";
                    for (String issueKey : listIssueKey) {
                        if (listIssueKey.indexOf(issueKey) != listIssueKey.size() - 1) {
                            queryString += "?, ";
                        } else {
                            queryString += "?)";
                        }
                    }
                    listParam.addAll(listIssueKey);
                } else {
                    return null;
                }
            } else {
                List<Issue> issues = getIssuesWithComponent(componentId);
                List<String> listIssueKey = new ArrayList<String>();
                for (Issue issue : issues) {
                    listIssueKey.add(issue.getKey());
                }
                if (listIssueKey.size() > 0) {
                    queryString += " AND ISSUE_KEY IN(";
                    for (String issueKey : listIssueKey) {
                        if (listIssueKey.indexOf(issueKey) != listIssueKey.size() - 1) {
                            queryString += "?, ";
                        } else {
                            queryString += "?)";
                        }
                    }
                    listParam.addAll(listIssueKey);
                } else {
                    return null;
                }
            }
        } else {
            if (!SELECT_ALL.equals(versionId)) {
                List<Issue> issues = getIssuesWithVersionTypeStatus(versionId, issueType, issueStatus);
                List<String> listIssueKey = new ArrayList<String>();
                for (Issue issue : issues) {
                    listIssueKey.add(issue.getKey());
                }
                if (listIssueKey.size() > 0) {
                    queryString += " AND ISSUE_KEY IN(";
                    for (String issueKey : listIssueKey) {
                        if (listIssueKey.indexOf(issueKey) != listIssueKey.size() - 1) {
                            queryString += "?, ";
                        } else {
                            queryString += "?)";
                        }
                    }
                    listParam.addAll(listIssueKey);
                } else {
                    return null;
                }
            } else {
                try {
                    List<Issue> listIssue = issueManager.getIssueObjects(issueManager
                            .getIssueIdsForProject(projectId));
                    if (listIssue.size() > 0) {
                        boolean checkExistsIssueType = false;
                        queryString += " AND ISSUE_KEY IN(";
                        if (!SELECT_ALL.equals(issueType)) {
                            if (issueStatus.equals("true")) {
                                for (Issue issue : listIssue) {
                                    if (issue.getIssueTypeObject().getName().equals(issueType)) {
                                        queryString += "?, ";
                                        checkExistsIssueType = true;
                                        listParam.add(issue.getKey());
                                    }
                                }
                            } else {
                                for (Issue issue : listIssue) {
                                    if (issue.getIssueTypeObject().getName().equals(issueType)
                                            && !issue.getStatusObject().getName().equals("Closed")
                                            && !issue.getStatusObject().getName().equals("Cancelled")) {
                                        queryString += "?, ";
                                        checkExistsIssueType = true;
                                        listParam.add(issue.getKey());
                                    }
                                }
                            }
                            if (checkExistsIssueType) {
                                queryString = queryString.substring(0, queryString.length() - 2) + ") ";
                            } else {
                                queryString = queryString.substring(0, queryString.length() - 10);
                            }
                        } else {
                            if (issueStatus.equals("true")) {
                                for (Issue issue : listIssue) {
                                    queryString += "?, ";
                                    checkExistsIssueType = true;
                                    listParam.add(issue.getKey());
                                }
                            } else {
                                for (Issue issue : listIssue) {
                                    if (!issue.getStatusObject().getName().equals("Closed")
                                            && !issue.getStatusObject().getName().equals("Cancelled")) {
                                        queryString += "?, ";
                                        checkExistsIssueType = true;
                                        listParam.add(issue.getKey());
                                    }
                                }
                            }
                            if (checkExistsIssueType) {
                                queryString = queryString.substring(0, queryString.length() - 2) + ") ";
                            } else {
                                queryString = queryString.substring(0, queryString.length() - 10);
                            }
                        }
                    } else {
                        return null;
                    }
                } catch (GenericEntityException e) {
                    e.printStackTrace();
                }
            }
        }
        Query query = Query.select()
                .select()
                .limit(recordInPage)
                .offset((currentPageNumber - 1) * recordInPage)
                .order("START_DATE DESC").where(queryString, listParam.toArray());
        return query;
    }

    private Query queryWorkLogForCurrentUserTotal(Long projectId, String userKey, Date startDate,
                                                  Date endDate, String type, String componentId,
                                                  String versionId, String status, String issueType, String issueStatus) {
        List<Object> listParam = new ArrayList<Object>();
        listParam.add(projectId);
        listParam.add(userKey);
        listParam.add(startDate);
        listParam.add(endDate);
        listParam.add(endDate);
        String queryString = "PROJECT_ID = ? AND USER_KEY = ? AND START_DATE >= ? AND START_DATE <= ? AND (END_DATE <= ? OR END_DATE IS NULL) ";
        if (!(SELECT_ALL.equals(type) || type == null)) {
            queryString += " AND TYPE_OF_WORK = ? ";
            listParam.add(type);
        }
        if (!(SELECT_ALL.equals(status) || status == null)) {
            queryString += " AND STATUS = ? ";
            listParam.add(status);
        }
        if (!SELECT_ALL.equals(componentId)) {
            if (!SELECT_ALL.equals(versionId)) {
                List<Issue> issues = getIssuesWithVersionTypeStatus(versionId, issueType, issueStatus);
                List<String> listIssueKey = new ArrayList<String>();
                for (Issue issue : issues) {
                    List<ProjectComponent> listComponent = (List<ProjectComponent>) issue
                            .getComponentObjects();
                    if (listComponent.size() > 0) {
                        listIssueKey.add(issue.getKey());
                    }
                }
                if (listIssueKey.size() > 0) {
                    queryString += " AND ISSUE_KEY IN(";
                    for (String issueKey : listIssueKey) {
                        if (listIssueKey.indexOf(issueKey) != listIssueKey.size() - 1) {
                            queryString += "?, ";
                        } else {
                            queryString += "?)";
                        }
                    }
                    listParam.addAll(listIssueKey);
                } else {
                    return null;
                }
            } else {
                List<Issue> issues = getIssuesWithComponent(componentId);
                List<String> listIssueKey = new ArrayList<String>();
                for (Issue issue : issues) {
                    listIssueKey.add(issue.getKey());
                }
                if (listIssueKey.size() > 0) {
                    queryString += " AND ISSUE_KEY IN(";
                    for (String issueKey : listIssueKey) {
                        if (listIssueKey.indexOf(issueKey) != listIssueKey.size() - 1) {
                            queryString += "?, ";
                        } else {
                            queryString += "?)";
                        }
                    }
                    listParam.addAll(listIssueKey);
                } else {
                    return null;
                }
            }
        } else {
            if (!SELECT_ALL.equals(versionId)) {
                List<Issue> issues = getIssuesWithVersionTypeStatus(versionId, issueType, issueStatus);
                List<String> listIssueKey = new ArrayList<String>();
                for (Issue issue : issues) {
                    listIssueKey.add(issue.getKey());
                }
                if (listIssueKey.size() > 0) {
                    queryString += " AND ISSUE_KEY IN(";
                    for (String issueKey : listIssueKey) {
                        if (listIssueKey.indexOf(issueKey) != listIssueKey.size() - 1) {
                            queryString += "?, ";
                        } else {
                            queryString += "?)";
                        }
                    }
                    listParam.addAll(listIssueKey);
                } else {
                    return null;
                }
            } else {
                try {
                    List<Issue> listIssue = issueManager.getIssueObjects(issueManager
                            .getIssueIdsForProject(projectId));
                    if (listIssue.size() > 0) {
                        boolean checkExistsIssueType = false;
                        queryString += " AND ISSUE_KEY IN(";
                        if (!SELECT_ALL.equals(issueType)) {
                            if (issueStatus.equals("true")) {
                                for (Issue issue : listIssue) {
                                    if (issue.getIssueTypeObject().getName().equals(issueType)) {
                                        queryString += "?, ";
                                        checkExistsIssueType = true;
                                        listParam.add(issue.getKey());
                                    }
                                }
                            } else {
                                for (Issue issue : listIssue) {
                                    if (issue.getIssueTypeObject().getName().equals(issueType)
                                            && !issue.getStatusObject().getName().equals("Closed")
                                            && !issue.getStatusObject().getName().equals("Cancelled")) {
                                        queryString += "?, ";
                                        checkExistsIssueType = true;
                                        listParam.add(issue.getKey());
                                    }
                                }
                            }
                            if (checkExistsIssueType) {
                                queryString = queryString.substring(0, queryString.length() - 2) + ") ";
                            } else {
                                queryString = queryString.substring(0, queryString.length() - 10);
                            }
                        } else {
                            if (issueStatus.equals("true")) {
                                for (Issue issue : listIssue) {
                                    queryString += "?, ";
                                    checkExistsIssueType = true;
                                    listParam.add(issue.getKey());
                                }
                            } else {
                                for (Issue issue : listIssue) {
                                    if (!issue.getStatusObject().getName().equals("Closed")
                                            && !issue.getStatusObject().getName().equals("Cancelled")) {
                                        queryString += "?, ";
                                        checkExistsIssueType = true;
                                        listParam.add(issue.getKey());
                                    }
                                }
                            }
                            if (checkExistsIssueType) {
                                queryString = queryString.substring(0, queryString.length() - 2) + ") ";
                            } else {
                                queryString = queryString.substring(0, queryString.length() - 10);
                            }
                        }
                    } else {
                        return null;
                    }
                } catch (GenericEntityException e) {
                    e.printStackTrace();
                }
            }
        }
        Query query = Query.select()
                .select()
                .order("START_DATE DESC").where(queryString, listParam.toArray());
        return query;
    }

    private Query queryTotalHourWorkLogForCurrentUser(Long projectId, String userKey,
                                                      Date startDate, Date endDate, String type,
                                                      String componentId, String versionId,
                                                      String status, String issueType, String issueStatus) {
        List<Object> listParam = new ArrayList<Object>();
        listParam.add(projectId);
        listParam.add(userKey);
        listParam.add(startDate);
        listParam.add(endDate);
        listParam.add(endDate);
        String queryString = "PROJECT_ID = ? AND USER_KEY = ? AND START_DATE >= ? AND START_DATE <= ? AND (END_DATE <= ? OR END_DATE IS NULL) ";
        if (!(SELECT_ALL.equals(type) || type == null)) {
            queryString += " AND TYPE_OF_WORK = ? ";
            listParam.add(type);
        }
        if (!(SELECT_ALL.equals(status) || status == null)) {
            queryString += " AND STATUS = ? ";
            listParam.add(status);
        }
        if (!SELECT_ALL.equals(componentId)) {
            if (!SELECT_ALL.equals(versionId)) {
                List<Issue> issues = getIssuesWithVersionTypeStatus(versionId, issueType, issueStatus);
                List<String> listIssueKey = new ArrayList<String>();
                for (Issue issue : issues) {
                    List<ProjectComponent> listComponent = (List<ProjectComponent>) issue
                            .getComponentObjects();
                    if (listComponent.size() > 0) {
                        listIssueKey.add(issue.getKey());
                    }
                }
                if (listIssueKey.size() > 0) {
                    queryString += " AND ISSUE_KEY IN(";
                    for (String issueKey : listIssueKey) {
                        if (listIssueKey.indexOf(issueKey) != listIssueKey.size() - 1) {
                            queryString += "?, ";
                        } else {
                            queryString += "?)";
                        }
                    }
                    listParam.addAll(listIssueKey);
                } else {
                    return null;
                }
            } else {
                List<Issue> issues = getIssuesWithComponent(componentId);
                List<String> listIssueKey = new ArrayList<String>();
                for (Issue issue : issues) {
                    listIssueKey.add(issue.getKey());
                }
                if (listIssueKey.size() > 0) {
                    queryString += " AND ISSUE_KEY IN(";
                    for (String issueKey : listIssueKey) {
                        if (listIssueKey.indexOf(issueKey) != listIssueKey.size() - 1) {
                            queryString += "?, ";
                        } else {
                            queryString += "?)";
                        }
                    }
                    listParam.addAll(listIssueKey);
                } else {
                    return null;
                }
            }
        } else {
            if (!SELECT_ALL.equals(versionId)) {
                List<Issue> issues = getIssuesWithVersionTypeStatus(versionId, issueType, issueStatus);
                List<String> listIssueKey = new ArrayList<String>();
                for (Issue issue : issues) {
                    listIssueKey.add(issue.getKey());
                }
                if (listIssueKey.size() > 0) {
                    queryString += " AND ISSUE_KEY IN(";
                    for (String issueKey : listIssueKey) {
                        if (listIssueKey.indexOf(issueKey) != listIssueKey.size() - 1) {
                            queryString += "?, ";
                        } else {
                            queryString += "?)";
                        }
                    }
                    listParam.addAll(listIssueKey);
                } else {
                    return null;
                }
            } else {
                try {
                    List<Issue> listIssue = issueManager.getIssueObjects(issueManager
                            .getIssueIdsForProject(projectId));
                    if (listIssue.size() > 0) {
                        boolean checkExistsIssueType = false;
                        queryString += " AND ISSUE_KEY IN(";
                        if (!SELECT_ALL.equals(issueType)) {
                            if (issueStatus.equals("true")) {
                                for (Issue issue : listIssue) {
                                    if (issue.getIssueTypeObject().getName().equals(issueType)) {
                                        queryString += "?, ";
                                        checkExistsIssueType = true;
                                        listParam.add(issue.getKey());
                                    }
                                }
                            } else {
                                for (Issue issue : listIssue) {
                                    if (issue.getIssueTypeObject().getName().equals(issueType)
                                            && !issue.getStatusObject().getName().equals("Closed")
                                            && !issue.getStatusObject().getName().equals("Cancelled")) {
                                        queryString += "?, ";
                                        checkExistsIssueType = true;
                                        listParam.add(issue.getKey());
                                    }
                                }
                            }
                            if (checkExistsIssueType) {
                                queryString = queryString.substring(0, queryString.length() - 2) + ") ";
                            } else {
                                queryString = queryString.substring(0, queryString.length() - 10);
                            }
                        } else {
                            if (issueStatus.equals("true")) {
                                for (Issue issue : listIssue) {
                                    queryString += "?, ";
                                    checkExistsIssueType = true;
                                    listParam.add(issue.getKey());
                                }
                            } else {
                                for (Issue issue : listIssue) {
                                    if (!issue.getStatusObject().getName().equals("Closed")
                                            && !issue.getStatusObject().getName().equals("Cancelled")) {
                                        queryString += "?, ";
                                        checkExistsIssueType = true;
                                        listParam.add(issue.getKey());
                                    }
                                }
                            }
                            if (checkExistsIssueType) {
                                queryString = queryString.substring(0, queryString.length() - 2) + ") ";
                            } else {
                                queryString = queryString.substring(0, queryString.length() - 10);
                            }
                        }
                    } else {
                        return null;
                    }
                } catch (GenericEntityException e) {
                    e.printStackTrace();
                }
            }
        }
        Query query = Query.select()
                .select("ID, WORK_PER_DAY, START_DATE")
                .order("START_DATE DESC").where(queryString, listParam.toArray());
        return query;
    }

    @Override
    public List<WorkLog> getAllWorkLogForAdmin(Long projectId,
                                               int currentPageNumber, int recordInPage, String userKey,
                                               Date startDate, Date endDate, String status, String typeOfWork,
                                               List<String> components, List<String> versions) {
        Query query = queryAllWorkLogWithListComponentForAdmin(projectId,
                userKey, startDate, endDate, status, typeOfWork, components,
                versions);
        if (query == null) {
            return new ArrayList<WorkLog>();
        } else {
            query.limit(recordInPage);
            query.offset((currentPageNumber - 1) * recordInPage);
            query.order("START_DATE DESC");
            WorkLog[] listWorkLog = ao.find(WorkLog.class, query);
            return newArrayList(listWorkLog);
        }

    }

    @Override
    public List<WorkLog> getWorkLogPendingForAdmin(Long projectId,
                                                   int currentPageNumber, int recordInPage, String userName,
                                                   Date startDate, Date endDate, String typeOfWork,
                                                   List<String> componentIds, List<String> versionIds) {
        Query query = queryWorkLogPendingForAdmin(projectId, userName,
                startDate, endDate, typeOfWork, componentIds, versionIds);
        if (query == null) {
            return new ArrayList<WorkLog>();
        } else {
            query.limit(recordInPage);
            query.offset((currentPageNumber - 1) * recordInPage);
            query.order("STATUS DESC, START_DATE DESC");
            WorkLog[] listWorkLog = ao.find(WorkLog.class, query);
            return newArrayList(listWorkLog);
        }
    }

    @Override
    public Map<String, Double> getActualWorkLogByIssueAndWorkLogForReport(
            Issue issue, WorkLog worklog) {
        Map<String, Double> maps = new HashMap<String, Double>();
        List<WorkLog> list = getWorkLogByIssue(issue);
        double sum = 0;
        double sumApproved = 0;
        for (WorkLog w : list) {
            sum += Double.valueOf(w.getWorkPerDay());
            if (APPROVED.equalsIgnoreCase(w.getStatus())) {
                sumApproved += Double.valueOf(w.getWorkPerDay());
            }
        }
        maps.put(SUM, sum);
        maps.put(APPROVED, sumApproved);
        return maps;
    }

    @Override
    public List<WorkLog> getWorkLogByIssueKeyOrderByDate(String issueKey) {
        WorkLog[] listWorkLog = ao
                .find(WorkLog.class,
                        Query.select()
                                .where("ISSUE_KEY = ? AND STATUS IN ( 'Pending','Approved' )",
                                        issueKey).order("START_DATE"));
        return newArrayList(listWorkLog);
    }

    @Override
    public List<WorkLog> getWorkLogByTimeAndStatus(Date startDate, Date endDate, String status) {
        try {
            WorkLog[] listWorkLog = ao.find(WorkLog.class, Query.select().order("USER_KEY ASC, PROJECT_ID ASC")
                    .where("STATUS = ? AND ( START_DATE BETWEEN ? AND ? ) AND (END_DATE <= ? OR END_DATE IS NULL)",
                            status, startDate, endDate, endDate));
            return newArrayList(listWorkLog);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public List<ProjectGroup> getAllGroupByUser(String reportName) {
        ApplicationUser user = jiraAuthenticationContext.getUser();
        Map<ProjectGroup, Map<ProjectUnit, List<ProjectConfigInfo>>> maps = projectConfigInfoService
                .getBGAndOUOfUser(user.getDirectoryUser(), reportName);
        List<ProjectGroup> result = new ArrayList<ProjectGroup>();
        for (Map.Entry<ProjectGroup, Map<ProjectUnit, List<ProjectConfigInfo>>> entry : maps
                .entrySet()) {
            if (entry.getKey() != null) {
                result.add(entry.getKey());
            }
        }
        return result;
    }

    @Override
    public List<ProjectUnit> getAllUnitByUser(String reportName) {
        ApplicationUser user = jiraAuthenticationContext.getUser();
        Map<ProjectGroup, Map<ProjectUnit, List<ProjectConfigInfo>>> maps = projectConfigInfoService
                .getBGAndOUOfUser(user.getDirectoryUser(), reportName);
        List<ProjectUnit> result = new ArrayList<ProjectUnit>();
        for (ProjectGroup pG : maps.keySet()) {
            if (pG != null) {
                result.addAll(maps.get(pG).keySet());
            }
        }
        return result;
    }

    @Override
    public List<ProjectConfigInfo> getAllProjectByUser(String reportName) {
        ApplicationUser user = jiraAuthenticationContext.getUser();
        Map<ProjectGroup, Map<ProjectUnit, List<ProjectConfigInfo>>> maps = projectConfigInfoService
                .getBGAndOUOfUser(user.getDirectoryUser(), reportName);
        List<ProjectConfigInfo> result = new ArrayList<ProjectConfigInfo>();
        Map<ProjectUnit, List<ProjectConfigInfo>> mapUnit = new HashMap<ProjectUnit, List<ProjectConfigInfo>>();
        for (ProjectGroup pG : maps.keySet()) {
            if (pG != null) {
                mapUnit = maps.get(pG);
                for (ProjectUnit unit : mapUnit.keySet()) {
                    result.addAll(mapUnit.get(unit));
                }
            }
        }
        return result;
    }

    @Override
    public List<ProjectUnit> findUnitByGroup(String groupCode, String reportName) {
        ApplicationUser user = jiraAuthenticationContext.getUser();
        Map<ProjectGroup, Map<ProjectUnit, List<ProjectConfigInfo>>> maps = projectConfigInfoService
                .getBGAndOUOfUser(user.getDirectoryUser(), reportName);
        List<ProjectUnit> result = new ArrayList<ProjectUnit>();
        Map<ProjectUnit, List<ProjectConfigInfo>> mapUnit = new HashMap<ProjectUnit, List<ProjectConfigInfo>>();
        if (ObjectUtils.isNotEmpty(groupCode)) {
            for (ProjectGroup pG : maps.keySet()) {
                if (pG != null) {
                    if (groupCode.equalsIgnoreCase(pG.getGroupName())) {
                        mapUnit = maps.get(pG);
                        for (ProjectUnit unit : mapUnit.keySet()) {
                            result.add(unit);
                        }
                    }
                }
            }
        } else {
            result = getAllUnitByUser(reportName);
        }
        return result;
    }

    @Override
    public List<ProjectConfigInfo> findProjectByGroupAndUnit(String groupCode,
                                                             String unitCode, String reportName) {
        ApplicationUser user = jiraAuthenticationContext.getUser();
        Map<ProjectGroup, Map<ProjectUnit, List<ProjectConfigInfo>>> maps = projectConfigInfoService
                .getBGAndOUOfUser(user.getDirectoryUser(), reportName);
        List<ProjectConfigInfo> result = new ArrayList<ProjectConfigInfo>();
        Map<ProjectUnit, List<ProjectConfigInfo>> mapUnit = new HashMap<ProjectUnit, List<ProjectConfigInfo>>();
        if (ObjectUtils.isNotEmpty(groupCode)) {
            if (ObjectUtils.isNotEmpty(unitCode)) {
                for (ProjectGroup pG : maps.keySet()) {
                    if (pG != null) {
                        if (groupCode.equalsIgnoreCase(pG.getGroupName())) {
                            mapUnit = maps.get(pG);
                            for (ProjectUnit unit : mapUnit.keySet()) {
                                if (unitCode.equalsIgnoreCase(unit
                                        .getUnitName())) {
                                    result.addAll(mapUnit.get(unit));
                                }
                            }
                        }
                    }
                }
            } else {
                for (ProjectGroup pG : maps.keySet()) {
                    if (pG != null) {
                        if (groupCode.equalsIgnoreCase(pG.getGroupName())) {
                            mapUnit = maps.get(pG);
                            for (ProjectUnit unit : mapUnit.keySet()) {
                                result.addAll(mapUnit.get(unit));
                            }
                        }
                    }
                }
            }
        } else {
            if (ObjectUtils.isNotEmpty(unitCode)) {
                for (ProjectGroup pG : maps.keySet()) {
                    if (pG != null) {
                        mapUnit = maps.get(pG);
                        for (ProjectUnit unit : mapUnit.keySet()) {
                            if (unitCode.equalsIgnoreCase(unit.getUnitName())) {
                                result.addAll(mapUnit.get(unit));
                            }
                        }
                    }
                }
            } else {
                result = getAllProjectByUser(reportName);
            }
        }
        return result;
    }

    @Override
    public List<ObjectSearch> getAllMemberByProject(
            List<ProjectConfigInfo> projects, String contextPath, String query) {
        Set<ApplicationUser> users = new HashSet<ApplicationUser>();
        Project project = null;
        ProjectRoleActors projectRoleActors = null;
        Collection<ProjectRole> projectRoles = projectRoleManager
                .getProjectRoles();
        List<ObjectSearch> listUser = new ArrayList<ObjectSearch>();
        Avatar avatar = null;
        ObjectSearch user = null;
        for (ProjectConfigInfo projectConfigInfo : projects) {
            project = projectManager.getProjectByCurrentKey(projectConfigInfo
                    .getProjectKey());
            projectRoleActors = null;
            for (ProjectRole projectRole : projectRoles) {
                projectRoleActors = projectRoleManager.getProjectRoleActors(
                        projectRole, project);
                if (projectRoleActors != null) {
                    users.addAll(projectRoleActors.getApplicationUsers());
                }
            }
        }
        avatar = null;
        user = null;
        for (ApplicationUser applicationUser : users) {
            if ((applicationUser.getUsername().toLowerCase()).contains(query
                    .toLowerCase())
                    || (applicationUser.getDisplayName().toLowerCase())
                    .contains(query.toLowerCase())) {
                user = new ObjectSearch();
                avatar = avatarService.getAvatar(null, applicationUser);
                long avatarId = avatar.getId();
                if (avatarId == 0) {
                    avatarId = 10122;
                }
                user = new ObjectSearch();
                user.setDisplayName(applicationUser.getDisplayName());
                user.setName(applicationUser.getUsername());
                user.setAvatarUrl(contextPath
                        + "/secure/useravatar?size=xsmall&avatarId=" + avatarId);
                user.setHtml("<div class=\"yad\" >"
                        + applicationUser.getDisplayName() + "&nbsp;-&nbsp;"
                        + applicationUser.getEmailAddress() + "&nbsp;"
                        + applicationUser.getUsername() + ")</div>");
                listUser.add(user);
            }
        }
        return listUser;
    }

    @Override
    public List<ProjectUnitBean> findUnitBeanByGroup(String groupCode,
                                                     String reportName) {
        List<ProjectUnitBean> result = new ArrayList<ProjectUnitBean>();
        List<ProjectUnit> units = findUnitByGroup(groupCode, reportName);
        for (ProjectUnit unit : units) {
            if (unit != null) {
                result.add(new ProjectUnitBean(unit));
            }
        }
        return result;
    }

    @Override
    public boolean checkPermission(String reportName) {
        ApplicationUser user = jiraAuthenticationContext.getUser();
        Map<ProjectGroup, Map<ProjectUnit, List<ProjectConfigInfo>>> maps = projectConfigInfoService
                .getBGAndOUOfUser(user.getDirectoryUser(), reportName);
        if (maps.keySet().isEmpty()) {
            return false;
        }
        return true;
    }

    @Override
    public List<String> getAllMemberByBgOu(List<ProjectConfigInfo> projects) {
        Set<ApplicationUser> users = new HashSet<ApplicationUser>();
        Project project = null;
        ProjectRoleActors projectRoleActors = null;
        Collection<ProjectRole> projectRoles = projectRoleManager
                .getProjectRoles();
        List<String> listUser = new ArrayList<String>();
        for (ProjectConfigInfo projectConfigInfo : projects) {
            project = projectManager.getProjectByCurrentKey(projectConfigInfo
                    .getProjectKey());
            projectRoleActors = null;
            for (ProjectRole projectRole : projectRoles) {
                projectRoleActors = projectRoleManager.getProjectRoleActors(
                        projectRole, project);
                if (projectRoleActors != null) {
                    users.addAll(projectRoleActors.getApplicationUsers());
                }
            }
        }
        for (ApplicationUser applicationUser : users) {
            listUser.add(applicationUser.getUsername());
        }
        return listUser;
    }

    @Override
    public List<WorkLog> getWorkLogByUsersAndProject(String[] userKeys,
                                                     int month, int year, List<ProjectConfigInfo> projects) {
        StringBuilder query = new StringBuilder();
        if (ObjectUtils.isNotEmpty(projects)) {
            String temp = "(";
            int i = 0;
            for (ProjectConfigInfo project : projects) {
                i++;
                if (projects.size() == i) {
                    temp += "'" + project.getProjectID() + "'";
                } else {
                    temp += "'" + project.getProjectID() + "',";
                }

            }
            temp += ")  AND ";
            query.append("PROJECT_ID IN ");
            query.append(temp);
            // PROJECT_ID = ?
        }
        if (!"null".equals(userKeys[0])) {
            String userQuerry = "(";
            int i = 0;
            for (String user : userKeys) {
                i++;
                if (userKeys.length == i) {
                    userQuerry += "'" + user + "'";
                } else {
                    userQuerry += "'" + user + "'" + ',';
                }

            }
            userQuerry += ")";
            query.append("USER_KEY IN ");
            query.append(userQuerry);
            query.append(" AND MONTH(START_DATE) = ? AND YEAR(START_DATE) = ?");
        } else {
            query.append(" MONTH(START_DATE) = ? AND YEAR(START_DATE) = ?");
        }
        WorkLog[] listWorkLog = ao.find(
                WorkLog.class,
                Query.select().order("USER_KEY ASC")
                        .where(query.toString(), month, year));
        return newArrayList(listWorkLog);
    }

    @Override
    public List<WorkLog> getWorkLogNotRejectedByUsers(String[] userKeys,
                                                      int month, int year) {
        StringBuilder query = new StringBuilder();
        if (!"null".equals(userKeys[0])) {
            String userQuerry = "(";
            int i = 0;
            for (String user : userKeys) {
                i++;
                if (userKeys.length == i) {
                    userQuerry += "'" + user + "'";
                } else {
                    userQuerry += "'" + user + "'" + ',';
                }

            }
            userQuerry += ")";
            query.append("USER_KEY IN ");
            query.append(userQuerry);
            query.append(" AND MONTH(START_DATE) = ? AND YEAR(START_DATE) = ? AND STATUS IN ('Pending','Approved')");
        } else {
            query.append(" MONTH(START_DATE) = ? AND YEAR(START_DATE) = ? AND STATUS IN ('Pending','Approved')");
        }
        WorkLog[] listWorkLog = ao.find(
                WorkLog.class,
                Query.select().order("USER_KEY ASC")
                        .where(query.toString(), month, year));
        return newArrayList(listWorkLog);
    }

    @Override
    public List<WorkLog> getWorkLogNotRejectedByUsersIgnoreCase(
            String[] userKeys, int month, int year) {
        if (userKeys.length == 0) {
            return new ArrayList<WorkLog>();
        }
        StringBuilder query = new StringBuilder();
        if (!"null".equals(userKeys[0])) {
            String userQuerry = "(";
            int i = 0;
            for (String user : userKeys) {
                i++;
                if (userKeys.length == i) {
                    userQuerry += "'" + user + "'";
                } else {
                    userQuerry += "'" + user + "'" + ',';
                }

            }
            userQuerry += ")";
            query.append("LOWER(USER_KEY) IN ");
            query.append(userQuerry);
            query.append(" AND MONTH(START_DATE) = ? AND YEAR(START_DATE) = ? AND STATUS IN ('Pending','Approved')");
        }
        WorkLog[] listWorkLog = ao.find(
                WorkLog.class,
                Query.select().order("USER_KEY ASC")
                        .where(query.toString(), month, year));
        return newArrayList(listWorkLog);
    }

    @Override
    public List<WorkLog> getWorkLogNotRejectedByUsersAndProject(
            String[] userKeys, int month, int year,
            List<ProjectConfigInfo> projects) {
        StringBuilder query = new StringBuilder();
        if (ObjectUtils.isNotEmpty(projects)) {
            String temp = "(";
            int i = 0;
            for (ProjectConfigInfo project : projects) {
                i++;
                if (projects.size() == i) {
                    temp += "'" + project.getProjectID() + "'";
                } else {
                    temp += "'" + project.getProjectID() + "',";
                }

            }
            temp += ")  AND ";
            query.append("PROJECT_ID IN ");
            query.append(temp);
            // PROJECT_ID = ?
        }
        if (!"null".equals(userKeys[0])) {
            String userQuerry = "(";
            int i = 0;
            for (String user : userKeys) {
                i++;
                if (userKeys.length == i) {
                    userQuerry += "'" + user + "'";
                } else {
                    userQuerry += "'" + user + "'" + ',';
                }

            }
            userQuerry += ")";
            query.append("USER_KEY IN ");
            query.append(userQuerry);
            query.append(" AND MONTH(START_DATE) = ? AND YEAR(START_DATE) = ? AND STATUS IN ('Pending','Approved')");
        } else {
            query.append(" MONTH(START_DATE) = ? AND YEAR(START_DATE) = ? AND STATUS IN ('Pending','Approved')");
        }
        WorkLog[] listWorkLog = ao.find(
                WorkLog.class,
                Query.select().order("USER_KEY ASC")
                        .where(query.toString(), month, year));
        return newArrayList(listWorkLog);
    }

    @Override
    public List<WorkLog> getWorkLogByUsersAndProject(String[] userKeys,
                                                     Date startDate, Date endDate, List<ProjectConfigInfo> projects) {
        if (startDate == null) {
            startDate = DateUtils.INVALID_START_DATE;
        }
        if (endDate == null) {
            endDate = DateUtils.INVALID_END_DATE;
        }
        startDate = DateUtils.standardizeDate(startDate, 0, 0, 0, 0);
        endDate = DateUtils.standardizeDate(endDate, 23, 59, 59, 999);
        StringBuilder query = new StringBuilder();
        if (ObjectUtils.isNotEmpty(projects)) {
            String temp = "(";
            int i = 0;
            for (ProjectConfigInfo project : projects) {
                i++;
                if (projects.size() == i) {
                    temp += "'" + project.getProjectID() + "'";
                } else {
                    temp += "'" + project.getProjectID() + "',";
                }

            }
            temp += ") AND ";
            query.append("PROJECT_ID IN ");
            query.append(temp);
            // PROJECT_ID = ?
        }
        if (!"null".equals(userKeys[0])) {
            String userQuerry = "(";
            int i = 0;
            for (String user : userKeys) {
                i++;
                if (userKeys.length == i) {
                    userQuerry += "'" + user + "'";
                } else {
                    userQuerry += "'" + user + "'" + ',';
                }

            }
            userQuerry += ")";
            query.append("USER_KEY IN ");
            query.append(userQuerry);
            query.append(" AND (START_DATE BETWEEN ? AND ?)");
        } else {
            query.append(" (START_DATE BETWEEN ? AND ?)");
        }
        WorkLog[] listWorkLog = ao.find(
                WorkLog.class,
                Query.select().order("USER_KEY ASC")
                        .where(query.toString(), startDate, endDate));
        return newArrayList(listWorkLog);
    }

    @Override
    public List<WorkLog> getWorkLogNotRejectedByUsersAndProject(
            String[] userKeys, Date startDate, Date endDate,
            List<ProjectConfigInfo> projects) {
        if (startDate == null) {
            startDate = DateUtils.INVALID_START_DATE;
        }
        if (endDate == null) {
            endDate = DateUtils.INVALID_END_DATE;
        }
        startDate = DateUtils.standardizeDate(startDate, 0, 0, 0, 0);
        endDate = DateUtils.standardizeDate(endDate, 23, 59, 59, 999);
        StringBuilder query = new StringBuilder();
        if (ObjectUtils.isNotEmpty(projects)) {
            String temp = "(";
            int i = 0;
            for (ProjectConfigInfo project : projects) {
                i++;
                if (projects.size() == i) {
                    temp += "'" + project.getProjectID() + "'";
                } else {
                    temp += "'" + project.getProjectID() + "',";
                }

            }
            temp += ") AND ";
            query.append("PROJECT_ID IN ");
            query.append(temp);
            // PROJECT_ID = ?
        }
        if (!"null".equals(userKeys[0])) {
            String userQuerry = "(";
            int i = 0;
            for (String user : userKeys) {
                i++;
                if (userKeys.length == i) {
                    userQuerry += "'" + user + "'";
                } else {
                    userQuerry += "'" + user + "'" + ',';
                }

            }
            userQuerry += ")";
            query.append("USER_KEY IN ");
            query.append(userQuerry);
            query.append(" AND (START_DATE BETWEEN ? AND ?) AND STATUS IN ('Pending','Approved')");
        } else {
            query.append(" (START_DATE BETWEEN ? AND ?) AND STATUS IN ('Pending','Approved')");
        }
        WorkLog[] listWorkLog = ao.find(
                WorkLog.class,
                Query.select().order("USER_KEY ASC")
                        .where(query.toString(), startDate, endDate));
        return newArrayList(listWorkLog);
    }

    @Override
    public List<WorkLog> getWorkLogNotRejectedByUsersInDuration(
            String[] userKeys, Date startDate, Date endDate) {
        if (startDate == null) {
            startDate = DateUtils.INVALID_START_DATE;
        }
        if (endDate == null) {
            endDate = DateUtils.INVALID_END_DATE;
        }
        startDate = DateUtils.standardizeDate(startDate, 0, 0, 0, 0);
        endDate = DateUtils.standardizeDate(endDate, 23, 59, 59, 999);
        StringBuilder query = new StringBuilder();
        if (!"null".equals(userKeys[0])) {
            String userQuerry = "(";
            int i = 0;
            for (String user : userKeys) {
                i++;
                if (userKeys.length == i) {
                    userQuerry += "'" + user + "'";
                } else {
                    userQuerry += "'" + user + "'" + ',';
                }

            }
            userQuerry += ")";
            query.append("USER_KEY IN ");
            query.append(userQuerry);
            query.append(" AND (START_DATE BETWEEN ? AND ?) AND STATUS IN ('Pending','Approved')");
        } else {
            query.append(" (START_DATE BETWEEN ? AND ?) AND STATUS IN ('Pending','Approved')");
        }
        WorkLog[] listWorkLog = ao.find(
                WorkLog.class,
                Query.select().order("USER_KEY ASC")
                        .where(query.toString(), startDate, endDate));
        return newArrayList(listWorkLog);
    }

    @Override
    public List<WorkLog> getWorkLogNotRejectedByUserNameIgnoreCaseInDuration(
            String[] userKeys, Date startDate, Date endDate) {
        if (startDate == null) {
            startDate = DateUtils.INVALID_START_DATE;
        }
        if (endDate == null) {
            endDate = DateUtils.INVALID_END_DATE;
        }
        startDate = DateUtils.standardizeDate(startDate, 0, 0, 0, 0);
        endDate = DateUtils.standardizeDate(endDate, 23, 59, 59, 999);
        StringBuilder query = new StringBuilder();
        if (!"null".equals(userKeys[0])) {
            String userQuerry = "(";
            int i = 0;
            for (String user : userKeys) {
                i++;
                if (userKeys.length == i) {
                    userQuerry += "'" + user + "'";
                } else {
                    userQuerry += "'" + user + "'" + ',';
                }

            }
            userQuerry += ")";
            query.append("LOWER(USER_KEY) IN ");
            query.append(userQuerry);
            query.append(" AND (START_DATE BETWEEN ? AND ?) AND STATUS IN ('Pending','Approved')");
        } else {
            query.append(" (START_DATE BETWEEN ? AND ?) AND STATUS IN ('Pending','Approved')");
        }
        WorkLog[] listWorkLog = ao.find(
                WorkLog.class,
                Query.select().order("USER_KEY ASC")
                        .where(query.toString(), startDate, endDate));
        return newArrayList(listWorkLog);
    }

    @Override
    public List<WorkLog> getAllWorkLogByUserNameIgnoreCaseInDuration(
            List<User> users, Date startDate, Date endDate) {
        if (startDate == null) {
            startDate = DateUtils.INVALID_START_DATE;
        }
        if (endDate == null) {
            endDate = DateUtils.INVALID_END_DATE;
        }
        startDate = DateUtils.standardizeDate(startDate, 0, 0, 0, 0);
        endDate = DateUtils.standardizeDate(endDate, 23, 59, 59, 999);
        StringBuilder query = new StringBuilder();
        if (!users.isEmpty()) {
            String userQuerry = "(";
            int i = 0;
            int size = users.size();
            for (User user : users) {
                i++;
                if (size == i) {
                    userQuerry += "'" + user.getName().toLowerCase() + "'";
                } else {
                    userQuerry += "'" + user.getName().toLowerCase() + "'"
                            + ',';
                }
            }
            userQuerry += ")";
            query.append("LOWER(USER_KEY) IN ");
            query.append(userQuerry);
            query.append(" AND (START_DATE BETWEEN ? AND ?)");
        } else {
            query.append(" (START_DATE BETWEEN ? AND ?)");
        }
        WorkLog[] listWorkLog = ao.find(
                WorkLog.class,
                Query.select().order("USER_KEY ASC")
                        .where(query.toString(), startDate, endDate));
        return newArrayList(listWorkLog);
    }

    @Override
    public Map<String, WorkLogForReportDetail> getMapsWorkLogForIssue(
            List<ProjectConfigInfo> projects, List<String> listStatus,
            Date startDate, Date endDate) {
        if (startDate == null) {
            startDate = DateUtils.INVALID_START_DATE;
        }
        if (endDate == null) {
            endDate = DateUtils.INVALID_END_DATE;
        }
        startDate = DateUtils.standardizeDate(startDate, 0, 0, 0, 0);
        endDate = DateUtils.standardizeDate(endDate, 23, 59, 59, 999);
        Map<String, WorkLogForReportDetail> result = new HashMap<String, WorkLogForReportDetail>();
        StringBuilder query = new StringBuilder();
        if (!projects.isEmpty()) {
            String temp = "(";
            int i = 0;
            for (ProjectConfigInfo project : projects) {
                i++;
                if (projects.size() == i) {
                    temp += "'" + project.getProjectID() + "'";
                } else {
                    temp += "'" + project.getProjectID() + "',";
                }
            }
            temp += ") ";
            query.append("PROJECT_ID IN ");
            query.append(temp);
            // PROJECT_ID = ?
        } else {
            return result;
        }
        List<WorkLog> workLogs = newArrayList(ao.find(WorkLog.class, Query
                .select().order("USER_KEY ASC").where(query.toString())));
        WorkLogForReportDetail mapValue = null;
        WorkLogForReportDetail mapListValue = null;
        double logged = 0;
        double approved = 0;
        List<WorkLog> workLogsForMap = null;
        Map<Long, List<WorkLog>> maps = null;
        Set<String> issueKeys = new HashSet<String>();
        for (WorkLog workLog : workLogs) {
            issueKeys.add(workLog.getIssueKey());
            if (!ObjectUtils.isNotEmpty(result.get(workLog.getIssueKey()))) {
                mapListValue = new WorkLogForReportDetail();
                mapValue = new WorkLogForReportDetail();
                if (!StaticTokenUtil.REJECT.equals(workLog.getStatus())) {
                    if (StaticTokenUtil.APPROVE.equals(workLog.getStatus())) {
                        approved = Double.parseDouble(workLog.getWorkPerDay());
                        mapValue.setApproved(approved);
                    } else {
                        mapValue.setApproved(0);
                    }
                    logged = Double.parseDouble(workLog.getWorkPerDay());
                    mapValue.setLogged(logged);
                } else {
                    mapValue.setLogged(0);
                    mapValue.setApproved(0);
                }
                result.put(workLog.getIssueKey(), mapValue);
            } else {
                mapValue = result.get(workLog.getIssueKey());
                if (!StaticTokenUtil.REJECT.equals(workLog.getStatus())) {
                    if (StaticTokenUtil.APPROVE.equals(workLog.getStatus())) {
                        approved = mapValue.getApproved()
                                + Double.parseDouble(workLog.getWorkPerDay());
                    } else {
                        approved = mapValue.getApproved();
                    }
                    logged = mapValue.getLogged()
                            + Double.parseDouble(workLog.getWorkPerDay());
                } else {
                    approved = mapValue.getApproved();
                    logged = mapValue.getLogged();
                }
                mapValue.setLogged(logged);
                mapValue.setApproved(approved);
                result.put(workLog.getIssueKey(), mapValue);
            }
            if (!ObjectUtils.isNotEmpty(result.get(TOTAL_WORK_LOGS))) {
                if (listStatus.contains(workLog.getStatus())) {
                    if (!startDate.after(workLog.getStartDate())
                            && !endDate.before(workLog.getStartDate())) {
                        mapListValue = new WorkLogForReportDetail();
                        workLogsForMap = new ArrayList<WorkLog>();
                        maps = new HashMap<Long, List<WorkLog>>();
                        workLogsForMap.add(workLog);
                        maps.put(workLog.getProjectId(), workLogsForMap);
                        mapListValue.setMapWorkLog(maps);
                        result.put(TOTAL_WORK_LOGS, mapListValue);
                    }
                }
            } else {
                if (listStatus.contains(workLog.getStatus())) {
                    if (!startDate.after(workLog.getStartDate())
                            && !endDate.before(workLog.getStartDate())) {
                        mapListValue = result.get(TOTAL_WORK_LOGS);
                        maps = mapListValue.getMapWorkLog();
                        if (!ObjectUtils.isNotEmpty(maps.get(workLog
                                .getProjectId()))) {
                            workLogsForMap = new ArrayList<WorkLog>();
                            workLogsForMap.add(workLog);
                            maps.put(workLog.getProjectId(), workLogsForMap);
                            mapListValue.setMapWorkLog(maps);
                            result.put(TOTAL_WORK_LOGS, mapListValue);
                        } else {
                            workLogsForMap = maps.get(workLog.getProjectId());
                            workLogsForMap.add(workLog);
                            maps.put(workLog.getProjectId(), workLogsForMap);
                            mapListValue.setMapWorkLog(maps);
                            result.put(TOTAL_WORK_LOGS, mapListValue);
                        }
                    }
                }
            }
        }

        WorkLogForReportDetail mapIssue = new WorkLogForReportDetail();
        mapIssue.setIssueKeys(issueKeys);
        result.put(TOTAL_ISSUE_WORK_LOGS, mapIssue);
        return result;
    }

    @Override
    public List<WorkLog> getWorkLogByUserInDuraiton(ApplicationUser user,
                                                    Date startDate, Date endDate) {
        WorkLog[] listWorkLog = ao
                .find(WorkLog.class,
                        Query.select()
                                .order("ISSUE_KEY ASC")
                                .where("USER_KEY = ? AND START_DATE >= ? AND START_DATE <= ?",
                                        user.getName(), startDate, endDate));
        return newArrayList(listWorkLog);
    }

    @Override
    public List<WorkLog> getAllPendingWLNotAfterDateByProjectId(Long projectId,
                                                                Date dateToQuery) {
        WorkLog[] listWorkLog = ao.find(
                WorkLog.class,
                Query.select().where(
                        "PROJECT_ID = ? AND CREATE_DATE <= ? AND STATUS =?",
                        projectId, dateToQuery, StaticTokenUtil.PENDING));
        return newArrayList(listWorkLog);
    }

    @Override
    public List<WorkLog> getAllPendingWLNotAfterDate(Date dateToQuery) {
        WorkLog[] listWorkLog = ao.find(
                WorkLog.class,
                Query.select().where("CREATE_DATE <= ? AND STATUS =?",
                        dateToQuery, StaticTokenUtil.PENDING));
        return newArrayList(listWorkLog);
    }

    @Override
    public List<WorkLog> getAllWorkLogs() {
        WorkLog[] listWorkLog = ao.find(WorkLog.class);
        return newArrayList(listWorkLog);
    }

    @Override
    public void deleteInvalidWorlog(int idWorkLog) {
        ao.delete(getWorkLogById(idWorkLog));
    }

    @Override
    public List<WorkLog> getAllPendingWorklogInDuration(Date startDate,
                                                        Date endDate) {
        startDate = DateUtils.standardizeDate(startDate, 0, 0, 0, 0);
        endDate = DateUtils.standardizeDate(endDate, 23, 59, 59, 999);
        WorkLog[] listWorkLog = ao.find(
                WorkLog.class,
                Query.select().where(
                        "STATUS = ? AND START_DATE >= ? AND START_DATE <= ?",
                        StaticTokenUtil.PENDING, startDate, endDate));
        return newArrayList(listWorkLog);
    }

    @Override
    public List<WorkLog> getAllWorkLogForAdmin(Long projectId,
                                               int currentPageNumber, int recordInPage, String userKey,
                                               Date startDate, Date endDate, String status, String typeOfWork,
                                               List<String> components, List<String> versions,
                                               List<String> userKeys) {
        Query query = queryAllWorkLogWithListComponentForAdmin(projectId,
                userKey, startDate, endDate, status, typeOfWork, components,
                versions, userKeys);
        if (query == null) {
            return new ArrayList<WorkLog>();
        } else {
            query.limit(recordInPage);
            query.offset((currentPageNumber - 1) * recordInPage);
            query.order("START_DATE DESC");
            WorkLog[] listWorkLog = ao.find(WorkLog.class, query);
            return newArrayList(listWorkLog);
        }
    }

    @Override
    public List<WorkLog> getWorkLogPendingForAdmin(Long projectId,
                                                   int currentPageNumber, int recordInPage, String userName,
                                                   Date startDate, Date endDate, String typeOfWork,
                                                   List<String> components, List<String> versions,
                                                   List<String> userKeys) {
        Query query = queryWorkLogPendingForAdmin(projectId, userName,
                startDate, endDate, typeOfWork, components, versions, userKeys);
        if (query == null) {
            return new ArrayList<WorkLog>();
        } else {
            query.limit(recordInPage);
            query.offset((currentPageNumber - 1) * recordInPage);
            query.order("STATUS DESC, START_DATE DESC");
            WorkLog[] listWorkLog = ao.find(WorkLog.class, query);
            return newArrayList(listWorkLog);
        }
    }

    private Query queryAllWorkLogWithListComponentForAdmin(Long projectId,
                                                           String userKey, Date startDate, Date endDate, String status,
                                                           String typeOfWork, List<String> componentIds,
                                                           List<String> versionIds, List<String> userKeys) {
        if (startDate == null) {
            startDate = DateUtils.INVALID_START_DATE;
        }
        if (endDate == null) {
            endDate = DateUtils.INVALID_END_DATE;
        }
        startDate = DateUtils.standardizeDate(startDate, 0, 0, 0, 0);
        endDate = DateUtils.standardizeDate(endDate, 23, 59, 59, 999);
        List<Object> listParam = new ArrayList<Object>();
        listParam.add(projectId);
        listParam.add(startDate);
        listParam.add(endDate);
        listParam.add(endDate);
        String querryString = "PROJECT_ID = ? AND START_DATE >= ? AND START_DATE <= ? AND (END_DATE <= ? OR END_DATE IS NULL)";

        if (!(SELECT_ALL.equals(status) || status == null)) {
            if ("A&P".equals(status)) {
                querryString += " AND STATUS IN ('Pending','Approved')";
            } else {
                querryString += " AND STATUS = ?";
                listParam.add(status);
            }
        }
        if (!(userKey == null || userKey == "")) {
            querryString += " AND USER_KEY = ?";
            listParam.add(userKey);
        } else if (userKeys != null && userKeys.size() > 0) {
            querryString += "AND (";
            for (String user : userKeys) {
                if (userKeys.indexOf(user) == 0) {
                    querryString += "USER_KEY = ?";
                } else {
                    querryString += "OR USER_KEY = ?";
                }
            }
            querryString += ")";
            listParam.addAll(userKeys);
        }
        if (!(SELECT_ALL.equals(typeOfWork) || typeOfWork == null)) {
            querryString += " AND TYPE_OF_WORK = ?";
            listParam.add(typeOfWork);
        }
        if (!(componentIds == null || componentIds.size() == 0)) {
            if (!(versionIds == null || versionIds.size() == 0)) {
                List<Issue> issues = getIssuesWithVersionList(versionIds);
                List<String> listIssueKey = new ArrayList<String>();
                for (Issue issue : issues) {
                    List<ProjectComponent> listComponent = (List<ProjectComponent>) issue
                            .getComponentObjects();
                    if (listComponent.size() > 0) {
                        if (componentIds.contains(listComponent.get(0).getId()
                                .toString())) {
                            listIssueKey.add(issue.getKey());
                        }
                    }
                }
                if (listIssueKey.size() > 0) {
                    querryString += "AND (";
                    for (String issueKey : listIssueKey) {
                        if (listIssueKey.indexOf(issueKey) == 0) {
                            querryString += "ISSUE_KEY = ?";
                        } else {
                            querryString += "OR ISSUE_KEY = ?";
                        }
                    }
                    querryString += ")";
                    listParam.addAll(listIssueKey);
                } else {
                    return null;
                }
            } else {
                List<Issue> issues = this
                        .getIssuesWithComponentList(componentIds);
                List<String> listIssueKey = new ArrayList<String>();
                for (Issue issue : issues) {
                    listIssueKey.add(issue.getKey());
                }
                if (listIssueKey.size() > 0) {
                    querryString += "AND (";
                    for (String issueKey : listIssueKey) {
                        if (listIssueKey.indexOf(issueKey) == 0) {
                            querryString += "ISSUE_KEY = ?";
                        } else {
                            querryString += "OR ISSUE_KEY = ?";
                        }
                    }
                    querryString += ")";
                    listParam.addAll(listIssueKey);
                } else {
                    return null;
                }
            }
        } else {
            if (!(versionIds == null || versionIds.size() == 0)) {
                List<Issue> issues = this.getIssuesWithVersionList(versionIds);
                List<String> listIssueKey = new ArrayList<String>();
                for (Issue issue : issues) {
                    listIssueKey.add(issue.getKey());
                }
                if (listIssueKey.size() > 0) {
                    querryString += "AND (";
                    for (String issueKey : listIssueKey) {
                        if (listIssueKey.indexOf(issueKey) == 0) {
                            querryString += "ISSUE_KEY = ?";
                        } else {
                            querryString += "OR ISSUE_KEY = ?";
                        }
                    }
                    querryString += ")";
                    listParam.addAll(listIssueKey);
                } else {
                    return null;
                }
            }
        }
        Query query = Query.select().where(querryString, listParam.toArray());
        return query;
    }

    private Query queryWorkLogPendingForAdmin(Long projectId, String userKey,
                                              Date startDate, Date endDate, String typeOfWork,
                                              List<String> componentIds, List<String> versionIds,
                                              List<String> userKeys) {
        if (startDate == null) {
            startDate = DateUtils.INVALID_START_DATE;
        }
        if (endDate == null) {
            endDate = DateUtils.INVALID_END_DATE;
        }
        startDate = DateUtils.standardizeDate(startDate, 0, 0, 0, 0);
        endDate = DateUtils.standardizeDate(endDate, 23, 59, 59, 999);
        List<Object> listParam = new ArrayList<Object>();
        listParam.add(projectId);
        listParam.add(startDate);
        listParam.add(endDate);
        listParam.add(endDate);
        listParam.add(StaticTokenUtil.REJECT);
        String querryString = "PROJECT_ID = ? AND START_DATE >= ? AND START_DATE <= ? AND (END_DATE <= ? OR END_DATE IS NULL) AND STATUS != ?";
        if (!(userKey == null || userKey == "")) {
            querryString += " AND USER_KEY = ?";
            listParam.add(userKey);
        }
        if (userKeys != null && userKeys.size() > 0) {
            querryString += "AND (";
            for (String user : userKeys) {
                if (userKeys.indexOf(user) == 0) {
                    querryString += "USER_KEY = ?";
                } else {
                    querryString += "OR USER_KEY = ?";
                }
            }
            querryString += ")";
            listParam.addAll(userKeys);
        }
        if (!(SELECT_ALL.equals(typeOfWork) || typeOfWork == null)) {
            querryString += " AND TYPE_OF_WORK = ?";
            listParam.add(typeOfWork);
        }
        if (!(componentIds == null || componentIds.size() == 0)) {
            if (!(versionIds == null || versionIds.size() == 0)) {
                List<Issue> issues = getIssuesWithVersionList(versionIds);
                List<String> listIssueKey = new ArrayList<String>();
                for (Issue issue : issues) {
                    List<ProjectComponent> listComponent = (List<ProjectComponent>) issue
                            .getComponentObjects();
                    if (listComponent.size() > 0) {
                        if (componentIds.contains(listComponent.get(0).getId()
                                .toString())) {
                            listIssueKey.add(issue.getKey());
                        }
                    }
                }
                if (listIssueKey.size() > 0) {
                    querryString += "AND (";
                    for (String issueKey : listIssueKey) {
                        if (listIssueKey.indexOf(issueKey) == 0) {
                            querryString += "ISSUE_KEY = ?";
                        } else {
                            querryString += "OR ISSUE_KEY = ?";
                        }
                    }
                    querryString += ")";
                    listParam.addAll(listIssueKey);
                } else {
                    return null;
                }
            } else {
                List<Issue> issues = getIssuesWithComponentList(componentIds);
                List<String> listIssueKey = new ArrayList<String>();
                for (Issue issue : issues) {
                    listIssueKey.add(issue.getKey());
                }
                if (listIssueKey.size() > 0) {
                    querryString += "AND (";
                    for (String issueKey : listIssueKey) {
                        if (listIssueKey.indexOf(issueKey) == 0) {
                            querryString += "ISSUE_KEY = ?";
                        } else {
                            querryString += "OR ISSUE_KEY = ?";
                        }
                    }
                    querryString += ")";
                    listParam.addAll(listIssueKey);
                } else {
                    return null;
                }
            }
        } else {
            if (!(versionIds == null || versionIds.size() == 0)) {
                List<Issue> issues = this.getIssuesWithVersionList(versionIds);
                List<String> listIssueKey = new ArrayList<String>();
                for (Issue issue : issues) {
                    listIssueKey.add(issue.getKey());
                }
                if (listIssueKey.size() > 0) {
                    querryString += "AND (";
                    for (String issueKey : listIssueKey) {
                        if (listIssueKey.indexOf(issueKey) == 0) {
                            querryString += "ISSUE_KEY = ?";
                        } else {
                            querryString += "OR ISSUE_KEY = ?";
                        }
                    }
                    querryString += ")";
                    listParam.addAll(listIssueKey);
                } else {
                    return null;
                }
            }
        }
        Query query = Query.select().where(querryString, listParam.toArray());
        return query;
    }

    @Override
    public List<WorkLog> getWorkLogByMutableIssueKeys(List<MutableIssue> issues) {
        List<WorkLog> worklogs = new ArrayList<WorkLog>();
        String issueQuerry;
        issueQuerry = "(";
        int i = 0;
        for (MutableIssue issue : issues) {
            i++;
            if (issues != null) {
                if (issues.size() == i) {
                    issueQuerry += "'" + issue.getKey() + "'";
                } else {
                    issueQuerry += "'" + issue.getKey() + "'" + ',';
                }
            }
        }
        issueQuerry += ")";
        if (!"()".equals(issueQuerry)) {
            WorkLog[] listWorkLog = ao.find(
                    WorkLog.class,
                    Query.select()
                            .order("START_DATE DESC")
                            .where("(STATUS = '" + StaticTokenUtil.APPROVE
                                    + "' OR STATUS ='"
                                    + StaticTokenUtil.PENDING
                                    + "') AND ISSUE_KEY IN " + issueQuerry));
            worklogs = newArrayList(listWorkLog);
        }
        return worklogs;
    }

    @Override
    public List<WorkLog> getWorkLogNotRejectedByProjectId(Long projectId) {
        List<WorkLog> listWorkLog;
        WorkLog[] workLogs = ao.find(
                WorkLog.class,
                Query.select().where(
                        "PROJECT_ID = ? AND STATUS IN ('Pending','Approved')",
                        projectId));
        if (workLogs == null) {
            listWorkLog = new ArrayList<WorkLog>();
        } else {
            listWorkLog = newArrayList(workLogs);
        }
        return listWorkLog;
    }

    @Override
    public double getOriginalEstimateByStringValue(String originalText) {
        try {
            return CustomFieldUtil.convertOriginalEstimateStringToHours(
                    originalText, pluginSettings);
        } catch (NullPointerException e) {
            return 0;
        }
    }

    @Override
    public double getOriginalEstimateOfMutableIssue(MutableIssue issue) {
        return this.getOriginalEstimateOfIssue(issue);
    }

    private final WorkLog createNewWorkLog(String userKey, String userName,
                                           Long projectId, String projectName, String issueKey,
                                           String issueSummary, Date startDate, Date endDate,
                                           String workPerDay, String desc, String typeOfWork, String status,
                                           String comment, Date currentDate) {
        final WorkLog workLog = ao.create(WorkLog.class);
        workLog.setProjectId(projectId);
        workLog.setProjectName(projectName);
        workLog.setUserKey(userKey);
        workLog.setUserName(userName);
        workLog.setIssueKey(issueKey);
        workLog.setIssueName(issueSummary);
        workLog.setStartDate(startDate);
        workLog.setEndDate(endDate);
        workLog.setWorkPerDay(workPerDay);
        workLog.setTypeOfWork(typeOfWork);
        workLog.setDesc(desc);
        workLog.setStatus(status);
        workLog.setCreateDate(currentDate);
        workLog.save();
        return workLog;
    }

    private final List<Issue> getIssuesWithVersionList(List<String> versionIds) {
        List<Version> versions = new ArrayList<Version>();
        List<Issue> issues = new ArrayList<Issue>();
        for (String versionId : versionIds) {
            try {
                versions.add(versionManager.getVersion(Long
                        .parseLong(versionId)));
            } catch (NullPointerException e) {
            }
        }
        for (Version version : versions) {
            issues.addAll(versionManager.getIssuesWithAffectsVersion(version));
            
        }
        return issues;
    }

    private  List<Issue> getIssuesWithVerionsType(List<String> versionIds, String issueType){
    	List<Issue> issues = new ArrayList<Issue>();
    	List<Version> versions = new ArrayList<Version>();
		for (String versionId : versionIds) {
			try {
				versions.add(versionManager.getVersion(Long
						.parseLong(versionId)));
			} catch (NullPointerException e) {
				e.printStackTrace();
			}
		}
		if(!SELECT_ALL.equals(issueType)){
			for (Version version : versions) {
				 for (Issue issue : versionManager.getIssuesWithAffectsVersion(version)) {
					 if (issue.getIssueTypeObject().getName().equals(issueType)){
						 issues.add(issue);
					 }
				 }
			}
		}else {
			for (Version version : versions) {
				for (Issue issue : versionManager
						.getIssuesWithAffectsVersion(version)) {
						issues.add(issue);
				}
			}
		}
    	return issues;
    }
    
    
    private final List<Issue> getIssuesWithVersionTypeStatus(String versionId, String issueType, String issueStatus) {
        List<Version> versions = new ArrayList<Version>();
        List<Issue> issues = new ArrayList<Issue>();
        try {
            versions.add(versionManager.getVersion(Long.parseLong(versionId)));
        } catch (NullPointerException e) {
            e.printStackTrace();
        }
        if (!SELECT_ALL.equals(issueType)) {
            if (issueStatus.equals("false")) {
                for (Version version : versions) {
                    for (Issue issue : versionManager.getIssuesWithAffectsVersion(version)) {
                        if (issue.getIssueTypeObject().getName().equals(issueType)
                                && !issue.getStatusObject().getName().equals("Closed")
                                && !issue.getStatusObject().getName().equals("Cancelled")) {
                            issues.add(issue);
                        }
                    }
                }
            } else {
                for (Version version : versions) {
                    for (Issue issue : versionManager.getIssuesWithAffectsVersion(version)) {
                        if (issue.getIssueTypeObject().getName().equals(issueType)) {
                            issues.add(issue);
                        }
                    }
                }
            }
        } else {
            if (issueStatus.equals("false")) {
                for (Version version : versions) {
                    for (Issue issue : versionManager.getIssuesWithAffectsVersion(version)) {
                        if ((!issue.getStatusObject().getName().equals("Closed"))
                                && !issue.getStatusObject().getName().equals("Cancelled")) {
                            issues.add(issue);
                        }
                    }
                }
            } else {
                for (Version version : versions) {
                    issues.addAll(versionManager.getIssuesWithAffectsVersion(version));
                }
            }
        }
        return issues;
    }

    private final List<Issue> getIssuesWithComponentList(
            List<String> componentIds) {
        List<Long> issueIds = new ArrayList<Long>();
        List<ProjectComponent> components = new ArrayList<ProjectComponent>();
        for (String componentId : componentIds) {
            try {
                components.add(projectComponentManager.getProjectComponent(Long
                        .parseLong(componentId)));
            } catch (NullPointerException e) {
            }
        }
        for (ProjectComponent component : components) {
            issueIds.addAll(projectComponentManager
                    .getIssueIdsWithComponent(component));
        }
        return issueManager.getIssueObjects(issueIds);
    }

    private final List<Issue> getIssuesWithComponent(
            ProjectComponent projectComponent) {
        List<Long> issueIds = new ArrayList<Long>();
        issueIds.addAll(projectComponentManager
                .getIssueIdsWithComponent(projectComponent));
        return issueManager.getIssueObjects(issueIds);
    }

    private final List<Issue> getIssuesWithComponent(String componentId) {
        List<Long> issueIds = new ArrayList<Long>();
        try {
            issueIds.addAll(projectComponentManager
                    .getIssueIdsWithComponent(projectComponentManager.find(Long.parseLong(componentId))));
        } catch (EntityNotFoundException e) {
            return null;
        }
        return issueManager.getIssueObjects(issueIds);
    }

    // For Import
    @Override
    public WorkLog addWorkLog(User user, Issue issue, Date startDate,
                              Date endDate, String workPerDay, String desc, String typeOfWork,
                              String status, String comment) {
        final WorkLog workLog = ao.create(WorkLog.class);
        workLog.setProjectId(issue.getProjectId());
        workLog.setProjectName(issue.getProjectObject().getName());
        workLog.setUserKey(user.getDisplayName());
        workLog.setUserName(user.getName());
        workLog.setIssueKey(issue.getKey());
        workLog.setIssueName(issue.getSummary());
        workLog.setStartDate(startDate);
        workLog.setEndDate(endDate);
        workLog.setWorkPerDay(workPerDay);
        workLog.setTypeOfWork(typeOfWork);
        workLog.setDesc(desc);
        workLog.setStatus(status);
        workLog.setCreateDate(new Date());
        workLog.save();
        ApplicationUser appuser = jiraAuthenticationContext.getUser();
        workLogHistoryService.createWorkLogHistory(appuser.getDisplayName(),
                issue.getKey(), CREATE_WORKLOG, workLog);
        return workLog;
    }

    private final boolean isUserInRoleExcludingDefaultGroup(Project project,
                                                            ApplicationUser user, String roleName) {
        try {
            ProjectRole role = projectRoleManager.getProjectRole(roleName);
            ProjectRoleActors actors = projectRoleManager.getProjectRoleActors(
                    role, project);
            Set<RoleActor> listRoleActor = actors
                    .getRoleActorsByType(ProjectRoleActor.USER_ROLE_ACTOR_TYPE);
            Set<User> listUser = new HashSet<User>();
            for (RoleActor roleActor : listRoleActor) {
                listUser.addAll(roleActor.getUsers());
            }
            if (listUser.contains(user.getDirectoryUser())) {
                return true;
            }
        } catch (Exception e) {
            return false;
        }
        return false;
    }

 // project work log -----------------------------------------------
    
	@Override
	public List<WorkLog> getAllWorkLogForAdmin(Long projectId,
			int currentPageNumber, int recordInPage, String userKey,
			Date startDate, Date endDate, String status, String typeOfWork,
			String issueType, List<String> components, List<String> versions) {
		Query query = queryAllWorkLogWithListComponentForAdmin(projectId,
				userKey, startDate, endDate, status, typeOfWork, issueType, components,
				versions);
		if (query == null) {
			return new ArrayList<WorkLog>();
		} else {
			query.limit(recordInPage);
			query.offset((currentPageNumber - 1) * recordInPage);
			query.order("START_DATE DESC");
			WorkLog[] listWorkLog = ao.find(WorkLog.class, query);
			return newArrayList(listWorkLog);
		}
	}

	private Query queryAllWorkLogWithListComponentForAdmin(Long projectId,
			String userKey, Date startDate, Date endDate, String status,
			String typeOfWork, String issueType, List<String> componentIds,
			List<String> versionIds) {
		if (startDate == null) {
			startDate = DateUtils.INVALID_START_DATE;
		}
		if (endDate == null) {
			endDate = DateUtils.INVALID_END_DATE;
		}
		List<Object> listParam = new ArrayList<Object>();
		listParam.add(projectId);
		listParam.add(startDate);
		listParam.add(endDate);
		listParam.add(endDate);
		String querryString = "PROJECT_ID = ? AND START_DATE >= ? AND START_DATE <= ? AND (END_DATE <= ? OR END_DATE IS NULL)";

		if (!(SELECT_ALL.equals(status) || status == null)) {
			if ("A&P".equals(status)) {
				querryString += " AND STATUS IN ('Pending','Approved')";
			} else {
				querryString += " AND STATUS = ?";
				listParam.add(status);
			}
		}
		if (!(userKey == null || userKey == "")) {
			querryString += " AND USER_KEY = ?";
			listParam.add(userKey);
		}
		if (!(SELECT_ALL.equals(typeOfWork) || typeOfWork == null)) {
			querryString += " AND TYPE_OF_WORK = ?";
			listParam.add(typeOfWork);
		}
		if (!(componentIds == null || componentIds.size() == 0)) {
			if (!(versionIds == null || versionIds.size() == 0)) {
				
				List<Issue> issues = getIssuesWithVerionsType(versionIds, issueType);
				List<String> listIssueKey = new ArrayList<String>();
				for (Issue issue : issues) {
					List<ProjectComponent> listComponent = (List<ProjectComponent>) issue
							.getComponentObjects();
					if(listComponent.size()>0){
						if (componentIds.contains(listComponent.get(0).getId().toString())){
							listIssueKey.add(issue.getKey());
						}
					}
                }
				if (listIssueKey.size() > 0) {
                    querryString += " AND(";
                    for (String issueKey : listIssueKey) {
                        if (listIssueKey.indexOf(issueKey) == 0) {
                            querryString += "ISSUE_KEY = ? ";
                        } else {
                            querryString += "OR ISSUE_KEY = ? ";
                        }
                    }
                    querryString +=" )";
                    listParam.addAll(listIssueKey);
                } else {
                    return null;
                }

			} else {
				List<Issue> issues = getIssuesWithComponentList(componentIds);
				List<String> listIssueKey = new ArrayList<String>();
				if(!SELECT_ALL.equals(issueType)){
					for (Issue issue : issues) {
						if (issue.getIssueTypeObject().getName().equals(issueType)){
							 listIssueKey.add(issue.getKey());
						 }
	                } 
				}else{
					for (Issue issue : issues) {
							 listIssueKey.add(issue.getKey());
	                } 
                }
                
                if (listIssueKey.size() > 0) {
                    querryString += " AND(";
                    for (String issueKey : listIssueKey) {
                        if (listIssueKey.indexOf(issueKey) == 0) {
                            querryString += " ISSUE_KEY = ? ";
                        } else {
                            querryString += " OR ISSUE_KEY = ? ";
                        }
                    }
                    querryString +=" )";
                    listParam.addAll(listIssueKey);
                } else {
                    return null;
                }
			}
		} else {
			if (!(versionIds == null || versionIds.size() == 0)) {
				List<Issue> issues = getIssuesWithVerionsType(versionIds, issueType);
				
                List<String> listIssueKey = new ArrayList<String>();
                for (Issue issue : issues) {
                    listIssueKey.add(issue.getKey());
                }
                if (listIssueKey.size() > 0) {
                    querryString += " AND (  ";
                    for (String issueKey : listIssueKey) {
                        if (listIssueKey.indexOf(issueKey) == 0) {
                            querryString += " ISSUE_KEY = ? ";
                        } else {
                            querryString += " OR ISSUE_KEY = ? ";
                        }
                    }
                    querryString +=" )";
                    listParam.addAll(listIssueKey);
                } else {
                    return null;
                }
			}else{
				try {
					List<Issue> listIssue = issueManager.getIssueObjects(issueManager.getIssueIdsForProject(projectId));
					List<String> listIssueKey = new ArrayList<String>();
					if (listIssue.size() > 0) {
						if (!SELECT_ALL.equals(issueType)) {
							for (Issue issue : listIssue) {
								if (issue.getIssueTypeObject().getName().equals(issueType)) {
									listIssueKey.add(issue.getKey());
								}
							}
						}else{
							for (Issue issue : listIssue) {
								listIssueKey.add(issue.getKey());
							}
						}
						
						if (listIssueKey.size() > 0) {
		                    querryString += " AND (  ";
		                    for (String issueKey : listIssueKey) {
		                        if (listIssueKey.indexOf(issueKey) == 0) {
		                            querryString += " ISSUE_KEY = ? ";
		                        } else {
		                            querryString += " OR ISSUE_KEY = ? ";
		                        }
		                    }
		                    querryString +=" )";
		                    listParam.addAll(listIssueKey);
		                } else {
		                    return null;
		                }
					}else{
						return null;
					}
				} catch (GenericEntityException e) {
					e.printStackTrace();
				}
			}
		}
		System.out.print(querryString);
		Query query = Query.select().where(querryString, listParam.toArray());
		return query;
	}
	
	
	@Override
	public List<WorkLog> getAllWorkLogForAdmin(Long projectId,
			int currentPageNumber, int recordInPage, String userKey,
			Date startDate, Date endDate, String status, String typeOfWork,
			String issueType, List<String> components, List<String> versions,
			List<String> userKeys) {
		Query query = queryAllWorkLogWithListComponentForAdmin(projectId,
				userKey, startDate, endDate, status, typeOfWork, issueType, components,
				versions, userKeys);
		if (query == null) {
			return new ArrayList<WorkLog>();
		} else {
			query.limit(recordInPage);
			query.offset((currentPageNumber - 1) * recordInPage);
			query.order("START_DATE DESC");
			WorkLog[] listWorkLog = ao.find(WorkLog.class, query);
			return newArrayList(listWorkLog);
		}
	}
	
	private Query queryAllWorkLogWithListComponentForAdmin(Long projectId,
			String userKey, Date startDate, Date endDate, String status,
			String typeOfWork, String issueType, List<String> componentIds,
			List<String> versionIds, List<String> userKeys) {
		if (startDate == null) {
			startDate = DateUtils.INVALID_START_DATE;
		}
		if (endDate == null) {
			endDate = DateUtils.INVALID_END_DATE;
		}
		startDate = DateUtils.standardizeDate(startDate, 0, 0, 0, 0);
		endDate = DateUtils.standardizeDate(endDate, 23, 59, 59, 999);
		List<Object> listParam = new ArrayList<Object>();
		listParam.add(projectId);
		listParam.add(startDate);
		listParam.add(endDate);
		listParam.add(endDate);
		String querryString = "PROJECT_ID = ? AND START_DATE >= ? AND START_DATE <= ? AND (END_DATE <= ? OR END_DATE IS NULL)";

		if (!(SELECT_ALL.equals(status) || status == null)) {
			if ("A&P".equals(status)) {
				querryString += " AND STATUS IN ('Pending','Approved')";
			} else {
				querryString += " AND STATUS = ?";
				listParam.add(status);
			}
		}
		if (!(userKey == null || userKey == "")) {
			querryString += " AND USER_KEY = ?";
			listParam.add(userKey);
		} else if (userKeys != null && userKeys.size() > 0) {
			querryString += "AND (";
			for (String user : userKeys) {
				if (userKeys.indexOf(user) == 0) {
					querryString += " USER_KEY = ?";
				} else {
					querryString += " OR USER_KEY = ?";
				}
			}
			querryString += ")";
			listParam.addAll(userKeys);
		}
		if (!(SELECT_ALL.equals(typeOfWork) || typeOfWork == null)) {
			querryString += " AND TYPE_OF_WORK = ? ";
			listParam.add(typeOfWork);
		}
		if (!(componentIds == null || componentIds.size() == 0)) {
			if (!(versionIds == null || versionIds.size() == 0)) {
				
				List<Issue> issues = getIssuesWithVerionsType(versionIds, issueType);
				List<String> listIssueKey = new ArrayList<String>();
				for (Issue issue : issues) {
					List<ProjectComponent> listComponent = (List<ProjectComponent>) issue
							.getComponentObjects();
					if(listComponent.size()>0){
						if (componentIds.contains(listComponent.get(0).getId().toString())){
							listIssueKey.add(issue.getKey());
						}
					}
                }
				if (listIssueKey.size() > 0) {
                    querryString += " AND(";
                    for (String issueKey : listIssueKey) {
                        if (listIssueKey.indexOf(issueKey) == 0) {
                            querryString += "ISSUE_KEY = ? ";
                        } else {
                            querryString += "OR ISSUE_KEY = ? ";
                        }
                    }
                    querryString +=" )";
                    listParam.addAll(listIssueKey);
                } else {
                    return null;
                }

			} else {
				List<Issue> issues = getIssuesWithComponentList(componentIds);
				List<String> listIssueKey = new ArrayList<String>();
				if(!SELECT_ALL.equals(issueType)){
					for (Issue issue : issues) {
						if (issue.getIssueTypeObject().getName().equals(issueType)){
//							 issues.add(issue);
							 listIssueKey.add(issue.getKey());
						 }
	                } 
				}else{
					for (Issue issue : issues) {
//							 issues.add(issue);
							 listIssueKey.add(issue.getKey());
	                } 
                }
                
                if (listIssueKey.size() > 0) {
                    querryString += " AND(";
                    for (String issueKey : listIssueKey) {
                        if (listIssueKey.indexOf(issueKey) == 0) {
                            querryString += " ISSUE_KEY = ? ";
                        } else {
                            querryString += " OR ISSUE_KEY = ? ";
                        }
                    }
                    querryString +=" )";
                    listParam.addAll(listIssueKey);
                } else {
                    return null;
                }
			}
		} else {
			if (!(versionIds == null || versionIds.size() == 0)) {
				List<Issue> issues = getIssuesWithVerionsType(versionIds, issueType);
				
                List<String> listIssueKey = new ArrayList<String>();
                for (Issue issue : issues) {
                    listIssueKey.add(issue.getKey());
                }
                if (listIssueKey.size() > 0) {
                    querryString += " AND (  ";
                    for (String issueKey : listIssueKey) {
                        if (listIssueKey.indexOf(issueKey) == 0) {
                            querryString += " ISSUE_KEY = ? ";
                        } else {
                            querryString += " OR ISSUE_KEY = ? ";
                        }
                    }
                    querryString +=" )";
                    listParam.addAll(listIssueKey);
                } else {
                    return null;
                }
			}else{
				try {
					List<Issue> listIssue = issueManager.getIssueObjects(issueManager.getIssueIdsForProject(projectId));
					List<String> listIssueKey = new ArrayList<String>();
					if (listIssue.size() > 0) {
						if (!SELECT_ALL.equals(issueType)) {
							for (Issue issue : listIssue) {
								if (issue.getIssueTypeObject().getName().equals(issueType)) {
									listIssueKey.add(issue.getKey());
								}
							}
						}else{
							for (Issue issue : listIssue) {
								listIssueKey.add(issue.getKey());
							}
						}
						
						if (listIssueKey.size() > 0) {
		                    querryString += " AND (  ";
		                    for (String issueKey : listIssueKey) {
		                        if (listIssueKey.indexOf(issueKey) == 0) {
		                            querryString += " ISSUE_KEY = ? ";
		                        } else {
		                            querryString += " OR ISSUE_KEY = ? ";
		                        }
		                    }
		                    querryString +=" )";
		                    listParam.addAll(listIssueKey);
		                } else {
		                    return null;
		                }
					}else{
						return null;
					}
				} catch (GenericEntityException e) {
					e.printStackTrace();
				}
			}
		}
		System.out.print(querryString);
		Query query = Query.select().where(querryString, listParam.toArray());
		
		return query;
	}

	@Override
	public Map<String, Float> totalHourAndRecordAllWorkLogForAdmin(
			Long projectId, String userKey, Date startDate, Date endDate,
			String status, String typeOfWork, String issueType,
			List<String> components, List<String> versions, boolean checkPm,
			boolean checkTeamLead, boolean checkQA, List<String> userKeys) {
		Map<String, Float> maps = new HashMap<String, Float>();
		float totalHour = 0;
		float totalRecord = 0;
		Query query = null;
		if (!checkPm && !checkQA && checkTeamLead) {
			if (userKeys.size() > 0) {
				query = queryAllWorkLogWithListComponentForAdmin(projectId,
						userKey, startDate, endDate, status, typeOfWork, issueType,
						components, versions, userKeys);
			}
		} else {
			query = queryAllWorkLogWithListComponentForAdmin(projectId,
					userKey, startDate, endDate, status, typeOfWork, issueType,
					components, versions);
		}
		if (query == null) {
			maps.put(TOTAL_HOUR, totalHour);
			maps.put(TOTAL_RECORD, totalRecord);
			return maps;
		} else {
			query.order("START_DATE DESC");
			WorkLog[] workLogs = ao.find(WorkLog.class, query);
			List<WorkLog> listWorkLog = newArrayList(workLogs);
			for (WorkLog workLog : listWorkLog) {
				totalHour += Float.valueOf(workLog.getWorkPerDay());
				totalRecord++;
			}
			maps.put(TOTAL_HOUR, totalHour);
			maps.put(TOTAL_RECORD, totalRecord);
			return maps;
		}
	}

	// -----------------pending project worklog
	
	
	@Override
	public Map<String, Float> totalHourWorkLogPendingForAdminTotal(
			Long projectId, String userKey, Date startDate, Date endDate,
			String typeOfWork, String issueType, List<String> components,
			List<String> versions, boolean checkPm, boolean checkTeamLead,
			List<String> userKeys) {
		Map<String, Float> maps = new HashMap<String, Float>();
        float totalHour = 0;
        float totalRecord = 0;
        Query query = null;
        if (!checkPm && checkTeamLead) {
            if (userKeys.size() > 0) {
                query = queryWorkLogPendingForAdmin(projectId, userKey,
                        startDate, endDate, typeOfWork, issueType, components, versions,
                        userKeys);
            }
        } else {
            query = queryWorkLogPendingForAdmin(projectId, userKey, startDate,
                    endDate, typeOfWork, issueType, components, versions);
        }
        if (query == null) {
            maps.put(TOTAL_HOUR, totalHour);
            maps.put(TOTAL_RECORD, totalRecord);
            return maps;
        } else {
            query.order("START_DATE DESC");
            WorkLog[] workLogs = ao.find(WorkLog.class, query);
            List<WorkLog> listWorkLog = newArrayList(workLogs);
            for (WorkLog workLog : listWorkLog) {
                totalHour += Float.valueOf(workLog.getWorkPerDay());
                totalRecord++;

            }
            maps.put(TOTAL_HOUR, totalHour);
            maps.put(TOTAL_RECORD, totalRecord);
            return maps;
        }
	}
	
	private Query queryWorkLogPendingForAdmin(Long projectId, String userKey,
			Date startDate, Date endDate, String typeOfWork, String issueType,
			List<String> componentIds, List<String> versionIds) {
		if (startDate == null) {
			startDate = DateUtils.INVALID_START_DATE;
		}
		if (endDate == null) {
			endDate = DateUtils.INVALID_END_DATE;
		}
		List<Object> listParam = new ArrayList<Object>();
		listParam.add(projectId);
		listParam.add(startDate);
		listParam.add(endDate);
		listParam.add(endDate);
		String querryString = "PROJECT_ID = ? AND START_DATE >= ? AND START_DATE <= ? AND (END_DATE <= ? OR END_DATE IS NULL)";

		if (!(userKey == null || userKey == "")) {
			querryString += " AND USER_KEY = ? ";
			listParam.add(userKey);
		}
		if (!(SELECT_ALL.equals(typeOfWork) || typeOfWork == null)) {
			querryString += " AND TYPE_OF_WORK = ? ";
			listParam.add(typeOfWork);
		}
		if (!(componentIds == null || componentIds.size() == 0)) {
			if (!(versionIds == null || versionIds.size() == 0)) {
				
				List<Issue> issues = getIssuesWithVerionsType(versionIds, issueType);
				List<String> listIssueKey = new ArrayList<String>();
				for (Issue issue : issues) {
					List<ProjectComponent> listComponent = (List<ProjectComponent>) issue
							.getComponentObjects();
					if(listComponent.size()>0){
						if (componentIds.contains(listComponent.get(0).getId().toString())){
							listIssueKey.add(issue.getKey());
						}
					}
                }
				if (listIssueKey.size() > 0) {
                    querryString += " AND(";
                    for (String issueKey : listIssueKey) {
                        if (listIssueKey.indexOf(issueKey) == 0) {
                            querryString += "ISSUE_KEY = ? ";
                        } else {
                            querryString += "OR ISSUE_KEY = ? ";
                        }
                    }
                    querryString +=" )";
                    listParam.addAll(listIssueKey);
                } else {
                    return null;
                }

			} else {
				List<Issue> issues = getIssuesWithComponentList(componentIds);
				List<String> listIssueKey = new ArrayList<String>();
				if(!SELECT_ALL.equals(issueType)){
					for (Issue issue : issues) {
						if (issue.getIssueTypeObject().getName().equals(issueType)){
//							 issues.add(issue);
							 listIssueKey.add(issue.getKey());
						 }
	                } 
				}else{
					for (Issue issue : issues) {
//							 issues.add(issue);
							 listIssueKey.add(issue.getKey());
	                } 
                }
                
                if (listIssueKey.size() > 0) {
                    querryString += " AND(";
                    for (String issueKey : listIssueKey) {
                        if (listIssueKey.indexOf(issueKey) == 0) {
                            querryString += " ISSUE_KEY = ? ";
                        } else {
                            querryString += " OR ISSUE_KEY = ? ";
                        }
                    }
                    querryString +=" )";
                    listParam.addAll(listIssueKey);
                } else {
                    return null;
                }
			}
		} else {
			if (!(versionIds == null || versionIds.size() == 0)) {
				List<Issue> issues = getIssuesWithVerionsType(versionIds, issueType);
				
                List<String> listIssueKey = new ArrayList<String>();
                for (Issue issue : issues) {
                    listIssueKey.add(issue.getKey());
                }
                if (listIssueKey.size() > 0) {
                    querryString += " AND (  ";
                    for (String issueKey : listIssueKey) {
                        if (listIssueKey.indexOf(issueKey) == 0) {
                            querryString += " ISSUE_KEY = ? ";
                        } else {
                            querryString += " OR ISSUE_KEY = ? ";
                        }
                    }
                    querryString +=" )";
                    listParam.addAll(listIssueKey);
                } else {
                    return null;
                }
			}else{
				try {
					List<Issue> listIssue = issueManager.getIssueObjects(issueManager.getIssueIdsForProject(projectId));
					List<String> listIssueKey = new ArrayList<String>();
					if (listIssue.size() > 0) {
						if (!SELECT_ALL.equals(issueType)) {
							for (Issue issue : listIssue) {
								if (issue.getIssueTypeObject().getName().equals(issueType)) {
									listIssueKey.add(issue.getKey());
								}
							}
						}else{
							for (Issue issue : listIssue) {
								listIssueKey.add(issue.getKey());
							}
						}
						
						if (listIssueKey.size() > 0) {
		                    querryString += " AND (  ";
		                    for (String issueKey : listIssueKey) {
		                        if (listIssueKey.indexOf(issueKey) == 0) {
		                            querryString += " ISSUE_KEY = ? ";
		                        } else {
		                            querryString += " OR ISSUE_KEY = ? ";
		                        }
		                    }
		                    querryString +=" )";
		                    listParam.addAll(listIssueKey);
		                } else {
		                    return null;
		                }
					}else{
						return null;
					}
				} catch (GenericEntityException e) {
					e.printStackTrace();
				}
			}
		}
		System.out.print(querryString);
		Query query = Query.select().where(querryString, listParam.toArray());
		return query;
	}
	
	
	private Query queryWorkLogPendingForAdmin(Long projectId, String userKey,
			Date startDate, Date endDate, String typeOfWork, String issueType,
			List<String> componentIds, List<String> versionIds,
			List<String> userKeys) {
		if (startDate == null) {
			startDate = DateUtils.INVALID_START_DATE;
		}
		if (endDate == null) {
			endDate = DateUtils.INVALID_END_DATE;
		}
		startDate = DateUtils.standardizeDate(startDate, 0, 0, 0, 0);
		endDate = DateUtils.standardizeDate(endDate, 23, 59, 59, 999);
		List<Object> listParam = new ArrayList<Object>();
		listParam.add(projectId);
		listParam.add(startDate);
		listParam.add(endDate);
		listParam.add(endDate);
		String querryString = "PROJECT_ID = ? AND START_DATE >= ? AND START_DATE <= ? AND (END_DATE <= ? OR END_DATE IS NULL)";

		if (!(userKey == null || userKey == "")) {
			querryString += " AND USER_KEY = ? ";
			listParam.add(userKey);
		} else if (userKeys != null && userKeys.size() > 0) {
			querryString += "AND (";
			for (String user : userKeys) {
				if (userKeys.indexOf(user) == 0) {
					querryString += " USER_KEY = ? ";
				} else {
					querryString += " OR USER_KEY = ? ";
				}
			}
			querryString += ")";
			listParam.addAll(userKeys);
		}
		if (!(SELECT_ALL.equals(typeOfWork) || typeOfWork == null)) {
			querryString += " AND TYPE_OF_WORK = ? ";
			listParam.add(typeOfWork);
		}
		if (!(componentIds == null || componentIds.size() == 0)) {
			if (!(versionIds == null || versionIds.size() == 0)) {
				
				List<Issue> issues = getIssuesWithVerionsType(versionIds, issueType);
				List<String> listIssueKey = new ArrayList<String>();
				for (Issue issue : issues) {
					List<ProjectComponent> listComponent = (List<ProjectComponent>) issue
							.getComponentObjects();
					if(listComponent.size()>0){
						if (componentIds.contains(listComponent.get(0).getId().toString())){
							listIssueKey.add(issue.getKey());
						}
					}
                }
				if (listIssueKey.size() > 0) {
                    querryString += " AND(";
                    for (String issueKey : listIssueKey) {
                        if (listIssueKey.indexOf(issueKey) == 0) {
                            querryString += "ISSUE_KEY = ? ";
                        } else {
                            querryString += "OR ISSUE_KEY = ? ";
                        }
                    }
                    querryString +=" )";
                    listParam.addAll(listIssueKey);
                } else {
                    return null;
                }

			} else {
				List<Issue> issues = getIssuesWithComponentList(componentIds);
				List<String> listIssueKey = new ArrayList<String>();
				if(!SELECT_ALL.equals(issueType)){
					for (Issue issue : issues) {
						if (issue.getIssueTypeObject().getName().equals(issueType)){
//							 issues.add(issue);
							 listIssueKey.add(issue.getKey());
						 }
	                } 
				}else{
					for (Issue issue : issues) {
//							 issues.add(issue);
							 listIssueKey.add(issue.getKey());
	                } 
                }
                
                if (listIssueKey.size() > 0) {
                    querryString += " AND(";
                    for (String issueKey : listIssueKey) {
                        if (listIssueKey.indexOf(issueKey) == 0) {
                            querryString += " ISSUE_KEY = ? ";
                        } else {
                            querryString += " OR ISSUE_KEY = ? ";
                        }
                    }
                    querryString +=" )";
                    listParam.addAll(listIssueKey);
                } else {
                    return null;
                }
			}
		} else {
			if (!(versionIds == null || versionIds.size() == 0)) {
				List<Issue> issues = getIssuesWithVerionsType(versionIds, issueType);
				
                List<String> listIssueKey = new ArrayList<String>();
                for (Issue issue : issues) {
                    listIssueKey.add(issue.getKey());
                }
                if (listIssueKey.size() > 0) {
                    querryString += " AND (  ";
                    for (String issueKey : listIssueKey) {
                        if (listIssueKey.indexOf(issueKey) == 0) {
                            querryString += " ISSUE_KEY = ? ";
                        } else {
                            querryString += " OR ISSUE_KEY = ? ";
                        }
                    }
                    querryString +=" )";
                    listParam.addAll(listIssueKey);
                } else {
                    return null;
                }
			}else{
				try {
					List<Issue> listIssue = issueManager.getIssueObjects(issueManager.getIssueIdsForProject(projectId));
					List<String> listIssueKey = new ArrayList<String>();
					if (listIssue.size() > 0) {
						if (!SELECT_ALL.equals(issueType)) {
							for (Issue issue : listIssue) {
								if (issue.getIssueTypeObject().getName().equals(issueType)) {
									listIssueKey.add(issue.getKey());
								}
							}
						}else{
							for (Issue issue : listIssue) {
								listIssueKey.add(issue.getKey());
							}
						}
						
						if (listIssueKey.size() > 0) {
		                    querryString += " AND (  ";
		                    for (String issueKey : listIssueKey) {
		                        if (listIssueKey.indexOf(issueKey) == 0) {
		                            querryString += " ISSUE_KEY = ? ";
		                        } else {
		                            querryString += " OR ISSUE_KEY = ? ";
		                        }
		                    }
		                    querryString +=" )";
		                    listParam.addAll(listIssueKey);
		                } else {
		                    return null;
		                }
					}else{
						return null;
					}
				} catch (GenericEntityException e) {
					e.printStackTrace();
				}
			}
		}
		System.out.print(querryString);
		Query query = Query.select().where(querryString, listParam.toArray());
		return query;
	}


	@Override
	public List<WorkLog> getWorkLogPendingForAdmin(Long projectId,
			int currentPageNumber, int recordInPage, String userName,
			Date startDate, Date endDate, String typeOfWork, String issueType,
			List<String> components, List<String> versions,
			List<String> userKeys) {
		Query query = queryWorkLogPendingForAdmin(projectId, userName,
                startDate, endDate, typeOfWork, issueType, components, versions, userKeys);
        if (query == null) {
            return new ArrayList<WorkLog>();
        } else {
            query.limit(recordInPage);
            query.offset((currentPageNumber - 1) * recordInPage);
            query.order("STATUS DESC, START_DATE DESC");
            WorkLog[] listWorkLog = ao.find(WorkLog.class, query);
            return newArrayList(listWorkLog);
        }
	}

	@Override
	public List<WorkLog> getWorkLogPendingForAdmin(Long projectId,
			int currentPageNumber, int recordInPage, String userName,
			Date startDate, Date endDate, String typeOfWork, String issueType,
			List<String> components, List<String> versions) {
		Query query = queryWorkLogPendingForAdmin(projectId, userName,
                startDate, endDate, typeOfWork,issueType, components, versions);
        if (query == null) {
            return new ArrayList<WorkLog>();
        } else {
            query.limit(recordInPage);
            query.offset((currentPageNumber - 1) * recordInPage);
            query.order("STATUS DESC, START_DATE DESC");
            WorkLog[] listWorkLog = ao.find(WorkLog.class, query);
            return newArrayList(listWorkLog);
        }
	}
//////---------------------gantt project work log
	
	@Override
	public List<WorkLogGantt> getGanttForProject(Long projectId,
			Date startDate, Date endDate, String status, String typeOfWork,
			String issueType, List<String> components, List<String> versions) {
		List<WorkLog> listWorkLog = getWorkLogApprovalAndPendingByProjectId(
                projectId, startDate, endDate, status, typeOfWork, issueType, components, versions);
        List<WorkLog> listWorkLogReturn = new ArrayList<WorkLog>();
        Project project = projectManager.getProjectObj(projectId);
        ApplicationUser currentUser = jiraAuthenticationContext.getUser();
        boolean checkPm = checkPM(project, currentUser);
        boolean checkQA = checkQA(project, currentUser);
        boolean checkTl = checkTeamLead(project, currentUser);
        if (checkPm || checkQA) {
            for (WorkLog workLog : listWorkLog) {
                listWorkLogReturn.add(workLog);
            }
        } else if (checkTl) {
            List<String> userKeys = new ArrayList<String>();
            userKeys = projectTeamServiceImpl.getUserByTeamLead(
                    project.getKey(), currentUser.getKey());
            for (WorkLog workLog : listWorkLog) {
                if (userKeys.contains(workLog.getUserKey().toLowerCase())) {
                    listWorkLogReturn.add(workLog);
                }
            }
        }
        return convertWorkLogListToGantt(listWorkLogReturn);
	}
	
	private List<WorkLog> getWorkLogApprovalAndPendingByProjectId(
            Long projectId, Date startDate, Date endDate, String status,String typeOfWork,
            String issueType, List<String> components, List<String> versions) {
        
        WorkLog[] listWorkLog = ao
                .find(WorkLog.class, 
                		queryWorkLogGanttForAdmin(projectId, 
                				startDate, endDate, status, typeOfWork, issueType, components, versions));
        return newArrayList(listWorkLog);
    }
	

	@Override
	public List<WorkLogGantt> getGanttForProject(Long projectId, Integer month,
			Integer year, String status, String typeOfWork, String issueType,
			List<String> components, List<String> versions) {
		 	List<WorkLog> listWorkLog = getWorkLogNotRejectByProject(projectId,
	                month, year, status, typeOfWork, issueType, components, versions);
	        List<WorkLog> listWorkLogReturn = new ArrayList<WorkLog>();
	        ApplicationUser currentUser = jiraAuthenticationContext.getUser();
	        Project project = projectManager.getProjectObj(projectId);
	        boolean checkPm = checkPM(project, currentUser);
	        boolean checkTl = checkTeamLead(project, currentUser);
	        boolean checkQA = checkQA(project, currentUser);
	        if (checkPm || checkQA) {
	            for (WorkLog workLog : listWorkLog) {
	                listWorkLogReturn.add(workLog);
	            }
	        } else if (checkTl) {
	            List<String> userKeys = new ArrayList<String>();
	            userKeys = projectTeamServiceImpl.getUserByTeamLead(
	                    project.getKey(), currentUser.getKey());
	            for (WorkLog workLog : listWorkLog) {
	                if (userKeys.contains(workLog.getUserKey().toLowerCase())) {
	                    listWorkLogReturn.add(workLog);
	                }
	            }
	        }
	        return convertWorkLogListToGantt(listWorkLogReturn);
	}
	
	private List<WorkLog> getWorkLogNotRejectByProject(long projectId,
			int month, int year, String status, String typeOfWork, 
			String issueType, List<String> components, List<String> versions) {

		WorkLog[] listWorkLog = ao.find(
				WorkLog.class,
				queryWorkLogGanttForAdmin(projectId, month, year,
						status, typeOfWork, issueType, components, versions));

		return newArrayList(listWorkLog);
	}
	
	  
	   private Query queryWorkLogGanttForAdmin(Long projectId,
			   int month, int year,String status, String typeOfWork, String issueType,
				List<String> componentIds, List<String> versionIds) {
			
			List<Object> listParam = new ArrayList<Object>();
			listParam.add(projectId);
			listParam.add(month);
			listParam.add(year);
			String querryString = "PROJECT_ID = ? AND MONTH(START_DATE) = ? AND YEAR(START_DATE) = ? ";
			if (!(SELECT_ALL.equals(status) || status == null)) {
				if ("A&P".equals(status)) {
					querryString += " AND STATUS IN ('Pending','Approved')";
				} else {
					querryString += " AND STATUS = ?";
					listParam.add(status);
				}
			}
			if (!(SELECT_ALL.equals(typeOfWork) || typeOfWork == null)) {
				querryString += " AND TYPE_OF_WORK = ? ";
				listParam.add(typeOfWork);
			}
			if (!(componentIds == null || componentIds.size() == 0)) {
				if (!(versionIds == null || versionIds.size() == 0)) {
					
					List<Issue> issues = getIssuesWithVerionsType(versionIds, issueType);
					List<String> listIssueKey = new ArrayList<String>();
					for (Issue issue : issues) {
						List<ProjectComponent> listComponent = (List<ProjectComponent>) issue
								.getComponentObjects();
						if(listComponent.size()>0){
							if (componentIds.contains(listComponent.get(0).getId().toString())){
								listIssueKey.add(issue.getKey());
							}
						}
	                }
					if (listIssueKey.size() > 0) {
	                    querryString += " AND(";
	                    for (String issueKey : listIssueKey) {
	                        if (listIssueKey.indexOf(issueKey) == 0) {
	                            querryString += "ISSUE_KEY = ? ";
	                        } else {
	                            querryString += "OR ISSUE_KEY = ? ";
	                        }
	                    }
	                    querryString +=" )";
	                    listParam.addAll(listIssueKey);
	                } else {
	                    return null;
	                }

				} else {
					List<Issue> issues = getIssuesWithComponentList(componentIds);
					List<String> listIssueKey = new ArrayList<String>();
					if(!SELECT_ALL.equals(issueType)){
						for (Issue issue : issues) {
							if (issue.getIssueTypeObject().getName().equals(issueType)){
//								 issues.add(issue);
								 listIssueKey.add(issue.getKey());
							 }
		                } 
					}else{
						for (Issue issue : issues) {
//								 issues.add(issue);
								 listIssueKey.add(issue.getKey());
		                } 
	                }
	                
	                if (listIssueKey.size() > 0) {
	                    querryString += " AND(";
	                    for (String issueKey : listIssueKey) {
	                        if (listIssueKey.indexOf(issueKey) == 0) {
	                            querryString += " ISSUE_KEY = ? ";
	                        } else {
	                            querryString += " OR ISSUE_KEY = ? ";
	                        }
	                    }
	                    querryString +=" )";
	                    listParam.addAll(listIssueKey);
	                } else {
	                    return null;
	                }
				}
			} else {
				if (!(versionIds == null || versionIds.size() == 0)) {
					List<Issue> issues = getIssuesWithVerionsType(versionIds, issueType);
					
	                List<String> listIssueKey = new ArrayList<String>();
	                for (Issue issue : issues) {
	                    listIssueKey.add(issue.getKey());
	                }
	                if (listIssueKey.size() > 0) {
	                    querryString += " AND (  ";
	                    for (String issueKey : listIssueKey) {
	                        if (listIssueKey.indexOf(issueKey) == 0) {
	                            querryString += " ISSUE_KEY = ? ";
	                        } else {
	                            querryString += " OR ISSUE_KEY = ? ";
	                        }
	                    }
	                    querryString +=" )";
	                    listParam.addAll(listIssueKey);
	                } else {
	                    return null;
	                }
				}else{
					try {
						List<Issue> listIssue = issueManager.getIssueObjects(issueManager.getIssueIdsForProject(projectId));
						List<String> listIssueKey = new ArrayList<String>();
						if (listIssue.size() > 0) {
							if (!SELECT_ALL.equals(issueType)) {
								for (Issue issue : listIssue) {
									if (issue.getIssueTypeObject().getName().equals(issueType)) {
										listIssueKey.add(issue.getKey());
									}
								}
							}else{
								for (Issue issue : listIssue) {
									listIssueKey.add(issue.getKey());
								}
							}
							
							if (listIssueKey.size() > 0) {
			                    querryString += " AND (  ";
			                    for (String issueKey : listIssueKey) {
			                        if (listIssueKey.indexOf(issueKey) == 0) {
			                            querryString += " ISSUE_KEY = ? ";
			                        } else {
			                            querryString += " OR ISSUE_KEY = ? ";
			                        }
			                    }
			                    querryString +=" )";
			                    listParam.addAll(listIssueKey);
			                } else {
			                    return null;
			                }
						}else{
							return null;
						}
					} catch (GenericEntityException e) {
						e.printStackTrace();
					}
				}
			}
			System.out.print(querryString);
			Query query = Query.select().where(querryString, listParam.toArray());
			return query;
		}
	
	   
	   
	   
	private Query queryWorkLogGanttForAdmin(Long projectId,
			Date startDate, Date endDate,String status, String typeOfWork, String issueType,
			List<String> componentIds, List<String> versionIds) {
		if (startDate == null) {
			startDate = DateUtils.INVALID_START_DATE;
		}
		if (endDate == null) {
			endDate = DateUtils.INVALID_END_DATE;
		}
		startDate = DateUtils.standardizeDate(startDate, 0, 0, 0, 0);
        endDate = DateUtils.standardizeDate(endDate, 23, 59, 59, 999);
		List<Object> listParam = new ArrayList<Object>();
		listParam.add(projectId);
		listParam.add(startDate);
		listParam.add(endDate);
		String querryString = "PROJECT_ID = ? AND (START_DATE BETWEEN ? AND ?) ";
		if (!(SELECT_ALL.equals(status) || status == null)) {
			if ("A&P".equals(status)) {
				querryString += " AND STATUS IN ('Pending','Approved')";
			} else {
				querryString += " AND STATUS = ?";
				listParam.add(status);
			}
		}
		if (!(SELECT_ALL.equals(typeOfWork) || typeOfWork == null)) {
			querryString += " AND TYPE_OF_WORK = ? ";
			listParam.add(typeOfWork);
		}
		if (!(componentIds == null || componentIds.size() == 0)) {
			if (!(versionIds == null || versionIds.size() == 0)) {
				
				List<Issue> issues = getIssuesWithVerionsType(versionIds, issueType);
				List<String> listIssueKey = new ArrayList<String>();
				for (Issue issue : issues) {
					List<ProjectComponent> listComponent = (List<ProjectComponent>) issue
							.getComponentObjects();
					if(listComponent.size()>0){
						if (componentIds.contains(listComponent.get(0).getId().toString())){
							listIssueKey.add(issue.getKey());
						}
					}
                }
				if (listIssueKey.size() > 0) {
                    querryString += " AND(";
                    for (String issueKey : listIssueKey) {
                        if (listIssueKey.indexOf(issueKey) == 0) {
                            querryString += "ISSUE_KEY = ? ";
                        } else {
                            querryString += "OR ISSUE_KEY = ? ";
                        }
                    }
                    querryString +=" )";
                    listParam.addAll(listIssueKey);
                } else {
                    return null;
                }

			} else {
				List<Issue> issues = getIssuesWithComponentList(componentIds);
				List<String> listIssueKey = new ArrayList<String>();
				if(!SELECT_ALL.equals(issueType)){
					for (Issue issue : issues) {
						if (issue.getIssueTypeObject().getName().equals(issueType)){
//							 issues.add(issue);
							 listIssueKey.add(issue.getKey());
						 }
	                } 
				}else{
					for (Issue issue : issues) {
//							 issues.add(issue);
							 listIssueKey.add(issue.getKey());
	                } 
                }
                
                if (listIssueKey.size() > 0) {
                    querryString += " AND(";
                    for (String issueKey : listIssueKey) {
                        if (listIssueKey.indexOf(issueKey) == 0) {
                            querryString += " ISSUE_KEY = ? ";
                        } else {
                            querryString += " OR ISSUE_KEY = ? ";
                        }
                    }
                    querryString +=" )";
                    listParam.addAll(listIssueKey);
                } else {
                    return null;
                }
			}
		} else {
			if (!(versionIds == null || versionIds.size() == 0)) {
				List<Issue> issues = getIssuesWithVerionsType(versionIds, issueType);
				
                List<String> listIssueKey = new ArrayList<String>();
                for (Issue issue : issues) {
                    listIssueKey.add(issue.getKey());
                }
                if (listIssueKey.size() > 0) {
                    querryString += " AND (  ";
                    for (String issueKey : listIssueKey) {
                        if (listIssueKey.indexOf(issueKey) == 0) {
                            querryString += " ISSUE_KEY = ? ";
                        } else {
                            querryString += " OR ISSUE_KEY = ? ";
                        }
                    }
                    querryString +=" )";
                    listParam.addAll(listIssueKey);
                } else {
                    return null;
                }
			}else{
				try {
					List<Issue> listIssue = issueManager.getIssueObjects(issueManager.getIssueIdsForProject(projectId));
					List<String> listIssueKey = new ArrayList<String>();
					if (listIssue.size() > 0) {
						if (!SELECT_ALL.equals(issueType)) {
							for (Issue issue : listIssue) {
								if (issue.getIssueTypeObject().getName().equals(issueType)) {
									listIssueKey.add(issue.getKey());
								}
							}
						}else{
							for (Issue issue : listIssue) {
								listIssueKey.add(issue.getKey());
							}
						}
						
						if (listIssueKey.size() > 0) {
		                    querryString += " AND (  ";
		                    for (String issueKey : listIssueKey) {
		                        if (listIssueKey.indexOf(issueKey) == 0) {
		                            querryString += " ISSUE_KEY = ? ";
		                        } else {
		                            querryString += " OR ISSUE_KEY = ? ";
		                        }
		                    }
		                    querryString +=" )";
		                    listParam.addAll(listIssueKey);
		                } else {
		                    return null;
		                }
					}else{
						return null;
					}
				} catch (GenericEntityException e) {
					e.printStackTrace();
				}
			}
		}
		System.out.print(querryString);
		Query query = Query.select().where(querryString, listParam.toArray());
		return query;
	}

	/* man hinh my work log moi
	 * 
	 */
	
	@Override
	public List<WorklogGanttForUser> getGanttForUserDuration(String userKey,
			Long projectId, Date startDate, Date endDate, String type,
			String componentId, String versionId, String status,
			String issueType, String issueStatus) {

		if (startDate == null) {
			startDate = DateUtils.INVALID_START_DATE;
		}
		if (endDate == null) {
			endDate = DateUtils.INVALID_END_DATE;
		}
		Query query = queryWorkLogForUser(userKey, projectId, startDate,
				endDate, type, componentId, versionId, status, issueType,
				issueStatus);

		WorkLog[] listWorkLog = ao.find(WorkLog.class, query);
		List<WorkLog> list = new ArrayList<WorkLog>(Arrays.asList(listWorkLog));
		
        List<WorkLogGantt> listWorkLogGantt = convertWorkLogListToGanttMyWorklog(list);
        WorklogGanttForUser worklogGanttForUser = new WorklogGanttForUser();
        worklogGanttForUser.setProject(projectManager.getProjectObj(projectId));
        worklogGanttForUser.setWorkLogGantt(listWorkLogGantt);
        List<WorklogGanttForUser>  worklogGanttForUserList = new ArrayList<WorklogGanttForUser>();
        worklogGanttForUserList.add(worklogGanttForUser);
		return worklogGanttForUserList;
	}
	
	private Query queryWorkLogForUser(String userKey, Long projectId, Date startDate, Date endDate,
			String type, String componentId, String versionId, String status,
			String issueType, String issueStatus) {
		List<Object> listParam = new ArrayList<Object>();
		listParam.add(projectId);
		listParam.add(userKey);
		listParam.add(startDate);
		listParam.add(endDate);
		listParam.add(endDate);
		String queryString = "PROJECT_ID = ? AND USER_KEY = ? AND START_DATE >= ? AND START_DATE <= ? AND (END_DATE <= ? OR END_DATE IS NULL) ";
		if (!(SELECT_ALL.equals(type) || type == null)) {
			queryString += " AND TYPE_OF_WORK = ? ";
			listParam.add(type);
		}
		if (!(SELECT_ALL.equals(status) || status == null)) {
			queryString += " AND STATUS = ? ";
			listParam.add(status);
		}
		if (!SELECT_ALL.equals(componentId)) {
			if (!SELECT_ALL.equals(versionId)) {
				List<Issue> issues = getIssuesWithVersionTypeStatus(versionId,
						issueType, issueStatus);
				List<String> listIssueKey = new ArrayList<String>();
				for (Issue issue : issues) {
					List<ProjectComponent> listComponent = (List<ProjectComponent>) issue
							.getComponentObjects();
					if (listComponent.size() > 0) {
						listIssueKey.add(issue.getKey());
					}
				}
				if (listIssueKey.size() > 0) {
					queryString += " AND ISSUE_KEY IN(";
					for (String issueKey : listIssueKey) {
						if (listIssueKey.indexOf(issueKey) != listIssueKey
								.size() - 1) {
							queryString += "?, ";
						} else {
							queryString += "?)";
						}
					}
					listParam.addAll(listIssueKey);
				} else {
					return null;
				}
			} else {
				List<Issue> issues = getIssuesWithComponent(componentId);
				List<String> listIssueKey = new ArrayList<String>();
				for (Issue issue : issues) {
					listIssueKey.add(issue.getKey());
				}
				if (listIssueKey.size() > 0) {
					queryString += " AND ISSUE_KEY IN(";
					for (String issueKey : listIssueKey) {
						if (listIssueKey.indexOf(issueKey) != listIssueKey
								.size() - 1) {
							queryString += "?, ";
						} else {
							queryString += "?)";
						}
					}
					listParam.addAll(listIssueKey);
				} else {
					return null;
				}
			}
		} else {
			if (!SELECT_ALL.equals(versionId)) {
				List<Issue> issues = getIssuesWithVersionTypeStatus(versionId,
						issueType, issueStatus);
				List<String> listIssueKey = new ArrayList<String>();
				for (Issue issue : issues) {
					listIssueKey.add(issue.getKey());
				}
				if (listIssueKey.size() > 0) {
					queryString += " AND ISSUE_KEY IN(";
					for (String issueKey : listIssueKey) {
						if (listIssueKey.indexOf(issueKey) != listIssueKey
								.size() - 1) {
							queryString += "?, ";
						} else {
							queryString += "?)";
						}
					}
					listParam.addAll(listIssueKey);
				} else {
					return null;
				}
			} else {
				try {
					List<Issue> listIssue = issueManager
							.getIssueObjects(issueManager
									.getIssueIdsForProject(projectId));
					if (listIssue.size() > 0) {
						boolean checkExistsIssueType = false;
						queryString += " AND ISSUE_KEY IN(";
						if (!SELECT_ALL.equals(issueType)) {
							if (issueStatus.equals("true")) {
								for (Issue issue : listIssue) {
									if (issue.getIssueTypeObject().getName()
											.equals(issueType)) {
										queryString += "?, ";
										checkExistsIssueType = true;
										listParam.add(issue.getKey());
									}
								}
							} else {
								for (Issue issue : listIssue) {
									if (issue.getIssueTypeObject().getName()
											.equals(issueType)
											&& !issue.getStatusObject()
													.getName().equals("Closed")
											&& !issue.getStatusObject()
													.getName()
													.equals("Cancelled")) {
										queryString += "?, ";
										checkExistsIssueType = true;
										listParam.add(issue.getKey());
									}
								}
							}
							if (checkExistsIssueType) {
								queryString = queryString.substring(0,
										queryString.length() - 2) + ") ";
							} else {
								queryString = queryString.substring(0,
										queryString.length() - 10);
							}
						} else {
							if (issueStatus.equals("true")) {
								for (Issue issue : listIssue) {
									queryString += "?, ";
									checkExistsIssueType = true;
									listParam.add(issue.getKey());
								}
							} else {
								for (Issue issue : listIssue) {
									if (!issue.getStatusObject().getName()
											.equals("Closed")
											&& !issue.getStatusObject()
													.getName()
													.equals("Cancelled")) {
										queryString += "?, ";
										checkExistsIssueType = true;
										listParam.add(issue.getKey());
									}
								}
							}
							if (checkExistsIssueType) {
								queryString = queryString.substring(0,
										queryString.length() - 2) + ") ";
							} else {
								queryString = queryString.substring(0,
										queryString.length() - 10);
							}
						}
					} else {
						return null;
					}
				} catch (GenericEntityException e) {
					e.printStackTrace();
				}
			}
		}
		Query query = Query.select().where(queryString, listParam.toArray());
		return query;
	}

	// duration all project ---------------------------------------------------
	
	@Override
	public List<WorklogGanttForUser> getGanttForUserDuration(String userKey,
			Date startDate, Date endDate, String type, String componentId,
			String versionId, String status, String issueType,
			String issueStatus) {
		List<WorklogGanttForUser> worklogGanttForUsers = null;
		
		// get all project by user
		List<Project> projectList = new ArrayList<Project>();
		projectList.addAll(projectRoleService.getProjectsContainingRoleActorByNameAndType(userKey, "atlassian-user-role-actor", new SimpleErrorCollection()));
		
//		List<Version> versions = (List<Version>) versionManager.getAllVersionsForProjects(projectList, true);
		Query query = null;
		for (Project project : projectList) {
			query = queryWorkLogGanttForUser(project.getId(), userKey, startDate,
					endDate, type, status, issueType, issueStatus);

			if (query == null) {
				worklogGanttForUsers = new ArrayList<WorklogGanttForUser>();
			} else {
				WorklogGanttForUser ganttForUserList = new WorklogGanttForUser();

				WorkLog[] listWorkLog = ao.find(WorkLog.class, query);
				List<WorkLog> list = new ArrayList<WorkLog>(Arrays.asList(listWorkLog));
				ganttForUserList.setProject(project);
				ganttForUserList.setWorkLogGantt(convertWorkLogListToGantt(list));

				worklogGanttForUsers =  newArrayList(ganttForUserList);
			}
		}
		return worklogGanttForUsers;
	}

	private Query queryWorkLogGanttForUser(Long projectId, String userKey, 
			Date startDate, Date endDate, String type, String status, String issueType,
			String issueStatus){
		
		if (startDate == null) {
			startDate = DateUtils.INVALID_START_DATE;
		}
		if (endDate == null) {
			endDate = DateUtils.INVALID_END_DATE;
		}
		startDate = DateUtils.standardizeDate(startDate, 0, 0, 0, 0);
		endDate = DateUtils.standardizeDate(endDate, 23, 59, 59, 999);
		List<Object> listParam = new ArrayList<Object>();
		listParam.add(projectId);
		listParam.add(startDate);
		listParam.add(endDate);
		listParam.add(endDate);
		String querryString = "PROJECT_ID = ? AND (START_DATE BETWEEN ? AND ?) ";

		if (!(SELECT_ALL.equals(status) || status == null)) {
			if ("A&P".equals(status)) {
				querryString += " AND STATUS IN ('Pending','Approved')";
			} else {
				querryString += " AND STATUS = ?";
				listParam.add(status);
			}
		}
		if (!(userKey == null || userKey == "")) {
			querryString += " AND USER_KEY = ?";
			listParam.add(userKey);
		} 
		if (!(SELECT_ALL.equals(type) || type == null)) {
			querryString += " AND TYPE_OF_WORK = ? ";
			listParam.add(type);
		}
		List<String> listIssueKey = new ArrayList<String>();
		List<Issue> issueList;
		try {
			issueList = issueManager.getIssueObjects(issueManager
					.getIssueIdsForProject(projectId));

			for (Issue issue : issueList) {
				if (issue.getIssueTypeObject().getName().equals(issueType)) {
					listIssueKey.add(issue.getKey());
				}
			}
			if (listIssueKey.size() > 0) {
				querryString += " AND(";
				for (String issueKey : listIssueKey) {
					if (listIssueKey.indexOf(issueKey) == 0) {
						querryString += " ISSUE_KEY = ? ";
					} else {
						querryString += " OR ISSUE_KEY = ? ";
					}
				}
				querryString += " )";
				listParam.addAll(listIssueKey);
			} else {
				return null;
			}
		} catch (GenericEntityException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		Query query = Query.select().where(querryString, listParam.toArray());
		return query;
	}
	
	//  month all project ----------------------------------------------------
	@Override
	public List<WorklogGanttForUser> getGanttForUserMonth(String userKey,
			Integer month, Integer year, String type, String componentId,
			String versionId, String status, String issueType,
			String issueStatus) {
		List<WorklogGanttForUser> worklogGanttForUsers = null;
		
		List<Project> projectList = new ArrayList<Project>();
		projectList.addAll(projectRoleService.getProjectsContainingRoleActorByNameAndType(userKey, "atlassian-user-role-actor", new SimpleErrorCollection()));
		
		Query query = null;
		for (Project project : projectList) {
			query = queryWorkLogGanttForUser(project.getId(), userKey, month,
					year, type, status, issueType, issueStatus);

			if (query == null) {
				worklogGanttForUsers = new ArrayList<WorklogGanttForUser>();
			} else {
				WorklogGanttForUser ganttForUserList = new WorklogGanttForUser();

				WorkLog[] listWorkLog = ao.find(WorkLog.class, query);
				List<WorkLog> list = new ArrayList<WorkLog>(Arrays.asList(listWorkLog));
				ganttForUserList.setProject(project);
				ganttForUserList.setWorkLogGantt(convertWorkLogListToGantt(list));

				worklogGanttForUsers =  newArrayList(ganttForUserList);
			}
		}
		return worklogGanttForUsers;
	}
	
	private Query queryWorkLogGanttForUser(Long projectId, String userKey,
			Integer month, Integer year, String type, 
			String status, String issueType, String issueStatus){
		
		List<Object> listParam = new ArrayList<Object>();
		listParam.add(projectId);
		listParam.add(month);
		listParam.add(year);
		String querryString = "PROJECT_ID = ? AND MONTH(START_DATE) = ? AND YEAR(START_DATE) = ? ";
		if (!(SELECT_ALL.equals(status) || status == null)) {
			if ("A&P".equals(status)) {
				querryString += " AND STATUS IN ('Pending','Approved')";
			} else {
				querryString += " AND STATUS = ?";
				listParam.add(status);
			}
		}
		if (!(SELECT_ALL.equals(type) || type == null)) {
			querryString += " AND TYPE_OF_WORK = ? ";
			listParam.add(type);
		}
		if (!(userKey == null || userKey == "")) {
			querryString += " AND USER_KEY = ?";
			listParam.add(userKey);
		} 
		List<String> listIssueKey = new ArrayList<String>();
		List<Issue> issueList;
		try {
			issueList = issueManager.getIssueObjects(issueManager
					.getIssueIdsForProject(projectId));

			for (Issue issue : issueList) {
				if (issue.getIssueTypeObject().getName().equals(issueType)) {
					listIssueKey.add(issue.getKey());
				}
			}
			if (listIssueKey.size() > 0) {
				querryString += " AND(";
				for (String issueKey : listIssueKey) {
					if (listIssueKey.indexOf(issueKey) == 0) {
						querryString += " ISSUE_KEY = ? ";
					} else {
						querryString += " OR ISSUE_KEY = ? ";
					}
				}
				querryString += " )";
				listParam.addAll(listIssueKey);
			} else {
				return null;
			}
		} catch (GenericEntityException e) {
			e.printStackTrace();
		}
		Query query = Query.select().where(querryString, listParam.toArray());
		return query;
	}
	
	// one project in month----------------------------------------------
	@Override
	public List<WorklogGanttForUser> getGanttForUserMonth(String userKey,
			Long projectId, Integer month, Integer year, String type,
			String componentId, String versionId, String status,
			String issueType, String issueStatus) {
		
		List<WorkLog> listWorkLog = getWorkLogByUserKey(projectId, userKey,
                month, year, type, componentId, versionId, status, issueType, issueStatus);
        List<WorkLogGantt> listWorkLogGantt = convertWorkLogListToGanttMyWorklog(listWorkLog);
        WorklogGanttForUser worklogGanttForUser = new WorklogGanttForUser();
        worklogGanttForUser.setProject(projectManager.getProjectObj(projectId));
        worklogGanttForUser.setWorkLogGantt(listWorkLogGantt);
        List<WorklogGanttForUser>  worklogGanttForUserList = new ArrayList<WorklogGanttForUser>();
        worklogGanttForUserList.add(worklogGanttForUser);
		return worklogGanttForUserList;
	}
}
