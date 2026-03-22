Ext.define("BuddiLive.util.I18n", {
	singleton: true,

	translations: {},

	constructor: function() {
		this.translations = window.__buddiI18n || {};
	},

	init: function(translations) {
		this.translations = translations || {};
	},

	translate: function(key) {
		return this.translations[key] || key;
	}
});

BuddiLive.translate = function(key) {
	return BuddiLive.util.I18n.translate(key);
};
