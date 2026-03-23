Ext.define('BuddiLive.view.preferences.PreferencesEditor', {
	extend: "Ext.window.Window",
	alias: "widget.preferenceseditor",
	requires: [
		"BuddiLive.view.component.CurrenciesCombobox",
		"BuddiLive.view.component.LocalesCombobox"
	],
	
	initComponent: function() {
		let d = this.initialConfig.data
		const separatorOptionLabel = function(symbol, descriptionKey) {
			return symbol + " (" + BuddiLive.translate(descriptionKey) + ")";
		};

		this.title = BuddiLive.translate("PREFERENCES");
		this.layout = "fit";
		this.modal = true;
		this.width = 500;
		this.items = [
			{
				xtype: "form",
				layout: "anchor",
				bodyPadding: 5,
				items: [
					{
						xtype: "selfdocumentingfield",
						messageBody: BuddiLive.translate("HELP_ENCRYPT_DATA"),
						type: "checkbox",
						itemId: "encrypt",
						fieldLabel: " ",
						labelSeparator: "",
						checked: d.encrypt,
						boxLabel: BuddiLive.translate("ENCRYPT_DATA"),
						listeners: {
							change: function(checkbox) {
								checkbox.up("form").down("textfield[itemId='password']").up("selfdocumentingfield").setVisible(d.encrypt != checkbox.getValue());
								checkbox.up("form").down("textfield[itemId='password']").focus(true);
							}
						}
					},
					{
						xtype: "selfdocumentingfield",
						messageBody: BuddiLive.translate("HELP_ENCRYPT_DATA_PASSWORD"),
						type: "textfield",
						inputType: "password", 
						allowBlank: false,
						itemId: "password",
						hidden: true,
						fieldLabel: BuddiLive.translate("PASSWORD")
					},
					{
						xtype: "selfdocumentingfield",
						messageBody: BuddiLive.translate("HELP_USE_TWO_FACTOR"),
						type: "checkbox",
						itemId: "useTwoFactor",
						fieldLabel: " ",
						labelSeparator: "",
						checked: d.useTwoFactor,
						boxLabel: BuddiLive.translate("USE_TWO_FACTOR")
					},
					d.useTwoFactor ? {
						xtype: "selfdocumentingfield",
						messageBody: BuddiLive.translate("HELP_REGENERATE_TWO_FACTOR_BACKUP"),
						type: "button",
						itemId: "regenerateTwoFactorBackup",
						fieldLabel: " ",
						labelSeparator: "",
						checked: d.useTwoFactor,
						text: BuddiLive.translate("REGENERATE_TWO_FACTOR_BACKUP")
					} : { xtype: "hidden" },
					{
						xtype: "selfdocumentingfield",
						messageBody: BuddiLive.translate("HELP_STORE_EMAIL"),
						type: "checkbox",
						itemId: "storeEmail",
						fieldLabel: " ",
						labelSeparator: "",
						checked: d.storeEmail,
						boxLabel: BuddiLive.translate("STORE_EMAIL")
					},
					{
						xtype: "selfdocumentingfield",
						messageBody: BuddiLive.translate("HELP_LOCALE"),
						type: "localescombobox",
						itemId: "locale",
						fieldLabel: BuddiLive.translate("LOCALE"),
						value: d.locale
					},
					{
						xtype: "selfdocumentingfield",
						messageBody: BuddiLive.translate("HELP_CURRENCY"),
						type: "currenciescombobox",
						itemId: "currency",
						fieldLabel: BuddiLive.translate("CURRENCY"),
						value: d.currency
					},
					{
						xtype: "checkbox",
						itemId: "showCurrencySymbol",
						fieldLabel: " ",
						labelSeparator: "",
						checked: d.showCurrencySymbol,
						boxLabel: BuddiLive.translate("SHOW_CURRENCY_SYMBOL")
					},
					{
						xtype: "checkbox",
						itemId: "currencyAfter",
						fieldLabel: " ",
						labelSeparator: "",
						checked: d.currencyAfter,
						boxLabel: BuddiLive.translate("SHOW_CURRENCY_SYMBOL_AFTER_AMOUNT")
					},
					{
						xtype: "checkbox",
						itemId: "currencySpacing",
						fieldLabel: " ",
						labelSeparator: "",
						checked: d.currencySpacing,
						boxLabel: BuddiLive.translate("SPACE_BETWEEN_CURRENCY_AND_AMOUNT")
					},
					{
						xtype: "selfdocumentingfield",
						anchor: "100%",
						messageBody: BuddiLive.translate("HELP_DECIMAL_SEPARATOR"),
						type: "combobox",
						itemId: "decimalSeparator",
						fieldLabel: BuddiLive.translate("DECIMAL_SEPARATOR"),
						editable: false,
						forceSelection: true,
						value: d.decimalSeparator || "",
						store: new Ext.data.Store({
							fields: ["text", "value"],
							data: [
								{text: BuddiLive.translate("USE_LOCALE_DEFAULTS"), value: ""},
								{text: separatorOptionLabel(".", "SEPARATOR_DOT"), value: "."},
								{text: separatorOptionLabel(",", "SEPARATOR_COMMA"), value: ","}
							]
						}),
						queryMode: "local",
						valueField: "value"
					},
					{
						xtype: "selfdocumentingfield",
						anchor: "100%",
						messageBody: BuddiLive.translate("HELP_THOUSAND_SEPARATOR"),
						type: "combobox",
						itemId: "thousandSeparator",
						fieldLabel: BuddiLive.translate("THOUSAND_SEPARATOR"),
						editable: false,
						forceSelection: true,
						value: d.thousandSeparator || "",
						store: new Ext.data.Store({
							fields: ["text", "value"],
							data: [
								{text: BuddiLive.translate("USE_LOCALE_DEFAULTS"), value: ""},
								{text: separatorOptionLabel(",", "SEPARATOR_COMMA"), value: ","},
								{text: separatorOptionLabel(".", "SEPARATOR_DOT"), value: "."},
								{text: separatorOptionLabel(" ", "SEPARATOR_SPACE"), value: " "},
								{text: separatorOptionLabel("'", "SEPARATOR_APOSTROPHE"), value: "'"}
							]
						}),
						queryMode: "local",
						valueField: "value"
					},
					{
						xtype: "selfdocumentingfield",
						anchor: "100%",
						messageBody: BuddiLive.translate("HELP_NEGATIVE_FORMAT"),
						type: "combobox",
						itemId: "negativeFormat",
						fieldLabel: BuddiLive.translate("NEGATIVE_FORMAT"),
						editable: false,
						forceSelection: true,
						value: d.negativeFormat || "N",
						store: new Ext.data.Store({
							fields: ["text", "value"],
							data: [
								{text: BuddiLive.translate("NEGATIVE_FORMAT_SIGN"), value: "N"},
								{text: BuddiLive.translate("NEGATIVE_FORMAT_BRACKETS"), value: "B"}
							]
						}),
						queryMode: "local",
						valueField: "value"
					},
 					{
						xtype: "selfdocumentingfield",
						messageBody: BuddiLive.translate("HELP_DATE_FORMAT"),
						type: "combobox",
						itemId: "dateFormat",
						fieldLabel: BuddiLive.translate("DATE_FORMAT"),
						editable: false,
						value: d.dateFormat && d.dateFormat.length > 0 ? d.dateFormat : "",
						forceSelection: true,
						store: new Ext.data.Store({
							fields: ["text", "value"],
							data: (function() {
								let now = new Date();
								return [
									{text: BuddiLive.translate("USE_LOCALE_DEFAULTS"), value: ""},
									{text: Ext.Date.format(now, "Y-m-d"), value: "yyyy-MM-dd"},
									{text: Ext.Date.format(now, "m/d/Y"), value: "MM/dd/yyyy"},
									{text: Ext.Date.format(now, "d/m/Y"), value: "dd/MM/yyyy"},
									{text: Ext.Date.format(now, "M d, Y"), value: "MMM dd, yyyy"},
									{text: Ext.Date.format(now, "F d, Y"), value: "MMMM dd, yyyy"}
								];
							})()
						}),
						queryMode: "local",
						valueField: "value"
 					},
					{
						xtype: "checkbox",
						itemId: "showDeleted",
						fieldLabel: " ",
						labelSeparator: "",
						checked: d.showDeleted,
						boxLabel: BuddiLive.translate("SHOW_DELETED")
					}
				]
			}
		];
		this.buttons = [
			{
				text: BuddiLive.translate("OK"),
				itemId: "ok"
			},
			{
				text: BuddiLive.translate("CANCEL"),
				itemId: "cancel"
			}
		]
	
		this.callParent(arguments);
	}
});
