Ext.define("BuddiLive.controller.scheduled.Editor", {
	extend: "Ext.app.Controller",

	init: function() {
		this.control({
			"schedulededitor component": {
				blur: this.updateButtons,
				keyup: this.updateButtons,
				afterrender: this.updateButtons
			},
			"schedulededitor button[itemId='ok']": {click: this.ok},
			"schedulededitor button[itemId='cancel']": {click: this.cancel}
		});
	},
	
	updateButtons: function(component) {
		let window = component.up("schedulededitor");
		let ok = window.down("button[itemId='ok']");
		let name = window.down("textfield[itemId='name']");
		let startDate = window.down("datefield[itemId='startDate']");
		let transaction = window.down("transactioneditor")
		
		ok.setDisabled(name.getValue().length == 0 || startDate.getValue() == null || !transaction.validate());
	},
	
	cancel: function(component) {
		component.up("schedulededitor").close();
	},
	
	ok: function(component) {
		let window = component.up("schedulededitor");
		let panel = window.initialConfig.panel;
		let selected = window.initialConfig.selected;

		let request = {action: (selected ? "update" : "insert")};
		request.id = window.down("hidden[itemId='id']").getValue();
		request.lastCreatedDate = window.down("hidden[itemId='lastCreatedDate']").getValue();
		request.name = window.down("textfield[itemId='name']").getValue();
		request.repeat = window.down("combobox[itemId='repeat']").getValue();
		request.start = Ext.Date.format(window.down("datefield[itemId='startDate']").getValue(), "Y-m-d");
		request.end = Ext.Date.format(window.down("datefield[itemId='endDate']").getValue(), "Y-m-d");
		request.transaction = window.down("transactioneditor").getTransaction();
		request.message = window.down("textarea[itemId='message']").getValue();
		
		let activeCard = window.down("panel[itemId='cardLayoutPanel']").getLayout().getActiveItem();
		request.scheduleDay = activeCard.getScheduleDay();
		request.scheduleWeek = activeCard.getScheduleWeek();
		request.scheduleMonth = activeCard.getScheduleMonth();

		let mask = new Ext.LoadMask({msg: BuddiLive.translate("PROCESSING"), target: window});
		mask.show();
		
		let conn = new Ext.data.Connection();
		conn.request({
			url: "data/scheduledtransactions",
			headers: {
				Accept: "application/json"
			},
			method: "POST",
			jsonData: request,

			success: function(response) {
				mask.hide();
				window.close();
				panel.getStore().load();
				panel.getSelectionModel().deselectAll()
			},

			failure: function(response) {
				mask.hide();
				BuddiLive.app.error(response);
			}
		});
	}
});
