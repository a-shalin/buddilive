Ext.define("BuddiLive.util.UserConfig", {
	"singleton": true,

	"config": {},

	"init": function(config) {
		this.config = config || {};
	},

	"get": function(key) {
		return this.config[key];
	}
});
