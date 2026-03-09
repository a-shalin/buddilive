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

<#if showCookieWarning!false>
	"listeners": {
		"afterrender": function(loginPanel){
			var allowCookiesStorage = Ext.util.LocalStorage.get("allowCookies");
			var allowCookies = allowCookiesStorage.getItem(window.location.href);
			allowCookiesStorage.release();
			if (allowCookies != "true"){
				var showMessage = function(){
					Ext.Msg.show({
						"title": "${i18n("COOKIES_USED_TITLE")?json_string}",
						"msg": "${i18n("COOKIES_USED_MESSAGE")?json_string}",
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
	},
</#if>

	"items": [
<#if showLogin!true>
		{
			"title": "${i18n("LOGIN_TITLE")?json_string}",
			"activeItem": "${activeItem}",
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
							"items": [
								{ "fieldLabel": "${i18n("IDENTIFIER_LABEL")?json_string}", "name": "identifier", "listeners": { "afterrender": function(component){ component.focus(); } } },
								{ "fieldLabel": "${i18n("PASSWORD_LABEL")?json_string}", "name": "password", "inputType": "password" },
								<#if showRemember!true>
								{ "xtype": "selfdocumentingfield", "messageBody": "${i18n("REMEMBER_HELP")?json_string}", "fieldLabel": "${i18n("REMEMBER_LABEL")?json_string}", "type": "checkbox", "name": "remember" },
								</#if>
								<#if showDisableIpLock!true>
								{ "xtype": "selfdocumentingfield", "messageBody": "${i18n("DISABLE_IP_LOCK_HELP")?json_string}", "fieldLabel": "${i18n("DISABLE_IP_LOCK_LABEL")?json_string}", "type": "checkbox", "name": "disableIpLock" },
								</#if>
								<#if extraLoginStep1Fields??><@extraLoginStep1Fields/></#if>
								{ "xtype": "transientlabel", "itemId": "messageLogin1" }
							],
							"buttons": [
								{ "text": "${i18n("LOGIN_LABEL")?json_string}", "itemId": "authenticate" }
							]
						}
					]
				},
				{
					"itemId": "passwordExpired",
					"items": [
						{ "name": "identifier", "xtype": "hidden" },
						{ "fieldLabel": "${i18n("NEW_PASSWORD_LABEL")?json_string}", "name": "password", "xtype": "passwordfield" },
						<#if extraLoginStep2Fields??><@extraLoginStep2Fields/></#if>
						{ "xtype": "transientlabel", "itemId": "messagePasswordExpired" }
					],
					"buttons": [
						{ "text": "${i18n("BACK_BUTTON")?json_string}", "itemId": "back" },
						{ "text": "${i18n("CHANGE_PASSWORD_BUTTON")?json_string}", "itemId": "passwordExpired" }
					]
				},
				{
					"itemId": "totpToken",
					"items": [
						{ "fieldLabel": "${i18n("TWO_FACTOR_LABEL")?json_string}", "name": "totpToken" },
						{ "xtype": "transientlabel", "itemId": "messageTwoFactorToken" }
					],
					"buttons": [
						{ "text": "${i18n("BACK_BUTTON")?json_string}", "itemId": "back" },
						{ "text": "${i18n("SUBMIT")?json_string}", "itemId": "totpToken" }
					]
				},
				{
					"itemId": "totpSetup",
					"items": [
						{ "xtype": "panel", "itemId": "qrCodeSecret", "height": 350, "border": false},
						{ "xtype": "textfield", "editable": false, "fieldLabel": "${i18n("TWO_FACTOR_SECRET_LABEL")?json_string}", "itemId": "textSecret", "height": 25, "hidden": true},
						{ "xtype": "button", "text": "${i18n("SHOW_SECRET_BUTTON")?json_string}", "fieldLabel": " ", "labelSeparator": "", "listeners": {"click": function(button){button.up("component[itemId=totpSetup]").down("component[itemId=textSecret]").setVisible(true); button.setVisible(false);}}},
						{ "xtype": "label", "html": "${i18n("TWO_FACTOR_SETUP_INSTRUCTIONS")?json_string}"},
						{ "fieldLabel": "${i18n("TWO_FACTOR_LABEL")?json_string}", "name": "totpToken" },
						{ "xtype": "transientlabel", "itemId": "messageTwoFactorSetup" }
					],
					"buttons": [
						{ "text": "${i18n("BACK_BUTTON")?json_string}", "itemId": "back" },
						{ "text": "${i18n("CANCEL_TOTP")?json_string}", "itemId": "totpDisable" },
						{ "text": "${i18n("RELOAD")?json_string}", "itemId": "totpLoadSecret" },
						{ "text": "${i18n("SUBMIT")?json_string}", "itemId": "totpSetupVerify" }
					]
				},
				{
					"itemId": "totpBackupCodes",
					"items": [
						{ "xtype": "textarea", "itemId": "totpBackupCodes", "height": 350, "border": false},
						{ "xtype": "label", "html": "${i18n("TWO_FACTOR_BACKUP_CODES_INSTRUCTIONS")?json_string}"},
						{ "xtype": "transientlabel", "itemId": "messageTwoFactorBackupCodes" }
					],
					"buttons": [
						{ "text": "${i18n("PRINT")?json_string}", "itemId": "totpBackupCodesPrint" },
						{ "text": "${i18n("OK")?json_string}", "itemId": "totpBackupCodesOk" }
					]
				}
			]
		}
</#if>
<#if showRegister!false>
		,{
			"title": "${i18n("REGISTER_TITLE")?json_string}",
			"items": [
				{
					"itemId": "register",
					"items": [
						{ "fieldLabel": "${i18n("EMAIL_LABEL")?json_string}", "name": "email", "vtype": "email" },
						<#if extraRegisterStep1Fields??><@extraRegisterStep1Fields/></#if>
						{ "xtype": "transientlabel", "itemId": "messageRegister1" }
						
					],
					"buttons": [
						{ "text": "${i18n("EXISTING_KEY_BUTTON")?json_string}", "itemId": "forward" },
						"->",
						{ "text": "${i18n("GENERATE_KEY_BUTTON")?json_string}", "itemId": "register" }
					]
				},
				{
					"itemId": "resetPassword",
					"items": [
						{ "fieldLabel": "${i18n("ACTIVATION_KEY_LABEL")?json_string}", "name": "activationKey" },
						{ "fieldLabel": "${i18n("PASSWORD_LABEL")?json_string}", "name": "password", "xtype": "passwordfield" },
						<#if extraRegisterStep2Fields??><@extraRegisterStep2Fields/></#if>
						{ "xtype": "transientlabel", "itemId": "messageRegister2" }
					],
					"buttons": [
						{ "text": "${i18n("BACK_BUTTON")?json_string}", "itemId": "back" },
						{ "text": "${i18n("CREATE_ACCOUNT_BUTTON")?json_string}", "itemId": "resetPassword" }
					]
				}
			]
		}
</#if>
<#if showForgotPassword!true>
		,{
			"title": "${i18n("RESET_TITLE")?json_string}",
			"items": [
				{
					"itemId": "forgotPassword",
					"items": [
						{ "fieldLabel": "${i18n("IDENTIFIER_LABEL")?json_string}", "name": "identifier" },
						<#if extraForgotPasswordStep1PanelFields??><@extraResetStep1PanelFields/></#if>
						{ "xtype": "transientlabel", "itemId": "messageForgotPassword1" }
					],
					"buttons": [
						{ "text": "${i18n("EXISTING_KEY_BUTTON")?json_string}", "itemId": "forward" },
						"->",
						{ "text": "${i18n("GENERATE_KEY_BUTTON")?json_string}", "itemId": "forgotPassword" }
					]
				},
				{
					"itemId": "resetPassword",
					"items": [
						{ "fieldLabel": "${i18n("ACTIVATION_KEY_LABEL")?json_string}", "name": "activationKey" },
						{ "fieldLabel": "${i18n("NEW_PASSWORD_LABEL")?json_string}", "name": "password", "xtype": "passwordfield" },
						<#if extraForgotPasswordStep2PanelFields??><@extraResetStep2PanelFields/></#if>
						{ "xtype": "transientlabel", "itemId": "messageForgotPassword2" }
					],
					"buttons": [
						{ "text": "${i18n("BACK_BUTTON")?json_string}", "itemId": "back" },
						{ "text": "${i18n("RESET_PASSWORD_BUTTON")?json_string}", "itemId": "resetPassword" }
					]
				}
			]
		}
</#if>
<#if showForgotUsername!false>
		,{
			"title": "${i18n("FORGOT_USERNAME_TITLE")?json_string}",
			"items": [
				{
					"itemId": "forgotUsername",
					"items": [
						{ "fieldLabel": "${i18n("EMAIL_LABEL")?json_string}", "name": "email" },
						<#if extraForgotUsernameStep1PanelFields??><@extraForgotUsernameStep1PanelFields/></#if>
						{ "xtype": "transientlabel", "itemId": "messageForgotUsername1" }
					],
					"buttons": [
						"->",
						{ "text": "${i18n("SUBMIT")?json_string}", "itemId": "forgotUsername" }
					]
				}
			]
		}
</#if>
	]
});
