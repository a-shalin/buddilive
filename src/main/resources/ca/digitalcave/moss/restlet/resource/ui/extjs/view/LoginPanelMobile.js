Ext.define('Login.view.LoginPanelMobile', {
	"extend": "Ext.tab.Panel",
	"xtype": "login",
	
	"requires": [
		"Login.view.PasswordField",
		"Login.view.TransientLabel"
		${applicationRequires!}
	],

	"title": "${i18n("FORM_TITLE")?json_string}",
	"tabPosition": "${tabPosition!"bottom"?json_string}",
	"height": "100%",
	"minTabWidth": 120,
	"cls": "login-tab-panel",
	"tabBar": {
		"cls": "login-tab-bar",		//Allows making the login buttons larger
		<#if tabBarBackgroundInvisible!false>
		"style": {
			"background-image": "none",
			"background-color": "transparent",
			"border": "none"
		},
		</#if>
		"layout": {
			"pack": "${tabPackAlignment!"start"?json_string}"
		}
	},
	"defaults": {
		"xtype": "panel",
		"layout": "card", 
		"defaults": {
			"xtype": "form",
			"border": false, 
			"margin": 10,
			"defaults": { 
				"xtype": "textfield", 
				"anchor": "100%", 
				"labelWidth": 120,
				"labelAlign": "top",
				"labelStyle": "font-size: 1.2em",
				"allowBlank": false, 
				"enableKeyEvents": true,
				"scale": "medium"
			}
		}
	},
	

<#if showCookieWarning!false>
	"listeners": {
		"afterrender": function(){
			var url = window.location.href;
			var allowCookiesStorage = Ext.util.LocalStorage.get("allowCookies");
			var allowCookies = allowCookiesStorage.getItem(url);
			allowCookiesStorage.release();
			if (allowCookies != "true"){
				var showMessage = function(){
					var window = Ext.create({
						"xtype": "panel",
						"modal": true,
						"floating": true,
						"width": "90%",
						"itemId": "cookieMessage",
						"title": "${i18n("COOKIES_USED_TITLE")?json_string}",
						"items": [
							{
								"xtype": "panel",
								"html": "${i18n("COOKIES_USED_MESSAGE")?json_string}",
								"buttons": [
									{
										"text": "Yes",
										"listeners": {
											"afterrender": function(button){
												button.focus();
											},
											"click": function(button){
												var allowCookiesStorage = Ext.util.LocalStorage.get("allowCookies");
												allowCookiesStorage.setItem(url, "true");
												allowCookiesStorage.release();
												
												button.up("panel[itemId=cookieMessage]").close();
											}
										}
									},
									{ "text": "No" }
								]
							}
						]
					});
					window.show();
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
								{ "fieldLabel": "${i18n("IDENTIFIER_LABEL")?json_string}", "name": "identifier", "inputAttrTpl": "autocapitalize='off'", "listeners": { "afterrender": function(component){ component.focus(); } } },
								{ "fieldLabel": "${i18n("PASSWORD_LABEL")?json_string}", "inputType": "password", "inputAttrTpl": "autocapitalize='off'", "name": "password" },
								<#if showRemember!true>
								{ "fieldLabel": "${i18n("REMEMBER_LABEL")?json_string}", "xtype": "checkbox", "name": "remember" },
								</#if>
								<#if extraLoginStep1Fields??><@extraLoginStep1Fields/></#if>
								{ "xtype": "transientlabel", "itemId": "messageLogin1", "height": 40 },
								{ "xtype": "label", "height": 100},
								{ "xtype": "button", "text": "${i18n("LOGIN_LABEL")?json_string}", "itemId": "authenticate" },
								{ "xtype": "label", "height": 50, "html": "&nbsp;", "style": {"display": "block"}},
								{ "xtype": "label", "html": "<a href='.?desktop' style='color: #666; align: right;'>Desktop View</a>", "style": {"display": "block"}}
							]
						},
						{
							"xtype": "panel",
							"border": false,
							"itemId": "ssoProviders",
							"items": [
								<#list ssoProviders as key, value>
								{"xtype": "button", "text": "${i18n("SAML_LOGIN_LABEL")?json_string} ${value}", "ssoProviderId": "${key}", "width": "100%", "margin": "5px"},
								</#list>
							]
						}
					]
				},
				{
					"itemId": "passwordExpired",
					"items": [
						{ "name": "identifier", "xtype": "hiddenfield" },
						{ "fieldLabel": "${i18n("NEW_PASSWORD_LABEL")?json_string}", "name": "password", "xtype": "passwordfield" },
						<#if extraLoginStep2Fields??><@extraLoginStep2Fields/></#if>
						{ "xtype": "transientlabel", "itemId": "messageActivate" },
						{ "xtype": "button", "text": "${i18n("CHANGE_PASSWORD_BUTTON")?json_string}", "itemId": "activate" },
						{ "xtype": "label", "height": 20, "html": "&nbsp;", "style": {"display": "block"}},
						{ "xtype": "button", "text": "${i18n("BACK_BUTTON")?json_string}", "itemId": "back" }
					]
				},
				{
					"itemId": "totpToken",
					"items": [
						{ "fieldLabel": "${i18n("TWO_FACTOR_LABEL")?json_string}", "name": "totpToken" },
						{ "xtype": "transientlabel", "itemId": "messageTwoFactorToken" },
						{ "xtype": "button", "text": "${i18n("SUBMIT")?json_string}", "itemId": "totpToken" },
						{ "xtype": "label", "height": 20, "html": "&nbsp;", "style": {"display": "block"}},
						{ "xtype": "button", "text": "${i18n("BACK_BUTTON")?json_string}", "itemId": "back" }
					]
				},
				{
					"itemId": "twoFactorSetup",
					"items": [
						{ "xtype": "textarea", "editable": false, "name": "2faSecret", "fieldLabel": "${i18n("TWO_FACTOR_SECRET_LABEL")?json_string}", "itemId": "textSecret", "height": 25},
						{ "xtype": "label", "html": "${i18n("TWO_FACTOR_SETUP_INSTRUCTIONS_MOBILE")?json_string}"},
						{ "fieldLabel": "${i18n("TWO_FACTOR_LABEL")?json_string}", "name": "totpToken" },
						{ "xtype": "transientlabel", "itemId": "messageTwoFactorSetup" },
						{ "xtype": "button", "text": "${i18n("RELOAD")?json_string}", "itemId": "totpLoadSecret" },
						{ "xtype": "label", "height": 20, "html": "&nbsp;", "style": {"display": "block"}},
						{ "xtype": "button", "text": "${i18n("SUBMIT")?json_string}", "itemId": "totpSetupVerify" },
						{ "xtype": "label", "height": 20, "html": "&nbsp;", "style": {"display": "block"}},
						{ "xtype": "button", "text": "${i18n("BACK_BUTTON")?json_string}", "itemId": "back" }

					]
				},
				{
					"itemId": "totpBackupCodes",
					"items": [
						{ "xtype": "textarea", "itemId": "totpBackupCodes", "height": 350, "border": false},
						{ "xtype": "label", "html": "${i18n("TWO_FACTOR_BACKUP_CODES_INSTRUCTIONS")?json_string}"},
						{ "xtype": "transientlabel", "itemId": "messageTwoFactorBackupCodes" },
						{ "xtype": "button", "text": "${i18n("OK")?json_string}", "itemId": "totpBackupCodesOk" }
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
						{ "fieldLabel": "${i18n("IDENTIFIER_LABEL")?json_string}", "name": "email", "inputAttrTpl": "autocapitalize='off'", "vtype": "email" },
						<#if extraRegisterStep1Fields??><@extraRegisterStep1Fields/></#if>
						{ "xtype": "transientlabel", "itemId": "messageRegister1" },
						{ "xtype": "button", "text": "${i18n("EXISTING_KEY_BUTTON")?json_string}", "itemId": "forward" },
						{ "xtype": "label", "height": 20, "html": "&nbsp;", "style": {"display": "block"}},
						{ "xtype": "button", "text": "${i18n("GENERATE_KEY_BUTTON")?json_string}", "itemId": "register" }
					]
				},
				{
					"itemId": "activate",
					"items": [
						{ "fieldLabel": "${i18n("ACTIVATION_KEY_LABEL")?json_string}", "inputAttrTpl": "autocapitalize='off'", "name": "identifier" },
						{ "fieldLabel": "${i18n("PASSWORD_LABEL")?json_string}", "name": "secret", "xtype": "passwordfield" },
						<#if extraRegisterStep2Fields??><@extraRegisterStep2Fields/></#if>
						{ "xtype": "transientlabel", "itemId": "messageRegister2" },
						{ "xtype": "button", "text": "${i18n("BACK_BUTTON")?json_string}", "itemId": "back" },
						{ "xtype": "label", "height": 20, "html": "&nbsp;", "style": {"display": "block"}},
						{ "xtype": "button", "text": "${i18n("CREATE_ACCOUNT_BUTTON")?json_string}", "itemId": "activate" }
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
						{ "fieldLabel": "${i18n("IDENTIFIER_LABEL")?json_string}", "inputAttrTpl": "autocapitalize='off'", "name": "identifier" },
						<#if extraResetStep1PanelFields??><@extraResetStep1PanelFields/></#if>
						{ "xtype": "transientlabel", "itemId": "messageForgotPassword1" },
						{ "xtype": "button", "text": "${i18n("GENERATE_KEY_BUTTON")?json_string}", "itemId": "forgotPassword" },
						{ "xtype": "label", "height": 20, "html": "&nbsp;", "style": {"display": "block"}},
						{ "xtype": "button", "text": "${i18n("EXISTING_KEY_BUTTON")?json_string}", "itemId": "forward" }
					]
				},
				{
					"itemId": "resetPassword",
					"items": [
						{ "fieldLabel": "${i18n("ACTIVATION_KEY_LABEL")?json_string}", "inputAttrTpl": "autocapitalize='off'", "name": "activationKey" },
						{ "fieldLabel": "${i18n("NEW_PASSWORD_LABEL")?json_string}", "name": "password", "xtype": "passwordfield" },
						<#if extraResetStep2PanelFields??><@extraResetStep2PanelFields/></#if>
						{ "xtype": "transientlabel", "itemId": "messageForgotPassword2" },
						{ "xtype": "button", "text": "${i18n("RESET_PASSWORD_BUTTON")?json_string}", "itemId": "resetPassword" },
						{ "xtype": "label", "height": 20, "html": "&nbsp;", "style": {"display": "block"}},
						{ "xtype": "button", "text": "${i18n("BACK_BUTTON")?json_string}", "itemId": "back" }
					]
				}
			]
		}
</#if>
<#if showForgotUsername!true>
		,{
			"title": "${i18n("FORGOT_USERNAME_TITLE")?json_string}",
			"items": [
				{
					"itemId": "forgotUsername",
					"items": [
						{ "fieldLabel": "${i18n("EMAIL_LABEL")?json_string}", "inputAttrTpl": "autocapitalize='off'", "name": "email" },
						<#if extraForgotUsernameStep1PanelFields??><@extraForgotUsernameStep1PanelFields/></#if>
						{ "xtype": "transientlabel", "itemId": "messageForgotUsername1" },
						{ "xtype": "button", "text": "${i18n("SUBMIT")?json_string}", "itemId": "forgotUsername" }
					]
				}
			]
		}
</#if>
	]
});
