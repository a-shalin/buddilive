Ext.define("BuddiLive.controller.preferences.PreferencesEditor", {
	"extend": "Ext.app.Controller",
	"stores": [
		"preferences.CurrenciesComboboxStore",
		"preferences.LocalesComboboxStore"
	],
	"onLaunch": function(){
		this.getPreferencesCurrenciesComboboxStoreStore().load();
		this.getPreferencesLocalesComboboxStoreStore().load();
	},

	"init": function() {
		this.control({
			"preferenceseditor button[itemId='regenerateTwoFactorBackup']": { "click": this.regenerateTwoFactorBackup },
			"preferenceseditor button[itemId='ok']": {"click": this.ok},
			"preferenceseditor button[itemId='cancel']": {"click": this.cancel},
			"preferenceseditor combobox[itemId='currency']": {"change": this.updateCurrencySymbolLabel},
			"preferenceseditor combobox[itemId='locale']": {"change": this.updateCurrencySymbolLabel},
			"preferenceseditor": {"show": this.updateCurrencySymbolLabel}
		});
	},
	
	"regenerateTwoFactorBackup": function(component){
		Ext.Ajax.request({
			"url": "data/userpreferences",
			"headers": {
				"Accept": "application/json"
			},
			"method": "POST",
			"jsonData": {
				"action": "invalidatetotpbackups"
			},
			"success": function(response){
				window.location.reload();
			},
			"failure": function(response){
				BuddiLive.app.error(response);
			}
		});
	},
	
	"cancel": function(component){
		component.up("preferenceseditor").close();
	},
	
	"ok": function(component){
		var window = component.up("preferenceseditor");
		var panel = window.initialConfig.panel;
		var originalData = window.initialConfig.data;

		if (window.down("checkbox[itemId='encrypt']").getValue() != originalData.encrypt && window.down("textfield[itemId='password']").getValue().length == 0){
			Ext.MessageBox.show({
				"title": BuddiLive.translate("INVALID"),
				"msg": BuddiLive.translate("ENTER_PASSWORD_TO_CHANGE_ENCRYPTION"),
				"buttons": Ext.MessageBox.OK
			});
			return;
		}

		var request = {"action": "update"};
		request.encrypt = window.down("checkbox[itemId='encrypt']").getValue();
		request.encryptPassword = window.down("textfield[itemId='password']").getValue();
		request.useTwoFactor = window.down("checkbox[itemId='useTwoFactor']").getValue();
		request.storeEmail = window.down("checkbox[itemId='storeEmail']").getValue();
		request.locale = window.down("combobox[itemId='locale']").getValue();
		request.currency = window.down("combobox[itemId='currency']").getValue();
		request.showCurrencySymbol = window.down("checkbox[itemId='showCurrencySymbol']").getValue();
		request.currencyAfter = window.down("checkbox[itemId='currencyAfter']").getValue();
		request.currencySpacing = window.down("checkbox[itemId='currencySpacing']").getValue();
		request.decimalSeparator = window.down("combobox[itemId='decimalSeparator']").getValue();
		request.thousandSeparator = window.down("combobox[itemId='thousandSeparator']").getValue();
		request.negativeFormat = window.down("combobox[itemId='negativeFormat']").getValue();
		var dateFormat = window.down("combobox[itemId='dateFormat']").getValue();
		request.dateFormat = dateFormat ? dateFormat : "";
		request.showDeleted = window.down("checkbox[itemId='showDeleted']").getValue();

		var mask = new Ext.LoadMask({"msg": BuddiLive.translate("PROCESSING"), "target": window});
		mask.show();
		
		Ext.Ajax.request({
			"url": "data/userpreferences",
			"headers": {
				"Accept": "application/json"
			},
			"method": "POST",
			"jsonData": request,
			"success": function(response){
				mask.hide();
				window.close();
				panel.reload();
			},
			"failure": function(response){
				mask.hide();
				BuddiLive.app.error(response);
			}
		});
	},

	"getCurrencySymbol": function(currencyCode, localeCode){
		if (!currencyCode) return "$";
		try {
			var locale = (localeCode || "en_US").replace("_", "-");
			var parts = new Intl.NumberFormat(locale, {"style": "currency", "currency": currencyCode}).formatToParts(1);
			for (var i = 0; i < parts.length; i++){
				if (parts[i].type == "currency") return parts[i].value;
			}
		}
		catch (e){}
		return currencyCode;
	},

	"updateCurrencySymbolLabel": function(component){
		var window = (component.xtype == "preferenceseditor" ? component : component.up("preferenceseditor"));
		if (!window) return;
		var checkbox = window.down("checkbox[itemId='showCurrencySymbol']");
		var currencyCode = window.down("combobox[itemId='currency']").getValue();
		var localeCode = window.down("combobox[itemId='locale']").getValue();
		var symbol = this.getCurrencySymbol(currencyCode, localeCode);
		checkbox.setBoxLabel(BuddiLive.translate("SHOW_CURRENCY_SYMBOL") + " (" + symbol + ")");
	}
});
