/**
 * GanttView for worklog
 */
WorklogPluginJs.GanttView = Backbone.View.extend({
    initialize: function (options) {
        var dialog = this;
        this.totalHours = 0;
        this.currentDate = new Date();
        if (this.options.type == "projectWorkLog") {
            this.typeSearch = "month";
            AJS.$(".searchGanttProjectDate").attr("style", "display: none");
            AJS.toInit(function () {


                AJS.params.dateFormat = DATE_FORMAT_JAVASCRIPT;
                Calendar.setup({
                    firstDay: 0,
                    inputField: "startDateGanttProject",
                    button: "start-Date-Gantt-Project",
                    align: "Br",
                    singleClick: true,
                    useISO8601WeekNumbers: false,
                    showsTime: "false",
                    ifFormat: AJS.params.dateFormat,
                });

                Calendar.setup({
                    firstDay: 0,
                    inputField: "endDateGanttProject",
                    button: "end-Date-Gantt-Project",
                    align: "Br",
                    singleClick: true,
                    useISO8601WeekNumbers: false,
                    showsTime: "false",
                    ifFormat: AJS.params.dateFormat,
                });
            });
        } else if (this.options.type == "user") {
            AJS.toInit(function () {
                AJS.$(document).on('click', '.fn-gantt-edit-worklog', function () {
                    var worklog = dialog.getWorklogOfElement(this);
                    dialog.editWorklogDialog.setData(worklog);
                    dialog.editWorklogDialog.render();
                    dialog.editWorklogDialog.show();

                });

            });
        }

//		this.getParamByForm();
//		this.refreshData();
        this.addActionHandler();
    },
    getParamByForm: function () {
        var queryParams = {};
        location.search.substr(1).split("&").forEach(function (item) {
            queryParams[item.split("=")[0]] = item.split("=")[1]
        });
        var monthParam = AJS.$("#ganttMonthWorkLog").val();
        var yearParam = AJS.$("#ganttYearWorkLog").val();
        var month = monthParam ? monthParam : 1 + this.currentDate.getMonth();
        var year = yearParam ? yearParam : 1900 + this.currentDate.getYear();
        AJS.$("#ganttMonthWorkLog").val(month);
        AJS.$("#ganttYearWorkLog").val(year);
        monthWorkLog = month;
        yearWorkLog = year;
        ganttVersionsWorkLog = AJS.$("#ganttVersionsWorkLog").val();
        ganttComponentsWorkLog = AJS.$("#ganttComponentsWorkLog").val();
        ganttStatusesWorkLog = AJS.$("#ganttStatusesWorkLog").val();
        ganttTypesWorkLog = AJS.$("#ganttTypesWorkLog").val();
        ganttIssueTypes = AJS.$("#ganttIssueTypes").val();
        ganttIssueTypes = encodeURIComponent(ganttIssueTypes);
        ganttIssueTypes = ganttIssueTypes.replace(/%2F/gi, "%252F");
        ganttCheckBoxTaskStatus = "false";
        if (AJS.$("#ganttCheckBoxTaskStatus").is(":checked")) {
            ganttCheckBoxTaskStatus = "true";
        }
        if (this.options.type == "user") {
            this.url = contextPath + "/rest/fis-worklog/1.0/gantt/user/"
                + AJS.Meta.get("remote-user") + "/" + PROJECT_ID + "/"
                + month + "/" + year + "/" + ganttTypesWorkLog + "/"
                + ganttComponentsWorkLog + "/" + ganttVersionsWorkLog + "/"
                + ganttStatusesWorkLog + "/" + ganttIssueTypes + "/" + ganttCheckBoxTaskStatus;
            this.urlIssueNoneWorkLog = contextPath + "/rest/fis-worklog/1.0/gantt/user/noneWorkLog/"
                + AJS.Meta.get("remote-user") + "/" + PROJECT_ID + "/"
                + month + "/" + year + "/" + ganttTypesWorkLog + "/"
                + ganttComponentsWorkLog + "/" + ganttVersionsWorkLog + "/"
                + ganttStatusesWorkLog + "/" + ganttIssueTypes + "/" + ganttCheckBoxTaskStatus;
        } else if (this.options.type == "projectWorkLog") {
            if (this.typeSearch == "month") {
                this.url = contextPath + "/rest/fis-worklog/1.0/gantt/projectWorkLog/"
                    + PROJECT_ID + "/" + "month/" + month + "/" + year+"/" 
					+ ganttStatusesWorkLog + "/" 
					+ ganttTypesWorkLog + "/"
					+ ganttIssueTypes+ "/"
					+ ganttComponentsWorkLog + "/" + ganttVersionsWorkLog ;
                MONTH_PROJECT_WORKLOG = month;
                YEAR_PROJECT_WORKLOG = year;
                START_DATE_GANTT_PROJECT_WORKLOG = '';
                END_DATE_GANTT_PROJECT_WORKLOG = '';
            } else {
                var startDateTimeTemp = 0;
                var endDateTimeTemp = 0;
                var startDate = AJS.$("#startDateGanttProject").val();
                var endDate = AJS.$("#endDateGanttProject").val();
                if (startDate == "") {
                    if (endDate == "") {
                        endDateTimeTemp = new Date().getTime();
                        startDateTimeTemp = endDateTimeTemp - 86400000 * 30;
                    } else {
                        endDateTimeTemp = moment(endDate, DATE_FORMAT_JIRA).toDate().getTime();
                        startDateTimeTemp = endDateTimeTemp - 86400000 * 30;
                    }
                } else {
                    if (endDate == "") {
                        startDateTimeTemp = moment(startDate, DATE_FORMAT_JIRA).toDate().getTime();
                        endDateTimeTemp = startDateTimeTemp + 86400000 * 30;
                    } else {
                        endDateTimeTemp = moment(endDate, DATE_FORMAT_JIRA).toDate().getTime();
                        startDateTimeTemp = moment(startDate, DATE_FORMAT_JIRA).toDate().getTime();
                    }
                }

                this.url = contextPath + "/rest/fis-worklog/1.0/gantt/projectWorkLog/"
                    + PROJECT_ID + "/" + "duration/" + startDateTimeTemp + "/" + endDateTimeTemp+ "/"
					+ ganttStatusesWorkLog + "/" 
					+ ganttTypesWorkLog + "/"
					+ ganttIssueTypes+ "/"
					+ ganttComponentsWorkLog + "/" + ganttVersionsWorkLog ;
                START_DATE_GANTT_PROJECT_WORKLOG = moment(new Date(startDateTimeTemp)).format(DATE_FORMAT_JIRA);
                END_DATE_GANTT_PROJECT_WORKLOG = moment(new Date(endDateTimeTemp)).format(DATE_FORMAT_JIRA);
            }
        }
    },

    getParamByUserForm: function () {
        var project = AJS.$("#ganttProjectWorkLog").val();
       
        PROJECT_ID = project.split(" ")[1];
        var queryParams = {};
        location.search.substr(1).split("&").forEach(function (item) {
            queryParams[item.split("=")[0]] = item.split("=")[1]
        });
        var monthParam = AJS.$("#ganttMonthWorkLog").val();
        var yearParam = AJS.$("#ganttYearWorkLog").val();
        var month = monthParam ? monthParam : 1 + this.currentDate.getMonth();
        var year = yearParam ? yearParam : 1900 + this.currentDate.getYear();
        AJS.$("#ganttMonthWorkLog").val(month);
        AJS.$("#ganttYearWorkLog").val(year);
        monthWorkLog = month;
        yearWorkLog = year;
        ganttVersionsWorkLog = AJS.$("#ganttVersionsWorkLog").val();
        ganttComponentsWorkLog = AJS.$("#ganttComponentsWorkLog").val();
        ganttStatusesWorkLog = AJS.$("#ganttStatusesWorkLog").val();
        ganttTypesWorkLog = AJS.$("#ganttTypesWorkLog").val();
        ganttIssueTypes = AJS.$("#ganttIssueTypes").val();
        ganttIssueTypes = encodeURIComponent(ganttIssueTypes);
        ganttIssueTypes = ganttIssueTypes.replace(/%2F/gi, "%252F");
        ganttCheckBoxTaskStatus = "false";
        if (AJS.$("#ganttCheckBoxTaskStatus").is(":checked")) {
            ganttCheckBoxTaskStatus = "true";
        }
       
            if (this.typeSearch == "month") {
            	 if (project == "All") {
            		 this.url = contextPath + "/rest/fis-worklog/1.0/gantt/userWorkLog/"
                      + "month/" + AJS.Meta.get("remote-user") +"/"+ month + "/" + year + "/" 
                      + ganttTypesWorkLog + "/"
                      + ganttComponentsWorkLog + "/" + ganttVersionsWorkLog + "/"
                      + ganttStatusesWorkLog + "/" + ganttIssueTypes + "/" + ganttCheckBoxTaskStatus;
                 }else{
                	 this.url = contextPath + "/rest/fis-worklog/1.0/gantt/userWorkLog/"
                     + PROJECT_ID + "/" + "month/" + AJS.Meta.get("remote-user")+"/" 
                     + month + "/" + year + "/" + ganttTypesWorkLog + "/"
                     + ganttComponentsWorkLog + "/" + ganttVersionsWorkLog + "/"
                     + ganttStatusesWorkLog + "/" + ganttIssueTypes + "/" + ganttCheckBoxTaskStatus;
                 }
                
                MONTH_PROJECT_WORKLOG = month;
                YEAR_PROJECT_WORKLOG = year;
                START_DATE_GANTT_PROJECT_WORKLOG = '';
                END_DATE_GANTT_PROJECT_WORKLOG = '';
            } else {
                var startDateTimeTemp = 0;
                var endDateTimeTemp = 0;
                var startDate = AJS.$("#startDateGanttProject").val();
                var endDate = AJS.$("#endDateGanttProject").val();
                if (startDate == "") {
                    if (endDate == "") {
                        endDateTimeTemp = new Date().getTime();
                        startDateTimeTemp = endDateTimeTemp - 86400000 * 30;
                    } else {
                        endDateTimeTemp = moment(endDate, DATE_FORMAT_JIRA).toDate().getTime();
                        startDateTimeTemp = endDateTimeTemp - 86400000 * 30;
                    }
                } else {
                    if (endDate == "") {
                        startDateTimeTemp = moment(startDate, DATE_FORMAT_JIRA).toDate().getTime();
                        endDateTimeTemp = startDateTimeTemp + 86400000 * 30;
                    } else {
                        endDateTimeTemp = moment(endDate, DATE_FORMAT_JIRA).toDate().getTime();
                        startDateTimeTemp = moment(startDate, DATE_FORMAT_JIRA).toDate().getTime();
                    }
                }
                if(project == "All"){
                	 this.url = contextPath + "/rest/fis-worklog/1.0/gantt/userWorkLog/"
                      + "duration/"+ AJS.Meta.get("remote-user")+"/"  + startDateTimeTemp + "/" + endDateTimeTemp + "/" + ganttTypesWorkLog + "/"
                      + ganttComponentsWorkLog + "/" + ganttVersionsWorkLog + "/"
                      + ganttStatusesWorkLog + "/" + ganttIssueTypes + "/" + ganttCheckBoxTaskStatus;
                }else{
                	 this.url = contextPath + "/rest/fis-worklog/1.0/gantt/userWorkLog/"
                     + PROJECT_ID + "/" + "duration/" + AJS.Meta.get("remote-user")+"/" + startDateTimeTemp + "/" + endDateTimeTemp + "/" 
                     + ganttTypesWorkLog + "/"
                     + ganttComponentsWorkLog + "/" + ganttVersionsWorkLog + "/"
                     + ganttStatusesWorkLog + "/" + ganttIssueTypes + "/" + ganttCheckBoxTaskStatus;
               
                START_DATE_GANTT_PROJECT_WORKLOG = moment(new Date(startDateTimeTemp)).format(DATE_FORMAT_JIRA);
                END_DATE_GANTT_PROJECT_WORKLOG = moment(new Date(endDateTimeTemp)).format(DATE_FORMAT_JIRA);
            }
         }
    },

    refreshData: function () {
        var ganttView = this;
        AJS.$('.table-spinner').spin();
        var dataGantt;
        // Get data from ajax
        AJS.$.ajax({
            url: ganttView.url,
            type: "GET",
            async: false,
            dataType: "json",
            contentType: "application/json; charset=utf-8",
            success: function (data) {
                dataGantt = data;
            }
        });
        this.totalHours = 0;
        for (var i = 0; i < dataGantt.length; i++) {
            var workLog = dataGantt[i];
            this.totalHours = this.totalHours + parseFloat(workLog.workPerDay);
        }
        this.setTotalHour();
        AJS.$('.table-spinner').spinStop();
        this.data = this.convertSource(dataGantt);
        if (this.options.type == "user") {
            this.dataNoneWorkLog = [];
            AJS.$.ajax({
                url: ganttView.urlIssueNoneWorkLog,
                type: "GET",
                async: false,
                dataType: "json",
                contentType: "application/json; charset=utf-8",
                success: function (data) {
                    ganttView.dataNoneWorkLog = data;
                }
            });
            this.addDataNoneWorkLog();
        }
        this.render();
    },
    convertSource: function (list) {
        var result;

        if (this.options.type == "user") {
            var clist = _.map(list, function (workLog) {
                return {
                    originalWorklog: workLog,
                    from: "/Date(" + workLog.startDate + ")/",
                    to: "/Date(" + workLog.endDate + ")/",
                    label: workLog.workPerDay,
                    customClass: "ganttBlue",
                    desc: workLog.json
                };
            });
            var issues = _.values(_.groupBy(clist, function (w) {
                return w.originalWorklog.issueKey;
            }));

            result = _.map(issues, function (issue) {
                var workLog = issue[0].originalWorklog;
                return {
                    name: workLog.issueName,
                    desc: workLog.issueKey,
                    issueKey: workLog.issueKey,
                    issueName: workLog.issueName,
                    values: issue
                }
            });
        } else if (this.options.type == "projectWorkLog") {
            var clist = _.map(list, function (workLog) {
                return {
                    originalWorklog: workLog,
                    from: "/Date(" + workLog.startDate + ")/",
                    to: "/Date(" + workLog.endDate + ")/",
                    label: workLog.workPerDay,
                    customClass: "ganttBlue",
                    desc: workLog.json
                };
            });

            var users = _.values(_.groupBy(clist, function (w) {
                return w.originalWorklog.userKey;
            }));

            result = _.map(users, function (user) {
                var workLog = user[0].originalWorklog;
                var totalHour = _.reduce(user, function (memo, w) {
                    return memo + parseFloat(w.originalWorklog.workPerDay);
                }, 0);
                return {
                    name: workLog.userKey,
                    desc: workLog.userKey,
                    values: user,
                    totalHours: totalHour
                }
            });
        }
        return result;

    },
    addDataNoneWorkLog: function () {
        var ganttView = this;
        _.each(this.dataNoneWorkLog, function (workLog) {
            ganttView.data.push({
                name: workLog.issueName,
                desc: workLog.issueKey,
                issueKey: workLog.issueKey,
                issueName: workLog.issueName,
                values: []
            });
        });

    },
    render: function () {
        var ganttView = this;
        if (ganttView.data.length > 0) {
            AJS.$("." + GANTT_ID).gantt({
                source: ganttView.data,
                navigate: "scroll",
                maxScale: "hours",
                itemsPerPage: 10,

                onItemClick: function (data) {
                },
                onAddClick: function (dt, issueKey, issueName) {
                    var worklog = {
                        startDate: dt,
                        issueKey: issueKey,
                        issueName: issueName
                    };
                    var checkWorkLog = false;
                    AJS.$.ajax({
                        url: AJS.contextPath()
                        + "/rest/fis-worklog/1.0/worklog/checkIssueCloseAndCancel/" + issueKey,
                        type: 'get',
                        dataType: 'json',
                        async: false,
                        success: function (data) {
                            checkWorkLog = data;
                        }
                    });
                    if (!checkWorkLog) {
                        ganttView.newWorklogDialogGantt.render(worklog);
                        ganttView.newWorklogDialogGantt.show();
                        ganttView.newWorklogDialogGantt.clearInfo();
                    } else {
                        AJS.dialog2("#alerMyWorkLog").show();
                        AJS.$("#btnCancelalerMyWorkLog").click(function (e) {
                            e.preventDefault();
                            AJS.dialog2("#alerMyWorkLog").hide();
                        });
                    }

                },
                onRender: function () {
                }
            });
        } else {
            AJS.$("." + GANTT_ID).html("No data!");
        }
    },
    clear: function () {
        AJS.$("." + GANTT_ID).html("");
    },
    addActionHandler: function () {
        var typeSearch = AJS.$("#typeSearchGanttSelect").val();
        if (typeSearch == "SearchMonth") {
            var endDateTimeTemp = new Date().getTime();
            startDateTimeTemp = endDateTimeTemp - 86400000 * 30;
            AJS.$("#startDateGanttProject").val(convertDatetoStringAdminGantt(new Date(startDateTimeTemp)));
            AJS.$("#endDateGanttProject").val(convertDatetoStringAdminGantt(new Date(endDateTimeTemp)));
        }
        var ganttView = this;
        AJS.toInit(function () {
            require(['aui/form-validation'], function () {
                AJS.$('#formGanttWorkLog').on('aui-valid-submit', function (event) {
                    event.preventDefault();
                    ganttView.getParamByForm();
                    ganttView.refreshData();
                });
            });

            require(['aui/form-validation'], function () {
                AJS.$('#formGanttMyWorkLog').on('aui-valid-submit', function (event) {
                    event.preventDefault();
                    ganttView.getParamByForm();
                    ganttView.refreshData();
                });
            });

            require(['aui/form-validation'], function () {
                AJS.$('#formGanttWorkLog').on('aui-valid-submit', function (event) {
                    event.preventDefault();
                    ganttView.getParamByUserForm();
                    ganttView.refreshData();
                });
            });

            require(['aui/form-validation/validator-register'], function (validator) {
                validator.register(['search-project-gantt-startdate'], function (field) {
                    var startDate = AJS.$("#startDateGanttProject").val();
                    if (startDate == "" || moment(startDate, DATE_FORMAT_JIRA, true).isValid()) {
                        field.validate();
                    } else {
                        field.invalidate();
                    }
                });
                validator.register(['search-project-gantt-enddate'], function (field) {
                    var endDate = AJS.$("#endDateGanttProject").val();
                    if (endDate == "" || moment(endDate, DATE_FORMAT_JIRA, true).isValid()) {
                        field.validate();
                    } else {
                        field.invalidate();
                    }
                });
            });

            AJS.$("#typeSearchGanttSelect").change(function (e) {
                var typeSearch = AJS.$("#typeSearchGanttSelect").val();
                if (typeSearch == "SearchMonth") {
                    AJS.$(".searchGanttProjectDate").attr("style", "display: none");
                    AJS.$(".searchGanttProjectMonth").attr("style", "display: table-cell");
                    AJS.$('.searchDate').attr('data-aui-validation-state',
                        'unvalidated').attr('data-aui-notification-field', '')
                        .removeAttr('data-aui-notification-error').val("");
                    var endDateTimeTemp = new Date().getTime();
                    startDateTimeTemp = endDateTimeTemp - 86400000 * 30;
                    AJS.$("#startDateGanttProject").val(convertDatetoStringAdminGantt(new Date(startDateTimeTemp)));
                    AJS.$("#endDateGanttProject").val(convertDatetoStringAdminGantt(new Date(endDateTimeTemp)));
                    ganttView.typeSearch = "month";
                } else if (typeSearch == "SearchDate") {
                    AJS.$(".searchGanttProjectMonth").attr("style", "display: none");
                    AJS.$(".searchGanttProjectDate").attr("style", "display: table-cell");
                    ganttView.typeSearch = "duration";
                }
            });
        });
    },
    getWorklogOfElement: function (el) {
        var id = AJS.$(el).data('worklogId');
        var currentWorklog = _.find(LIST_WORKLOG_FOR_CREATE_GANNT, function (w) {
            return w.id == id
        });
        return currentWorklog;
    },
    setTotalHour: function () {
        AJS.$("#totalHourGanttProjectWorkLog").html("Total: " + numeral(this.totalHours).format("0.00") + " hours");
    }
});

function convertDatetoStringAdminGantt(d) {
    var month = new Array();
    month[0] = "Jan";
    month[1] = "Feb";
    month[2] = "Mar";
    month[3] = "Apr";
    month[4] = "May";
    month[5] = "Jun";
    month[6] = "Jul";
    month[7] = "Aug";
    month[8] = "Sep";
    month[9] = "Oct";
    month[10] = "Nov";
    month[11] = "Dec";

    var n = month[d.getMonth()];
    return d.getDate() + "/" + n + "/" + d.getFullYear().toString().substr(2, 2);
}


