Ext.define("BuddiLive.controller.preferences.ChangePasswordEditor", {
	extend: "Ext.app.Controller",

	init: function() {
		this.control({
			"changepasswordeditor button[itemId='ok']": {click: this.ok},
			"changepasswordeditor button[itemId='cancel']": {click: this.cancel}
		});
	},

	cancel: function(component) {
		component.up("changepasswordeditor").close();
	},

	ok: function(component) {
		let window = component.up("changepasswordeditor");
		let panel = window.initialConfig.panel;

		let request = {action: "update"};
		request.newPassword = window.down("passwordfield[itemId='newPassword']").getValue();
		request.currentPassword = window.down("textfield[itemId='currentPassword']").getValue();

		let mask = new Ext.LoadMask({msg: BuddiLive.translate("PROCESSING"), target: window});
		mask.show();

		let conn = new Ext.data.Connection();
		conn.request({
			url: "data/changepassword",
			headers: {
				Accept: "application/json"
			},
			method: "POST",
			jsonData: request,

			success: function(response) {
				mask.hide();
				window.close();
				let connLogin = new Ext.data.Connection();
				connLogin.request({
					url: "index",
					method: "POST",
					params: { action: "login", identifier: BuddiLive.util.UserConfig.get('plaintextIdentifier'), secret: request.newPassword },

					failure: function(response) {
						mask.hide();
						BuddiLive.app.error(response);
					}
				});
			},

			failure: function(response) {
				mask.hide();
				BuddiLive.app.error(response);
			}
		});
	}
});
