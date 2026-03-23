let __ac = window.__authConfig || {};

let loaderPaths = {Login: __ac.routerAttachPoint || "authentication"};
if (__ac.applicationLoaderPaths) {
	for (let key in __ac.applicationLoaderPaths) {
		loaderPaths[key] = __ac.applicationLoaderPaths[key];
	}
}
Ext.Loader.setConfig({
	enabled: true,
	paths: loaderPaths
});

let __requires = ["Login.util.I18n"];
if (__ac.applicationRequires) {
	__requires = __requires.concat(__ac.applicationRequires);
}

Ext.require(__requires, function() {
	Login.util.I18n.init(window.__authI18n);

	let views = ["LoginPanel"];
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
		name: "Login",
		appFolder: __ac.routerAttachPoint || "authentication",

		views: views,
		controllers: controllers,
		models: models,

		launch: function() {
			Ext.create("Login.view.LoginPanel");
		}
	});
});
