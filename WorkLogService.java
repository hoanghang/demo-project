package com.fpt.jira.worklog.service;

import java.util.Date;
import java.util.List;
import java.util.Map;

import org.ofbiz.core.entity.GenericEntityException;

import com.atlassian.activeobjects.tx.Transactional;
import com.atlassian.crowd.embedded.api.User;
import com.atlassian.jira.bc.project.component.ProjectComponent;
import com.atlassian.jira.event.issue.IssueEvent;
import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.issue.IssueManager;
import com.atlassian.jira.issue.MutableIssue;
import com.atlassian.jira.project.Project;
import com.atlassian.jira.project.version.Version;
import com.atlassian.jira.user.ApplicationUser;
import com.fpt.jira.project.projectconfig.ProjectConfigInfo;
import com.fpt.jira.project.projectconfig.group.ProjectGroup;
import com.fpt.jira.project.projectconfig.team.ObjectSearch;
import com.fpt.jira.project.projectconfig.unit.ProjectUnit;
import com.fpt.jira.project.rest.bean.ProjectConfigInfoBean;
import com.fpt.jira.project.rest.bean.ProjectUnitBean;
import com.fpt.jira.worklog.WorkLog;
import com.fpt.jira.worklog.bean.WorkLogForReportDetail;
import com.fpt.jira.worklog.bean.WorkLogGantt;
import com.fpt.jira.worklog.bean.WorkLogRestModel;
import com.fpt.jira.worklog.bean.WorkLogView;
import com.fpt.jira.worklog.bean.WorklogGanttForUser;

@Transactional
public interface WorkLogService {
    WorkLog addWorkLog(String userKey, String userName, Long projectId,
                       String projectName, String issueKey, String issueName,
                       Date startDate, Date endDate, String workPerDay, String desc,
                       String typeOfWork, String status, String comment);

    WorkLog addWorkLog(User user, Issue issue, Date startDate, Date endDate,
                       String workPerDay, String desc, String typeOfWork, String status,
                       String comment);

    WorkLog editWorkLog(String id, Date startDate, Date endDate,
                        String workForDay, String desc, String typeOfWork);

    WorkLog updateWorkLogStatus(long id, String status, String reason);

//    my work log user - man hinh moi 18/7
    
    List<WorklogGanttForUser> getGanttForUserDuration(String userKey, Long projectId,
            Date startDate, Date endDate, String type, String componentId,
            String versionId, String status,String issueType, String issueStatus);
    
    List<WorklogGanttForUser> getGanttForUserDuration(String userKey, 
            Date startDate, Date endDate, String type, String componentId,
            String versionId, String status,String issueType, String issueStatus);
    
    List<WorklogGanttForUser> getGanttForUserMonth(String userKey, 
    		Integer month, Integer year, String type, String componentId,
            String versionId, String status,String issueType, String issueStatus);
    
    List<WorklogGanttForUser> getGanttForUserMonth(String userKey, Long projectId,
    		Integer month, Integer year, String type, String componentId,
            String versionId, String status,String issueType, String issueStatus);
    
    // Get list
    List<WorkLog> getWorkLogByIssue(String issueKey, String userKey);

    List<WorkLog> getWorkLogByIssue(String issueKey);

    List<WorkLog> getWorkLogByIssueKey(String issueKey);

    List<WorkLog> getWorkLogByProjectId(Long projectId);

    List<WorkLog> getWorkLogNotRejectedByProjectId(Long projectId);

    List<WorkLog> getWorkLogByProject(long projectId, int month, int year);

    List<WorkLog> getWorkLogByProjectId(Long projectId, Date startDate,
                                        Date endDate);

    List<WorkLog> getWorkLogByUsers(String[] userKeys, Date startDate,
                                    Date endDate);

    List<WorkLog> getWorkLogByUserKey(String userKey);

    List<WorkLog> getWorkLogByUserKey(long projectId, String userKey,
                                      int month, int year);

    List<WorkLog> getWorkLogByUserKey(long projectId, String userKey,
                                      int month, int year, String type,
                                      String component, String status, String version, String issueType, String issueStatus);

    List<WorkLog> getWorkLogForCurrentUser(int currentPageNumber,
                                           int recordInPage, String userKey, Long projectId, Date startDate,
                                           Date endDate, String status);

    List<WorkLog> getWorkLogForCurrentUser(int currentPageNumber,
                                           int recordInPage, String userKey, Long projectId,
                                           Date startDate, Date endDate, String type,
                                           String componentId, String versionId,
                                           String status, String issueType, String issueStatus);

    List<WorkLog> getAllWorkLogForAdmin(Long projectId, int currentPageNumber,
                                        int recordInPage);

    List<WorkLog> getAllWorkLogForAdmin(Long projectId, int currentPageNumber,
                                        int recordInPage, String userKey, Date startDate, Date endDate,
                                        String status, String typeOfWork, String component);

    List<WorkLog> getAllPendingWorkLogForAdmin(Long projectId,
                                               int currentPageNumber, int recordInPage);

    List<WorkLog> getWorkLogPendingForAdmin(Long projectId,
                                            int currentPageNumber, int recordInPage, String userName,
                                            Date startDate, Date endDate, String typeOfWork, String component);

    List<WorkLog> getWorkLogCheckWorkPerDay(Date startDate, Date endDate,
                                            String userKey);

    List<WorkLog> getWorkLogApprovedByProjectIdExcludeReject(Long projectId,
                                                             Date startDate, Date endDate);

    List<WorkLog> getAllWorkLogs();

    // boolean checkReleaseComponent(long componentId);

    // Count--------------------------------
    int getWorkLogForCurrentUserTotal(String userKey, Long projectId,
                                      Date startDate, Date endDate, String status);

    int getWorkLogForCurrentUserTotal(Long projectId, String userKey,
                                      Date startDate, Date endDate, String type, String componentId,
                                      String versionId, String status, String issueType, String issueStatus);

    float getTotalHourWorkLogForCurrentUser(String userKey, Long projectId,
                                            Date startDate, Date endDate, String status);

    float getTotalHourWorkLogForCurrentUser(Long projectId, String userKey, Date startDate,
                                            Date endDate, String type, String componentId,
                                            String versionId, String status, String issueType, String issueStatus);

    int getWorkLogPendingForAdminTotal(Long projectId, String userKey,
                                       Date startDate, Date endDate, String typeOfWork, String component);

    float totalHourWorkLogPendingForAdminTotal(Long projectId, String userKey,
                                               Date startDate, Date endDate, String typeOfWork, String component);

    int getAllWorkLogForAdminTotal(Long projectId, String userKey,
                                   Date startDate, Date endDate, String status, String typeOfWork,
                                   String component);

    float totalHourAllWorkLogForAdmin(Long projectId, String userKey,
                                      Date startDate, Date endDate, String status, String typeOfWork,
                                      String component);

    List<WorkLog> getWorkLogByIssueKeys(List<Issue> issues);

    float getTotalWorkLogByProjectId(Long projectId);

    float getCorrectionCost(Long projectId, List<Long> listComponentId);

    // Actual worklog--------------------------------

    double getActualWorkLogByIssue(Issue issue);

    List<WorkLog> getWorkLogByUsers(String[] userKeys, int month, int year);

    double getActualWorkLogByProject(long projectId);

    double getActualWorkLogByVersion(Version version);

    double getActualWorkLogByProject(long projectId, Date startDate,
                                     Date endDate);

    public abstract Map<Long, Double> getActualWorkLogByProject(
            List<Long> listProjectId, Date startDate, Date endDate);

    double getActualWorkLogByVersion(Version version, Date startDate,
                                     Date endDate);

    double getActualWorkLogByComponent(ProjectComponent component,
                                       Date startDate, Date endDate);

    double getActual(WorkLog w);

    double getActualForWorklogList(List<WorkLog> list);

    double getActualEffortByVersion(long versionId, long projectId);

    double getActualWorkLogByProjectExcludeReject(long projectId,
                                                  Date startDate, Date endDate);

    // Convert data---------------------------------------

    List<WorkLogGantt> convertWorkLogListToGantt(List<WorkLog> collectionWorkLog);

    List<WorkLogView> convertWorkLogList(List<WorkLog> collectionWorkLog);

    WorkLogRestModel convertModel(WorkLog worklog);

    List<WorkLog> getWorkLogByIssue(Issue issue);

    List<WorkLogGantt> convertWorkLogListToGanttMyWorklog(
            List<WorkLog> listWorkLog);

    float convertJiraDurationToHours(String string);

    List<WorkLogGantt> getIssueNoneWorkLogForGantt(
            List<WorkLogGantt> listWorkLogGantt, Long projectId);

    List<WorkLogGantt> getIssueNoneWorkLogForGantt(
            List<WorkLogGantt> listWorkLogGantt, Long projectId, String issueStatus);

    List<WorkLogGantt> getGanttForProject(Long projectId, Date startDate,
                                          Date endDate);

    List<WorkLogGantt> getGanttForProject(Long projectId, Integer month,
                                          Integer year);
    
    ////- project work log gantt--------------------------------------------
    
    List<WorkLogGantt> getGanttForProject(Long projectId, Date startDate,
            Date endDate, String status, String typeOfWork, String issueType, 
            List<String> components, List<String> versions);
    
    List<WorkLogGantt> getGanttForProject(Long projectId, Integer month,
            Integer year, String status, String typeOfWork, String issueType, 
            List<String> components, List<String> versions);

//    -------------------------------------------------------------------------
    
    List<WorkLogGantt> getGanttForUser(String userKey, Long projectId,
                                       Date startDate, Date endDate);

    List<WorkLogGantt> getGanttForUser(String userKey, Long projectId,
                                       Integer month, Integer year);

    List<WorkLogGantt> getGanttForUser(String userKey, Long projectId,
                                       Integer month, Integer year, String type,
                                       String componentId, String versionId, String status,
                                       String issueType, String issueStatus);

    List<WorkLogGantt> getGanttForQA(String[] userKeys, int month, int year,
                                     List<ProjectConfigInfo> projects);

    List<WorkLogGantt> getGanttForQA(String[] userKeys, Date startDate,
                                     Date endDate, List<ProjectConfigInfo> projects);

    public abstract List<WorkLog> getWorkLogByUserKey(Long projectId,
                                                      String userKey, Date startDate, Date endDate);

    // For component
    String getMinMaxWorkLogDateByComponent(long componentId, long projectId);

    // DELETE
    boolean deleteWorkLog(String idWorkLog);

    void deleteInvalidWorlog(int idWorkLog);

    boolean deleteWorkLogByIssue(String issueKey);

    boolean checkDurationProject(long projectId, Date startDate, Date endDate);

    // boolean checkDurationComponent(long componentId, Date startDate, Date
    // endDate);

    boolean isProjectManager(Project currentProject);

    boolean isProjectManagerOrTeamLeader(Project currentProject);

    boolean isAssigee(String userKey, Issue issue);

    boolean isWatcher(String userKey, IssueManager issueManager, Issue issue);

    boolean isManager(Project currentProject);

    boolean checkProjectPending(long projectId);

    public void sendEmail(String to, String subject, String body);

    void updateIssueWorklogByChange(IssueEvent issueEvent);

    Map<Long, List<Date>> createMapMinMaxDateWorkLogToComponentByProjectId(
            long projectId);

    Map<Long, List<Date>> createMapMinMaxDateWorkLogToVersionByProjectId(
            long projectId);

    List<WorkLog> getWorkLogForWorkLogDetails(List<Long> listProjectId,
                                              List<String> listIssueKey, List<String> listStatus, Date startDate,
                                              Date endDate);

    double getOriginalEstimateOfIssue(Issue issue);

    double getOriginalEstimateByStringValue(String originalText);

    Date getStartDateOfIssue(Issue issue);

    List<WorkLog> searchWorkLogNoneIssue();

    boolean deleteWorkLogNoneIssue(List<WorkLog> listWorkLog);

    List<WorkLog> getWorkLogApprovedByIssueKeys(List<Issue> issues);

    boolean isIssueWorkLog(Issue issue);

    boolean isProjectManagerOrTeamLeader(Project currentProject, String userKey);

    double getActualWorkLogByIssueAndWorkLog(Issue issue, WorkLog worklog);

    List<WorkLog> getResultForResourceTotalReport(
            ProjectConfigInfoBean project, Date fromDate, Date toDate,
            List<String> users);

    boolean checkIssueClose(String issueKey);

    boolean checkRequiredDefectResolved(String issueKey,
                                        String[] customFieldNames);

    void deleteWorkLogByProjectId(long projectId);

    boolean checkPM(Project currentProject);

    boolean checkTL(Project currentProject);

    boolean checkCurrentUserIsLeadOfUser(String projectKey, String userKey);

    public Map<String, Float> totalHourAndRecordAllWorkLogForAdmin(
            Long projectId, String userKey, Date startDate, Date endDate,
            String status, String typeOfWork, List<String> component,
            List<String> versions, boolean checkPm, boolean checkTeamLead,
            boolean checkQA, List<String> userKeys);

    public Map<String, Float> totalHourWorkLogPendingForAdminTotal(
            Long projectId, String userKey, Date startDate, Date endDate,
            String typeOfWork, List<String> component, List<String> verison,
            boolean checkPm, boolean checkTeamLead, List<String> userKeys);
    
    // ----------pending project worklog
    public Map<String, Float> totalHourWorkLogPendingForAdminTotal(
            Long projectId, String userKey, Date startDate, Date endDate,
            String typeOfWork, String issueType, List<String> component, List<String> verison,
            boolean checkPm, boolean checkTeamLead, List<String> userKeys);

    List<WorkLog> getWorkLogPendingForAdmin(Long projectId,
            int currentPageNumber, int recordInPage, String userName,
            Date startDate, Date endDate, String typeOfWork, String issueType, 
            List<String> components, List<String> versions,
            List<String> userKeys);
    
    List<WorkLog> getWorkLogPendingForAdmin(Long projectId,
            int currentPageNumber, int recordInPage, String userName,
            Date startDate, Date endDate, String typeOfWork, String issueType,
            List<String> components, List<String> versions);
    
    //-----------------------------------------
    public void addActualStartAndEndDateAllIssue()
            throws GenericEntityException;

    public List<WorkLog> getWorkLogByProjectIdOrderByDate(Long projectId);

    public double resourceOfVersion(List<Long> versionIds, String projectKey);

    boolean checkQA(Project currentProject);

    List<WorkLog> getAllWorkLogForAdmin(Long projectId, int currentPageNumber,
                                        int recordInPage, String userKey, Date startDate, Date endDate,
                                        String status, String typeOfWork, List<String> components,
                                        List<String> versions);

    List<WorkLog> getWorkLogPendingForAdmin(Long projectId,
                                            int currentPageNumber, int recordInPage, String userName,
                                            Date startDate, Date endDate, String typeOfWork,
                                            List<String> components, List<String> versions);

    Map<String, Double> getActualWorkLogByIssueAndWorkLogForReport(Issue issue,
                                                                   WorkLog worklog);

    // For report
    List<WorkLog> getWorkLogByUserInDuraiton(ApplicationUser user,
                                             Date startDate, Date endDate);

    List<WorkLog> getWorkLogByIssueKeyOrderByDate(String issueKey);

    List<WorkLog> getWorkLogByTimeAndStatus(Date startDate, Date endDate, String status);

    List<ProjectUnit> getAllUnitByUser(String reportName);

    List<ProjectGroup> getAllGroupByUser(String reportName);

    List<ProjectConfigInfo> findProjectByGroupAndUnit(String groupCode,
                                                      String unitCode, String reportName);

    List<ProjectUnit> findUnitByGroup(String groupCode, String reportName);

    List<ProjectConfigInfo> getAllProjectByUser(String reportName);

    List<ObjectSearch> getAllMemberByProject(List<ProjectConfigInfo> projects,
                                             String contextPath, String query);

    List<ProjectUnitBean> findUnitBeanByGroup(String groupCode,
                                              String reportName);

    boolean checkPermission(String reportName);

    List<String> getAllMemberByBgOu(List<ProjectConfigInfo> projects);

    List<WorkLog> getWorkLogByUsersAndProject(String[] userKeys, int month,
                                              int year, List<ProjectConfigInfo> projects);

    List<WorkLog> getWorkLogByUsersAndProject(String[] userKeys,
                                              Date startDate, Date endDate, List<ProjectConfigInfo> projects);

    List<WorkLog> getWorkLogApprovalAndPendingByProjectId(Long projectId,
                                                          Date startDate, Date endDate);

    List<WorkLog> getWorkLogNotRejectedByUsersAndProject(String[] userKeys,
                                                         int month, int year, List<ProjectConfigInfo> projects);

    List<WorkLog> getWorkLogNotRejectedByUsersAndProject(String[] userKeys,
                                                         Date startDate, Date endDate, List<ProjectConfigInfo> projects);

    List<WorkLog> getWorkLogNotRejectByProject(long projectId, int month,
                                               int year);

    Map<String, WorkLogForReportDetail> getMapsWorkLogForIssue(
            List<ProjectConfigInfo> projects, List<String> listStatus,
            Date startDate, Date endDate);

    boolean checkQAandTopUser(Project currentProject);

    List<WorkLog> getAllPendingWLNotAfterDateByProjectId(Long projectId,
                                                         Date dateToQuery);

    List<WorkLog> getAllPendingWLNotAfterDate(Date dateToQuery);

    List<WorkLogGantt> getGanttForQAByBGAndOu(String[] userKeys, int month,
                                              int year);

    List<WorkLog> getWorkLogNotRejectedByUsers(String[] userKeys, int month,
                                               int year);

    WorkLog getWorkLogById(int id);

    List<WorkLog> getWorkLogNotRejectedByUsersInDuration(String[] userKeys,
                                                         Date startDate, Date endDate);

    List<WorkLogGantt> getGanttForQAByBGAndOuInDuration(String[] userKeys,
                                                        Date startDate, Date endDate);

    List<WorkLog> getAllPendingWorklogInDuration(Date startDate, Date endDate);

    List<WorkLog> getAllWorkLogForAdmin(Long projectId, int currentPageNumber,
                                        int recordInPage, String userKey, Date startDate, Date endDate,
                                        String status, String typeOfWork, List<String> components,
                                        List<String> versions, List<String> userKeys);

    List<WorkLog> getWorkLogPendingForAdmin(Long projectId,
                                            int currentPageNumber, int recordInPage, String userName,
                                            Date startDate, Date endDate, String typeOfWork,
                                            List<String> components, List<String> versions,
                                            List<String> userKeys);
    // project workLog -------------------------------------------
    List<WorkLog> getAllWorkLogForAdmin(Long projectId, int currentPageNumber,
            int recordInPage, String userKey, Date startDate, Date endDate,
            String status, String typeOfWork, String issueType, List<String> components,
            List<String> versions);

    List<WorkLog> getAllWorkLogForAdmin(Long projectId, int currentPageNumber,
            int recordInPage, String userKey, Date startDate, Date endDate,
            String status, String typeOfWork, String issueType, List<String> components,
            List<String> versions, List<String> userKeys);
	
	 public Map<String, Float> totalHourAndRecordAllWorkLogForAdmin(
	            Long projectId, String userKey, Date startDate, Date endDate,
	            String status, String typeOfWork,String issueType, List<String> component,
	            List<String> versions, boolean checkPm, boolean checkTeamLead,
	            boolean checkQA, List<String> userKeys);
	 // ------------------------------------------------------
    List<WorkLog> getWorkLogByMutableIssueKeys(List<MutableIssue> issues);

    double getOriginalEstimateOfMutableIssue(MutableIssue issue);

    List<WorkLog> getWorkLogNotRejectedByUsersIgnoreCase(String[] userKeys,
                                                         int month, int year);

    List<WorkLog> getWorkLogNotRejectedByUserNameIgnoreCaseInDuration(
            String[] userKeys, Date startDate, Date endDate);

    List<WorkLog> getAllWorkLogByUserNameIgnoreCaseInDuration(List<User> users,
                                                              Date startDate, Date endDate);

    boolean isProjectManager(Project project, ApplicationUser user);

    boolean isQaOrTopUser(Project project, ApplicationUser user);

    boolean isQualityAssurance(Project project, ApplicationUser user);

    boolean checkPM(Project currentProject, ApplicationUser currentUser);

    boolean checkQA(Project currentProject, ApplicationUser currentUser);

    boolean checkTeamLead(Project currentProject, ApplicationUser currentUser);

}
