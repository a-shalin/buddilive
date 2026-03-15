Ext.define('BuddiLive.view.budget.Editor', {
	extend: "Ext.window.Window",
	alias: "widget.budgeteditor",
	requires: [
		"BuddiLive.view.component.CurrencyField",
		"BuddiLive.view.budget.ParentCombobox"
	],
	
	initComponent: function(){
		var s = this.initialConfig.selected
		var editor = this;
		
		this.title = (s ? BuddiLive.translate("EDIT_BUDGET_CATEGORY") : BuddiLive.translate("ADD_BUDGET_CATEGORY"))
		this.layout = "fit";
		this.modal = true;
		this.width = 400;
		this.items = [
			{
				xtype: "form",
				layout: "anchor",
				bodyPadding: 5,
				items: [
					{
						xtype: "hidden",
						itemId: "id",
						value: (s ? s.id : null)
					},
					{
						xtype: "selfdocumentingfield",
						anchor: "100%",
						messageBody: BuddiLive.translate("HELP_BUDGET_CATEGORY_NAME"),
						type: "textfield",
						itemId: "name",
						value: (s ? s.name : null),
						fieldLabel: BuddiLive.translate("BUDGET_CATEGORY_NAME"),
						allowBlank: false,
						enableKeyEvents: true,
						emptyText: BuddiLive.translate("BUDGET_CATEGORY_EXAMPLES"),
						listeners: {
							afterrender: function(field) {
								field.focus(false, 500);
							}
						}
					},
					{
						xtype: "selfdocumentingfield",
						messageBody: BuddiLive.translate("HELP_BUDGET_CATEGORY_PARENT"),
						type: "parentcombobox",
						itemId: "parent",
						fieldLabel: BuddiLive.translate("BUDGET_CATEGORY_PARENT"),
						emptyText: "Parent",
						value: (s ? s.parent : null),
						url: "data/categories/parents.json" + (s ? "?exclude=" + s.id : ""),
						listeners: {
							change: function(){
								var parent = editor.down("parentcombobox[itemId='parent']");
								if (parent.getValue() != null && (parent.getValue() + "").length > 0){
									editor.down("combobox[itemId='periodType']").setValue(parent.getStore().findRecord("value", parent.getValue()).data.periodType);
									editor.down("combobox[itemId='type']").setValue(parent.getStore().findRecord("value", parent.getValue()).data.type);
								}
								editor.down("combobox[itemId='periodType']").setDisabled(parent.getValue() != null && parent.getValue() != "");
								editor.down("combobox[itemId='type']").setDisabled(parent.getValue() != null && parent.getValue() != "");
							}
						}
					},
					{
						xtype: "selfdocumentingfield",
						messageBody: BuddiLive.translate("HELP_BUDGET_CATEGORY_PERIOD_TYPE"),
						type: "combobox",
						itemId: "periodType",
						value: (s ? s.type : "MONTH"),
						hidden: s != null,
						fieldLabel: BuddiLive.translate("BUDGET_CATEGORY_PERIOD_TYPE"),
						editable: false,
						allowBlank: false,
						store: new Ext.data.Store({
							fields: ["text", "value"],
							data: [
								{text: BuddiLive.translate("BUDGET_CATEGORY_TYPE_WEEK"), value: "WEEK"},
								{text: BuddiLive.translate("BUDGET_CATEGORY_TYPE_SEMI_MONTH"), value: "SEMI_MONTH"},
								{text: BuddiLive.translate("BUDGET_CATEGORY_TYPE_MONTH"), value: "MONTH"},
								{text: BuddiLive.translate("BUDGET_CATEGORY_TYPE_QUARTER"), value: "QUARTER"},
								{text: BuddiLive.translate("BUDGET_CATEGORY_TYPE_SEMI_YEAR"), value: "SEMI_YEAR"},
								{text: BuddiLive.translate("BUDGET_CATEGORY_TYPE_YEAR"), value: "YEAR"}
							]
						}),
						queryMode: "local",
						valueField: "value"
					},
					{
						xtype: "selfdocumentingfield",
						messageBody: BuddiLive.translate("HELP_BUDGET_CATEGORY_TYPE"),
						type: "combobox",
						itemId: "type",
						value: (s ? s.categoryType : "E"),
						hidden: s != null,
						fieldLabel: BuddiLive.translate("BUDGET_CATEGORY_TYPE"),
						editable: false,
						allowBlank: false,
						store: new Ext.data.Store({
							fields: ["text", "value"],
							data: [
								{text: BuddiLive.translate("INCOME"), value: "I"},
								{text: BuddiLive.translate("EXPENSE"), value: "E"}
							]
						}),
						queryMode: "local",
						valueField: "value"
					}
				]
			}
		];
		this.buttons = [
			{
				text: BuddiLive.translate("OK"),
				itemId: "ok",
				disabled: true
			},
			{
				text: BuddiLive.translate("CANCEL"),
				itemId: "cancel"
			}
		]
	
		this.callParent(arguments);
	}
});