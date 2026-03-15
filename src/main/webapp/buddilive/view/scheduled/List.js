Ext.define("BuddiLive.view.scheduled.List", {
	extend: "Ext.panel.Panel",
	alias: "widget.scheduledlist",
	requires: [
		"BuddiLive.store.scheduled.ListStore",
		"BuddiLive.view.scheduled.Editor"
	],
	
	title: BuddiLive.translate("SCHEDULED_TRANSACTIONS"),
	layout: "fit",
	closable: true,
	initComponent: function(){
		var d = this.initialConfig.data

		this.items = [
			{
				xtype: "grid",
				itemId: "scheduledTransactions",
				store: Ext.create("BuddiLive.store.scheduled.ListStore"),
				columns: [
					{
						text: BuddiLive.translate("SCHEDULED_TRANSACTION_NAME"),
						dataIndex: "name",
						flex: 1
					},
					{
						text: BuddiLive.translate("SCHEDULED_TRANSACTION_REPEAT"),
						dataIndex: "repeat",
						flex: 2,
						renderer: function(value, metadata, record){
							var frequencyLookup = {
								SCHEDULE_FREQUENCY_MONTHLY_BY_DATE: BuddiLive.translate("SCHEDULE_FREQUENCY_MONTHLY_BY_DATE"),
								SCHEDULE_FREQUENCY_MONTHLY_BY_DAY_OF_WEEK: BuddiLive.translate("SCHEDULE_FREQUENCY_MONTHLY_BY_DAY_OF_WEEK"),
								SCHEDULE_FREQUENCY_WEEKLY: BuddiLive.translate("SCHEDULE_FREQUENCY_WEEKLY"),
								SCHEDULE_FREQUENCY_BIWEEKLY: BuddiLive.translate("SCHEDULE_FREQUENCY_BIWEEKLY"),
								SCHEDULE_FREQUENCY_EVERY_DAY: BuddiLive.translate("SCHEDULE_FREQUENCY_EVERY_DAY"),
								SCHEDULE_FREQUENCY_EVERY_X_DAYS: BuddiLive.translate("SCHEDULE_FREQUENCY_EVERY_X_DAYS"),
								SCHEDULE_FREQUENCY_EVERY_WEEKDAY: BuddiLive.translate("SCHEDULE_FREQUENCY_EVERY_WEEKDAY"),
								SCHEDULE_FREQUENCY_MULTIPLE_WEEKS_EVERY_MONTH: BuddiLive.translate("SCHEDULE_FREQUENCY_MULTIPLE_WEEKS_EVERY_MONTH"),
								SCHEDULE_FREQUENCY_MULTIPLE_MONTHS_EVERY_YEAR: BuddiLive.translate("SCHEDULE_FREQUENCY_MULTIPLE_MONTHS_EVERY_YEAR")
							};

							var result = frequencyLookup[value] || value;
							if (value == "SCHEDULE_FREQUENCY_MONTHLY_BY_DATE"){
								var monthlyByDateLookup = {
									1: BuddiLive.translate("SCHEDULE_DATE_FIRST"),
									2: BuddiLive.translate("SCHEDULE_DATE_SECOND"),
									3: BuddiLive.translate("SCHEDULE_DATE_THIRD"),
									4: BuddiLive.translate("SCHEDULE_DATE_FOURTH"),
									5: BuddiLive.translate("SCHEDULE_DATE_FIFTH"),
									6: BuddiLive.translate("SCHEDULE_DATE_SIXTH"),
									7: BuddiLive.translate("SCHEDULE_DATE_SEVENTH"),
									8: BuddiLive.translate("SCHEDULE_DATE_EIGHTH"),
									9: BuddiLive.translate("SCHEDULE_DATE_NINETH"),
									10: BuddiLive.translate("SCHEDULE_DATE_TENTH"),
									11: BuddiLive.translate("SCHEDULE_DATE_ELEVENTH"),
									12: BuddiLive.translate("SCHEDULE_DATE_TWELFTH"),
									13: BuddiLive.translate("SCHEDULE_DATE_THIRTEENTH"),
									14: BuddiLive.translate("SCHEDULE_DATE_FOURTEENTH"),
									15: BuddiLive.translate("SCHEDULE_DATE_FIFTEENTH"),
									16: BuddiLive.translate("SCHEDULE_DATE_SIXTEENTH"),
									17: BuddiLive.translate("SCHEDULE_DATE_SEVENTEENTH"),
									18: BuddiLive.translate("SCHEDULE_DATE_EIGHTEENTH"),
									19: BuddiLive.translate("SCHEDULE_DATE_NINETEENTH"),
									20: BuddiLive.translate("SCHEDULE_DATE_TWENTIETH"),
									21: BuddiLive.translate("SCHEDULE_DATE_TWENTYFIRST"),
									22: BuddiLive.translate("SCHEDULE_DATE_TWENTYSECOND"),
									23: BuddiLive.translate("SCHEDULE_DATE_TWENTYTHIRD"),
									24: BuddiLive.translate("SCHEDULE_DATE_TWENTYFOURTH"),
									25: BuddiLive.translate("SCHEDULE_DATE_TWENTYFIFTH"),
									26: BuddiLive.translate("SCHEDULE_DATE_TWENTYSIXTH"),
									27: BuddiLive.translate("SCHEDULE_DATE_TWENTYSEVENTH"),
									28: BuddiLive.translate("SCHEDULE_DATE_TWENTYEIGHTH"),
									29: BuddiLive.translate("SCHEDULE_DATE_TWENTYNINETH"),
									30: BuddiLive.translate("SCHEDULE_DATE_THIRTIETH"),
									31: BuddiLive.translate("SCHEDULE_DATE_THIRTYFIRST"),
									32: BuddiLive.translate("SCHEDULE_DATE_LAST_DAY")
								};
								result += " " + monthlyByDateLookup[record.get("scheduleDay")];
							}
							else if (value == "SCHEDULE_FREQUENCY_MONTHLY_BY_DAY_OF_WEEK"){
								var monthlyByDayOfWeekLookup = {
									0: BuddiLive.translate("SCHEDULE_DAY_FIRST_SUNDAY"),
									1: BuddiLive.translate("SCHEDULE_DAY_FIRST_MONDAY"),
									2: BuddiLive.translate("SCHEDULE_DAY_FIRST_TUESDAY"),
									3: BuddiLive.translate("SCHEDULE_DAY_FIRST_WEDNESDAY"),
									4: BuddiLive.translate("SCHEDULE_DAY_FIRST_THURSDAY"),
									5: BuddiLive.translate("SCHEDULE_DAY_FIRST_FRIDAY"),
									6: BuddiLive.translate("SCHEDULE_DAY_FIRST_SATURDAY")
								};
								result += " " + monthlyByDayOfWeekLookup[record.get("scheduleDay")];
							}
							else if (value == "SCHEDULE_FREQUENCY_WEEKLY"){
								var weekLookup = {
									0: BuddiLive.translate("SCHEDULE_DAY_SUNDAY"),
									1: BuddiLive.translate("SCHEDULE_DAY_MONDAY"),
									2: BuddiLive.translate("SCHEDULE_DAY_TUESDAY"),
									3: BuddiLive.translate("SCHEDULE_DAY_WEDNESDAY"),
									4: BuddiLive.translate("SCHEDULE_DAY_THURSDAY"),
									5: BuddiLive.translate("SCHEDULE_DAY_FRIDAY"),
									6: BuddiLive.translate("SCHEDULE_DAY_SATURDAY")
								};
								result += " " + weekLookup[record.get("scheduleDay")];
							}
							else if (value == "SCHEDULE_FREQUENCY_BIWEEKLY"){
								var biWeeklyLookup = {
									0: BuddiLive.translate("SCHEDULE_DAY_EVERY_OTHER_SUNDAY"),
									1: BuddiLive.translate("SCHEDULE_DAY_EVERY_OTHER_MONDAY"),
									2: BuddiLive.translate("SCHEDULE_DAY_EVERY_OTHER_TUESDAY"),
									3: BuddiLive.translate("SCHEDULE_DAY_EVERY_OTHER_WEDNESDAY"),
									4: BuddiLive.translate("SCHEDULE_DAY_EVERY_OTHER_THURSDAY"),
									5: BuddiLive.translate("SCHEDULE_DAY_EVERY_OTHER_FRIDAY"),
									6: BuddiLive.translate("SCHEDULE_DAY_EVERY_OTHER_SATURDAY")
								};
								result += " " + biWeeklyLookup[record.get("scheduleDay")];
							}
							else if (value == "SCHEDULE_FREQUENCY_EVERY_DAY"){
								//Nothing to do here - no configuration
							}
							else if (value == "SCHEDULE_FREQUENCY_EVERY_X_DAYS"){
								result = BuddiLive.translate("REPEATING_EVERY_X_DAYS") + " " + record.get("scheduleDay") + " " + BuddiLive.translate("DAYS");
							}
							else if (value == "SCHEDULE_FREQUENCY_EVERY_WEEKDAY"){
								//Nothing to do here - no configuration
							}
							else if (value == "SCHEDULE_FREQUENCY_MULTIPLE_WEEKS_EVERY_MONTH"){
							
							}
							else if (value == "SCHEDULE_FREQUENCY_MULTIPLE_MONTHS_EVERY_YEAR"){
								var multipleMonthsEveryYearLookup = {
									1: BuddiLive.translate("SCHEDULE_DATE_MONTHS_FIRST"),
									2: BuddiLive.translate("SCHEDULE_DATE_MONTHS_SECOND"),
									3: BuddiLive.translate("SCHEDULE_DATE_MONTHS_THIRD"),
									4: BuddiLive.translate("SCHEDULE_DATE_MONTHS_FOURTH"),
									5: BuddiLive.translate("SCHEDULE_DATE_MONTHS_FIFTH"),
									6: BuddiLive.translate("SCHEDULE_DATE_MONTHS_SIXTH"),
									7: BuddiLive.translate("SCHEDULE_DATE_MONTHS_SEVENTH"),
									8: BuddiLive.translate("SCHEDULE_DATE_MONTHS_EIGHTH"),
									9: BuddiLive.translate("SCHEDULE_DATE_MONTHS_NINETH"),
									10: BuddiLive.translate("SCHEDULE_DATE_MONTHS_TENTH"),
									11: BuddiLive.translate("SCHEDULE_DATE_MONTHS_ELEVENTH"),
									12: BuddiLive.translate("SCHEDULE_DATE_MONTHS_TWELFTH"),
									13: BuddiLive.translate("SCHEDULE_DATE_MONTHS_THIRTEENTH"),
									14: BuddiLive.translate("SCHEDULE_DATE_MONTHS_FOURTEENTH"),
									15: BuddiLive.translate("SCHEDULE_DATE_MONTHS_FIFTEENTH"),
									16: BuddiLive.translate("SCHEDULE_DATE_MONTHS_SIXTEENTH"),
									17: BuddiLive.translate("SCHEDULE_DATE_MONTHS_SEVENTEENTH"),
									18: BuddiLive.translate("SCHEDULE_DATE_MONTHS_EIGHTEENTH"),
									19: BuddiLive.translate("SCHEDULE_DATE_MONTHS_NINETEENTH"),
									20: BuddiLive.translate("SCHEDULE_DATE_MONTHS_TWENTIETH"),
									21: BuddiLive.translate("SCHEDULE_DATE_MONTHS_TWENTYFIRST"),
									22: BuddiLive.translate("SCHEDULE_DATE_MONTHS_TWENTYSECOND"),
									23: BuddiLive.translate("SCHEDULE_DATE_MONTHS_TWENTYTHIRD"),
									24: BuddiLive.translate("SCHEDULE_DATE_MONTHS_TWENTYFOURTH"),
									25: BuddiLive.translate("SCHEDULE_DATE_MONTHS_TWENTYFIFTH"),
									26: BuddiLive.translate("SCHEDULE_DATE_MONTHS_TWENTYSIXTH"),
									27: BuddiLive.translate("SCHEDULE_DATE_MONTHS_TWENTYSEVENTH"),
									28: BuddiLive.translate("SCHEDULE_DATE_MONTHS_TWENTYEIGHTH"),
									29: BuddiLive.translate("SCHEDULE_DATE_MONTHS_TWENTYNINETH"),
									30: BuddiLive.translate("SCHEDULE_DATE_MONTHS_THIRTIETH"),
									31: BuddiLive.translate("SCHEDULE_DATE_MONTHS_THIRTYFIRST"),
									32: BuddiLive.translate("SCHEDULE_DATE_MONTHS_LAST_DAY")
								};
								result += " " + multipleMonthsEveryYearLookup[record.get("scheduleDay")] + " (";
								var month = record.get("scheduleMonth");
								if (month & 1){
									result += BuddiLive.translate("MONTH_JANUARY") + ", ";
								}
								if (month & 2){
									result += BuddiLive.translate("MONTH_FEBRUARY") + ", ";
								}
								if (month & 4){
									result += BuddiLive.translate("MONTH_MARCH") + ", ";
								}
								if (month & 8){
									result += BuddiLive.translate("MONTH_APRIL") + ", ";
								}
								if (month & 16){
									result += BuddiLive.translate("MONTH_MAY") + ", ";
								}
								if (month & 32){
									result += BuddiLive.translate("MONTH_JUNE") + ", ";
								}
								if (month & 64){
									result += BuddiLive.translate("MONTH_JULY") + ", ";
								}
								if (month & 128){
									result += BuddiLive.translate("MONTH_AUGUST") + ", ";
								}
								if (month & 256){
									result += BuddiLive.translate("MONTH_SEPTEMBER") + ", ";
								}
								if (month & 512){
									result += BuddiLive.translate("MONTH_OCTOBER") + ", ";
								}
								if (month & 1024){
									result += BuddiLive.translate("MONTH_NOVEMBER") + ", ";
								}
								if (month & 2048){
									result += BuddiLive.translate("MONTH_DECEMBER") + ", ";
								}
								result = result.slice(0, result.length - 2);	//Remove the trailing comma and space
								result += ")";
							}

							return result;
						}
					},
					{
						text: BuddiLive.translate("SCHEDULED_TRANSACTION_LAST_TRIGGERED_DATE"),
						dataIndex: "lastCreatedDate",
						width: 120
					},
					{
						text: BuddiLive.translate("SCHEDULED_TRANSACTION_END_DATE"),
						dataIndex: "end",
						width: 120
					},
					{
						text: BuddiLive.translate("AMOUNT"),
						dataIndex: "splits",
						width: 100,
						renderer: function(value, metadata, record){
							var result = "";
							for (var i = 0; i < value.length; i++){
								if (i > 0){
									result += "<br/>";
								}
								result += value[i].amount;
							}
							return result;
						}
					},
					{
						text: BuddiLive.translate("SCHEDULED_TRANSACTION_MESSAGE"),
						dataIndex: "message",
						flex: 2
					}
				]
			}
		];
		this.dockedItems = BuddiLive.app.viewport.getDockedItems("scheduled")
	
		this.callParent(arguments);
	}
});