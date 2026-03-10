Ext.define('BuddiLive.view.scheduled.Editor', {
	"extend": "Ext.window.Window",
	"alias": "widget.schedulededitor",
	"requires": [
		"BuddiLive.view.scheduled.panel.MonthlyByDate",
		"BuddiLive.view.scheduled.panel.MonthlyByDayOfWeek",
		"BuddiLive.view.scheduled.panel.Weekly",
		"BuddiLive.view.scheduled.panel.BiWeekly",
		"BuddiLive.view.scheduled.panel.EveryDay",
		"BuddiLive.view.scheduled.panel.EveryXDays",
		"BuddiLive.view.scheduled.panel.EveryWeekday",
		"BuddiLive.view.scheduled.panel.MultipleWeeksEveryMonth",
		"BuddiLive.view.scheduled.panel.MultipleMonthsEveryYear"
	],
	
	"initComponent": function(){
		var s = this.initialConfig.selected
		var editor = this;
		
		this.title = (s ? BuddiLive.translate("EDIT_SCHEDULED_TRANSACTION") : BuddiLive.translate("ADD_SCHEDULED_TRANSACTION"));
		this.layout = "fit";
		this.modal = true;
		this.width = 750;
		this.items = [
			{
				"xtype": "form",
				"layout": "anchor",
				"bodyPadding": 5,
				"items": [
					{
						"xtype": "hidden",
						"itemId": "id",
						"value": (s ? s.id : null)
					},
					{
						"xtype": "hidden",
						"itemId": "lastCreatedDate",
						"value": (s ? s.lastCreatedDate : null)
					},
					{
						"xtype": "selfdocumentingfield",
						"messageBody": BuddiLive.translate("HELP_SCHEDULED_TRANSACTION_NAME"),
						"type": "textfield",
						"itemId": "name",
						"enableKeyEvents": true,
						"value": (s ? s.name : null),
						"fieldLabel": BuddiLive.translate("SCHEDULED_TRANSACTION_NAME"),
						"allowBlank": false,
						"listeners": {
							"afterrender": function(field) {
								field.focus(false, 500);
							}
						}
					},
					{
						"xtype": "selfdocumentingfield",
						"messageBody": BuddiLive.translate("HELP_SCHEDULED_TRANSACTION_REPEAT"),
						"type": "combobox",
						"itemId": "repeat",
						"fieldLabel": BuddiLive.translate("SCHEDULED_TRANSACTION_REPEAT"),
						"value": (s ? s.repeat : "SCHEDULE_FREQUENCY_MONTHLY_BY_DATE"),
						"disabled": s != null,
						"displayField": "text",
						"valueField": "value",
						"allowBlank": false,
						"editable": false,
						"forceSelection": true,
						"store": new Ext.data.Store({
							"fields": ["text", "value"],
							"data": [
								{"text": BuddiLive.translate("SCHEDULE_FREQUENCY_MONTHLY_BY_DATE"), "value": "SCHEDULE_FREQUENCY_MONTHLY_BY_DATE"},
								{"text": BuddiLive.translate("SCHEDULE_FREQUENCY_MONTHLY_BY_DAY_OF_WEEK"), "value": "SCHEDULE_FREQUENCY_MONTHLY_BY_DAY_OF_WEEK"},
								{"text": BuddiLive.translate("SCHEDULE_FREQUENCY_WEEKLY"), "value": "SCHEDULE_FREQUENCY_WEEKLY"},
								{"text": BuddiLive.translate("SCHEDULE_FREQUENCY_BIWEEKLY"), "value": "SCHEDULE_FREQUENCY_BIWEEKLY"},
								{"text": BuddiLive.translate("SCHEDULE_FREQUENCY_EVERY_DAY"), "value": "SCHEDULE_FREQUENCY_EVERY_DAY"},
								{"text": BuddiLive.translate("SCHEDULE_FREQUENCY_EVERY_X_DAYS"), "value": "SCHEDULE_FREQUENCY_EVERY_X_DAYS"},
								{"text": BuddiLive.translate("SCHEDULE_FREQUENCY_EVERY_WEEKDAY"), "value": "SCHEDULE_FREQUENCY_EVERY_WEEKDAY"},
								{"text": BuddiLive.translate("SCHEDULE_FREQUENCY_MULTIPLE_WEEKS_EVERY_MONTH"), "value": "SCHEDULE_FREQUENCY_MULTIPLE_WEEKS_EVERY_MONTH"},
								{"text": BuddiLive.translate("SCHEDULE_FREQUENCY_MULTIPLE_MONTHS_EVERY_YEAR"), "value": "SCHEDULE_FREQUENCY_MULTIPLE_MONTHS_EVERY_YEAR"}
							]
						}),
						"queryMode": "local",
						"valueField": "value",
						"listeners": {
							"change": function(component){
								//Change the card layout to show the new item
								component.up("form").down("panel[itemId='cardLayoutPanel']").getLayout().setActiveItem(component.getValue());
							}
						}
					},
					{
						"xtype": "selfdocumentingfield",
						"messageBody": BuddiLive.translate("HELP_SCHEDULED_TRANSACTION_START_DATE"),
						"type": "datefield",
						"itemId": "startDate",
						"value": (s ? s.start : new Date),
						"disabled": s != null,
						"fieldLabel": BuddiLive.translate("SCHEDULED_TRANSACTION_START_DATE"),
						"allowBlank": false
					},
					{
						"xtype": "selfdocumentingfield",
						"messageBody": BuddiLive.translate("HELP_SCHEDULED_TRANSACTION_END_DATE"),
						"type": "datefield",
						"itemId": "endDate",
						"emptyText": BuddiLive.translate("SCHEDULED_TRANSACTION_END_DATE_EMPTY_TEXT"),
						"value": (s ? s.end : null),
						"fieldLabel": BuddiLive.translate("SCHEDULED_TRANSACTION_END_DATE")
					},
					{
						"xtype": "panel",
						"itemId": "cardLayoutPanel",
						"layout": "card",
						"border": false,
						"padding": 0,
						"items": [
							{"xtype": "scheduledpanelmonthlybydate", "selected": s},
							{"xtype": "scheduledpanelmonthlybydayofweek", "selected": s},
							{"xtype": "scheduledpanelweekly", "selected": s},
							{"xtype": "scheduledpanelbiweekly", "selected": s},
							{"xtype": "scheduledpaneleveryday", "selected": s},
							{"xtype": "scheduledpaneleveryxdays", "selected": s},
							{"xtype": "scheduledpaneleveryweekday", "selected": s},
							{"xtype": "scheduledpanelmultipleweekseverymonth", "selected": s},
							{"xtype": "scheduledpanelmultiplemonthseveryyear", "selected": s}
						],
						"listeners": {
							"afterrender": function(component){
								//Change the card layout to show the selected item if this is an editor
								if (s != null){
									component.getLayout().setActiveItem(s.repeat);
								}
							}
						}

					},
					{
						"xtype": "selfdocumentingfield",
						"messageBody": BuddiLive.translate("HELP_SCHEDULED_TRANSACTION_TRANSACTION"),
						"type": "transactioneditor",
						"scheduledTransaction": true,
						"fieldLabel": BuddiLive.translate("SCHEDULED_TRANSACTION_TRANSACTION"),
						"transaction": s
					},
					{
						"xtype": "selfdocumentingfield",
						"messageBody": BuddiLive.translate("HELP_SCHEDULED_TRANSACTION_MESSAGE"),
						"type": "textarea",
						"itemId": "message",
						"value": (s ? s.message : null),
						"fieldLabel": BuddiLive.translate("SCHEDULED_TRANSACTION_MESSAGE")
					}
				]
			}
		];
		this.buttons = [
			{
				"text": BuddiLive.translate("OK"),
				"itemId": "ok",
				"disabled": true
			},
			{
				"text": BuddiLive.translate("CANCEL"),
				"itemId": "cancel"
			}
		]
	
		this.callParent(arguments);
	}
});