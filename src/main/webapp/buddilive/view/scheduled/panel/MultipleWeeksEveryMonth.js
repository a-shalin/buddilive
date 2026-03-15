Ext.define("BuddiLive.view.scheduled.panel.MultipleWeeksEveryMonth", {
	extend: "Ext.panel.Panel",
	alias: "widget.scheduledpanelmultipleweekseverymonth",
	requires: [
	],
	
	initComponent: function(){
		var s = this.initialConfig.selected;
		this.itemId = "SCHEDULE_FREQUENCY_MULTIPLE_WEEKS_EVERY_MONTH";
		this.border = false;
		this.layout = "form";
		this.padding = 0;
		this.items = [
			{
				xtype: "selfdocumentingfield",
				messageBody: BuddiLive.translate("HELP_REPEATING_MULTIPLE_WEEKS_EVERY_MONTH"),
				type: "combobox",
				fieldLabel: BuddiLive.translate("REPEATING_WEEKLY"),
				value: (s ? s.scheduleDay : 0),
				displayField: "text",
				valueField: "value",
				allowBlank: false,
				forceSelection: true,
				store: new Ext.data.Store({
					fields: ["text", "value"],
					data: [
						{text: BuddiLive.translate("SCHEDULE_DAY_SUNDAY"), value: 0},
						{text: BuddiLive.translate("SCHEDULE_DAY_MONDAY"), value: 1},
						{text: BuddiLive.translate("SCHEDULE_DAY_TUESDAY"), value: 2},
						{text: BuddiLive.translate("SCHEDULE_DAY_WEDNESDAY"), value: 3},
						{text: BuddiLive.translate("SCHEDULE_DAY_THURSDAY"), value: 4},
						{text: BuddiLive.translate("SCHEDULE_DAY_FRIDAY"), value: 5},
						{text: BuddiLive.translate("SCHEDULE_DAY_SATURDAY"), value: 6}
					]
				}),
				queryMode: "local",
				valueField: "value"
			},
			{
				xtype: "fieldcontainer",
				fieldLabel: BuddiLive.translate("REPEATING_ON_WEEKS"),
				layout: "hbox",
				border: false,
				items: [
					{
						xtype: "checkbox",
						boxLabel: BuddiLive.translate("SCHEDULE_WEEK_FIRST"),
						margin: "0 10 0 0",
						checked: (s ? s.scheduleWeek & 1 : false),
						itemId: "SCHEDULE_WEEK_FIRST"
					},
					{
						xtype: "checkbox",
						boxLabel: BuddiLive.translate("SCHEDULE_WEEK_SECOND"),
						margin: "0 10 0 0",
						checked: (s ? s.scheduleWeek & 2 : false),
						itemId: "SCHEDULE_WEEK_SECOND"
					},
					{
						xtype: "checkbox",
						boxLabel: BuddiLive.translate("SCHEDULE_WEEK_THIRD"),
						margin: "0 10 0 0",
						checked: (s ? s.scheduleWeek & 4 : false),
						itemId: "SCHEDULE_WEEK_THIRD"
					},
					{
						xtype: "checkbox",
						boxLabel: BuddiLive.translate("SCHEDULE_WEEK_FOURTH"),
						margin: "0 25 0 0",
						checked: (s ? s.scheduleWeek & 8 : false),
						itemId: "SCHEDULE_WEEK_FOURTH"
					},
					{
						xtype: "displayfield",
						value: BuddiLive.translate("OF_THE_MONTH")
					}

				]
			}
		];
	
		this.callParent(arguments);
	},
	
	getScheduleDay: function(){
		return this.down("combobox").getValue();
	},
	getScheduleWeek: function(){
		var result = 0;
		if (this.down("checkbox[itemId='SCHEDULE_WEEK_FIRST']").getValue()) result += 1;
		if (this.down("checkbox[itemId='SCHEDULE_WEEK_SECOND']").getValue()) result += 2;
		if (this.down("checkbox[itemId='SCHEDULE_WEEK_THIRD']").getValue()) result += 4;
		if (this.down("checkbox[itemId='SCHEDULE_WEEK_FOURTH']").getValue()) result += 8;
		return result;
	},
	getScheduleMonth: function(){
		return 0;
	}
});