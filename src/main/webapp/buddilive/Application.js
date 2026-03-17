Ext.Loader.setConfig({
	enabled: true,
	disableCaching: true,
	paths: {
		BuddiLive: "buddilive",
		Login: "authentication"
	}
});

Ext.state.Manager.setProvider(Ext.supports.LocalStorage ? new Ext.state.LocalStorageProvider() : new Ext.state.CookieProvider());

Ext.require(["BuddiLive.util.I18n", "BuddiLive.util.UserConfig", "Login.util.I18n"], function() {
	BuddiLive.util.I18n.init(window.__buddiI18n);
	BuddiLive.util.UserConfig.init(window.__buddiConfig);
	Login.util.I18n.init(window.__buddiI18n);
	Login.translate = function(key) { return Login.util.I18n.translate(key); };

	const sessionLogoutLeadMillis = 1000 * 60 * 2;
	let sessionLogoutTimeout = null;
	let sessionLogoutTriggered = false;

	const triggerSessionLogout = function() {
		if (sessionLogoutTriggered) {
			return;
		}
		sessionLogoutTriggered = true;
		if (sessionLogoutTimeout != null) {
			clearTimeout(sessionLogoutTimeout);
			sessionLogoutTimeout = null;
		}
		window.location.href = "authentication/logout";
	};

	const scheduleSessionLogout = function() {
		if (sessionLogoutTriggered) {
			return;
		}
		const sessionTimeoutMillis = BuddiLive.util.UserConfig.get("sessionTimeoutMillis");
		const sessionRefreshWindowMillis = BuddiLive.util.UserConfig.get("sessionRefreshWindowMillis");
		if (!Ext.isNumber(sessionTimeoutMillis) || !Ext.isNumber(sessionRefreshWindowMillis)) {
			return;
		}

		// The server only refreshes cookie expiry on a cadence, so we subtract that window to stay ahead of true expiry.
		const delayMillis = sessionTimeoutMillis - sessionRefreshWindowMillis - sessionLogoutLeadMillis;
		if (sessionLogoutTimeout != null) {
			clearTimeout(sessionLogoutTimeout);
			sessionLogoutTimeout = null;
		}

		if (delayMillis <= 0) {
			triggerSessionLogout();
			return;
		}
		sessionLogoutTimeout = setTimeout(triggerSessionLogout, delayMillis);
	};

	Ext.application({
		name: "BuddiLive",
		appFolder: "buddilive",

		requires: [
			"BuddiLive.view.Viewport"
		],

		controllers: [
			"Reports",
			"Viewport",
			"account.Tree",
			"account.Editor",
			"budget.Editor",
			"budget.Panel",
			"budget.Tree",
			"scheduled.Editor",
			"scheduled.List",
			"preferences.PreferencesEditor",
			"preferences.ChangePasswordEditor",
			"restore.Form",
			"transaction.List",
			"transaction.Editor",
			"transaction.split.Editor"
		],

		launch: function() {
			let viewport = Ext.create("BuddiLive.view.Viewport");
			BuddiLive.app = this;
			BuddiLive.app.viewport = viewport;
			Ext.Ajax.on("requestcomplete", function() {
				scheduleSessionLogout();
			});
			Ext.Ajax.on("requestexception", function(conn, response) {
				if (response != null && response.status === 401) {
					triggerSessionLogout();
					return;
				}
				scheduleSessionLogout();
			});
			scheduleSessionLogout();

			Ext.EventManager.addListener(Ext.getBody(), 'keydown', function(e) {
				if (e.getTarget().type != 'text' && e.getKey() == '8' ) {
					e.preventDefault();
				}
			});

			Ext.util.TaskManager.start({
				interval: 1000 * 60 * 60,

				run: function() {
					let conn = Ext.create("Ext.data.Connection");
					conn.request({
						url: "data/scheduledtransactions/execute",
						method: "POST",
						jsonData: Ext.Date.format(new Date(), "Y-m-d"),

						success: function(response) {
							let messages = Ext.decode(response.responseText, true);
							if (messages != null && messages.messages != null) {
								if (messages.messages.length > 0) {
									Ext.MessageBox.show({
										title: BuddiLive.translate("SCHEDULED_TRANSACTION_MESSAGES"),
										msg: messages.messages,
										buttons: Ext.Msg.OK
									});
								}
								BuddiLive.app.controllers.get("transaction.Editor").getTransactionDescriptionComboboxStoreStore().load();
								BuddiLive.app.viewport.down("panel[itemId='myAccounts']").down("accounttree").getStore().reload();
								if (BuddiLive.app.viewport.down("transactionlist").getStore().getCount() > 0) {
									BuddiLive.app.viewport.down("transactionlist").reload();
								}
							}
						}
					});
				}
			});
		},

		error: function(error) {
			let message;
			let title;
			if (Ext.isString(error)) {
				message = error;
				title = "Error";
			}
			else if (error.responseText != null) {
				title = (error.statusText ? error.statusText : BuddiLive.translate("ERROR"));
				let json = Ext.decode(error.responseText, true);
				if (json == null) {
					message = error.responseText;
				}
				else {
					message = json.msg;
				}
			}
			else {
				message = BuddiLive.translate("ERROR_UNKNOWN");
				title = BuddiLive.translate("ERROR");
			}

			Ext.MessageBox.show({
				title: title,
				msg: message,
				buttons: Ext.Msg.OK
			});
		}
	});

	Ext.override(Ext.form.DateField, {
		format: BuddiLive.util.UserConfig.get('extDateFormat') || 'Y-m-d'
	});
});
