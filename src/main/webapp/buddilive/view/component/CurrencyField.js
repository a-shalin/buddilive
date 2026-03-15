Ext.define('BuddiLive.view.component.CurrencyField', {
	extend: "Ext.form.NumberField",
	alias: "widget.currencyfield",

	forcePrecision: false,
	hideTrigger: true,
	keyNavEnabled: false,
	mouseWheelEnabled: false,

	formatText: "0,000.00",

	initComponent: function(){
		var decimalSeparator = BuddiLive.util.UserConfig.get('decimalSeparator') || '.';
		var thousandSeparator = BuddiLive.util.UserConfig.get('thousandSeparator') || ',';

		this.decimalSeparator = decimalSeparator;
		this.thousandSeparator = thousandSeparator;
		this.emptyText = "0" + decimalSeparator + "00";

		Ext.util.Format.decimalSeparator = decimalSeparator;
		Ext.util.Format.thousandSeparator = thousandSeparator;

		this.callParent(arguments);
	},

	parseValue: function(value){
		var me = this;
		if (!isNaN(value)) return value;
		var currencySymbol = BuddiLive.util.UserConfig.get('currencySymbol') || '';
		var thousandSeparator = BuddiLive.util.UserConfig.get('thousandSeparator') || ',';
		var decimalSeparator = BuddiLive.util.UserConfig.get('decimalSeparator') || '.';
		var parsedValue = parseFloat(String(value).split(currencySymbol).join("").split(thousandSeparator).join("").split(decimalSeparator).join("."));
		return isNaN(parsedValue) ? null : parsedValue;
	},

	valueToRaw: function(value) {
		var me = this;
		value = me.parseValue(value);
		if (isNaN(value)){
			return "";
		}
		else {
			return Ext.util.Format.number(value, me.formatText);
		}
	},

	rawToValue: function(raw) {
		var me = this;
		return me.parseValue(raw);
	},

	validate: function(){
		return !isNaN(this.rawToValue(this.getRawValue()));
	},

	getValue: function(){
		var me = this;
		return me.parseValue(me.rawValue);
	}
});
