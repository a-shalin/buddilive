var __ac = window.__authConfig || {};

var loaderPaths = {"Login": __ac.routerAttachPoint || "authentication"};
if (__ac.applicationLoaderPaths) {
	for (var key in __ac.applicationLoaderPaths) {
		loaderPaths[key] = __ac.applicationLoaderPaths[key];
	}
}
Ext.Loader.setConfig({
	"enabled": true,
	"paths": loaderPaths
});

Ext.require(["Login.util.I18n"], function(){
	Login.util.I18n.init(window.__authI18n);

	var views = ["LoginPanel"];
	var controllers = ["LoginController"];
	var models = [];

	if (__ac.applicationViews) {
		views = views.concat(__ac.applicationViews);
	}
	if (__ac.applicationControllers) {
		controllers = controllers.concat(__ac.applicationControllers);
	}
	if (__ac.applicationModels) {
		models = models.concat(__ac.applicationModels);
	}

	Ext.application({
		"name": "Login",
		"appFolder": __ac.routerAttachPoint || "authentication",

		"views": views,
		"controllers": controllers,
		"models": models,

		"launch": function() {
			Ext.create("Login.view.LoginPanel");
		}
	});
});
