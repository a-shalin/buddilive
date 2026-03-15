Ext.define('BuddiLive.view.transaction.split.Editor', {
	extend: "Ext.panel.Panel",
	alias: "widget.spliteditor",
	
	requires: [
		"BuddiLive.view.component.CurrencyField",
		"BuddiLive.view.transaction.split.FromCombobox",
		"BuddiLive.view.transaction.split.ToCombobox"
	],
	
	layout: "hbox",
	border: false,
	width: "100%",
	height: 28,
	defaults: {
		padding: "0 0 5 5"
	},
	initComponent: function(){
		var v = this.initialConfig.value ? this.initialConfig.value : {};
		var splitEditor = this;
		
		this.listeners = {
			boxready: function(){
				//Needed when there are multiple splits loaded from setTransaction()
				splitEditor.fireEvent("updateButtons", splitEditor.up("transactioneditor"));
			}
		};
		
		this.items = [
			{
				xtype: "currencyfield",
				itemId: "amount",
				flex: 1,
				forcePrecision: true,
				padding: this.initialConfig.scheduledTransaction ? "0 0 5 0" : "0 0 5 5",
				value: v.amountNumber
			},
			{
				xtype: "fromcombobox",
				itemId: "from",
				flex: 1,
				source: this.initialConfig.source,
				emptyText: BuddiLive.translate("FROM"),
				value: v.fromId
			},
			{xtype: "panel", html: "<img style='padding-top: 3px;' src='img/arrow.png'/>", border: false, width: 25, height: 25},
			{
				xtype: "tocombobox",
				itemId: "to",
				flex: 1,
				source: this.initialConfig.source,
				emptyText: BuddiLive.translate("TO"),
				value: v.toId
			},
			{
				xtype: "textfield",
				itemId: "memo",
				flex: 1,
				emptyText: BuddiLive.translate("MEMO"),
				value: v.memo,
				padding: this.initialConfig.scheduledTransaction ? "1 0 5 5" : "0 0 5 5"
			},
			{
				xtype: "button",
				icon: "img/minus-circle.png",
				itemId: "removeSplit",
				tooltip: BuddiLive.translate("REMOVE_SPLIT"),
				padding: "2 5 2 5",
				margin: this.initialConfig.scheduledTransaction ? "2 0 0 5" : "0 0 0 5",
				hidden: true
			},
			{
				xtype: "tbspacer",
				itemId: "addSpacer",
				width: 28,
				padding: "2 5 2 5",
				margin: this.initialConfig.scheduledTransaction ? "0 0 0 5" : "0 5 0 5"
			},
			{
				xtype: "button",
				icon: "img/plus-circle.png",
				itemId: "addSplit",
				tooltip: BuddiLive.translate("ADD_SPLIT"),
				padding: "2 5 2 5",
				margin: this.initialConfig.scheduledTransaction ? "2 0 0 5" : "0 5 0 5"
			}
		];
		
		this.callParent(arguments);
		
		this.down("fromcombobox").getStore().clearFilter();
		this.down("tocombobox").getStore().clearFilter();
		
		if (this.down("currencyfield").getValue() != "" 
				&& (this.down("fromcombobox").getValue() == null || this.down("tocombobox").getValue() == null)){
			Ext.MessageBox.show({
				title: BuddiLive.translate("INFO_LOADING_DELETED_SOURCE_TITLE"),
				msg: BuddiLive.translate("INFO_LOADING_DELETED_SOURCE"),
				buttons: Ext.MessageBox.OK
			});
		}
	},
	
	getSplit: function(){
		var s = {};
		s.amount = this.down("currencyfield[itemId='amount']").getValue();
		s.fromId = this.down("combo[itemId='from']").getValue();
		s.toId = this.down("combo[itemId='to']").getValue();
		s.memo = this.down("textfield[itemId='memo']").getValue();
		return s;
	}
});