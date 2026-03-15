Ext.define("BuddiLive.view.scheduled.panel.Weekly", {
	extend: "Ext.panel.Panel",
	alias: "widget.scheduledpanelweekly",
	requires: [
	],
	
	initComponent: function(){
		var s = this.initialConfig.selected;
		this.itemId = "SCHEDULE_FREQUENCY_WEEKLY";
		this.border = false;
		this.layout = "form";
		this.padding = 0;
		this.items = [
			{
				xtype: "selfdocumentingfield",
				messageBody: BuddiLive.translate("HELP_REPEATING_WEEKLY"),
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