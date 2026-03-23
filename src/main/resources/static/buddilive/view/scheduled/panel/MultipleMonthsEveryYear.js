Ext.define("BuddiLive.view.scheduled.panel.MultipleMonthsEveryYear", {
	extend: "Ext.panel.Panel",
	alias: "widget.scheduledpanelmultiplemonthseveryyear",
	requires: [
	],
	
	initComponent: function() {
		let s = this.initialConfig.selected;
		this.itemId = "SCHEDULE_FREQUENCY_MULTIPLE_MONTHS_EVERY_YEAR";
		this.border = false;
		this.layout = "form";
		this.padding = 0;
		this.items = [
			{
				xtype: "selfdocumentingfield",
				messageBody: BuddiLive.translate("HELP_REPEATING_MULTIPLE_MONTHS_EVERY_YEAR"),
				type: "combobox",
				fieldLabel: BuddiLive.translate("REPEATING_MONTHLY"),
				value: (s ? s.scheduleDay : 1),
				displayField: "text",
				valueField: "value",
				allowBlank: false,
				forceSelection: true,
				store: new Ext.data.Store({
					fields: ["text", "value"],
					data: [
						{text: BuddiLive.translate("SCHEDULE_DATE_MONTHS_FIRST"), value: 1},
						{text: BuddiLive.translate("SCHEDULE_DATE_MONTHS_SECOND"), value: 2},
						{text: BuddiLive.translate("SCHEDULE_DATE_MONTHS_THIRD"), value: 3},
						{text: BuddiLive.translate("SCHEDULE_DATE_MONTHS_FOURTH"), value: 4},
						{text: BuddiLive.translate("SCHEDULE_DATE_MONTHS_FIFTH"), value: 5},
						{text: BuddiLive.translate("SCHEDULE_DATE_MONTHS_SIXTH"), value: 6},
						{text: BuddiLive.translate("SCHEDULE_DATE_MONTHS_SEVENTH"), value: 7},
						{text: BuddiLive.translate("SCHEDULE_DATE_MONTHS_EIGHTH"), value: 8},
						{text: BuddiLive.translate("SCHEDULE_DATE_MONTHS_NINETH"), value: 9},
						{text: BuddiLive.translate("SCHEDULE_DATE_MONTHS_TENTH"), value: 10},
						{text: BuddiLive.translate("SCHEDULE_DATE_MONTHS_ELEVENTH"), value: 11},
						{text: BuddiLive.translate("SCHEDULE_DATE_MONTHS_TWELFTH"), value: 12},
						{text: BuddiLive.translate("SCHEDULE_DATE_MONTHS_THIRTEENTH"), value: 13},
						{text: BuddiLive.translate("SCHEDULE_DATE_MONTHS_FOURTEENTH"), value: 14},
						{text: BuddiLive.translate("SCHEDULE_DATE_MONTHS_FIFTEENTH"), value: 15},
						{text: BuddiLive.translate("SCHEDULE_DATE_MONTHS_SIXTEENTH"), value: 16},
						{text: BuddiLive.translate("SCHEDULE_DATE_MONTHS_SEVENTEENTH"), value: 17},
						{text: BuddiLive.translate("SCHEDULE_DATE_MONTHS_EIGHTEENTH"), value: 18},
						{text: BuddiLive.translate("SCHEDULE_DATE_MONTHS_NINETEENTH"), value: 19},
						{text: BuddiLive.translate("SCHEDULE_DATE_MONTHS_TWENTIETH"), value: 20},
						{text: BuddiLive.translate("SCHEDULE_DATE_MONTHS_TWENTYFIRST"), value: 21},
						{text: BuddiLive.translate("SCHEDULE_DATE_MONTHS_TWENTYSECOND"), value: 22},
						{text: BuddiLive.translate("SCHEDULE_DATE_MONTHS_TWENTYTHIRD"), value: 23},
						{text: BuddiLive.translate("SCHEDULE_DATE_MONTHS_TWENTYFOURTH"), value: 24},
						{text: BuddiLive.translate("SCHEDULE_DATE_MONTHS_TWENTYFIFTH"), value: 25},
						{text: BuddiLive.translate("SCHEDULE_DATE_MONTHS_TWENTYSIXTH"), value: 26},
						{text: BuddiLive.translate("SCHEDULE_DATE_MONTHS_TWENTYSEVENTH"), value: 27},
						{text: BuddiLive.translate("SCHEDULE_DATE_MONTHS_TWENTYEIGHTH"), value: 28},
						{text: BuddiLive.translate("SCHEDULE_DATE_MONTHS_TWENTYNINETH"), value: 29},
						{text: BuddiLive.translate("SCHEDULE_DATE_MONTHS_THIRTIETH"), value: 30},
						{text: BuddiLive.translate("SCHEDULE_DATE_MONTHS_THIRTYFIRST"), value: 31},
						{text: BuddiLive.translate("SCHEDULE_DATE_MONTHS_LAST_DAY"), value: 32}
					]
				}),
				queryMode: "local",
				valueField: "value"
			},
			{
				xtype: "fieldcontainer",
				fieldLabel: " ",
				labelSeparator: "",
				layout: "hbox",
				border: false,
				items: [
					{
						xtype: "checkbox",
						boxLabel: BuddiLive.translate("MONTH_JANUARY"),
						margin: "0 10 0 0",
						flex: 1,
						checked: (s ? s.scheduleMonth & 1 : false),
						itemId: "MONTH_JANUARY"
					},
					{
						xtype: "checkbox",
						boxLabel: BuddiLive.translate("MONTH_FEBRUARY"),
						margin: "0 10 0 0",
						flex: 1,
						checked: (s ? s.scheduleMonth & 2 : false),
						itemId: "MONTH_FEBRUARY"
					},
					{
						xtype: "checkbox",
						boxLabel: BuddiLive.translate("MONTH_MARCH"),
						margin: "0 10 0 0",
						flex: 1,
						checked: (s ? s.scheduleMonth & 4 : false),
						itemId: "MONTH_MARCH"
					},
					{
						xtype: "checkbox",
						boxLabel: BuddiLive.translate("MONTH_APRIL"),
						margin: "0 10 0 0",
						flex: 1,
						checked: (s ? s.scheduleMonth & 8 : false),
						itemId: "MONTH_APRIL"
					},
					{
						xtype: "checkbox",
						boxLabel: BuddiLive.translate("MONTH_MAY"),
						margin: "0 10 0 0",
						flex: 1,
						checked: (s ? s.scheduleMonth & 16 : false),
						itemId: "MONTH_MAY"
					},
					{
						xtype: "checkbox",
						boxLabel: BuddiLive.translate("MONTH_JUNE"),
						margin: "0 10 0 0",
						flex: 1,
						checked: (s ? s.scheduleMonth & 32 : false),
						itemId: "MONTH_JUNE"
					}
				]
			},
			{
				xtype: "fieldcontainer",
				fieldLabel: " ",
				labelSeparator: "",
				layout: "hbox",
				border: false,
				items: [
					{
						xtype: "checkbox",
						boxLabel: BuddiLive.translate("MONTH_JULY"),
						margin: "0 10 0 0",
						flex: 1,
						checked: (s ? s.scheduleMonth & 64 : false),
						itemId: "MONTH_JULY"
					},
					{
						xtype: "checkbox",
						boxLabel: BuddiLive.translate("MONTH_AUGUST"),
						margin: "0 10 0 0",
						flex: 1,
						checked: (s ? s.scheduleMonth & 128 : false),
						itemId: "MONTH_AUGUST"
					},
					{
						xtype: "checkbox",
						boxLabel: BuddiLive.translate("MONTH_SEPTEMBER"),
						margin: "0 10 0 0",
						flex: 1,
						checked: (s ? s.scheduleMonth & 256 : false),
						itemId: "MONTH_SEPTEMBER"
					},
					{
						xtype: "checkbox",
						boxLabel: BuddiLive.translate("MONTH_OCTOBER"),
						margin: "0 10 0 0",
						flex: 1,
						checked: (s ? s.scheduleMonth & 512 : false),
						itemId: "MONTH_OCTOBER"
					},
					{
						xtype: "checkbox",
						boxLabel: BuddiLive.translate("MONTH_NOVEMBER"),
						margin: "0 10 0 0",
						flex: 1,
						checked: (s ? s.scheduleMonth & 1024 : false),
						itemId: "MONTH_NOVEMBER"
					},
					{
						xtype: "checkbox",
						boxLabel: BuddiLive.translate("MONTH_DECEMBER"),
						margin: "0 10 0 0",
						flex: 1,
						checked: (s ? s.scheduleMonth & 2048 : false),
						itemId: "MONTH_DECEMBER"
					}
				]
			}
		];
	
		this.callParent(arguments);
	},
	
	getScheduleDay: function() {
		return this.down("combobox").getValue();
	},

	getScheduleWeek: function() {
		return 0;
	},

	getScheduleMonth: function() {
		let result = 0;
		if (this.down("checkbox[itemId='MONTH_JANUARY']").getValue()) result += 1;
		if (this.down("checkbox[itemId='MONTH_FEBRUARY']").getValue()) result += 2;
		if (this.down("checkbox[itemId='MONTH_MARCH']").getValue()) result += 4;
		if (this.down("checkbox[itemId='MONTH_APRIL']").getValue()) result += 8;
		if (this.down("checkbox[itemId='MONTH_MAY']").getValue()) result += 16;
		if (this.down("checkbox[itemId='MONTH_JUNE']").getValue()) result += 32;
		if (this.down("checkbox[itemId='MONTH_JULY']").getValue()) result += 64;
		if (this.down("checkbox[itemId='MONTH_AUGUST']").getValue()) result += 128;
		if (this.down("checkbox[itemId='MONTH_SEPTEMBER']").getValue()) result += 256;
		if (this.down("checkbox[itemId='MONTH_OCTOBER']").getValue()) result += 512;
		if (this.down("checkbox[itemId='MONTH_NOVEMBER']").getValue()) result += 1024;
		if (this.down("checkbox[itemId='MONTH_DECEMBER']").getValue()) result += 2048;
		return result;
	}
});