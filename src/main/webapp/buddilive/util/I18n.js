Ext.define("BuddiLive.util.I18n", {
	"singleton": true,

	"translations": {},

	"init": function(translations) {
		this.translations = translations || {};
	},

	"translate": function(key) {
		return this.translations[key] || key;
	}
});

BuddiLive.translate = function(key) {
	return BuddiLive.util.I18n.translate(key);
};
