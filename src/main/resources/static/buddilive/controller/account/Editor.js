Ext.define("BuddiLive.controller.account.Editor", {
	extend: "Ext.app.Controller",
	stores: [
		"transaction.split.FromComboboxStore",
		"transaction.split.ToComboboxStore"
	],

	init: function() {
		this.control({
			"accounteditor component": {
				blur: this.updateButtons,
				keypress: this.updateButtons,
				afterrender: this.updateButtons,
				specialkey: this.specialKey
			},
			"accounteditor button[itemId='ok']": {click: this.ok},
			"accounteditor button[itemId='cancel']": {click: this.cancel}
		});
	},
	
	specialKey: function(component, e) {
		if (e.getKey() == e.ENTER) {
			this.ok(component);
		}
	},
	
	cancel: function(component) {
		component.up("accounteditor").close();
	},
	
	ok: function(component) {
		let me = this;
		let window = component.up("accounteditor");
		let grid = window.initialConfig.grid;
		let selected = window.initialConfig.selected;

		let request = {};
		request.action = (selected ? "update" : "insert");
		if (selected) request.id = selected.id;
		request.name = window.down("textfield[itemId='name']").getValue();
		request.accountType = window.down("textfield[itemId='accountType']").getValue();
		request.type = window.down("combobox[itemId='type']").getValue();
		let startBalance = window.down("numberfield[itemId='startBalance']").getValue();
		if (startBalance) request.startBalance = startBalance;
		
		let mask = new Ext.LoadMask({msg: BuddiLive.translate("PROCESSING"), target: window});
		mask.show();

		let conn = new Ext.data.Connection();
		conn.request({
			url: "data/accounts",
			headers: {
				Accept: "application/json"
			},
			method: "POST",
			jsonData: request,

			success: function(response) {
				mask.hide();
				window.close();
				location.reload();
			},

			failure: function(response) {
				mask.hide();
				BuddiLive.app.error(response);
			}
		});
	},
	
	updateButtons: function(component) {
		let window = component.up("accounteditor");
		let ok = window.down("button[itemId='ok']");
		let name = window.down("textfield[itemId='name']");
		let accountType = window.down("textfield[itemId='accountType']");
		let type = window.down("combobox[itemId='type']");
		
		ok.setDisabled(name.getValue().length == 0 || accountType.getValue().length == 0 || type.getValue().length == 0);
	}
});
