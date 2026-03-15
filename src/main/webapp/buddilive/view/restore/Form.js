Ext.define('BuddiLive.view.restore.Form', {
	extend: "Ext.window.Window",
	alias: "widget.restoreform",
	requires: [
	],
	
	initComponent: function(){
		this.title = BuddiLive.translate("RESTORE"),
		this.layout = "fit";
		this.modal = true;
		this.width = 400;
		this.items = [
			{
				xtype: "form",
				layout: "anchor",
				bodyPadding: 5,
				timeout: 3600,	//1 hour, way higher than needed.
				items: [
					{
						xtype: "selfdocumentingfield",
						messageBody: BuddiLive.translate("HELP_RESTORE_FILE"),
						type: "filefield",
						fieldLabel: BuddiLive.translate("RESTORE_FILE"),
						allowBlank: false,
						name: "file"
					},
					{
						xtype: "selfdocumentingfield",
						messageBody: BuddiLive.translate("HELP_DELETE_DATA"),
						type: "checkbox",
						itemId: "deleteData",
						fieldLabel: " ",
						labelSeparator: "",
						boxLabel: BuddiLive.translate("DELETE_DATA")
					}
				]
			}
		];
		this.buttons = [
			{
				text: BuddiLive.translate("OK"),
				itemId: "ok",
				disabled: true
			},
			{
				text: BuddiLive.translate("CANCEL"),
				itemId: "cancel"
			}
		];
	
		this.callParent(arguments);
	}
});