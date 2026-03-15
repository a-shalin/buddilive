Ext.define("BuddiLive.controller.transaction.split.Editor", {
	extend: "BuddiLive.controller.transaction.Editor",
	stores: [
		"transaction.split.FromComboboxStore",
		"transaction.split.ToComboboxStore"
	],

	onLaunch: function() {
		this.getTransactionSplitFromComboboxStoreStore().load();
		this.getTransactionSplitToComboboxStoreStore().load();
	},
	
	init: function() {
		this.control({
			"spliteditor button[itemId='addSplit']": {click: this.addSplit},
			"spliteditor button[itemId='removeSplit']": {click: this.removeSplit},
			spliteditor: {updateButtons: this.updateButtons}
		});
	},
	
	addSplit: function(component) {
		let transactionEditor = component.up("transactioneditor");
		transactionEditor.add({xtype: "spliteditor", scheduledTransaction: transactionEditor.initialConfig.scheduledTransaction});
		this.updateButtons(transactionEditor);
	},
	
	removeSplit: function(component) {
		let splitLine = component.up("spliteditor");
		let transactionEditor = component.up("transactioneditor");
		if (Ext.ComponentQuery.query("spliteditor", transactionEditor).length > 1) {
			transactionEditor.remove(splitLine);
		}
		this.updateButtons(transactionEditor);
	},
	
	updateButtons: function(transactionEditor) {
		let splitEditors = Ext.ComponentQuery.query("spliteditor", transactionEditor);
		let visible = splitEditors.length > 1;
		for (let i = 0; i < splitEditors.length; i++) {
			splitEditors[i].down("button[itemId='removeSplit']").setVisible(visible);
			splitEditors[i].down("button[itemId='addSplit']").setVisible(i == splitEditors.length - 1);
			splitEditors[i].down("tbspacer[itemId='addSpacer']").setVisible(i != splitEditors.length - 1);
		}
	}
});
