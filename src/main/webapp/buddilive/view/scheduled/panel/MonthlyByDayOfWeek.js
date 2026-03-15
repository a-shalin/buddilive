Ext.define("BuddiLive.view.scheduled.panel.MonthlyByDayOfWeek", {
	extend: "Ext.panel.Panel",
	alias: "widget.scheduledpanelmonthlybydayofweek",
	requires: [
	],
	
	initComponent: function(){
		var s = this.initialConfig.selected;
		this.itemId = "SCHEDULE_FREQUENCY_MONTHLY_BY_DAY_OF_WEEK";
		this.border = false;
		this.layout = "form";
		this.padding = 0;
		this.items = [
			{
				xtype: "selfdocumentingfield",
				messageBody: BuddiLive.translate("HELP_REPEATING_MONTHLY_BY_DAY_OF_WEEK"),
				type: "combobox",
				fieldLabel: BuddiLive.translate("REPEATING_MONTHLY"),
				value: (s ? s.scheduleDay : 0),
				displayField: "text",
				valueField: "value",
				allowBlank: false,
				forceSelection: true,
				store: new Ext.data.Store({
					fields: ["text", "value"],
					data: [
						{text: BuddiLive.translate("SCHEDULE_DAY_FIRST_SUNDAY"), value: 0},
						{text: BuddiLive.translate("SCHEDULE_DAY_FIRST_MONDAY"), value: 1},
						{text: BuddiLive.translate("SCHEDULE_DAY_FIRST_TUESDAY"), value: 2},
						{text: BuddiLive.translate("SCHEDULE_DAY_FIRST_WEDNESDAY"), value: 3},
						{text: BuddiLive.translate("SCHEDULE_DAY_FIRST_THURSDAY"), value: 4},
						{text: BuddiLive.translate("SCHEDULE_DAY_FIRST_FRIDAY"), value: 5},
						{text: BuddiLive.translate("SCHEDULE_DAY_FIRST_SATURDAY"), value: 6}
					]
				}),
				queryMode: "local",
				valueField: "value"
			}
		];
	
		this.callParent(arguments);
	},
	
	getScheduleDay: function(){
		return this.down("combobox").getValue();
	},
	getScheduleWeek: function(){
		return 0;
	},
	getScheduleMonth: function(){
		return 0;
	}
});