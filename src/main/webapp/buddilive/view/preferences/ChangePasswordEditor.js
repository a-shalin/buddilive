Ext.define('BuddiLive.view.preferences.ChangePasswordEditor', {
	extend: "Ext.window.Window",
	alias: "widget.changepasswordeditor",
	requires: [
		"Login.view.PasswordField"
	],
	
	initComponent: function() {
		let d = this.initialConfig.data

		this.title = BuddiLive.translate("CHANGE_PASSWORD");
		this.layout = "fit";
		this.modal = true;
		this.width = 500;
		this.items = [
			{
				xtype: "form",
				layout: "anchor",
				bodyPadding: 5,
				items: [
					{
						xtype: "textfield",
						itemId: "currentPassword",
						inputType: "password",
						fieldLabel: BuddiLive.translate("CURRENT_PASSWORD")
					},
					{
						xtype: "passwordfield",
						itemId: "newPassword",
						fieldLabel: BuddiLive.translate("NEW_PASSWORD"),
						identifier: "anonymous"
					}
				]
			}
		];
		this.buttons = [
			{
				text: BuddiLive.translate("OK"),
				itemId: "ok"
			},
			{
				text: BuddiLive.translate("CANCEL"),
				itemId: "cancel"
			}
		]
	
		this.callParent(arguments);
	}
});