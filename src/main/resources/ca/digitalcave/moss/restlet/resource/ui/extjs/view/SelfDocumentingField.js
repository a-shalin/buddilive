Ext.define('Login.view.SelfDocumentingField', {
	extend: "Ext.form.FieldContainer",
	alias: "widget.selfdocumentingfield",

	initComponent: function() {
		this.layout = "hbox";
		this.childItemId = this.itemId;
		var component = Ext.applyIf({
			xtype: this.initialConfig.type,
			flex: 1
		}, this.initialConfig);
		delete component.hidden;
		delete component.type;
		delete component.fieldLabel;
		delete this.itemId;
		delete this.listeners;
		delete this.disabled;

		var messageTitle = (this.initialConfig.messageTitle ? this.initialConfig.messageTitle : Login.translate("WHAT_IS_THIS"));
		var messageBody = this.initialConfig.messageBody;

		this.items = [
			component,
			{
				xtype: "button",
				icon: "img/question.png",
				margin: "1 0 0 5",
				tooltip: (this.initialConfig.helpButtonTooltip ? this.initialConfig.helpButtonTooltip : Login.translate("WHAT_IS_THIS")),
				tabIndex: -1,
				listeners: {
					click: function() {
						Ext.MessageBox.show({
							title: messageTitle,
							msg: messageBody,
							buttons: Ext.MessageBox.OK
						});
					}
				}
			}
		];

		this.callParent(arguments);
	}
});
