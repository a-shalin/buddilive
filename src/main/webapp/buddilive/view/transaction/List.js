Ext.define('BuddiLive.view.transaction.List', {
	extend: "Ext.grid.Panel",
	alias: "widget.transactionlist",
	requires: [
		"BuddiLive.store.transaction.ListStore"
	],
	
	initComponent: function() {
		let transactionList = this;
		this.layout = "fit";
		this.store = Ext.create("BuddiLive.store.transaction.ListStore");
		this.border = false;
		this.stateId = "transactionlist";
		this.stateful = true;
		this.disabled = true;
		this.viewConfig = {stripeRows: true};
		this.plugins = [{ptype: "bufferedrenderer"}];
		this.features = [
			{
				ftype: "rowbody",

				getAdditionalData: function(data, rowIndex, record, orig) {
					let rowBody = "";
					let s = record.data.splits;
					let headerCt = this.view.headerCt, colspan = headerCt.getColumnCount();
					for (let i = 0; i < s.length; i++) {
						rowBody += "<div style='padding: 2px; height: 20px; width: 100%;'>"
								+ "<span style='display: inline-block; width: 23%;'></span>"
								+ "<span style='display: inline-block; width: 26%;'><i>" + s[i].from + " &rarr; " + s[i].to + "</i></span>" 
								+ "<span style='display: inline-block; text-align: right; width: 15%; " + s[i].amountStyle + "'>" + (s[i].amountInDebitColumn ? s[i].amount : "") + "</span>" 
								+ "<span style='display: inline-block; text-align: right; width: 15%; " + s[i].amountStyle + "'>" + (!s[i].amountInDebitColumn ? s[i].amount : "") + "</span>" 
								+ "<span style='display: inline-block; text-align: right; width: 20%; " + s[i].balanceStyle + "'>" + s[i].balance + "</span>"
								+ "</div>";
					}
					return {
						rowBody: rowBody,
						rowBodyCls: "",
						rowBodyColspan: colspan
					};
				}
			}
		];
		
		this.columns = [
			{
				text: BuddiLive.translate("DATE"),
				dataIndex: "date",
				hideable: false,
				sortable: false,
				flex: 20
			},
			{
				text: BuddiLive.translate("DESCRIPTION"),
				dataIndex: "description",
				hideable: false,
				sortable: false,
				flex: 30,

				renderer: function(value, metadata, record) {
					return "<b>" + value + "</b>";
				}
			},
			{
				text: BuddiLive.translate("AMOUNT_FROM"),
				hideable: false,
				sortable: false,
				flex: 15,
				align: "right"
			},
			{
				text: BuddiLive.translate("AMOUNT_TO"),
				hideable: false,
				sortable: false,
				flex: 15,
				align: "right"
			},
			{
				text: BuddiLive.translate("BALANCE"),
				hideable: false,
				sortable: false,
				flex: 20,
				align: "right"
			}
		];
		
		this.dockedItems = [
			{
				xtype: "toolbar",
				dock: "bottom",
				items: [
					"->",
					{
						xtype: "textfield",
						width: 200,
						itemId: "search",
						emptyText: BuddiLive.translate("SEARCH")
					}
				]
			},
			{
				xtype: "transactioneditor",
				dock: "top"
			}
		];
		
		this.callParent(arguments);
		
		this.getStore().addListener("load", function(store, records) {
			//We start the transaction list disabled, for now.  Unsure if this will stay.
			transactionList.enable();
		});
	},
	
	reload: function() {
		const scrollable = this.getView().getScrollable();
		if (scrollable) {
			scrollable.scrollTo(0, 0);
		}
		this.getStore().load();
	}
});