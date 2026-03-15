Ext.define('Login.view.LoginPanelMobile', {
	extend: "Ext.tab.Panel",
	xtype: "login",

	requires: [
		"Login.view.PasswordField",
		"Login.view.TransientLabel"
	],

	height: "100%",
	minTabWidth: 120,
	cls: "login-tab-panel",
	defaults: {
		xtype: "panel",
		layout: "card",
		defaults: {
			xtype: "form",
			border: false,
			margin: 10,
			defaults: {
				xtype: "textfield",
				anchor: "100%",
				labelWidth: 120,
				labelAlign: "top",
				labelStyle: "font-size: 1.2em",
				allowBlank: false,
				enableKeyEvents: true,
				scale: "medium"
			}
		}
	},

	initComponent: function() {
		let __ac = window.__authConfig || {};
		let items = [];

		this.title = Login.translate("FORM_TITLE");
		this.tabPosition = __ac.tabPosition || "bottom";

		let tabBarConfig = {
			cls: "login-tab-bar",
			layout: {
				pack: __ac.tabPackAlignment || "start"
			}
		};
		if (__ac.tabBarBackgroundInvisible) {
			tabBarConfig.style = {
				"background-image": "none",
				"background-color": "transparent",
				border: "none"
			};
		}
		this.tabBar = tabBarConfig;

		if (__ac.showCookieWarning) {
			this.listeners = {
				afterrender: function() {
					let url = window.location.href;
					let allowCookiesStorage = Ext.util.LocalStorage.get("allowCookies");
					let allowCookies = allowCookiesStorage.getItem(url);
					allowCookiesStorage.release();
					if (allowCookies != "true") {
						let showMessage = function() {
							let win = Ext.create({
								xtype: "panel",
								modal: true,
								floating: true,
								width: "90%",
								itemId: "cookieMessage",
								title: Login.translate("COOKIES_USED_TITLE"),
								items: [
									{
										xtype: "panel",
										html: Login.translate("COOKIES_USED_MESSAGE"),
										buttons: [
											{
												text: "Yes",
												listeners: {
													afterrender: function(button) {
														button.focus();
													},

													click: function(button) {
														let allowCookiesStorage = Ext.util.LocalStorage.get("allowCookies");
														allowCookiesStorage.setItem(url, "true");
														allowCookiesStorage.release();

														button.up("panel[itemId=cookieMessage]").close();
													}
												}
											},
											{ text: "No" }
										]
									}
								]
							});
							win.show();
						};

						Ext.defer(function() {
							showMessage();
						}, 10);
					}
				}
			};
		}

		if (__ac.showLogin !== false) {
			let loginFormItems = [
				{ fieldLabel: Login.translate("IDENTIFIER_LABEL"), name: "identifier", inputAttrTpl: "autocapitalize='off'", listeners: { afterrender: function(component) { component.focus(); } } },
				{ fieldLabel: Login.translate("PASSWORD_LABEL"), inputType: "password", inputAttrTpl: "autocapitalize='off'", name: "password" }
			];
			if (__ac.showRemember !== false) {
				loginFormItems.push({ fieldLabel: Login.translate("REMEMBER_LABEL"), xtype: "checkbox", name: "remember" });
			}
			if (__ac.extraLoginStep1Fields) {
				loginFormItems = loginFormItems.concat(__ac.extraLoginStep1Fields);
			}
			loginFormItems.push(
				{ xtype: "transientlabel", itemId: "messageLogin1", height: 40 },
				{ xtype: "label", height: 100},
				{ xtype: "button", text: Login.translate("LOGIN_LABEL"), itemId: "authenticate" },
				{ xtype: "label", height: 50, html: "&nbsp;", style: {display: "block"}},
				{ xtype: "label", html: "<a href='.?desktop' style='color: #666; align: right;'>Desktop View</a>", style: {display: "block"}}
			);

			let ssoItems = [];
			if (__ac.ssoProviders) {
				for (let key in __ac.ssoProviders) {
					ssoItems.push({xtype: "button", text: Login.translate("SAML_LOGIN_LABEL") + " " + __ac.ssoProviders[key], ssoProviderId: key, width: "100%", margin: "5px"});
				}
			}

			let passwordExpiredItems = [
				{ name: "identifier", xtype: "hiddenfield" },
				{ fieldLabel: Login.translate("NEW_PASSWORD_LABEL"), name: "password", xtype: "passwordfield" }
			];
			if (__ac.extraLoginStep2Fields) {
				passwordExpiredItems = passwordExpiredItems.concat(__ac.extraLoginStep2Fields);
			}
			passwordExpiredItems.push(
				{ xtype: "transientlabel", itemId: "messageActivate" },
				{ xtype: "button", text: Login.translate("CHANGE_PASSWORD_BUTTON"), itemId: "activate" },
				{ xtype: "label", height: 20, html: "&nbsp;", style: {display: "block"}},
				{ xtype: "button", text: Login.translate("BACK_BUTTON"), itemId: "back" }
			);

			items.push({
				title: Login.translate("LOGIN_TITLE"),
				activeItem: __ac.activeItem || "authenticate",
				items: [
					{
						itemId: "authenticate",
						xtype: "panel",
						items: [
							{
								xtype: "form",
								width: "100%",
								border: false,
								defaults: {
									xtype: "textfield",
									anchor: "100%",
									allowBlank: false,
									enableKeyEvents: true
								},
								items: loginFormItems
							},
							{
								xtype: "panel",
								border: false,
								itemId: "ssoProviders",
								items: ssoItems
							}
						]
					},
					{
						itemId: "passwordExpired",
						items: passwordExpiredItems
					},
					{
						itemId: "totpToken",
						items: [
							{ fieldLabel: Login.translate("TWO_FACTOR_LABEL"), name: "totpToken" },
							{ xtype: "transientlabel", itemId: "messageTwoFactorToken" },
							{ xtype: "button", text: Login.translate("SUBMIT"), itemId: "totpToken" },
							{ xtype: "label", height: 20, html: "&nbsp;", style: {display: "block"}},
							{ xtype: "button", text: Login.translate("BACK_BUTTON"), itemId: "back" }
						]
					},
					{
						itemId: "twoFactorSetup",
						items: [
							{ xtype: "textarea", editable: false, name: "2faSecret", fieldLabel: Login.translate("TWO_FACTOR_SECRET_LABEL"), itemId: "textSecret", height: 25},
							{ xtype: "label", html: Login.translate("TWO_FACTOR_SETUP_INSTRUCTIONS_MOBILE")},
							{ fieldLabel: Login.translate("TWO_FACTOR_LABEL"), name: "totpToken" },
							{ xtype: "transientlabel", itemId: "messageTwoFactorSetup" },
							{ xtype: "button", text: Login.translate("RELOAD"), itemId: "totpLoadSecret" },
							{ xtype: "label", height: 20, html: "&nbsp;", style: {display: "block"}},
							{ xtype: "button", text: Login.translate("SUBMIT"), itemId: "totpSetupVerify" },
							{ xtype: "label", height: 20, html: "&nbsp;", style: {display: "block"}},
							{ xtype: "button", text: Login.translate("BACK_BUTTON"), itemId: "back" }
						]
					},
					{
						itemId: "totpBackupCodes",
						items: [
							{ xtype: "textarea", itemId: "totpBackupCodes", height: 350, border: false},
							{ xtype: "label", html: Login.translate("TWO_FACTOR_BACKUP_CODES_INSTRUCTIONS")},
							{ xtype: "transientlabel", itemId: "messageTwoFactorBackupCodes" },
							{ xtype: "button", text: Login.translate("OK"), itemId: "totpBackupCodesOk" }
						]
					}
				]
			});
		}

		if (__ac.showRegister) {
			let registerItems = [
				{ fieldLabel: Login.translate("IDENTIFIER_LABEL"), name: "email", inputAttrTpl: "autocapitalize='off'", vtype: "email" }
			];
			if (__ac.extraRegisterStep1Fields) {
				registerItems = registerItems.concat(__ac.extraRegisterStep1Fields);
			}
			registerItems.push(
				{ xtype: "transientlabel", itemId: "messageRegister1" },
				{ xtype: "button", text: Login.translate("EXISTING_KEY_BUTTON"), itemId: "forward" },
				{ xtype: "label", height: 20, html: "&nbsp;", style: {display: "block"}},
				{ xtype: "button", text: Login.translate("GENERATE_KEY_BUTTON"), itemId: "register" }
			);

			let activateItems = [
				{ fieldLabel: Login.translate("ACTIVATION_KEY_LABEL"), inputAttrTpl: "autocapitalize='off'", name: "identifier" },
				{ fieldLabel: Login.translate("PASSWORD_LABEL"), name: "secret", xtype: "passwordfield" }
			];
			if (__ac.extraRegisterStep2Fields) {
				activateItems = activateItems.concat(__ac.extraRegisterStep2Fields);
			}
			activateItems.push(
				{ xtype: "transientlabel", itemId: "messageRegister2" },
				{ xtype: "button", text: Login.translate("BACK_BUTTON"), itemId: "back" },
				{ xtype: "label", height: 20, html: "&nbsp;", style: {display: "block"}},
				{ xtype: "button", text: Login.translate("CREATE_ACCOUNT_BUTTON"), itemId: "activate" }
			);

			items.push({
				title: Login.translate("REGISTER_TITLE"),
				items: [
					{
						itemId: "register",
						items: registerItems
					},
					{
						itemId: "activate",
						items: activateItems
					}
				]
			});
		}

		if (__ac.showForgotPassword !== false) {
			let forgotItems = [
				{ fieldLabel: Login.translate("IDENTIFIER_LABEL"), inputAttrTpl: "autocapitalize='off'", name: "identifier" }
			];
			if (__ac.extraforgotPasswordStep1PanelFields) {
				forgotItems = forgotItems.concat(__ac.extraforgotPasswordStep1PanelFields);
			}
			forgotItems.push(
				{ xtype: "transientlabel", itemId: "messageForgotPassword1" },
				{ xtype: "button", text: Login.translate("GENERATE_KEY_BUTTON"), itemId: "forgotPassword" },
				{ xtype: "label", height: 20, html: "&nbsp;", style: {display: "block"}},
				{ xtype: "button", text: Login.translate("EXISTING_KEY_BUTTON"), itemId: "forward" }
			);

			let resetItems = [
				{ fieldLabel: Login.translate("ACTIVATION_KEY_LABEL"), inputAttrTpl: "autocapitalize='off'", name: "activationKey" },
				{ fieldLabel: Login.translate("NEW_PASSWORD_LABEL"), name: "password", xtype: "passwordfield" }
			];
			if (__ac.extraforgotPasswordStep2PanelFields) {
				resetItems = resetItems.concat(__ac.extraforgotPasswordStep2PanelFields);
			}
			resetItems.push(
				{ xtype: "transientlabel", itemId: "messageForgotPassword2" },
				{ xtype: "button", text: Login.translate("RESET_PASSWORD_BUTTON"), itemId: "resetPassword" },
				{ xtype: "label", height: 20, html: "&nbsp;", style: {display: "block"}},
				{ xtype: "button", text: Login.translate("BACK_BUTTON"), itemId: "back" }
			);

			items.push({
				title: Login.translate("RESET_TITLE"),
				items: [
					{
						itemId: "forgotPassword",
						items: forgotItems
					},
					{
						itemId: "resetPassword",
						items: resetItems
					}
				]
			});
		}

		if (__ac.showForgotUsername) {
			let forgotUsernameItems = [
				{ fieldLabel: Login.translate("EMAIL_LABEL"), inputAttrTpl: "autocapitalize='off'", name: "email" }
			];
			if (__ac.extraForgotUsernameStep1PanelFields) {
				forgotUsernameItems = forgotUsernameItems.concat(__ac.extraForgotUsernameStep1PanelFields);
			}
			forgotUsernameItems.push(
				{ xtype: "transientlabel", itemId: "messageForgotUsername1" },
				{ xtype: "button", text: Login.translate("SUBMIT"), itemId: "forgotUsername" }
			);

			items.push({
				title: Login.translate("FORGOT_USERNAME_TITLE"),
				items: [
					{
						itemId: "forgotUsername",
						items: forgotUsernameItems
					}
				]
			});
		}

		this.items = items;
		this.callParent(arguments);
	}
});
