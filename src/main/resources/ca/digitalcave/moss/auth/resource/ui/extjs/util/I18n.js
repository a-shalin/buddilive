Ext.define("Login.util.I18n", {
	singleton: true,

	translations: {},

	init: function(translations) {
		this.translations = translations || {};
	},

	translate: function(key) {
		return this.translations[key] || key;
	}
});

Login.translate = function(key) {
	return Login.util.I18n.translate(key);
};
