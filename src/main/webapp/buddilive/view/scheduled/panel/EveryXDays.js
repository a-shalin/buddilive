Ext.define("BuddiLive.view.scheduled.panel.EveryXDays", {
	"extend": "Ext.panel.Panel",
	"alias": "widget.scheduledpaneleveryxdays",
	"requires": [
	],
	
	"initComponent": function(){
		var s = this.initialConfig.selected;
		this.itemId = "SCHEDULE_FREQUENCY_EVERY_X_DAYS";
		this.border = false;
		this.layout = "form";
		this.padding = 0;
		this.items = [
			{
				"xtype": "selfdocumentingfield",
				"messageBody": BuddiLive.translate("HELP_REPEATING_EVERY_X_DAYS"),
				"type": "panel",
				"border": false,
				"layout": "hbox",
				"fieldLabel": BuddiLive.translate("REPEATING_EVERY_X_DAYS"),
				"items": [
					{
						"xtype": "numberfield",
						"value": (s ? s.scheduleDay : 7),
						"allowBlank": false,
						"minValue": 1,
						"flex": 1
					},
					{
						"xtype": "displayfield",
						"value": BuddiLive.translate("DAYS"),
						"margin": "0 0 0 10",
						"width": 50
					}
				]
			}
		];
	
		this.callParent(arguments);
	},
	
	"getScheduleDay": function(){
		return this.down("numberfield").getValue();
	},
	"getScheduleWeek": function(){
		return 0;
	},
	"getScheduleMonth": function(){
		return 0;
	}
});