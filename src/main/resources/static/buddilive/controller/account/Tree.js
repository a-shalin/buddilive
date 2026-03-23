Ext.define("BuddiLive.controller.account.Tree", {
	extend: "Ext.app.Controller",

	init: function() {
		this.control({
			accounttree: {selectionchange: this.selectionChange}
		});
	},
	
	selectionChange: function(selectionModel, selected) {
		let panel = selectionModel.view.panel.up("buddiviewport");
		let selectedItem = selected[0].data;
		let selectedType = selected.length > 0 ? selectedItem.nodeType : null;
		panel.down("button[itemId='editAccount']").setDisabled(selectedType != "account");
		panel.down("button[itemId='deleteAccount']").setDisabled(selectedType != "account");
		if (selectedType == "account" && selected[0].data.deleted) {
			panel.down("button[itemId='deleteAccount']").setText(BuddiLive.translate("UNDELETE_ACCOUNT"));
		}
		else {
			panel.down("button[itemId='deleteAccount']").setText(BuddiLive.translate("DELETE_ACCOUNT"));
		}
		
		if (selectedType == "account") {
			let transactionList = panel.down("transactionlist");
			Ext.apply(transactionList.getStore().getProxy().extraParams, {
				source: selectedItem.id
			}); 
			transactionList.reload();
			
			transactionList.down("transactioneditor").setSource(selectedItem.id);
			transactionList.down("transactioneditor").setTransaction();
		}
	}
});