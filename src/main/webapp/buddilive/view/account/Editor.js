Ext.define('BuddiLive.view.account.Editor', {
	"extend": "Ext.window.Window",
	"alias": "widget.accounteditor",
	"requires": [
		"BuddiLive.view.component.CurrencyField"
	],
	
	"initComponent": function(){
		var s = this.initialConfig.selected

		this.title = (s ? BuddiLive.translate("EDIT_ACCOUNT") : BuddiLive.translate("ADD_ACCOUNT"));
		this.layout = "fit";
		this.modal = true;
		this.width = 400;
		this.items = [
			{
				"xtype": "form",
				"layout": "anchor",
				"bodyPadding": 5,
				"items": [
					{
						"xtype": "hidden",
						"itemId": "id",
						"value": (s ? s.id : null)
					},
					{
						"xtype": "selfdocumentingfield",
						"messageBody": BuddiLive.translate("HELP_ACCOUNT_EDITOR_NAME"),
						"type": "textfield",
						"itemId": "name",
						"value": (s ? s.name : null),
						"fieldLabel": BuddiLive.translate("ACCOUNT_EDITOR_NAME"),
						"allowBlank": false,
						"enableKeyEvents": true,
						"emptyText": BuddiLive.translate("ACCOUNT_EDITOR_NAME_EXAMPLES"),
						"listeners": {
							"afterrender": function(field) {
								field.focus(false, 500);
							}
						}
					},
					{
						"xtype": "selfdocumentingfield",
						"messageBody": BuddiLive.translate("HELP_ACCOUNT_EDITOR_ACCOUNT_TYPE"),
						"type": "textfield",
						"itemId": "accountType",
						"value": (s ? s.accountType : null),
						"fieldLabel": BuddiLive.translate("ACCOUNT_EDITOR_ACCOUNT_TYPE"),
						"allowBlank": false,
						"enableKeyEvents": true,
						"emptyText": BuddiLive.translate("ACCOUNT_EDITOR_ACCOUNT_TYPE_EXAMPLES")
					},
					{
						"xtype": "selfdocumentingfield",
						"messageBody": BuddiLive.translate("HELP_ACCOUNT_EDITOR_TYPE"),
						"type": "combobox",
						"itemId": "type",
						"value": (s ? s.type : "D"),
						"fieldLabel": BuddiLive.translate("ACCOUNT_EDITOR_TYPE"),
						"editable": false,
						"allowBlank": false,
						"store": new Ext.data.Store({
							"fields": ["text", "value"],
							"data": [
								{"text": BuddiLive.translate("DEBIT"), "value": "D"},
								{"text": BuddiLive.translate("CREDIT"), "value": "C"}
							]
						}),
						"queryMode": "local",
						"valueField": "value"
					},
					{
						"xtype": "selfdocumentingfield",
						"messageBody": BuddiLive.translate("HELP_ACCOUNT_EDITOR_STARTING_BALANCE"),
						"type": "currencyfield",
						"itemId": "startBalance",
						"value": (s ? s.startBalance : null),
						"fieldLabel": BuddiLive.translate("ACCOUNT_EDITOR_STARTING_BALANCE")
					}
				
				]
			}
		];
		this.buttons = [
			{
				"text": BuddiLive.translate("OK"),
				"itemId": "ok",
				"disabled": true
			},
			{
				"text": BuddiLive.translate("CANCEL"),
				"itemId": "cancel"
			}
		];
	
		this.callParent(arguments);
	}
});