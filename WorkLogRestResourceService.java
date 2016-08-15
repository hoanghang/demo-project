package com.fpt.jira.worklog.rest;

import static com.google.common.collect.Lists.newArrayList;

import java.io.IOException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Base64.Decoder;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.commons.httpclient.auth.AuthenticationException;
import org.apache.log4j.Logger;
import org.codehaus.jackson.JsonGenerationException;
import org.codehaus.jackson.map.JsonMappingException;
import org.codehaus.jackson.map.ObjectMapper;
import org.ofbiz.core.entity.GenericEntityException;
import org.springframework.beans.factory.annotation.Autowired;

import com.atlassian.core.util.ObjectUtils;
import com.atlassian.crowd.embedded.api.User;
import com.atlassian.jira.bc.project.component.ProjectComponent;
import com.atlassian.jira.config.StatusManager;
import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.issue.IssueManager;
import com.atlassian.jira.issue.MutableIssue;
import com.atlassian.jira.issue.comments.Comment;
import com.atlassian.jira.issue.comments.CommentManager;
import com.atlassian.jira.project.Project;
import com.atlassian.jira.project.ProjectManager;
import com.atlassian.jira.project.version.Version;
import com.atlassian.jira.security.JiraAuthenticationContext;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.plugins.rest.common.security.AnonymousAllowed;
import com.atlassian.sal.api.pluginsettings.PluginSettings;
import com.atlassian.sal.api.pluginsettings.PluginSettingsFactory;
import com.atlassian.sal.api.user.UserManager;
import com.fpt.jira.core.util.constant.ReportName;
import com.fpt.jira.project.projectconfig.ProjectConfigInfo;
import com.fpt.jira.project.projectconfig.ProjectConfigInfoService;
import com.fpt.jira.project.projectconfig.group.ProjectGroup;
import com.fpt.jira.project.projectconfig.group.ProjectGroupService;
import com.fpt.jira.project.projectconfig.team.ObjectSearch;
import com.fpt.jira.project.projectconfig.team.ProjectTeamService;
import com.fpt.jira.project.projectconfig.unit.ProjectUnit;
import com.fpt.jira.project.projectconfig.unit.ProjectUnitService;
import com.fpt.jira.project.util.RestUtil;
import com.fpt.jira.worklog.WorkLog;
import com.fpt.jira.worklog.bean.WorkLogForList;
import com.fpt.jira.worklog.bean.WorkLogGantt;
import com.fpt.jira.worklog.bean.WorkLogRestModel;
import com.fpt.jira.worklog.bean.WorkLogView;
import com.fpt.jira.worklog.bean.WorklogGanttForUser;
import com.fpt.jira.worklog.service.WorkLogHistoryService;
import com.fpt.jira.worklog.service.WorkLogService;
import com.fpt.jira.worklog.token.StaticTokenUtil;
import com.fpt.jira.worklog.util.CommonUtil;
import com.fpt.jira.worklog.util.DateUtils;
import com.fpt.jira.worklog.util.PaginationUtil;

/**
 * A resource of message.
 */

@Consumes({"application/json"})
@Produces({"application/json"})
@Path("/")
public class WorkLogRestResourceService {
    private static final String TOTAL_RECORD = "totalRecord";
    private static final String TOTAL_HOUR = "totalHour";
    private static final String EMPTY_HMTL = "null";
    private static final String EMPTY = "";
    private final WorkLogService workLogServiceImp;
    private final UserManager userManager;
    private final com.atlassian.jira.user.util.UserManager userManagerUtil;
    private final ProjectManager projectManager;
    private final PluginSettingsFactory pluginSettingsFactory;
    private PluginSettings pluginSettings;
    private ProjectTeamService projectTeamService;
    private Logger logger;
    private JiraAuthenticationContext jiraAuthenticationContext;
    private final IssueManager issueManager;
    private final CommentManager commentManager;
    private final StatusManager statusManager;
    private final ProjectUnitService projectUnitService;
    private final ProjectGroupService projectGroupService;
    private final ProjectConfigInfoService projectConfigInfoService;
    private final WorkLogHistoryService workLogHistoryService;
    private final static String STRING_TRUE = "true";
    private final static String STRING_FALSE = "false";
    private final static String CLOSED = "Closed";
    private final static String CANCELED = "Canceled";
    private final static String ACCEPTED = "Accepted";

    @Autowired
    public WorkLogRestResourceService(final WorkLogService workLogServiceImp,
                                      UserManager userManager, ProjectManager projectManager,
                                      PluginSettingsFactory pluginSettingsFactory,
                                      ProjectTeamService projectTeamService,
                                      JiraAuthenticationContext jiraAuthenticationContext,
                                      IssueManager issueManager, CommentManager commentManager,
                                      StatusManager statusManager, ProjectUnitService projectUnitService,
                                      com.atlassian.jira.user.util.UserManager userManagerUtil,
                                      ProjectConfigInfoService projectConfigInfoService,
                                      ProjectGroupService projectGroupService,
                                      WorkLogHistoryService workLogHistoryService) {
        this.pluginSettingsFactory = pluginSettingsFactory;
        this.pluginSettings = this.pluginSettingsFactory.createGlobalSettings();
        this.workLogServiceImp = workLogServiceImp;
        this.userManager = userManager;
        this.projectManager = projectManager;
        logger = Logger.getLogger(WorkLogRestResourceService.class);
        logger.setLevel(org.apache.log4j.Level.INFO);
        this.projectTeamService = projectTeamService;
        this.jiraAuthenticationContext = jiraAuthenticationContext;
        this.issueManager = issueManager;
        this.commentManager = commentManager;
        this.statusManager = statusManager;
        this.projectUnitService = projectUnitService;
        this.userManagerUtil = userManagerUtil;
        this.projectConfigInfoService = projectConfigInfoService;
        this.projectGroupService = projectGroupService;
        this.workLogHistoryService = workLogHistoryService;
    }

    @GET
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public Response getMessage() {
        WorkLogRestModel logRESTServiceModel = new WorkLogRestModel();
        return Response.ok(logRESTServiceModel).build();
    }

    @GET
    @Path("allForAdmin")
    @Produces({"application/json"})
    public Response getAllForAdmin() {
        List<WorkLog> listWorkLog = workLogServiceImp.getAllWorkLogForAdmin(
                (long) 4, 1, 1);
        List<WorkLogRestModel> models = new ArrayList<WorkLogRestModel>();
        for (WorkLog workLog : listWorkLog) {
            models.add(workLogServiceImp.convertModel(workLog));
        }
        return Response.ok(models).build();
    }

    @GET
    @Path("issue/{userIdOrKey}/{issueIdOrKey}")
    @Produces({"application/json"})
    public Response issue(@PathParam("issueIdOrKey") String issueIdOrKey,
                          @PathParam("userIdOrKey") String userIdOrKey) {
        List<WorkLog> listWorkLog = workLogServiceImp.getWorkLogByIssue(
                issueIdOrKey, userIdOrKey);
        List<WorkLogRestModel> models = new ArrayList<WorkLogRestModel>();
        for (WorkLog workLog : listWorkLog) {
            models.add(workLogServiceImp.convertModel(workLog));
        }
        return Response.ok(models).build();
    }

    @GET
    @Path("issueForCreator/{userIdOrKey}/{projectId}/{issueIdOrKey}")
    @Produces({"application/json"})
    public Response issueForCreator(@PathParam("projectId") long projectId,
                                    @PathParam("issueIdOrKey") String issueIdOrKey,
                                    @PathParam("userIdOrKey") String userIdOrKey) {
        Project project = projectManager.getProjectObj(projectId);
        List<WorkLogRestModel> models = new ArrayList<WorkLogRestModel>();
        ApplicationUser currentUser = jiraAuthenticationContext.getUser();
        if (workLogServiceImp.isProjectManagerOrTeamLeader(project)) {
            List<WorkLog> listWorkLogForCreator = workLogServiceImp
                    .getWorkLogByIssueKey(issueIdOrKey);
            List<WorkLog> listWorkLogForCreatorResult = new ArrayList<WorkLog>();

            for (WorkLog workLog : listWorkLogForCreator) {
                if (!workLog.getUserKey().equals(
                        userManager.getRemoteUsername())) {
                    listWorkLogForCreatorResult.add(workLog);
                }
            }
            if (workLogServiceImp.isProjectManager(project, currentUser)) {
                for (WorkLog workLog : listWorkLogForCreatorResult) {
                    models.add(workLogServiceImp.convertModel(workLog));
                }
            } else if (workLogServiceImp.checkTL(project)) {
                List<String> teamMember = new ArrayList<String>();
                teamMember = projectTeamService.getUserByTeamLead(
                        project.getKey(), currentUser.getKey());
                for (WorkLog workLog : listWorkLogForCreatorResult) {
                    if (teamMember.contains(workLog.getUserKey().toLowerCase())) {
                        models.add(workLogServiceImp.convertModel(workLog));
                    }
                }
            }
        } else if (workLogServiceImp.isQaOrTopUser(project, currentUser)) {
            List<WorkLog> listWorkLogForCreator = workLogServiceImp
                    .getWorkLogByIssueKey(issueIdOrKey);
            for (WorkLog workLog : listWorkLogForCreator) {
                if (!workLog.getUserKey().equalsIgnoreCase(
                        userManager.getRemoteUsername())) {
                    models.add(workLogServiceImp.convertModel(workLog));
                }
            }
        }
        return Response.ok(models).build();
    }

    @GET
    @Path("allPendingForAdmin")
    @Produces({"application/json"})
    public Response getAllPending() {
        List<WorkLog> listWorkLog = workLogServiceImp
                .getAllPendingWorkLogForAdmin((long) 3, 1, 1);
        List<WorkLogRestModel> listWorkLogRESTServiceModels = new ArrayList<WorkLogRestModel>();
        for (WorkLog workLog : listWorkLog) {
            listWorkLogRESTServiceModels.add(workLogServiceImp
                    .convertModel(workLog));
        }
        return Response.ok(listWorkLogRESTServiceModels).build();
    }

    @DELETE
    @Path("deleteWorkLog/{workLogId}")
    @Produces({"application/json"})
    public Response deleteWorkLog(@PathParam("workLogId") String workLogId) {
        return Response.ok(workLogServiceImp.deleteWorkLog(workLogId)).build();
    }

    @DELETE
    @Path("deleteListWorkLog/{workLogIds}")
    @Produces({"application/json"})
    public Response deleteListWorkLog(@PathParam("workLogIds") String workLogIds) {
        try {
            String[] arrayIdWorkLog = workLogIds.split(",");
            for (String workLogId : arrayIdWorkLog) {
                workLogServiceImp.deleteWorkLog(workLogId);
            }
            return Response.ok(true).build();
        } catch (Exception e) {
            return Response.ok(false).build();
        }
    }

    @PUT
    @Path("rejectWorkLog")
    @Produces({"application/json"})
    public Response rejectWorkLog(@QueryParam("workLogId") String workLogIds,
                                  @QueryParam("reason") String reason) {
        try {
            String[] arrayIdWorkLog = workLogIds.split(",");
            for (String workLogId : arrayIdWorkLog) {
                workLogServiceImp.updateWorkLogStatus(Long.valueOf(workLogId),
                        StaticTokenUtil.REJECT, reason);
            }
            return Response.ok(true).build();
        } catch (Exception e) {
            return Response.ok(false).build();
        }
    }

    @PUT
    @Path("approveWorkLog/{workLogId}")
    @Produces({"application/json"})
    public Response approveWorkLog(@PathParam("workLogId") String workLogId) {
        try {
            workLogServiceImp.updateWorkLogStatus(Long.valueOf(workLogId),
                    StaticTokenUtil.APPROVE, "OK");
            return Response.ok(true).build();
        } catch (Exception e) {
            return Response.ok(false).build();
        }

    }

    @PUT
    @Path("approveAllWorkLog/{workLogIds}")
    @Produces({"application/json"})
    public Response approveAllWorkLog(@PathParam("workLogIds") String workLogIds) {
        try {
            String[] arrayIdWorkLog = workLogIds.split(",");
            for (String workLogId : arrayIdWorkLog) {
                workLogServiceImp.updateWorkLogStatus(Long.valueOf(workLogId),
                        StaticTokenUtil.APPROVE, "OK");
            }
            return Response.ok(true).build();
        } catch (Exception e) {
            return Response.ok(false).build();
        }

    }

    @GET
    @Path("updateWorkLogStatus/{workLogId}/{status}/{reason}")
    @Produces({"application/json"})
    public Response updateWorkLogStatus(@PathParam("workLogId") Long workLogId,
                                        @PathParam("status") String status,
                                        @PathParam("reason") String reason) {
        return Response.ok(
                workLogServiceImp.convertModel(workLogServiceImp
                        .updateWorkLogStatus(workLogId, status, reason)))
                .build();
    }

    @GET
    @Path("allWorkLogForCurrentUser/{userName}/{projectKey}")
    @Produces({"application/json"})
    public Response allWorkLogForCurrentUser(
            @PathParam("userName") String userKey,
            @PathParam("projectKey") String projectKey) {
        List<WorkLogRestModel> models = new ArrayList<WorkLogRestModel>();
        return Response.ok(models).build();
    }

    @GET
    @Path("project/worklog/{projectKey}")
    @Produces({"application/json"})
    public Response allWorkLogForCurrentUser(
            @PathParam("projectKey") String projectKey) {
        List<String> listWorkLog = new ArrayList<String>();
        return Response.ok(listWorkLog).build();
    }

    @GET
    @Path("createWorkLog")
    @Consumes({"application/json"})
    public Response createWorkLog(@QueryParam("projectId") String projectId,
                                  @QueryParam("projectName") String projectName,
                                  @QueryParam("issueKey") String issueKey,
                                  @QueryParam("issueName") String issueName,
                                  @QueryParam("userKey") String userKey,
                                  @QueryParam("userName") String userName,
                                  @QueryParam("period") String period,
                                  @QueryParam("startDate") Long startDate,
                                  @QueryParam("endDate") Long endDate,
                                  @QueryParam("workPerDay") String workPerDay,
                                  @QueryParam("typeOfWork") String typeOfWork,
                                  @QueryParam("desc") String desc, @QueryParam("type") String type) {
        Date endDateObject = null;
        if ("null".equals(desc)) {
            desc = "";
        }
        if (null == endDate || endDate == 0) {
            endDate = null;
        } else {
            endDateObject = new Date(endDate);
        }
        if ("issue".equals(type)) {
            workLogServiceImp.addWorkLog(userKey, userName, Long
                            .valueOf(projectId), projectName, issueKey, issueName,
                    new Date(startDate), endDateObject, CommonUtil
                            .convertFloatStringToDisPlay(Float
                                    .valueOf(workPerDay)), desc, typeOfWork,
                    StaticTokenUtil.PENDING, "");
        } else if ("gantt".equals(type)) {
            workLogServiceImp.addWorkLog(userKey, userName, Long
                            .valueOf(projectId), projectName, issueKey, issueName,
                    new Date(startDate), null, CommonUtil
                            .convertFloatStringToDisPlay(Float
                                    .valueOf(workPerDay)), desc, typeOfWork,
                    StaticTokenUtil.PENDING, "");
        }

        return Response.ok(true).build();
    }

    @GET
    @Path("editWorkLog")
    @Produces({"application/json"})
    public Response editWorkLog(@QueryParam("idWorkLog") String idWorkLog,
                                @QueryParam("startDate") String startDate,
                                @QueryParam("workPerDay") String workPerDay,
                                @QueryParam("typeOfWork") String typeOfWork,
                                @QueryParam("desc") String desc) {
        if ("null".equals(desc)) {
            desc = "";
        }
        workLogServiceImp.editWorkLog(idWorkLog,
                new Date(Long.valueOf(startDate)), null,
                CommonUtil.convertFloatStringToDisPlay(Float
                        .valueOf(workPerDay)), desc, typeOfWork);
        return Response.ok(null).build();
    }

    // Get all worklog in project - DinhDV4
    @GET
    @Path("project/{projectId}")
    @Produces({"application/json"})
    public Response getWorkLogByProjectKey(
            @PathParam("projectId") Long projectId) {
        List<WorkLog> listWorkLog = workLogServiceImp
                .getWorkLogByProjectId(projectId);
        List<WorkLogRestModel> models = new ArrayList<WorkLogRestModel>();
        for (WorkLog workLog : listWorkLog) {
            models.add(workLogServiceImp.convertModel(workLog));
        }
        return Response.ok(models).build();
    }

    @GET
    @Path("checkBackDate/{startDate}/{endDate}")
    @Produces({"application/json"})
    public Response checkBackDate(@PathParam("startDate") Date startDate,
                                  @PathParam("endDate") Date endDate) {
        int backDate = 0;
        try {
            backDate = Integer.valueOf((String) pluginSettings
                    .get(StaticTokenUtil.BACK_DATE_WORKLOG));
        } catch (Exception e) {
            backDate = 0;
        }
        if (backDate <= 0) {
            int startDateNumber = (int) (startDate.getTime() / DateUtils.MILI_SEC_PER_DAY);
            int toDayNumber = (int) (new Date().getTime() / DateUtils.MILI_SEC_PER_DAY);
            if (startDateNumber >= (toDayNumber - 30)) {
                return Response.ok(true).build();
            }
            return Response.ok(false).build();
        } else {
            int startDateNumber = (int) (startDate.getTime() / DateUtils.MILI_SEC_PER_DAY);
            int toDayNumber = (int) (new Date().getTime() / DateUtils.MILI_SEC_PER_DAY);
            if (startDateNumber >= (toDayNumber - backDate)) {
                return Response.ok(true).build();
            }
            return Response.ok(false).build();
        }
    }

    @GET
    @Path("getBackDate")
    @Produces({"application/json"})
    public Response getBackDate() {
        int backDate = 0;
        try {
            backDate = Integer.valueOf((String) pluginSettings
                    .get(StaticTokenUtil.BACK_DATE_WORKLOG));
            if (backDate == 0) {
                backDate = 30;
            }
        } catch (Exception e) {
            backDate = 30;
        }

        return Response.ok(backDate).build();
    }

    @GET
    @Path("checkDurationProject/{projectId}/{startDate}/{endDate}")
    @Produces({"application/json"})
    public Response checkDurationProject(
            @PathParam("projectId") long projectId,
            @PathParam("startDate") Date startDate,
            @PathParam("endDate") Date endDate) {
        boolean result = workLogServiceImp.checkDurationProject(projectId,
                startDate, endDate);
        return Response.ok(result).build();
    }

    @GET
    @Path("check/{startDate}/{endDate}/{workPerDay}/{workLogId}")
    @Produces({"application/json"})
    public Response checkWorkLog(@PathParam("startDate") Date startDate,
                                 @PathParam("endDate") Date endDate,
                                 @PathParam("workPerDay") Float workPerDay,
                                 @PathParam("workLogId") int workLogId) {
        List<Date> listDate = new ArrayList<Date>();

        List<Float> listHours = new ArrayList<Float>();
        Calendar calendar = Calendar.getInstance();
        for (long i = startDate.getTime(); i <= endDate.getTime(); i += DateUtils.MILI_SEC_PER_DAY) {
            calendar.setTimeInMillis(i);
            if (calendar.get(Calendar.DAY_OF_WEEK) != Calendar.SUNDAY
                    && calendar.get(Calendar.DAY_OF_WEEK) != Calendar.SATURDAY) {
                listDate.add(calendar.getTime());
                listHours.add(Float.valueOf(workPerDay));
            }

        }
        List<WorkLog> listWorkLog = workLogServiceImp
                .getWorkLogCheckWorkPerDay(startDate, endDate,
                        userManager.getRemoteUsername());
        for (WorkLog workLog : listWorkLog) {
            if (workLog.getID() != workLogId) {
                if (workLog.getEndDate() == null) {
                    for (Date date : listDate) {
                        if (date.getTime() == workLog.getStartDate().getTime()) {
                            float h = listHours.get(listDate.indexOf(date));
                            h = h + Float.valueOf(workLog.getWorkPerDay());
                            listHours.set(listDate.indexOf(date), h);
                        }
                    }
                } else {
                    for (Date date : listDate) {
                        if (date.getTime() >= workLog.getStartDate().getTime()
                                && date.getTime() <= workLog.getEndDate()
                                .getTime()) {
                            float h = listHours.get(listDate.indexOf(date));
                            h = h + Float.valueOf(workLog.getWorkPerDay());
                            listHours.set(listDate.indexOf(date), h);
                        }
                    }
                }
            }

        }
        for (Float f : listHours) {
            if (f > 24) {
                return Response.ok(false).build();
            }
        }
        return Response.ok(true).build();
    }
    
    /*    user worklog - man hinh moi 
     * get all work log for all project*/
    
    @GET
    @Path("gantt/userWorkLog/month/{userName}/{month}/{year}/{type}/{componentId}/{versionId}/{status}/{issueType}/{issueStatus}")
    @Produces({"application/json"})
    public Response getGanttForUserMonthInMyWorklog(@PathParam("userName") String userKey,
                                             @PathParam("month") Integer month,
                                             @PathParam("year") Integer year,
                                             @PathParam("type") String type,
                                             @PathParam("componentId") String componentId,
                                             @PathParam("versionId") String versionId,
                                             @PathParam("status") String status,
                                             @PathParam("issueType") String issueType,
                                             @PathParam("issueStatus") String issueStatus) {
        issueType.replaceAll("%252F", "%2F");
        issueType = URLDecoder.decode(issueType);
        List<WorklogGanttForUser> list = workLogServiceImp.getGanttForUserMonth(userKey,
                month, year, type, componentId, versionId, status, issueType, issueStatus);
        return Response.ok(list).build();
    }
    
    @GET
    @Path("gantt/userWorkLog/month/{userName}/{projectId}/{month}/{year}/{type}/{componentId}/{versionId}/{status}/{issueType}/{issueStatus}")
    @Produces({"application/json"})
    public Response getGanttForUserMonthInMyWorklog(@PathParam("userName") String userKey,
    										 @PathParam("projectId") Long projectId,
                                             @PathParam("month") Integer month,
                                             @PathParam("year") Integer year,
                                             @PathParam("type") String type,
                                             @PathParam("componentId") String componentId,
                                             @PathParam("versionId") String versionId,
                                             @PathParam("status") String status,
                                             @PathParam("issueType") String issueType,
                                             @PathParam("issueStatus") String issueStatus) {
        issueType.replaceAll("%252F", "%2F");
        issueType = URLDecoder.decode(issueType);
        List<WorklogGanttForUser> list = workLogServiceImp.getGanttForUserMonth(userKey, projectId,
                month, year, type, componentId, versionId, status, issueType, issueStatus);
        return Response.ok(list).build();
    }
    
    @GET
    @Path("gantt/userWorkLog/duration/{userName}/{startDate}/{endDate}/{type}/{componentId}/{versionId}/{status}/{issueType}/{issueStatus}")
    @Produces({"application/json"})
    public Response getGanttForUserDurationInMyWorklog(@PathParam("userName") String userKey,
    										 @PathParam("startDate") Long startDate,
    								         @PathParam("endDate") Long endDate,
                                             @PathParam("type") String type,
                                             @PathParam("componentId") String componentId,
                                             @PathParam("versionId") String versionId,
                                             @PathParam("status") String status,
                                             @PathParam("issueType") String issueType,
                                             @PathParam("issueStatus") String issueStatus) {
        issueType.replaceAll("%252F", "%2F");
        issueType = URLDecoder.decode(issueType);
        
        Date startDateObject;
        Date endDateObject;
        if (startDate == 0) {
            startDateObject = null;
        } else {
            startDateObject = new Date(startDate);
        }
        if (endDate == 0) {
            endDateObject = null;
        } else {
            endDateObject = new Date(endDate);
        }
        
        List<WorklogGanttForUser> list = workLogServiceImp.getGanttForUserDuration(userKey,
        		startDateObject, endDateObject, type, componentId, versionId, status, issueType, issueStatus);
        return Response.ok(list).build();
    }
    
    @GET
    @Path("gantt/userWorkLog/duration/{userName}/{projectId}/{startDate}/{endDate}/{type}/{componentId}/{versionId}/{status}/{issueType}/{issueStatus}")
    @Produces({"application/json"})
    public Response getGanttForUserDurationInMyWorklog(@PathParam("userName") String userKey,
    										 @PathParam("projectId") Long projectId,
    										 @PathParam("startDate") Long startDate,
    								         @PathParam("endDate") Long endDate,
                                             @PathParam("type") String type,
                                             @PathParam("componentId") String componentId,
                                             @PathParam("versionId") String versionId,
                                             @PathParam("status") String status,
                                             @PathParam("issueType") String issueType,
                                             @PathParam("issueStatus") String issueStatus) {
        issueType.replaceAll("%252F", "%2F");
        issueType = URLDecoder.decode(issueType);
        
        Date startDateObject;
        Date endDateObject;
        if (startDate == 0) {
            startDateObject = null;
        } else {
            startDateObject = new Date(startDate);
        }
        if (endDate == 0) {
            endDateObject = null;
        } else {
            endDateObject = new Date(endDate);
        }
        
        List<WorklogGanttForUser> list = workLogServiceImp.getGanttForUserDuration(userKey, projectId,
        		startDateObject, endDateObject, type, componentId, versionId, status, issueType, issueStatus);
        return Response.ok(list).build();
    }
    
    //-------------------------------------------------
    
    @GET
    @Path("gantt/user/{userName}/{projectId}/{month}/{year}")
    @Produces({"application/json"})
    public Response getGanttForUserInProject(
            @PathParam("userName") String userKey,
            @PathParam("projectId") Long projectId,
            @PathParam("month") Integer month, @PathParam("year") Integer year) {
        List<WorkLogGantt> list = workLogServiceImp.getGanttForUser(userKey,
                projectId, month, year);
        return Response.ok(list).build();
    }

    @GET
    @Path("gantt/user/{userName}/{projectId}/{month}/{year}/{type}/{componentId}/{versionId}/{status}/{issueType}/{issueStatus}")
    @Produces({"application/json"})
    public Response getGanttForUserInProject(@PathParam("userName") String userKey,
                                             @PathParam("projectId") Long projectId,
                                             @PathParam("month") Integer month,
                                             @PathParam("year") Integer year,
                                             @PathParam("type") String type,
                                             @PathParam("componentId") String componentId,
                                             @PathParam("versionId") String versionId,
                                             @PathParam("status") String status,
                                             @PathParam("issueType") String issueType,
                                             @PathParam("issueStatus") String issueStatus) {
        issueType.replaceAll("%252F", "%2F");
        issueType = URLDecoder.decode(issueType);
        List<WorkLogGantt> list = workLogServiceImp.getGanttForUser(userKey,
                projectId, month, year, type, componentId, versionId, status, issueType, issueStatus);
        return Response.ok(list).build();
    }

    @GET
    @Path("gantt/user/noneWorkLog/{userName}/{projectId}/{month}/{year}")
    @Produces({"application/json"})
    public Response getGanttForUserInProjectNoneWorkLog(
            @PathParam("userName") String userKey,
            @PathParam("projectId") Long projectId,
            @PathParam("month") Integer month, @PathParam("year") Integer year) {
        List<WorkLogGantt> list = workLogServiceImp.getGanttForUser(userKey,
                projectId, month, year);
        List<WorkLogGantt> listResult = workLogServiceImp
                .getIssueNoneWorkLogForGantt(list, projectId);
        return Response.ok(listResult).build();
    }

    @GET
    @Path("gantt/user/noneWorkLog/{userName}/{projectId}/{month}/{year}/{type}/{componentId}/{versionId}/{status}/{issueType}/{issueStatus}")
    @Produces({"application/json"})
    public Response getGanttForUserInProjectNoneWorkLog(@PathParam("userName") String userKey,
                                                        @PathParam("projectId") Long projectId,
                                                        @PathParam("month") Integer month,
                                                        @PathParam("year") Integer year,
                                                        @PathParam("type") String type,
                                                        @PathParam("componentId") String componentId,
                                                        @PathParam("versionId") String versionId,
                                                        @PathParam("status") String status,
                                                        @PathParam("issueType") String issueType,
                                                        @PathParam("issueStatus") String issueStatus) {
        issueType.replaceAll("%252F", "%2F");
        issueType = URLDecoder.decode(issueType);
        List<WorkLogGantt> list = workLogServiceImp.getGanttForUser(userKey,
                projectId, month, year, type, componentId, versionId, status, issueType, issueStatus);
        List<WorkLogGantt> listResult = workLogServiceImp
                .getIssueNoneWorkLogForGantt(list, projectId, issueStatus);
        return Response.ok(listResult).build();
    }

    // For search WorkLog in My WorkLog List
    @GET
    @Path("user/{userName}/{projectId}/{startDate}/{endDate}/{status}/{currentPage}/{recordInPage}")
    @Produces({"application/json"})
    public Response getWorkLogForUserInProject(
            @PathParam("userName") String userKey,
            @PathParam("projectId") Long projectId,
            @PathParam("startDate") Long startDate,
            @PathParam("endDate") Long endDate,
            @PathParam("status") String status,
            @PathParam("currentPage") Integer currentPage,
            @PathParam("recordInPage") Integer recordInPage) {
        Date startDateObject;
        Date endDateObject;
        if (startDate == 0) {
            startDateObject = null;
        } else {
            startDateObject = new Date(startDate);
        }
        if (endDate == 0) {
            endDateObject = null;
        } else {
            endDateObject = new Date(endDate);
        }

        List<WorkLog> list = workLogServiceImp.getWorkLogForCurrentUser(
                currentPage, recordInPage, userKey, projectId, startDateObject,
                endDateObject, status);
        List<WorkLogRestModel> models = new ArrayList<WorkLogRestModel>();
        for (WorkLog workLog : list) {
            models.add(workLogServiceImp.convertModel(workLog));
        }
        float totalHour = 0;
        totalHour = workLogServiceImp.getTotalHourWorkLogForCurrentUser(
                userManager.getRemoteUsername(), projectId, startDateObject,
                endDateObject, status);
        int totalRecord = workLogServiceImp.getWorkLogForCurrentUserTotal(
                userKey, projectId, startDateObject, endDateObject, status);
        PaginationUtil paginationUtil = new PaginationUtil(totalRecord, "",
                currentPage);
        paginationUtil.setRecordInPage(recordInPage);
        List<Integer> listRelatePage = paginationUtil.getRelatePageIndexes();

        WorkLogForList workLogForList = new WorkLogForList();
        workLogForList.setListWorkLog(models);
        workLogForList.setTotal(CommonUtil
                .convertFloatStringToDisPlay(totalHour));
        workLogForList.setCurrentPage(currentPage);
        workLogForList.setRecordInPage(recordInPage);
        workLogForList.setTotalPage(paginationUtil.getNumOfPages());
        workLogForList.setListRelatePage(listRelatePage);
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            return Response.ok(objectMapper.writeValueAsString(workLogForList))
                    .build();
        } catch (JsonGenerationException e) {
        } catch (JsonMappingException e) {
        } catch (IOException e) {
        }
        return Response.ok(null).build();
    }

    @GET
    @Path("user/{userName}/{projectId}/{startDate}/{endDate}/{type}/{componentId}/{versionId}/{status}/{issueType}/{issueStatus}/{currentPage}/{recordInPage}")
    @Produces({"application/json"})
    public Response getWorkLogForUserInProject(
            @PathParam("userName") String userKey,
            @PathParam("projectId") Long projectId,
            @PathParam("startDate") Long startDate,
            @PathParam("endDate") Long endDate,
            @PathParam("type") String type,
            @PathParam("componentId") String componentId,
            @PathParam("versionId") String versionId,
            @PathParam("status") String status,
            @PathParam("issueType") String issueType,
            @PathParam("issueStatus") String issueStatus,
            @PathParam("currentPage") Integer currentPage,
            @PathParam("recordInPage") Integer recordInPage) {
        issueType.replaceAll("%252F", "%2F");
        issueType = URLDecoder.decode(issueType);
        Date startDateObject;
        Date endDateObject;
        if (startDate == 0) {
            startDateObject = null;
        } else {
            startDateObject = new Date(startDate);
        }
        if (endDate == 0) {
            endDateObject = null;
        } else {
            endDateObject = new Date(endDate);
        }
        List<WorkLog> list = workLogServiceImp.getWorkLogForCurrentUser(
                currentPage, recordInPage, userKey, projectId, startDateObject,
                endDateObject, type, componentId, versionId, status, issueType, issueStatus);
        List<WorkLogRestModel> models = new ArrayList<WorkLogRestModel>();
        for (WorkLog workLog : list) {
            models.add(workLogServiceImp.convertModel(workLog));
        }
        float totalHour = workLogServiceImp.getTotalHourWorkLogForCurrentUser(
                projectId, userManager.getRemoteUsername(), startDateObject,
                endDateObject, type, componentId, versionId, status, issueType, issueStatus);
        int totalRecord = workLogServiceImp.getWorkLogForCurrentUserTotal(projectId, userKey,
                startDateObject, endDateObject,
                type, componentId, versionId, status, issueType, issueStatus);
        PaginationUtil paginationUtil = new PaginationUtil(totalRecord, "",
                currentPage);
        paginationUtil.setRecordInPage(recordInPage);
        List<Integer> listRelatePage = paginationUtil.getRelatePageIndexes();
        WorkLogForList workLogForList = new WorkLogForList();
        workLogForList.setListWorkLog(models);
        workLogForList.setTotal(CommonUtil
                .convertFloatStringToDisPlay(totalHour));
        workLogForList.setCurrentPage(currentPage);
        workLogForList.setRecordInPage(recordInPage);
        workLogForList.setTotalPage(paginationUtil.getNumOfPages());
        workLogForList.setListRelatePage(listRelatePage);
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            return Response.ok(objectMapper.writeValueAsString(workLogForList))
                    .build();
        } catch (JsonGenerationException e) {
        } catch (JsonMappingException e) {
        } catch (IOException e) {
        }
        return Response.ok(null).build();
    }

    @GET
    @Path("gantt/projectWorkLog/{projectId}/duration/{startDate}/{endDate}/{status}/{typeOfWork}/{issueType}/{component}/{version}")
    @Produces({"application/json"})
    public Response getGanttForProject(
    		@PathParam("projectId") Long projectId,
            @PathParam("startDate") Long startDate,
            @PathParam("endDate") Long endDate,
            @PathParam("status") String status,
            @PathParam("typeOfWork") String typeOfWork,
            @PathParam("issueType") String issueType,
            @PathParam("component") String componentIds,
            @PathParam("version") String version) {
    	issueType.replaceAll("%252F", "%2F");
    	issueType = URLDecoder.decode(issueType);
    	System.out.println("issueType "+issueType);
        Date startDateObject;
        Date endDateObject;
        if (startDate == 0) {
            startDateObject = null;
        } else {
            startDateObject = new Date(startDate);
        }
        if (endDate == 0) {
            endDateObject = null;
        } else {
            endDateObject = new Date(endDate);
        }
        
        List<String> components = new ArrayList<String>();
        if (ObjectUtils.isNotEmpty(componentIds)
                && !"null".equalsIgnoreCase(componentIds)) {
            components = newArrayList(componentIds.split(","));
        } else {
            components = null;
        }
        List<String> versions = new ArrayList<String>();
        if (ObjectUtils.isNotEmpty(version)
                && !"null".equalsIgnoreCase(version)) {
            versions = newArrayList(version.split(","));
        } else {
            versions = null;
        }
        
        List<WorkLogGantt> list = workLogServiceImp.getGanttForProject(
                projectId, startDateObject, endDateObject, status, typeOfWork, issueType,
                components, versions);
        return Response.ok(list).build();
    }

    @GET
    @Path("gantt/qa/month/{month}/{year}")
    @Produces({"application/json"})
    public Response getGanttForQAMonth(@PathParam("month") int month,
                                       @PathParam("year") int year, @QueryParam("user") String user,
                                       @QueryParam("bg") String bg, @QueryParam("ou") String ou) {
        List<String> listUser = new ArrayList<String>();
        List<WorkLogGantt> list = new ArrayList<WorkLogGantt>();
        User authenticatedUser = userManagerUtil.getUserByKey(
                userManager.getRemoteUsername().toLowerCase())
                .getDirectoryUser();
        Map<ProjectGroup, Map<ProjectUnit, List<ProjectConfigInfo>>> maps = projectConfigInfoService
                .getBGAndOUOfUser(authenticatedUser, ReportName.QA_TIME_SHEET);
        if (ObjectUtils.isNotEmpty(user)) {
            String users[] = user.toLowerCase().split(",");
            list = workLogServiceImp.getGanttForQAByBGAndOu(users, month, year);
        } else {
            if (!ObjectUtils.isNotEmpty(bg) && !ObjectUtils.isNotEmpty(ou)) {
                // FIXME Find user by BG and OU only
                List<ProjectUnit> projectUnits = new ArrayList<ProjectUnit>();
                List<ProjectGroup> projectGroups = new ArrayList<ProjectGroup>(
                        maps.keySet());
                for (ProjectGroup projectGroup : projectGroups) {
                    projectUnits.addAll(maps.get(projectGroup).keySet());
                }
                List<User> userList = new ArrayList<User>();
                for (ProjectUnit projectUnit : projectUnits) {
                    if (projectUnit != null) {
                        userList.addAll(projectUnitService
                                .getListUserInOrganizationUnitExcludingTopUser(projectUnit));
                    }
                }
                for (User user2 : userList) {
                    listUser.add(user2.getName().toLowerCase());
                }
                String users[] = listUser.toArray(new String[listUser.size()]);
                list = workLogServiceImp.getGanttForQAByBGAndOu(users, month,
                        year);
            } else {
                if (!ObjectUtils.isNotEmpty(ou)) {
                    ProjectGroup projectGroup = projectGroupService
                            .getProjectGroupByName(bg);
                    List<ProjectUnit> projectUnits = new ArrayList<ProjectUnit>(
                            maps.get(projectGroup).keySet());
                    List<User> userList = new ArrayList<User>();
                    for (ProjectUnit projectUnit : projectUnits) {
                        if (projectUnit != null) {
                            userList.addAll(projectUnitService
                                    .getListUserInOrganizationUnitExcludingTopUser(projectUnit));
                        }
                    }
                    for (User user2 : userList) {
                        listUser.add(user2.getName().toLowerCase());
                    }
                    String users[] = listUser.toArray(new String[listUser
                            .size()]);
                    list = workLogServiceImp.getGanttForQAByBGAndOu(users,
                            month, year);
                } else {
                    ProjectUnit projectUnit = projectUnitService
                            .getProjectUnitByName(ou);
                    List<User> userList = projectUnitService
                            .getListUserInOrganizationUnitExcludingTopUser(projectUnit);
                    for (User user2 : userList) {
                        listUser.add(user2.getName().toLowerCase());
                    }
                    String users[] = listUser.toArray(new String[listUser
                            .size()]);
                    list = workLogServiceImp.getGanttForQAByBGAndOu(users,
                            month, year);
                }
            }
        }
        return Response.ok(list).build();
    }

    @GET
    @Path("gantt/qa/duration/{startDate}/{endDate}")
    @Produces({"application/json"})
    public Response getGanttForQADuration(
            @PathParam("startDate") Long startDate,
            @PathParam("endDate") Long endDate,
            @QueryParam("user") String user, @QueryParam("bg") String bg,
            @QueryParam("ou") String ou) {
        Date startDateObject;
        Date endDateObject;
        if (startDate == 0) {
            startDateObject = null;
        } else {
            startDateObject = new Date(startDate);
        }
        if (endDate == 0) {
            endDateObject = null;
        } else {
            endDateObject = new Date(endDate);
        }
        List<String> listUser = new ArrayList<String>();
        List<WorkLogGantt> list = new ArrayList<WorkLogGantt>();
        User authenticatedUser = userManagerUtil.getUserByKey(
                userManager.getRemoteUsername().toLowerCase())
                .getDirectoryUser();
        Map<ProjectGroup, Map<ProjectUnit, List<ProjectConfigInfo>>> maps = projectConfigInfoService
                .getBGAndOUOfUser(authenticatedUser, ReportName.QA_TIME_SHEET);
        if (ObjectUtils.isNotEmpty(user)) {
            if (ObjectUtils.isNotEmpty(bg) || ObjectUtils.isNotEmpty(ou)) {
                String users[] = user.split(",");
                list = workLogServiceImp.getGanttForQAByBGAndOuInDuration(
                        users, startDateObject, endDateObject);
            } else {
                String users[] = user.split(",");
                list = workLogServiceImp.getGanttForQAByBGAndOuInDuration(
                        users, startDateObject, endDateObject);
            }
        } else {
            if (!ObjectUtils.isNotEmpty(bg) && !ObjectUtils.isNotEmpty(ou)) {
                // FIXME Find user by BG and OU only
                List<ProjectUnit> projectUnits = new ArrayList<ProjectUnit>();
                List<ProjectGroup> projectGroups = new ArrayList<ProjectGroup>(
                        maps.keySet());
                for (ProjectGroup projectGroup : projectGroups) {
                    projectUnits.addAll(projectUnitService
                            .getListProjectUnitByGroupCode(projectGroup
                                    .getGroupCode()));
                }
                List<User> userList = new ArrayList<User>();
                for (ProjectUnit projectUnit : projectUnits) {
                    userList.addAll(projectUnitService
                            .getListUserInOrganizationUnitExcludingTopUser(projectUnit));
                }
                for (User user2 : userList) {
                    try {
                        listUser.add(userManagerUtil.getUserByKey(
                                user2.getName().toLowerCase()).getUsername());
                    } catch (NullPointerException e) {
                    }
                }
                String users[] = listUser.toArray(new String[listUser.size()]);
                list = workLogServiceImp.getGanttForQAByBGAndOuInDuration(
                        users, startDateObject, endDateObject);
            } else {
                if (!ObjectUtils.isNotEmpty(ou)) {
                    ProjectGroup projectGroup = projectGroupService
                            .getProjectGroupByName(bg);
                    List<ProjectUnit> projectUnits = new ArrayList<ProjectUnit>(
                            maps.get(projectGroup).keySet());
                    List<User> userList = new ArrayList<User>();
                    for (ProjectUnit projectUnit : projectUnits) {
                        userList.addAll(projectUnitService
                                .getListUserInOrganizationUnitExcludingTopUser(projectUnit));
                    }
                    for (User user2 : userList) {
                        listUser.add(userManagerUtil.getUserByKey(
                                user2.getName().toLowerCase()).getUsername());
                    }
                    String users[] = listUser.toArray(new String[listUser
                            .size()]);
                    list = workLogServiceImp.getGanttForQAByBGAndOuInDuration(
                            users, startDateObject, endDateObject);
                } else {
                    ProjectUnit projectUnit = projectUnitService
                            .getProjectUnitByName(ou);
                    List<User> userList = projectUnitService
                            .getListUserInOrganizationUnitExcludingTopUser(projectUnit);
                    for (User user2 : userList) {
                        listUser.add(userManagerUtil.getUserByKey(
                                user2.getName().toLowerCase()).getUsername());
                    }
                    String users[] = listUser.toArray(new String[listUser
                            .size()]);
                    list = workLogServiceImp.getGanttForQAByBGAndOuInDuration(
                            users, startDateObject, endDateObject);
                }
            }
        }
        return Response.ok(list).build();
    }

    @GET
    @Path("gantt/user/month/{month}/{year}")
    @Produces({"application/json"})
    public Response getGanttForUserMonth(@PathParam("month") int month,
                                         @PathParam("year") int year) {
        List<WorkLogGantt> list = new ArrayList<WorkLogGantt>();
        @SuppressWarnings("deprecation")
        String user = jiraAuthenticationContext.getLoggedInUser().getName();
        if (ObjectUtils.isNotEmpty(user)) {
            String users[] = user.toLowerCase().split(",");
            list = workLogServiceImp.getGanttForQAByBGAndOu(users, month, year);
        }
        return Response.ok(list).build();
    }

    @GET
    @Path("gantt/user/duration/{startDate}/{endDate}")
    @Produces({"application/json"})
    public Response getGanttForUserDuration(
            @PathParam("startDate") Long startDate,
            @PathParam("endDate") Long endDate) {
        Date startDateObject;
        Date endDateObject;
        if (startDate == 0) {
            startDateObject = null;
        } else {
            startDateObject = new Date(startDate);
        }
        if (endDate == 0) {
            endDateObject = null;
        } else {
            endDateObject = new Date(endDate);
        }
        String user = jiraAuthenticationContext.getUser().getName();
        List<WorkLogGantt> list = new ArrayList<WorkLogGantt>();
        String users[] = user.split(",");
        list = workLogServiceImp.getGanttForQAByBGAndOuInDuration(users,
                startDateObject, endDateObject);
        return Response.ok(list).build();
    }
/*----------------project work log gantt 
 * */
    @GET
    @Path("gantt/projectWorkLog/{projectId}/month/{month}/{year}/{status}/{typeOfWork}/{issueType}/{component}/{version}")
    @Produces({"application/json"})
    public Response getGanttForProject(
    		@PathParam("projectId") Long projectId,
            @PathParam("month") Integer month, 
            @PathParam("year") Integer year,
            @PathParam("status") String status,
            @PathParam("typeOfWork") String typeOfWork,
            @PathParam("issueType") String issueType,
            @PathParam("component") String componentIds,
            @PathParam("version") String version) {
    	issueType.replaceAll("%252F", "%2F");
    	issueType = URLDecoder.decode(issueType);
    	System.out.println("issueType "+issueType);
    	List<String> components = new ArrayList<String>();
        if (ObjectUtils.isNotEmpty(componentIds)
                && !"null".equalsIgnoreCase(componentIds)) {
            components = newArrayList(componentIds.split(","));
        } else {
            components = null;
        }
        List<String> versions = new ArrayList<String>();
        if (ObjectUtils.isNotEmpty(version)
                && !"null".equalsIgnoreCase(version)) {
            versions = newArrayList(version.split(","));
        } else {
            versions = null;
        }
    	
        List<WorkLogGantt> list = workLogServiceImp.getGanttForProject(
                projectId, month, year,status, typeOfWork, issueType, components, versions);
        return Response.ok(list).build();
    }

    // For search all WorkLog in Project WorkLog List
    @GET
    @Path("project/{projectId}/{userKey}/{startDate}/{endDate}/{status}/{typeOfWork}/{issueType}/{component}/{version}/{currentPage}/{recordInPage}")
    @Produces({"application/json"})
    public Response getWorkLogForAllWorkLogProject(
            @PathParam("userKey") String userKey,
            @PathParam("projectId") Long projectId,
            @PathParam("startDate") Long startDate,
            @PathParam("endDate") Long endDate,
            @PathParam("status") String status,
            @PathParam("typeOfWork") String typeOfWork,
            @PathParam("issueType") String issueType,
            @PathParam("component") String componentIds,
            @PathParam("version") String version,
            @PathParam("currentPage") Integer currentPage,
            @PathParam("recordInPage") Integer recordInPage) {
    	
    	issueType.replaceAll("%252F", "%2F");
    	issueType = URLDecoder.decode(issueType);
    	
    	System.out.println("issueType "+issueType);
        Date startDateObject;
        Date endDateObject;
        if (startDate == 0) {
            startDateObject = null;
        } else {
            startDateObject = new Date(startDate);
            startDateObject = DateUtils.standardizeDate(startDateObject, 0, 0,
                    0, 0);
        }
        if (endDate == 0) {
            endDateObject = null;
        } else {
            endDateObject = new Date(endDate);
            endDateObject = DateUtils.standardizeDate(endDateObject, 23, 59,
                    59, 999);
        }

        if ("null".equals(userKey)) {
            userKey = null;
        }
        List<String> components = new ArrayList<String>();
        if (ObjectUtils.isNotEmpty(componentIds)
                && !"null".equalsIgnoreCase(componentIds)) {
            components = newArrayList(componentIds.split(","));
        } else {
            components = null;
        }
        List<String> versions = new ArrayList<String>();
        if (ObjectUtils.isNotEmpty(version)
                && !"null".equalsIgnoreCase(version)) {
            versions = newArrayList(version.split(","));
        } else {
            versions = null;
        }
        ApplicationUser currentUser = jiraAuthenticationContext.getUser();
        Project project = projectManager.getProjectObj(projectId);
        boolean checkPm = workLogServiceImp.checkPM(project, currentUser);
        boolean checkQA = workLogServiceImp.checkQA(project, currentUser);
        boolean checkTl = workLogServiceImp.checkTeamLead(project, currentUser);
        List<String> userKeys = new ArrayList<String>();
        List<WorkLog> list = new ArrayList<WorkLog>();
        if (checkPm || checkQA) {
            list = workLogServiceImp.getAllWorkLogForAdmin(projectId,
                    currentPage, recordInPage, userKey, startDateObject,
                    endDateObject, status, typeOfWork, issueType, components, versions);
        } else if (checkTl) {
            userKeys = projectTeamService.getUserByTeamLead(project.getKey(),
                    currentUser.getKey());
            if (userKeys.size() > 0) {
                list = workLogServiceImp.getAllWorkLogForAdmin(projectId,
                        currentPage, recordInPage, userKey, startDateObject,
                        endDateObject, status, typeOfWork, issueType, components,
                        versions, userKeys);
            }
        }
        List<WorkLogRestModel> models = new ArrayList<WorkLogRestModel>();
        for (WorkLog workLog : list) {
            models.add(workLogServiceImp.convertModel(workLog));
        }
        Map<String, Float> map = workLogServiceImp
                .totalHourAndRecordAllWorkLogForAdmin(projectId, userKey,
                        startDateObject, endDateObject, status, typeOfWork, issueType,
                        components, versions, checkPm, checkTl, checkQA,
                        userKeys);
        float totalHour = map.get(TOTAL_HOUR);
        int totalRecord = map.get(TOTAL_RECORD).intValue();
        PaginationUtil paginationUtil = new PaginationUtil(totalRecord, "",
                currentPage);
        paginationUtil.setRecordInPage(recordInPage);
        List<Integer> listRelatePage = paginationUtil.getRelatePageIndexes();

        WorkLogForList workLogForList = new WorkLogForList();
        workLogForList.setListWorkLog(models);
        workLogForList.setTotal(CommonUtil
                .convertFloatStringToDisPlay(totalHour));
        workLogForList.setCurrentPage(currentPage);
        workLogForList.setRecordInPage(recordInPage);
        workLogForList.setTotalPage(paginationUtil.getNumOfPages());
        workLogForList.setListRelatePage(listRelatePage);
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            return Response.ok(objectMapper.writeValueAsString(workLogForList))
                    .build();
        } catch (JsonGenerationException e) {
        } catch (JsonMappingException e) {
        } catch (IOException e) {
        }
        return Response.ok(null).build();
    }

    // For search Pending WorkLog in Project WorkLog List
    @GET
    @Path("project/{projectId}/{userKey}/{startDate}/{endDate}/{typeOfWork}/{issueType}/{component}/{version}/{currentPage}/{recordInPage}")
    @Produces({"application/json"})
    public Response getWorkLogForPendingWorkLogProject(
            @PathParam("userKey") String userKey,
            @PathParam("projectId") Long projectId,
            @PathParam("startDate") Long startDate,
            @PathParam("endDate") Long endDate,
            @PathParam("typeOfWork") String typeOfWork,
            @PathParam("issueType") String issueType,
            @PathParam("component") String component,
            @PathParam("version") String version,
            @PathParam("currentPage") Integer currentPage,
            @PathParam("recordInPage") Integer recordInPage) {
    	issueType.replaceAll("%252F", "%2F");
    	issueType = URLDecoder.decode(issueType);
    	System.out.println("issueType "+issueType);
        Date startDateObject;
        Date endDateObject;
        if (startDate == 0) {
            startDateObject = null;
        } else {
            startDateObject = new Date(startDate);
            startDateObject = DateUtils.standardizeDate(startDateObject, 0, 0,
                    0, 0);
        }
        if (endDate == 0) {
            endDateObject = null;
        } else {
            endDateObject = new Date(endDate);
            endDateObject = DateUtils.standardizeDate(endDateObject, 23, 59,
                    59, 999);
        }

        if ("null".equals(userKey)) {
            userKey = null;
        }
        List<String> components = new ArrayList<String>();
        if (ObjectUtils.isNotEmpty(component)
                && !"null".equalsIgnoreCase(component)) {
            components = newArrayList(component.split(","));
        } else {
            components = null;
        }
        List<String> versions = new ArrayList<String>();
        if (ObjectUtils.isNotEmpty(version)
                && !"null".equalsIgnoreCase(version)) {
            versions = newArrayList(version.split(","));
        } else {
            versions = null;
        }
        ApplicationUser user = jiraAuthenticationContext.getUser();
        Project project = projectManager.getProjectObj(projectId);
        boolean checkPm = workLogServiceImp.checkPM(project);
        boolean checkTl = workLogServiceImp.checkTL(project);
        List<String> userKeys = new ArrayList<String>();
        ApplicationUser currentUser = jiraAuthenticationContext.getUser();
        userKeys = projectTeamService.getUserByTeamLead(project.getKey(),
                currentUser.getKey());
        List<WorkLog> list = new ArrayList<WorkLog>();
        if (!checkPm && checkTl) {
            if (userKeys.size() > 0) {
                list = workLogServiceImp.getWorkLogPendingForAdmin(projectId,
                        currentPage, recordInPage, userKey, startDateObject,
                        endDateObject, typeOfWork, issueType, components, versions,
                        userKeys);
            }
        } else {
            list = workLogServiceImp.getWorkLogPendingForAdmin(projectId,
                    currentPage, recordInPage, userKey, startDateObject,
                    endDateObject, typeOfWork, issueType, components, versions);
        }
        List<WorkLogRestModel> models = new ArrayList<WorkLogRestModel>();

        List<String> users = projectTeamService.getUserByTeamLead(
                project.getKey(), user.getKey());
        String check = STRING_TRUE;
        String status = EMPTY;
        for (WorkLog workLog : list) {
            check = STRING_TRUE;
            status = EMPTY;
            if (checkPm || checkTl) {
                try {
                    status = statusManager.getStatus(
                            issueManager.getIssueByCurrentKey(workLog.getIssueKey()).getStatusId())
                            .getName();
                } catch (NullPointerException e) {

                }
                if (CLOSED.equalsIgnoreCase(status)
                        || CANCELED.equalsIgnoreCase(status)
                        || ACCEPTED.equalsIgnoreCase(status)) {
                    check = STRING_FALSE;
                }
                workLog.setProjectName(check);
                models.add(workLogServiceImp.convertModel(workLog));
            } else {
                for (String member : users) {
                    if (member.equalsIgnoreCase(workLog.getUserKey())) {
                        try {
                            status = statusManager.getStatus(
                                    issueManager.getIssueByCurrentKey(
                                            workLog.getIssueKey())
                                            .getStatusId()).getName();
                        } catch (NullPointerException e) {

                        }
                        if (CLOSED.equalsIgnoreCase(status)
                                || CANCELED.equalsIgnoreCase(status)
                                || ACCEPTED.equalsIgnoreCase(status)) {
                            check = STRING_FALSE;
                        }
                        workLog.setProjectName(check);
                        models.add(workLogServiceImp.convertModel(workLog));
                    }
                }
            }

        }
        Map<String, Float> map = workLogServiceImp
                .totalHourWorkLogPendingForAdminTotal(projectId, userKey,
                        startDateObject, endDateObject, typeOfWork, issueType, components,
                        versions, checkPm, checkTl, userKeys);
        float totalHour = map.get(TOTAL_HOUR);
        int totalRecord = map.get(TOTAL_RECORD).intValue();
        PaginationUtil paginationUtil = new PaginationUtil(totalRecord, "",
                currentPage);
        paginationUtil.setRecordInPage(recordInPage);
        List<Integer> listRelatePage = paginationUtil.getRelatePageIndexes();

        WorkLogForList workLogForList = new WorkLogForList();
        workLogForList.setListWorkLog(models);
        workLogForList.setTotal(CommonUtil
                .convertFloatStringToDisPlay(totalHour));
        workLogForList.setCurrentPage(currentPage);
        workLogForList.setRecordInPage(recordInPage);

        workLogForList.setTotalPage(paginationUtil.getNumOfPages());
        workLogForList.setListRelatePage(listRelatePage);
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            return Response.ok(objectMapper.writeValueAsString(workLogForList))
                    .build();
        } catch (JsonGenerationException e) {
        } catch (JsonMappingException e) {
        } catch (IOException e) {
        }
        return Response.ok(null).build();
    }

    @GET
    @Path("minMaxWorkLogDateByComponent/{projectId}/{componentId}")
    @Produces({"application/json"})
    public Response getMinMaxWorkLogDateByComponent(
            @PathParam("projectId") long projectId,
            @PathParam("componentId") long componentId) {
        return Response.ok(
                workLogServiceImp.getMinMaxWorkLogDateByComponent(componentId,
                        projectId)).build();
    }

    @GET
    @Path("checkForAbsence/{dateStr}")
    @Produces({"application/json"})
    public Response checkForAbsence(@PathParam("dateStr") String dateStr) {
        String[] dateStrs = dateStr.split(",");
        List<WorkLog> listWorkLog = new ArrayList<WorkLog>();
        List<WorkLog> listWorkLogReturn = new ArrayList<WorkLog>();
        for (String date : dateStrs) {
            listWorkLog.addAll(workLogServiceImp.getWorkLogCheckWorkPerDay(
                    new Date(Long.valueOf(date)), new Date(Long.valueOf(date)),
                    userManager.getRemoteUsername()));
        }
        for (WorkLog worklog : listWorkLog) {
            try {
                if (issueManager.isExistingIssueKey(worklog.getIssueKey())) {
                    listWorkLogReturn.add(worklog);
                }
            } catch (GenericEntityException e) {
                e.printStackTrace();
            }
        }
        if (!listWorkLogReturn.isEmpty()) {
            return Response.ok(false).build();
        } else {
            return Response.ok(true).build();
        }
    }

    @GET
    @Path("checkForAbsenceForUser/{UserKey}/{dateStr}")
    @Produces({"application/json"})
    public Response checkForAbsenceByUser(@PathParam("UserKey") String userKey,
                                          @PathParam("dateStr") String dateStr) {
        String[] dateStrs = dateStr.split(",");
        List<WorkLog> listWorkLog = new ArrayList<WorkLog>();
        List<WorkLog> listWorkLogReturn = new ArrayList<WorkLog>();
        for (String date : dateStrs) {
            listWorkLog.addAll(workLogServiceImp.getWorkLogCheckWorkPerDay(
                    new Date(Long.valueOf(date)), new Date(Long.valueOf(date)),
                    userKey));
        }
        for (WorkLog worklog : listWorkLog) {
            try {
                if (issueManager.isExistingIssueKey(worklog.getIssueKey())) {
                    listWorkLogReturn.add(worklog);
                }
            } catch (GenericEntityException e) {
                e.printStackTrace();
            }
        }
        if (!listWorkLogReturn.isEmpty()) {
            return Response.ok(false).build();
        } else {
            return Response.ok(true).build();
        }
    }

    @GET
    @Path("worklognoneissue")
    @Produces({"application/json"})
    public Response getWorkLogNoneIssue() {
        List<WorkLog> listWorkLog = workLogServiceImp.searchWorkLogNoneIssue();
        List<WorkLogView> listWorkLogView = workLogServiceImp
                .convertWorkLogList(listWorkLog);
        return Response.ok(listWorkLogView).build();
    }

    @GET
    @Path("worklog/checkComponent/{issueKey}")
    @Produces({"application/json"})
    public Response checkComponent(@PathParam("issueKey") String issueKey) {
        String rest = "";
        String result = "";
        Map<String, Boolean> map = new HashMap<String, Boolean>();
        try {
            MutableIssue issue = issueManager.getIssueByCurrentKey(issueKey);
            List<ProjectComponent> components = new ArrayList<ProjectComponent>(
                    issue.getComponentObjects());
            List<Version> fixedVersion = new ArrayList<Version>(
                    issue.getAffectedVersions());
            if (components.size() != 1) {
                map.put("Component", false);
                return Response.ok(map).build();
            } else {
                if (fixedVersion.size() < 1) {
                    map.put("Version", false);
                    return Response.ok(map).build();
                }
                rest = "/rest/fis-component-details/1.0/cdetails/checkReleaseAndCancelComponent/"
                        + components.get(0).getId();
                result = "";
                try {
                    result = RestUtil.getResponseRestUnauthorized(rest);
                    if ("true".equalsIgnoreCase(result)) {
                        map.put("Component", false);
                        return Response.ok(map).build();
                    } else {
                        map.put("Component", true);
                        return Response.ok(map).build();
                    }
                } catch (AuthenticationException e) {
                    map.put("Component", false);
                    return Response.ok(map).build();
                }

            }
        } catch (NullPointerException e) {
            map.put("Both", false);
            return Response.ok(map).build();
        }
    }

    @GET
    @Path("worklog/checkFixedVersion/{issueKey}")
    @Produces({"application/json"})
    public Response checkFixedVersion(@PathParam("issueKey") String issueKey) {

        try {
            MutableIssue issue = issueManager.getIssueByCurrentKey(issueKey);
            List<Version> fixedVersion = new ArrayList<Version>(
                    issue.getAffectedVersions());
            if (fixedVersion.size() != 1) {
                return Response.ok(false).build();
            } else {
                String rest = "/rest/fisversiondetails/1.0/versiondetails/checkCompleteVersion/"
                        + fixedVersion.get(0).getId();
                String result = "";
                try {
                    result = RestUtil.getResponseRestUnauthorized(rest);
                    if ("true".equalsIgnoreCase(result)) {
                        return Response.ok(false).build();
                    }
                } catch (AuthenticationException e) {
                    return Response.ok(false).build();
                }

            }
            return Response.ok(true).build();
        } catch (NullPointerException e) {
            return Response.ok(false).build();
        }

    }

    @GET
    @Path("worklog/checkAffectedVersion/{issueKey}")
    @Produces({"application/json"})
    public Response checkAffectedVersion(@PathParam("issueKey") String issueKey) {
        try {
            MutableIssue issue = issueManager.getIssueByCurrentKey(issueKey);
            List<Version> affectedVersions = new ArrayList<Version>(
                    issue.getFixVersions());
            if (affectedVersions.size() != 1) {
                return Response.ok(false).build();
            } else {
                String rest = "/rest/fisversiondetails/1.0/versiondetails/checkCompleteVersion/"
                        + affectedVersions.get(0).getId();
                String result = "";
                try {
                    result = RestUtil.getResponseRestUnauthorized(rest);
                    if ("true".equalsIgnoreCase(result)) {
                        return Response.ok(false).build();
                    }
                } catch (AuthenticationException e) {
                    return Response.ok(false).build();
                }

            }
            return Response.ok(true).build();
        } catch (NullPointerException e) {
            return Response.ok(false).build();
        }
    }

    @GET
    @Path("worklog/checkIssueCloseAndCancel/{issueKey}")
    @Produces({"application/json"})
    public Response checkIssueCloseAndCancel(
            @PathParam("issueKey") String issueKey) {
        return Response.ok(workLogServiceImp.checkIssueClose(issueKey)).build();
    }

    @GET
    @Path("worklog/checkRequiredDefectResolved/{issueKey}")
    @Produces({"application/json"})
    public Response checkRequiredDefectResolved(
            @PathParam("issueKey") String issueKey) {
        String[] requireFieldName = {"C&P Action", "Defect Origin",
                "Cause Analysis", "Defect Category"};
        return Response.ok(
                workLogServiceImp.checkRequiredDefectResolved(issueKey,
                        requireFieldName)).build();
    }

    @GET
    @AnonymousAllowed
    @Path("worklog/deleteByProjectId/{projectId}")
    @Produces({"application/json"})
    public Response deleteByProjectId(@PathParam("projectId") int projectId) {
        workLogServiceImp.deleteWorkLogByProjectId(projectId);
        return Response.ok().build();
    }

    @GET
    @Path("worklog/updateAllIssue")
    @Produces({"application/json"})
    public Response upDateAllIssue() {
        try {
            workLogServiceImp.addActualStartAndEndDateAllIssue();
        } catch (Exception e) {
            return Response.ok(false).build();
        }
        return Response.ok(true).build();
    }

    @GET
    @Path("getIssueDescription/{issueKey}")
    @Produces({"application/json"})
    public Response checkIssueDescription(@PathParam("issueKey") String issueKey) {
        List<String> issueDescriptions = new ArrayList<String>();
        issueDescriptions.add(issueManager.getIssueObject(issueKey)
                .getDescription());
        return Response.ok(issueDescriptions).build();
    }

    @GET
    @Path("getIssueComment/{commentId}")
    @Produces({"application/json"})
    public Response checkIssueComment(@PathParam("commentId") Long commentId) {
        List<String> comments = new ArrayList<String>();
        String issueComment = commentManager.getCommentById(commentId)
                .getBody();
        comments.add(issueComment);
        return Response.ok(comments).build();
    }

    @GET
    @Path("getIssueCommentByIssue/{issueKey}")
    @Produces({"application/json"})
    public Response checkIssueCommentByIssue(
            @PathParam("issueKey") String issueKey) {
        Issue issue = issueManager.getIssueObject(issueKey);
        List<Comment> listComment = commentManager.getComments(issue);
        List<String> comments = new ArrayList<String>();
        for (Comment comment : listComment) {
            comments.add(comment.getId() + ": " + comment.getBody());
        }
        return Response.ok(comments).build();
    }

    @GET
    @Path("/QA/search/orginationUnit/{bg}/{reportName}")
    @Produces({"application/json"})
    public Response getOrginationUnitByUser(@PathParam("bg") String bg,
                                            @PathParam("reportName") String reportName) {
        return Response.ok(
                workLogServiceImp.findUnitBeanByGroup(bg, reportName)).build();
    }

    @GET
    @Path("/QA/getMember/{reportName}")
    @Produces({"application/json"})
    public Response getAllMemberByProject(
            @PathParam("reportName") String reportName,
            @QueryParam("bg") String bg, @QueryParam("unit") String unit,
            @QueryParam("query") String query,
            @Context HttpServletRequest request) {
        Map<String, Object> map = new HashMap<String, Object>();
        if (EMPTY_HMTL.equalsIgnoreCase(bg)) {
            bg = "";
        }
        if (EMPTY_HMTL.equalsIgnoreCase(unit)) {
            unit = "";
        }
        List<ProjectConfigInfo> projects = workLogServiceImp
                .findProjectByGroupAndUnit(bg, unit, reportName);
        List<ObjectSearch> users = workLogServiceImp.getAllMemberByProject(
                projects, request.getContextPath(), query);
        map.put("users", users);
        map.put("total", users.size());
        map.put("footer", "Showing " + users.size() + " of " + users.size()
                + " matching users");
        return Response.ok(map).build();
    }

    @GET
    @Path("checkApprovedWorklogs/{workLogIds}")
    @Produces({"application/json"})
    public Response checkApprovedWorklogs(
            @PathParam("workLogIds") String workLogIds) {
        String approvedWorklogsIds = "";
        List<String> listApprovedWorklogsIds = new ArrayList<String>();
        String[] arrayIdWorkLog = workLogIds.split(",");
        for (String workLogId : arrayIdWorkLog) {
            WorkLog workLog = workLogServiceImp.getWorkLogById(Integer
                    .parseInt(workLogId));
            if (workLog != null) {
                try {
                    String issueStatus = workLog.getStatus();
                    if ("Approved".equalsIgnoreCase(issueStatus)) {
                        listApprovedWorklogsIds.add(String.valueOf(workLog
                                .getID()));
                    }
                } catch (Exception e) {
                }
            }
        }
        for (int i = 0; i < listApprovedWorklogsIds.size(); i++) {
            if (i == 0) {
                approvedWorklogsIds += listApprovedWorklogsIds.get(i);
            } else {
                approvedWorklogsIds += "," + listApprovedWorklogsIds.get(i);
            }
        }
        RestReturnModel restString = new RestReturnModel();
        restString.setString(approvedWorklogsIds);
        return Response.ok(restString).build();
    }

    @GET
    @Path("checkRejectPendingWorklogs/{workLogIds}")
    @Produces({"application/json"})
    public Response checkRejectPendingWorklogs(
            @PathParam("workLogIds") String workLogIds) {
        String rejectPendingWorklogsIds = "";
        List<String> listRejectPendingWorklogsIds = new ArrayList<String>();
        String[] arrayIdWorkLog = workLogIds.split(",");
        for (String workLogId : arrayIdWorkLog) {
            WorkLog workLog = workLogServiceImp.getWorkLogById(Integer
                    .parseInt(workLogId));
            if (workLog != null) {
                try {
                    String issueStatus = workLog.getStatus();
                    if ("Rejected".equalsIgnoreCase(issueStatus)
                            || "Pending".equalsIgnoreCase(issueStatus)) {
                        listRejectPendingWorklogsIds.add(String.valueOf(workLog
                                .getID()));
                    }
                } catch (Exception e) {
                }
            }
        }
        for (int i = 0; i < listRejectPendingWorklogsIds.size(); i++) {
            if (i == 0) {
                rejectPendingWorklogsIds += listRejectPendingWorklogsIds.get(i);
            } else {
                rejectPendingWorklogsIds += ","
                        + listRejectPendingWorklogsIds.get(i);
            }
        }
        RestReturnModel restString = new RestReturnModel();
        restString.setString(rejectPendingWorklogsIds);
        return Response.ok(restString).build();
    }

    @GET
    @Path("viewHistory/{issueKey}")
    @Produces({"application/json"})
    public Response updateWorkLogStatus(@PathParam("issueKey") String issueKey) {
        return Response.ok(
                workLogHistoryService.getWorkLogHistoryByIssue(issueKey))
                .build();
    }
}