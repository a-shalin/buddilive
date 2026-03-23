Ext.define('BuddiLive.view.component.CurrencyField', {
	extend: "Ext.form.NumberField",
	alias: "widget.currencyfield",

	forcePrecision: false,
	hideTrigger: true,
	keyNavEnabled: false,
	mouseWheelEnabled: false,

	formatText: "0,000.00",

	initComponent: function() {
		let decimalSeparator = BuddiLive.util.UserConfig.get('decimalSeparator') || '.';
		let thousandSeparator = BuddiLive.util.UserConfig.get('thousandSeparator') || ',';

		this.decimalSeparator = decimalSeparator;
		this.thousandSeparator = thousandSeparator;
		this.emptyText = "0" + decimalSeparator + "00";

		Ext.util.Format.decimalSeparator = decimalSeparator;
		Ext.util.Format.thousandSeparator = thousandSeparator;

		this.callParent(arguments);
	},

	parseValue: function(value) {
		let me = this;
		if (!isNaN(value)) return value;
		let currencySymbol = BuddiLive.util.UserConfig.get('currencySymbol') || '';
		let thousandSeparator = BuddiLive.util.UserConfig.get('thousandSeparator') || ',';
		let decimalSeparator = BuddiLive.util.UserConfig.get('decimalSeparator') || '.';
		let parsedValue = parseFloat(String(value).split(currencySymbol).join("").split(thousandSeparator).join("").split(decimalSeparator).join("."));
		return isNaN(parsedValue) ? null : parsedValue;
	},

	valueToRaw: function(value) {
		let me = this;
		value = me.parseValue(value);
		if (isNaN(value)) {
			return "";
		}
		else {
			return Ext.util.Format.number(value, me.formatText);
		}
	},

	rawToValue: function(raw) {
		let me = this;
		return me.parseValue(raw);
	},

	validate: function() {
		return !isNaN(this.rawToValue(this.getRawValue()));
	},

	getValue: function() {
		let me = this;
		return me.parseValue(me.rawValue);
	}
});
