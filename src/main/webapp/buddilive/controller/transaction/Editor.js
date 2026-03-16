Ext.define("BuddiLive.controller.transaction.Editor", {
	extend: "Ext.app.Controller",
	stores: [
		"transaction.DescriptionComboboxStore"
	],

	onLaunch: function() {
		this.getTransactionDescriptionComboboxStoreStore().load();
	},

	init: function() {
		this.control({
			"transactionlist button[itemId='recordTransaction']": {click: this.recordTransaction},
			"transactionlist button[itemId='clearTransaction']": {click: this.clearTransaction},
			"transactionlist button[itemId='deleteTransaction']": {click: this.deleteTransaction},
			transactioneditor: {
				change: this.validateFields
			},
			"transactioneditor field": {
				blur: this.validateFields,
				select: this.validateFields,
				keypress: this.validateFields,
				specialkey: this.checkKeys
			}
		});
	},
	
	checkKeys: function(component, e) {
		this.validateFields(component);
		let editor = component.up("transactioneditor");
		let record = editor.down("button[itemId='recordTransaction']");
		if (e.getKey() == e.ENTER && e.ctrlKey && !record.isDisabled()) {
			record.fireEvent("click", record);
		}
	},
	
	validateFields: function(component) {
		let editor = (component.xtype == "transactioneditor" ? component : component.up("transactioneditor"));
		let enabled = editor.validate();
		editor.down("button[itemId='recordTransaction']").setDisabled(!enabled);
	},
	
	recordTransaction: function(component) {
		let me = this;
		let editor = component.up("transactioneditor");
		let mask = new Ext.LoadMask({msg: BuddiLive.translate("PROCESSING"), target: editor});
		mask.show();
		
		let lastTransaction = editor.lastTransaction;
		
		let request = editor.getTransaction();
		if (request.date == null || request.description == null || request.splits.length == 0) {
			mask.hide();
			return;
		}
			request.action = (request.id ? "update" : "insert");
		
		//Disable the button before submitting to prevent double clicks
		editor.down("button[itemId='recordTransaction']").disable();

		let doPost = function() {
			let conn = new Ext.data.Connection();
			conn.request({
				url: "data/transactions",
				headers: {
					Accept: "application/json"
				},
				method: "POST",
				jsonData: request,

				success: function(response) {
					mask.hide();
					me.getTransactionDescriptionComboboxStoreStore().load();
					editor.setTransaction(null, false, true);
					editor.up("panel[itemId='myAccounts']").down("accounttree").getStore().reload();
					editor.up("transactionlist").reload();
					editor.down("datefield[itemId='date']").focus(false, 500);
				},

				failure: function(response) {
					mask.hide();
					BuddiLive.app.error(response);
				}
			});
		}
		
		let d = Ext.Date.parse(request.date, "Y-m-d");
		let validBeginDate = Ext.Date.add(new Date(), Ext.Date.YEAR, -1);
		let validEndDate = Ext.Date.add(new Date(), Ext.Date.MONTH, 1);
		if (d < validBeginDate || d > validEndDate) {
			let msg = d < validBeginDate ? BuddiLive.translate("CONFIRM_DATE_OUT_OF_RANGE_BEFORE") : BuddiLive.translate("CONFIRM_DATE_OUT_OF_RANGE_AFTER");
			Ext.MessageBox.show({
				title: BuddiLive.translate("CONFIRM_DATE_OUT_OF_RANGE_TITLE"),
				msg: msg,
				buttons: Ext.MessageBox.YESNO,

				fn: function(buttonId) {
					if (buttonId == "yes") {
						doPost();
					}
					else {
						mask.hide();
						//Re-enable the button
						editor.down("button[itemId='recordTransaction']").enable();
					}
				}
			});
		}
		else if (lastTransaction != null && (
				lastTransaction.date != request.date ||
				lastTransaction.description != request.description ||
				lastTransaction.number != request.number ||
				lastTransaction.splits.length != request.splits.length)) {
			Ext.MessageBox.show({
				title: BuddiLive.translate("CONFIRM_CHANGE_EXISTING_TRANSACTION_TITLE"),
				msg: BuddiLive.translate("CONFIRM_CHANGE_EXISTING_TRANSACTION"),
				buttons: Ext.MessageBox.YESNO,

				fn: function(buttonId) {
					if (buttonId == "yes") {
						doPost();
					}
					else {
						mask.hide();
						//Re-enable the button
						editor.down("button[itemId='recordTransaction']").enable();
					}
				}
			});
		}
		else {
			doPost();
		}
	},
	
	clearTransaction: function(component) {
		//TODO Possibly check if there is data here... if so, verify that we really want to clear it?  This may be excessive...
		let editor = component.up("transactioneditor");
		let list = component.up("transactionlist");
		editor.setTransaction();
		list.getSelectionModel().deselectAll();
	},
	
	deleteTransaction: function(component) {
		let editor = component.up("transactioneditor");
		let list = editor.up("transactionlist");
		let selection = list.getSelectionModel().getSelection();
		if (selection.length > 0) {
			Ext.MessageBox.show({
				title: BuddiLive.translate("DELETE_TRANSACTION"),
				msg: BuddiLive.translate("CONFIRM_DELETE_TRANSACTION"),
				buttons: Ext.MessageBox.YESNO,

				fn: function(buttonId) {
					if (buttonId == "yes") {
						let id = selection[0].data.id;
						
						let conn = new Ext.data.Connection();
						conn.request({
							url: "data/transactions",
							headers: {
								Accept: "application/json"
							},
							method: "POST",
							jsonData: {action: "delete", id: id},

							success: function(response) {
								editor.setTransaction();
								editor.up("transactionlist").reload();
								editor.up("panel[itemId='myAccounts']").down("accounttree").getStore().reload();
							},

							failure: function(response) {
								BuddiLive.app.error(response);
							}
						});
					}
				}
			});
		}
	}
});
