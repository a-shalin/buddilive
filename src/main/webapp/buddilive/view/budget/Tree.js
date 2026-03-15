Ext.define('BuddiLive.view.budget.Tree', {
	extend: "Ext.tree.Panel",
	alias: "widget.budgettree",
	requires: [
		"BuddiLive.store.budget.TreeStore"
	],
	
	rootVisible: false,
	border: false,
	flex: 1,
	width: "100%",
	viewConfig: {
		stripeRows: true
	},
	plugins: [{
		ptype: "cellediting",
		clicksToEdit: 1
	}],
	
	dockedItems: [{
		xtype: "toolbar",
		dock: "top",
		items: [
			{
				xtype: "button",
				tooltip: BuddiLive.translate("COPY_FROM_PREVIOUS_BUDGET_PERIOD_TOOLTIP"),
				text: BuddiLive.translate("COPY_FROM_PREVIOUS_BUDGET_PERIOD"),
				icon: "img/calendar-import.png",
				itemId: "copyFromPreviousPeriod"
			},
			"->",
			{
				xtype: "label",
				text: BuddiLive.translate("CURRENT_BUDGET_PERIOD")
			},
			" ",
			{
				xtype: "button",
				tooltip: BuddiLive.translate("PREVIOUS_BUDGET_PERIOD"),
				icon: "img/calendar-previous.png",
				itemId: "previousPeriod"
			},
			{
				xtype: "textfield",
				width: 200,
				itemId: "currentPeriod",
				disabled: true,
				disabledCls: "",
				style: "color: black"
			},
			{
				xtype: "button",
				tooltip: BuddiLive.translate("NEXT_BUDGET_PERIOD"),
				icon: "img/calendar-next.png",
				itemId: "nextPeriod"
			}
		]
	}],
	
	initComponent: function() {
		var budgetTree = this;
		this.itemId = this.initialConfig.periodValue;
		this.stateId = "budgettree" + this.initialConfig.periodValue;
		this.store = Ext.create("BuddiLive.store.budget.TreeStore", {periodType: this.initialConfig.periodValue});
		this.title = this.initialConfig.periodText;
		
		var styledRenderer = function(value, metaData, record) {
			metaData.style = record.data[metaData.column.dataIndex + "Style"];
			return value;
		};
		
		this.columns = [
			{
				text: BuddiLive.translate("NAME"),
				dataIndex: "name",
				flex: 2,
				xtype: "treecolumn",
				sortable: false,
				hideable: false,

				renderer: function(value, metaData, record) {
					metaData.style = record.data.nameStyle;
					return value;
				}
			},
			{
				text: BuddiLive.translate("PREVIOUS"),
				dataIndex: "previous",
				flex: 1,
				align: "right",
				sortable: false,
				hideable: false,
				renderer: styledRenderer
			},
			{
				text: BuddiLive.translate("CURRENT"),
				dataIndex: "current",
				flex: 1,
				sortable: false,
				hideable: false,
				align: "right",
				editor: {
					xtype: "currencyfield",
					fieldStyle: "text-align: right;",
					formatText: "0.00",
					listeners: {
						focus: function(component) {
							component.selectText();
						}
					}
				},

				renderer: function(value, metaData, record) {
					metaData.style = record.data.currentStyle;
					if (record.data.currentAmount == 0) {
						return BuddiLive.translate("CLICK_TO_ENTER_BUDGETED_AMOUNT");
					}
					return value;
				}
			},
			{
				text: BuddiLive.translate("ACTUAL_INCOME_EXPENSES"),
				dataIndex: "actual",
				flex: 1,
				sortable: false,
				hideable: false,
				align: "right",
				renderer: styledRenderer
			},
			{
				text: BuddiLive.translate("AMOUNT_REMAINING"),
				dataIndex: "difference",
				flex: 1,
				sortable: false,
				hideable: false,
				align: "right",
				renderer: styledRenderer
			}
		];

		this.callParent(arguments);
	}
});