Ext.define('BuddiLive.view.preferences.Restore', {
	"extend": "Ext.window.Window",
	"alias": "widget.preferenceseditor",
	"requires": [
		
	],
	
	"initComponent": function(){
		var d = this.initialConfig.data

		this.title = BuddiLive.translate("PREFERENCES");
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
						"xtype": "checkbox",
						"itemId": "encrypt",
						"fieldLabel": " ",
						"labelSeparator": "",
						"checked": d.encrypt,
						"boxLabel": BuddiLive.translate("ENCRYPT_DATA"),
						"listeners": {
							"change": function(checkbox){
								checkbox.up("form").down("textfield[itemId='password']").setVisible(d.encrypt != checkbox.getValue());
								checkbox.up("form").down("textfield[itemId='password']").focus(true);
							}
						}
					},
					{
						"xtype": "textfield",
						"inputType": "password", 
						"allowBlank": false,
						"itemId": "password",
						"hidden": true,
						"fieldLabel": BuddiLive.translate("PASSWORD")
					},
					{
						"xtype": "combobox",
						"itemId": "locale",
						"fieldLabel": BuddiLive.translate("LANGUAGE"),
						"editable": false,
						"value": d.locale,
						"forceSelection": true,
						"store": new Ext.data.Store({
							"fields": ["text", "value"],
							"data": [
								{"text": BuddiLive.translate("USE_BROWSER_LOCALE_SETTINGS"), "value": ""},
								{"text": "Deutsch", "value": "de"},
								{"text": "English", "value": "en"},
								{"text": "English (US)", "value": "en_US"},
								{"text": "Español", "value": "es"},
								{"text": "Español (Mexico)", "value": "es_MX"},
								{"text": "Francais", "value": "fr"},
								{"text": "Italiano", "value": "it"},
								{"text": "Nederlands", "value": "nl"},	//Dutch
								{"text": "Norsk", "value": "no"},	//Norwegian
								{"text": "Portugues", "value": "pt"},
								{"text": "Portugues (Brasil)", "value": "pt-BR"},
								{"text": "Svenska", "value": "sv"},	//Swedish
								{"text": "ελληνικά", "value": "el"},	//Greek
								{"text": "עִבְרִית", "value": "he"},	//Hebrew
								{"text": "ру́сский", "value": "ru"},	//Russian
								{"text": "српски", "value": "sr"}	//Serbian
							]
						}),
						"queryMode": "local",
						"valueField": "value"
					},
					{
						"xtype": "combobox",
						"itemId": "dateFormat",
						"fieldLabel": BuddiLive.translate("DATE_FORMAT"),
						"editable": false,
						"value": d.dateFormat,
						"forceSelection": true,
						"store": new Ext.data.Store({
							"fields": ["text", "value"],
							"data": (function(){
								var now = new Date();
								return [
									{"text": Ext.Date.format(now, "Y-m-d"), "value": "yyyy-MM-dd"},
									{"text": Ext.Date.format(now, "m/d/Y"), "value": "MM/dd/yyyy"},
									{"text": Ext.Date.format(now, "d/m/Y"), "value": "dd/MM/yyyy"},
									{"text": Ext.Date.format(now, "M d, Y"), "value": "MMM dd, yyyy"},
									{"text": Ext.Date.format(now, "F d, Y"), "value": "MMMM dd, yyyy"}
								];
							})()
						}),
						"queryMode": "local",
						"valueField": "value"
					},
					{
						"xtype": "combobox",
						"itemId": "currencySymbol",
						"fieldLabel": BuddiLive.translate("CURRENCY_FORMAT"),
						"editable": true,
						"value": d.currencySymbol,
						"store": new Ext.data.Store({
							"fields": ["text"],
							"data": [
								{"text": "$"},
								{"text": "\u20ac"},		//Euro
								{"text": "\u00a3"},		//British Pounds
								{"text": "p."},			//Russian Ruble
								{"text": "\u00a5"},		//Yen
								{"text": "\u20a3"},		//French Franc
								{"text": "SFr"}, 		//Swiss Franc (?)
								{"text": "Rs"}, 		//Indian Rupees
								{"text": "Kr"}, 		//Norwegian
								{"text": "Bs"}, 		//Venezuela
								{"text": "S/."}, 		//Peru
								{"text": "\u20b1"},		//Peso
								{"text": "\u20aa"}, 	//Israel Sheqel 
								{"text": "Mex$"},		//Mexican Peso
								{"text": "R$"},			//Brazilian Real
								{"text": "Ch$"},		//Chilean Peso
								{"text": "C"},			//Costa Rican Colon
								{"text": "Arg$"},		//Argentinan Peso
								{"text": "Kc"}			//Something else; requested by a user
							]
						}),
						"valueField": "text"
					},
					{
						"xtype": "checkbox",
						"itemId": "currencyAfter",
						"fieldLabel": " ",
						"labelSeparator": "",
						"checked": d.currencyAfter,
						"boxLabel": BuddiLive.translate("SHOW_CURRENCY_SYMBOL_AFTER_AMOUNT")
					},
					{
						"xtype": "checkbox",
						"itemId": "showDeleted",
						"fieldLabel": " ",
						"labelSeparator": "",
						"checked": d.showDeleted,
						"boxLabel": BuddiLive.translate("SHOW_DELETED")
					},
					{
						"xtype": "checkbox",
						"itemId": "showCleared",
						"fieldLabel": " ",
						"labelSeparator": "",
						"checked": d.showCleared,
						"boxLabel": BuddiLive.translate("SHOW_CLEARED")
					},
					{
						"xtype": "checkbox",
						"itemId": "showReconciled",
						"fieldLabel": " ",
						"labelSeparator": "",
						"checked": d.showReconciled,
						"boxLabel": BuddiLive.translate("SHOW_RECONCILED")
					}
				]
			}
		];
		this.buttons = [
			{
				"text": BuddiLive.translate("OK"),
				"itemId": "ok"
			},
			{
				"text": BuddiLive.translate("CANCEL"),
				"itemId": "cancel"
			}
		]
	
		this.callParent(arguments);
	}
});