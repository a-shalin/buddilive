Ext.define("BuddiLive.view.report.picker.Interval", {
	extend: "Ext.window.Window",
	alias: "widget.reportpickerinterval",
	requires: [
	],

	initComponent: function() {
		let me = this;
		let s = this.initialConfig.selected
		let extDateFormat = BuddiLive.util.UserConfig.get('extDateFormat') || 'Y-m-d';

		this.title = BuddiLive.translate("INTERVAL_PICKER");
		this.layout = "fit";
		this.modal = true;
		this.width = 400;
		this.items = [
			{
				xtype: "form",
				layout: "anchor",
				bodyPadding: 5,
				items: [
					{
						xtype: "selfdocumentingfield",
						messageBody: BuddiLive.translate("HELP_INTERVAL_PICKER"),
						type: "combobox",
						itemId: "interval",
						value: "PLUGIN_FILTER_THIS_MONTH",
						fieldLabel: BuddiLive.translate("INTERVAL"),
						forceSelection: true,
						editable: false,
						allowBlank: false,
						store: new Ext.data.Store({
							fields: ["text", "value"],
							data: [
								{text: BuddiLive.translate("PLUGIN_FILTER_THIS_WEEK"), value: "PLUGIN_FILTER_THIS_WEEK"},
								{text: BuddiLive.translate("PLUGIN_FILTER_LAST_WEEK"), value: "PLUGIN_FILTER_LAST_WEEK"},
								{text: BuddiLive.translate("PLUGIN_FILTER_THIS_SEMI_MONTH"), value: "PLUGIN_FILTER_THIS_SEMI_MONTH"},
								{text: BuddiLive.translate("PLUGIN_FILTER_LAST_SEMI_MONTH"), value: "PLUGIN_FILTER_LAST_SEMI_MONTH"},
								{text: BuddiLive.translate("PLUGIN_FILTER_THIS_MONTH"), value: "PLUGIN_FILTER_THIS_MONTH"},
								{text: BuddiLive.translate("PLUGIN_FILTER_LAST_MONTH"), value: "PLUGIN_FILTER_LAST_MONTH"},
								{text: BuddiLive.translate("PLUGIN_FILTER_THIS_QUARTER"), value: "PLUGIN_FILTER_THIS_QUARTER"},
								{text: BuddiLive.translate("PLUGIN_FILTER_LAST_QUARTER"), value: "PLUGIN_FILTER_LAST_QUARTER"},
								{text: BuddiLive.translate("PLUGIN_FILTER_THIS_YEAR"), value: "PLUGIN_FILTER_THIS_YEAR"},
								{text: BuddiLive.translate("PLUGIN_FILTER_THIS_YEAR_TO_DATE"), value: "PLUGIN_FILTER_THIS_YEAR_TO_DATE"},
								{text: BuddiLive.translate("PLUGIN_FILTER_LAST_YEAR"), value: "PLUGIN_FILTER_LAST_YEAR"},
								{text: BuddiLive.translate("PLUGIN_FILTER_ALL_TIME"), value: "PLUGIN_FILTER_ALL_TIME"},
								{text: BuddiLive.translate("PLUGIN_FILTER_OTHER"), value: "PLUGIN_FILTER_OTHER"}
							]
						}),
						listeners: {
							select: function(combo) {
								combo.up("form").down("selfdocumentingfield[childItemId='startDate']").setVisible(combo.getValue() == "PLUGIN_FILTER_OTHER");
								combo.up("form").down("selfdocumentingfield[childItemId='endDate']").setVisible(combo.getValue() == "PLUGIN_FILTER_OTHER");
							}
						},
						queryMode: "local",
						valueField: "value"
					},
					{
						xtype: "selfdocumentingfield",
						messageBody: BuddiLive.translate("HELP_START_DATE"),
						type: "datefield",
						itemId: "startDate",
						allowBlank: false,
						fieldLabel: BuddiLive.translate("START_DATE"),
						msgTarget: "none",
						hidden: true,
						value: new Date(),
						maxValue: new Date(),
						listeners: {
							change: function(field) {
								field.up("form").down("datefield[itemId='endDate']").setMinValue(field.getValue());
							}
						}
					},
					{
						xtype: "selfdocumentingfield",
						messageBody: BuddiLive.translate("HELP_END_DATE"),
						type: "datefield",
						itemId: "endDate",
						allowBlank: false,
						fieldLabel: BuddiLive.translate("END_DATE"),
						msgTarget: "none",
						hidden: true,
						value: new Date(),
						minValue: new Date(),
						listeners: {
							change: function(field) {
								field.up("form").down("datefield[itemId='startDate']").setMaxValue(field.getValue());
							}
						}
					}
				]
			}
		];
		this.buttons = [
			{
				text: BuddiLive.translate("OK"),
				itemId: "ok",
				listeners: {
					click: function() {
						let interval = me.down("combobox[itemId='interval']").getValue();
						let dateRange = me.down("combobox[itemId='interval']").getRawValue();
						let query = "interval=" + interval;
						if (interval == "PLUGIN_FILTER_OTHER") {
							let startValid = me.down("datefield[itemId='startDate']").validate();
							let endValid = me.down("datefield[itemId='endDate']").validate();
							if (!startValid || !endValid) return;

							let startDate = me.down("datefield[itemId='startDate']").getValue();
							let endDate = me.down("datefield[itemId='endDate']").getValue();
							query += ("&startDate=" + Ext.Date.format(startDate, "Y-m-d"));
							query += ("&endDate=" + Ext.Date.format(endDate, "Y-m-d"));

							dateRange = Ext.Date.format(startDate, extDateFormat) + " - " + Ext.Date.format(endDate, extDateFormat);
						}
						let options = {
							query: query,
							dateRange: dateRange
						};
						me.initialConfig.callback(options);
						me.close();
					}
				}
			},
			{
				text: BuddiLive.translate("CANCEL"),
				itemId: "cancel",
				listeners: {
					click: function() {
						me.close();
					}
				}
			}
		]

		this.callParent(arguments);
	}
});
