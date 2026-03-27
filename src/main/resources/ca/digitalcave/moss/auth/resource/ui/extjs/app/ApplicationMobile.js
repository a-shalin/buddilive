let __ac = window.__authConfig || {};
const assetVersion = window.__assetVersion || "0";
Ext.manifest = Ext.manifest || {};
Ext.manifest.loader = Ext.manifest.loader || {};
Ext.manifest.loader.cache = assetVersion;
Ext.manifest.loader.cacheParam = "v";

let loaderPaths = {Login: __ac.routerAttachPoint || "authentication"};
if (__ac.applicationLoaderPaths) {
	for (let key in __ac.applicationLoaderPaths) {
		loaderPaths[key] = __ac.applicationLoaderPaths[key];
	}
}
Ext.Loader.setConfig({
	enabled: true,
	disableCaching: false,
	disableCachingParam: "v",
	paths: loaderPaths
});

Ext.require(["Login.util.I18n"], function() {
	Login.util.I18n.init(window.__authI18n);

	let views = ["LoginPanelMobile"];
	let controllers = ["LoginController"];
	let models = [];

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
		extend: "Ext.app.Application",
		name: "Login",
		appFolder: __ac.routerAttachPoint || "authentication",
		views: views,
		controllers: controllers,
		models: models,
		mainView: "Login.view.LoginPanelMobile"
	});
});
