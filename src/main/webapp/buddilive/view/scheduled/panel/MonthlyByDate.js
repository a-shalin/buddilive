Ext.define("BuddiLive.view.scheduled.panel.MonthlyByDate", {
	"extend": "Ext.panel.Panel",
	"alias": "widget.scheduledpanelmonthlybydate",
	"requires": [
	],
	
	"initComponent": function(){
		var s = this.initialConfig.selected;
		this.itemId = "SCHEDULE_FREQUENCY_MONTHLY_BY_DATE";
		this.border = false;
		this.layout = "form";
		this.padding = 0;
		this.items = [
			{
				"xtype": "selfdocumentingfield",
				"messageBody": BuddiLive.translate("HELP_REPEATING_MONTHLY"),
				"type": "combobox",
				"fieldLabel": BuddiLive.translate("REPEATING_MONTHLY"),
				"value": (s ? s.scheduleDay : 1),
				"displayField": "text",
				"valueField": "value",
				"allowBlank": false,
				"forceSelection": true,
				"store": new Ext.data.Store({
					"fields": ["text", "value"],
					"data": [
						{"text": BuddiLive.translate("SCHEDULE_DATE_FIRST"), "value": 1},
						{"text": BuddiLive.translate("SCHEDULE_DATE_SECOND"), "value": 2},
						{"text": BuddiLive.translate("SCHEDULE_DATE_THIRD"), "value": 3},
						{"text": BuddiLive.translate("SCHEDULE_DATE_FOURTH"), "value": 4},
						{"text": BuddiLive.translate("SCHEDULE_DATE_FIFTH"), "value": 5},
						{"text": BuddiLive.translate("SCHEDULE_DATE_SIXTH"), "value": 6},
						{"text": BuddiLive.translate("SCHEDULE_DATE_SEVENTH"), "value": 7},
						{"text": BuddiLive.translate("SCHEDULE_DATE_EIGHTH"), "value": 8},
						{"text": BuddiLive.translate("SCHEDULE_DATE_NINETH"), "value": 9},
						{"text": BuddiLive.translate("SCHEDULE_DATE_TENTH"), "value": 10},
						{"text": BuddiLive.translate("SCHEDULE_DATE_ELEVENTH"), "value": 11},
						{"text": BuddiLive.translate("SCHEDULE_DATE_TWELFTH"), "value": 12},
						{"text": BuddiLive.translate("SCHEDULE_DATE_THIRTEENTH"), "value": 13},
						{"text": BuddiLive.translate("SCHEDULE_DATE_FOURTEENTH"), "value": 14},
						{"text": BuddiLive.translate("SCHEDULE_DATE_FIFTEENTH"), "value": 15},
						{"text": BuddiLive.translate("SCHEDULE_DATE_SIXTEENTH"), "value": 16},
						{"text": BuddiLive.translate("SCHEDULE_DATE_SEVENTEENTH"), "value": 17},
						{"text": BuddiLive.translate("SCHEDULE_DATE_EIGHTEENTH"), "value": 18},
						{"text": BuddiLive.translate("SCHEDULE_DATE_NINETEENTH"), "value": 19},
						{"text": BuddiLive.translate("SCHEDULE_DATE_TWENTIETH"), "value": 20},
						{"text": BuddiLive.translate("SCHEDULE_DATE_TWENTYFIRST"), "value": 21},
						{"text": BuddiLive.translate("SCHEDULE_DATE_TWENTYSECOND"), "value": 22},
						{"text": BuddiLive.translate("SCHEDULE_DATE_TWENTYTHIRD"), "value": 23},
						{"text": BuddiLive.translate("SCHEDULE_DATE_TWENTYFOURTH"), "value": 24},
						{"text": BuddiLive.translate("SCHEDULE_DATE_TWENTYFIFTH"), "value": 25},
						{"text": BuddiLive.translate("SCHEDULE_DATE_TWENTYSIXTH"), "value": 26},
						{"text": BuddiLive.translate("SCHEDULE_DATE_TWENTYSEVENTH"), "value": 27},
						{"text": BuddiLive.translate("SCHEDULE_DATE_TWENTYEIGHTH"), "value": 28},
						{"text": BuddiLive.translate("SCHEDULE_DATE_TWENTYNINETH"), "value": 29},
						{"text": BuddiLive.translate("SCHEDULE_DATE_THIRTIETH"), "value": 30},
						{"text": BuddiLive.translate("SCHEDULE_DATE_THIRTYFIRST"), "value": 31},
						{"text": BuddiLive.translate("SCHEDULE_DATE_LAST_DAY"), "value": 32}
					]
				}),
				"queryMode": "local",
				"valueField": "value"
			}
		];
	
		this.callParent(arguments);
	},
	
	"getScheduleDay": function(){
		return this.down("combobox").getValue();
	},
	"getScheduleWeek": function(){
		return 0;
	},
	"getScheduleMonth": function(){
		return 0;
	}
});