Ext.define('Login.view.LoginPanel', {
	"extend": "Ext.tab.Panel",
	"alias": "widget.login",
	"requires": [
		"Login.view.TransientLabel",
		"Login.view.PasswordField",
		"Login.view.SelfDocumentingField"
	],
	"border": false,
	"renderTo": "loginform",
	"tabPosition": "top",
	"tabBar": {
		"layout": {
			"pack": "start"
		},
		"defaults": {
			"width": "150px"
		}
	},
	"defaults": {
		"xtype": "panel",
		"layout": "card",
		"border": false,
		"defaults": {
			"xtype": "form",
			"border": false,
			"margin": 10,
			"defaults": {
				"xtype": "textfield",
				"anchor": "100%",
				"allowBlank": false,
				"enableKeyEvents": true
			}
		}
	},

	"initComponent": function() {
		var __ac = window.__authConfig || {};
		var items = [];

		if (__ac.showCookieWarning) {
			this.listeners = {
				"afterrender": function(loginPanel){
					var allowCookiesStorage = Ext.util.LocalStorage.get("allowCookies");
					var allowCookies = allowCookiesStorage.getItem(window.location.href);
					allowCookiesStorage.release();
					if (allowCookies != "true"){
						var showMessage = function(){
							Ext.Msg.show({
								"title": Login.translate("COOKIES_USED_TITLE"),
								"msg": Login.translate("COOKIES_USED_MESSAGE"),
								"buttons": Ext.Msg.YESNO,
								"modal": true,
								"fn": function(buttonId){
									if (buttonId == "yes"){
										var allowCookiesStorage = Ext.util.LocalStorage.get("allowCookies");
										allowCookiesStorage.setItem(window.location.href, "true");
										allowCookiesStorage.release();
									}
									else {
										showMessage();
									}
								}
							});
						};

						Ext.defer(function(){
							showMessage();
						}, 10);
					}
				}
			};
		}

		if (__ac.showLogin !== false) {
			var loginItems = [
				{ "fieldLabel": Login.translate("IDENTIFIER_LABEL"), "name": "identifier", "listeners": { "afterrender": function(component){ component.focus(); } } },
				{ "fieldLabel": Login.translate("PASSWORD_LABEL"), "name": "password", "inputType": "password" }
			];
			if (__ac.showRemember !== false) {
				loginItems.push({ "xtype": "selfdocumentingfield", "messageBody": Login.translate("REMEMBER_HELP"), "fieldLabel": Login.translate("REMEMBER_LABEL"), "type": "checkbox", "name": "remember" });
			}
			if (__ac.showDisableIpLock !== false) {
				loginItems.push({ "xtype": "selfdocumentingfield", "messageBody": Login.translate("DISABLE_IP_LOCK_HELP"), "fieldLabel": Login.translate("DISABLE_IP_LOCK_LABEL"), "type": "checkbox", "name": "disableIpLock" });
			}
			if (__ac.extraLoginStep1Fields) {
				loginItems = loginItems.concat(__ac.extraLoginStep1Fields);
			}
			loginItems.push({ "xtype": "transientlabel", "itemId": "messageLogin1" });

			var passwordExpiredItems = [
				{ "name": "identifier", "xtype": "hidden" },
				{ "fieldLabel": Login.translate("NEW_PASSWORD_LABEL"), "name": "password", "xtype": "passwordfield" }
			];
			if (__ac.extraLoginStep2Fields) {
				passwordExpiredItems = passwordExpiredItems.concat(__ac.extraLoginStep2Fields);
			}
			passwordExpiredItems.push({ "xtype": "transientlabel", "itemId": "messagePasswordExpired" });

			items.push({
				"title": Login.translate("LOGIN_TITLE"),
				"activeItem": __ac.activeItem || "authenticate",
				"items": [
					{
						"itemId": "authenticate",
						"xtype": "panel",
						"items": [
							{
								"xtype": "form",
								"width": "100%",
								"border": false,
								"defaults": {
									"xtype": "textfield",
									"anchor": "100%",
									"allowBlank": false,
									"enableKeyEvents": true
								},
								"items": loginItems,
								"buttons": [
									{ "text": Login.translate("LOGIN_LABEL"), "itemId": "authenticate" }
								]
							}
						]
					},
					{
						"itemId": "passwordExpired",
						"items": passwordExpiredItems,
						"buttons": [
							{ "text": Login.translate("BACK_BUTTON"), "itemId": "back" },
							{ "text": Login.translate("CHANGE_PASSWORD_BUTTON"), "itemId": "passwordExpired" }
						]
					},
					{
						"itemId": "totpToken",
						"items": [
							{ "fieldLabel": Login.translate("TWO_FACTOR_LABEL"), "name": "totpToken" },
							{ "xtype": "transientlabel", "itemId": "messageTwoFactorToken" }
						],
						"buttons": [
							{ "text": Login.translate("BACK_BUTTON"), "itemId": "back" },
							{ "text": Login.translate("SUBMIT"), "itemId": "totpToken" }
						]
					},
					{
						"itemId": "totpSetup",
						"items": [
							{ "xtype": "panel", "itemId": "qrCodeSecret", "height": 350, "border": false},
							{ "xtype": "textfield", "editable": false, "fieldLabel": Login.translate("TWO_FACTOR_SECRET_LABEL"), "itemId": "textSecret", "height": 25, "hidden": true},
							{ "xtype": "button", "text": Login.translate("SHOW_SECRET_BUTTON"), "fieldLabel": " ", "labelSeparator": "", "listeners": {"click": function(button){button.up("component[itemId=totpSetup]").down("component[itemId=textSecret]").setVisible(true); button.setVisible(false);}}},
							{ "xtype": "label", "html": Login.translate("TWO_FACTOR_SETUP_INSTRUCTIONS")},
							{ "fieldLabel": Login.translate("TWO_FACTOR_LABEL"), "name": "totpToken" },
							{ "xtype": "transientlabel", "itemId": "messageTwoFactorSetup" }
						],
						"buttons": [
							{ "text": Login.translate("BACK_BUTTON"), "itemId": "back" },
							{ "text": Login.translate("CANCEL_TOTP"), "itemId": "totpDisable" },
							{ "text": Login.translate("RELOAD"), "itemId": "totpLoadSecret" },
							{ "text": Login.translate("SUBMIT"), "itemId": "totpSetupVerify" }
						]
					},
					{
						"itemId": "totpBackupCodes",
						"items": [
							{ "xtype": "textarea", "itemId": "totpBackupCodes", "height": 350, "border": false},
							{ "xtype": "label", "html": Login.translate("TWO_FACTOR_BACKUP_CODES_INSTRUCTIONS")},
							{ "xtype": "transientlabel", "itemId": "messageTwoFactorBackupCodes" }
						],
						"buttons": [
							{ "text": Login.translate("PRINT"), "itemId": "totpBackupCodesPrint" },
							{ "text": Login.translate("OK"), "itemId": "totpBackupCodesOk" }
						]
					}
				]
			});
		}

		if (__ac.showRegister) {
			var registerItems = [
				{ "fieldLabel": Login.translate("EMAIL_LABEL"), "name": "email", "vtype": "email" }
			];
			if (__ac.extraRegisterStep1Fields) {
				registerItems = registerItems.concat(__ac.extraRegisterStep1Fields);
			}
			registerItems.push({ "xtype": "transientlabel", "itemId": "messageRegister1" });

			var registerStep2Items = [
				{ "fieldLabel": Login.translate("ACTIVATION_KEY_LABEL"), "name": "activationKey" },
				{ "fieldLabel": Login.translate("PASSWORD_LABEL"), "name": "password", "xtype": "passwordfield" }
			];
			if (__ac.extraRegisterStep2Fields) {
				registerStep2Items = registerStep2Items.concat(__ac.extraRegisterStep2Fields);
			}
			registerStep2Items.push({ "xtype": "transientlabel", "itemId": "messageRegister2" });

			items.push({
				"title": Login.translate("REGISTER_TITLE"),
				"items": [
					{
						"itemId": "register",
						"items": registerItems,
						"buttons": [
							{ "text": Login.translate("EXISTING_KEY_BUTTON"), "itemId": "forward" },
							"->",
							{ "text": Login.translate("GENERATE_KEY_BUTTON"), "itemId": "register" }
						]
					},
					{
						"itemId": "resetPassword",
						"items": registerStep2Items,
						"buttons": [
							{ "text": Login.translate("BACK_BUTTON"), "itemId": "back" },
							{ "text": Login.translate("CREATE_ACCOUNT_BUTTON"), "itemId": "resetPassword" }
						]
					}
				]
			});
		}

		if (__ac.showForgotPassword !== false) {
			var forgotItems = [
				{ "fieldLabel": Login.translate("IDENTIFIER_LABEL"), "name": "identifier" }
			];
			if (__ac.extraforgotPasswordStep1PanelFields) {
				forgotItems = forgotItems.concat(__ac.extraforgotPasswordStep1PanelFields);
			}
			forgotItems.push({ "xtype": "transientlabel", "itemId": "messageForgotPassword1" });

			var forgotStep2Items = [
				{ "fieldLabel": Login.translate("ACTIVATION_KEY_LABEL"), "name": "activationKey" },
				{ "fieldLabel": Login.translate("NEW_PASSWORD_LABEL"), "name": "password", "xtype": "passwordfield" }
			];
			if (__ac.extraforgotPasswordStep2PanelFields) {
				forgotStep2Items = forgotStep2Items.concat(__ac.extraforgotPasswordStep2PanelFields);
			}
			forgotStep2Items.push({ "xtype": "transientlabel", "itemId": "messageForgotPassword2" });

			items.push({
				"title": Login.translate("RESET_TITLE"),
				"items": [
					{
						"itemId": "forgotPassword",
						"items": forgotItems,
						"buttons": [
							{ "text": Login.translate("EXISTING_KEY_BUTTON"), "itemId": "forward" },
							"->",
							{ "text": Login.translate("GENERATE_KEY_BUTTON"), "itemId": "forgotPassword" }
						]
					},
					{
						"itemId": "resetPassword",
						"items": forgotStep2Items,
						"buttons": [
							{ "text": Login.translate("BACK_BUTTON"), "itemId": "back" },
							{ "text": Login.translate("RESET_PASSWORD_BUTTON"), "itemId": "resetPassword" }
						]
					}
				]
			});
		}

		if (__ac.showForgotUsername) {
			var forgotUsernameItems = [
				{ "fieldLabel": Login.translate("EMAIL_LABEL"), "name": "email" }
			];
			if (__ac.extraForgotUsernameStep1PanelFields) {
				forgotUsernameItems = forgotUsernameItems.concat(__ac.extraForgotUsernameStep1PanelFields);
			}
			forgotUsernameItems.push({ "xtype": "transientlabel", "itemId": "messageForgotUsername1" });

			items.push({
				"title": Login.translate("FORGOT_USERNAME_TITLE"),
				"items": [
					{
						"itemId": "forgotUsername",
						"items": forgotUsernameItems,
						"buttons": [
							"->",
							{ "text": Login.translate("SUBMIT"), "itemId": "forgotUsername" }
						]
					}
				]
			});
		}

		this.items = items;
		this.callParent(arguments);
	}
});
