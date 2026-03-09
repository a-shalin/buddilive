Ext.define("Login.controller.LoginController", {
	"extend": "Ext.app.Controller",

	"init": function() {
		this.control({
			"login button[itemId=back]": {
				"click": function(button) {
					button.up('form').up('panel').getLayout().setActiveItem(0);
				}
			},
			"login button[itemId=forward]": {
				"click": function(button) {
					button.up('form').up('panel').getLayout().next();
				}
			},
			//Login - credentials, TOTP, expired password
			"login button[itemId='authenticate']": { "click": this.authenticate },
			"login panel[itemId='authenticate'] form textfield": { "keypress": this.authenticate },
			
			"login button[itemId='passwordExpired']": { "click": this.passwordExpired },
			"login form[itemId='passwordExpired'] textfield": { "keypress": this.passwordExpired },
			
			//TOTP - validation and setup
			"login button[itemId='totpToken']": { "click": this.totpToken },
			"login form[itemId='totpToken'] textfield": { "keypress": this.totpToken },
			
			"login form[itemId='totpSetup']": { "activate": this.totpLoadSecret },
			"login button[itemId='totpLoadSecret']": { "click": this.totpLoadSecret },
			"login button[itemId='totpDisable']": { "click": this.totpDisable },
			
			"login button[itemId='totpSetupVerify']": { "click": this.totpStoreSecret },
			"login form[itemId='totpSetup'] textfield": { "keypress": this.totpStoreSecret },
			
			"login form[itemId='totpBackupCodes']": { "activate": this.loadTotpBackupCodes },
			
			"login button[itemId='totpBackupCodesOk']": { "click": this.reloadPage },
			"login button[itemId='totpBackupCodesPrint']": { "click": this.printBackupCodes },
			
			//Register for a new account - request an activation key, then use it to finish the account setup
			"login button[itemId=register]": { "click": this.register },
			"login form[itemId=register] textfield": { "keypress": this.register },
			
			//Reset forgotten password - request an activation key, then use it to reset
			"login button[itemId=forgotPassword]": { "click": this.forgotPassword },
			"login form[itemId=forgotPassword] textfield": { "keypress": this.forgotPassword },
			"login button[itemId=resetPassword]": { "click": this.resetPassword },
			"login form[itemId=resetPassword] textfield": { "keypress": this.resetPassword },
			
			//Forgot username - submit an email and get sent all associated accounts
			"login button[itemId=forgotUsername]": { "click": this.forgotUsername },
			"login form[itemId=forgotUsername] textfield": { "keypress": this.forgotUsername }
		});
	},

	"authenticate": function(cmp, e) {
		if (!(e.getKey()) || e.getKey() == e.ENTER) {
			var form = cmp.up('form').getForm();
			if (form.isValid() == false) return;
			form.submit({
				"url": "authentication/login",
				"success": function() {
					window.location.reload();
				},
				"failure": function(form, action) {
					var response = action.result;
					if (response && response.next == "passwordExpired") {
						var card = cmp.up("panel[itemId='authenticate']").up('panel').getLayout().setActiveItem("passwordExpired");
						card.down('hiddenfield[name=identifier]').setValue(response.key);
						cmp.up("panel[itemId='authenticate']").up("panel").down("transientlabel[itemId='messagePasswordExpired']").setDisappearingHtml("${i18n("FORCED_PASSWORD_CHANGE_MESSAGE")?json_string}", 30000);
					}
					else if (response && response.next == "totpToken"){
						cmp.up("panel[itemId='authenticate']").up('panel').getLayout().setActiveItem("totpToken");
						cmp.up("panel[itemId='authenticate']").up('panel').down('transientlabel[itemId=messageTwoFactorToken]').setDisappearingHtml("${i18n("TWO_FACTOR_MESSAGE")?json_string}", 30000);
					}
					else if (response && response.next == "totpSetup"){
						cmp.up("panel[itemId='authenticate']").up('panel').getLayout().setActiveItem("totpSetup");
					}
					else {
						cmp.up("panel[itemId='authenticate']").down('transientlabel[itemId=messageLogin1]').setDisappearingHtml("${i18n("INVALID_CREDENTIALS_MESSAGE")?json_string}");
					}
				}
			});
		}
	},
	
	"passwordExpired": function(cmp, e) {
		if (!(e.getKey()) || e.getKey() == e.ENTER) {
			var form = cmp.up('form').getForm();
			if (form.isValid() == false) return;
			form.submit({
				"url": "authentication/passwordExpired",
				"success": function() {
					window.location.reload();
				},
				"failure": function(form, action) {
					cmp.up('form').down('transientlabel[itemId=messagePasswordExpired]').setDisappearingHtml("${i18n("FORCED_PASSWORD_CHANGE_ERROR_MESSAGE")?json_string}");
				}
			});
		}
	},
	
	"totpToken": function(cmp, e) {
		if (!(e.getKey()) || e.getKey() == e.ENTER) {
			var form = cmp.up('form').getForm();
			if (form.isValid() == false) return;
			form.submit({
				"url": "authentication/totpToken",
				"success": function() {
					window.location.reload();
				},
				"failure": function(form, action) {
					var response = action.result;
					
					if (response && response.next == "totpBackupCodesNeeded") {
						cmp.up("panel[itemId='totpToken']").up('panel').getLayout().setActiveItem("totpBackupCodes");
					}
					else if (response && response.next == "passwordExpired") {
						cmp.up("panel[itemId='totpToken']").up('panel').getLayout().setActiveItem("passwordExpired");
					}
					else {
						cmp.up('form').down('transientlabel[itemId=messageTwoFactorToken]').setDisappearingHtml("${i18n("INVALID_TWO_FACTOR_MESSAGE")?json_string}");
					}
				}
			});
		}
	},
	
	"totpLoadSecret": function(component){
		var panel = component.xtype == "form" ? component : component.up('form');
		Ext.Ajax.request({
			"url": "authentication/totpSetup",
			"method": "GET",
			"success": function(response, options){
				var data = Ext.decode(response.responseText, true);
				if (data){
					//Load the QR code and secret key into the UI
					this.down("panel[itemId='qrCodeSecret']").setHtml("<div style='width: 100%;'><img src='" + data["totpSharedSecretQr"] + "' style='display: block; margin-left: auto; margin-right: auto;'></img></div>");
					this.down("textfield[itemId='textSecret']").setValue(data["totpSharedSecret"]);
				}
			},
			"failure": function(form, action){
				//Not sure what we need to do here
				//debugger;
			},
			"scope": panel
		});
	},
	
	"totpDisable": function(component){
		var panel = component.xtype == "form" ? component : component.up('form');
		Ext.Ajax.request({
			"url": "authentication/totpSetup",
			"method": "DELETE",
			"success": function(response, options){
				window.location.reload();
			},
			"failure": function(form, action){
				//Not sure what we need to do here
				window.location.reload();
			},
			"scope": panel
		});
	},
	
	"totpStoreSecret": function(cmp, e) {
		if (!(e.getKey()) || e.getKey() == e.ENTER) {
			var form = cmp.up('form').getForm();
			if (form.isValid() == false) return;
			form.submit({
				"url": "authentication/totpSetup",
				"success": function() {
					cmp.up("panel[itemId='totpSetup']").up('panel').getLayout().setActiveItem("totpBackupCodes");
				},
				"failure": function(form, action) {
					//TODO Not sure what errors can happen here...
					cmp.up('form').down('transientlabel[itemId=messageTwoFactorSetup]').setDisappearingHtml("${i18n("INVALID_TWO_FACTOR_MESSAGE")?json_string}");
				}
			});
		}
	},
	
	"loadTotpBackupCodes": function(panel){
		//Generate and load backup codes on panel load
		Ext.Ajax.request({
			"url": "authentication/generateBackupCodes",
			"method": "POST",
			"success": function(response, options){
				var data = response.responseText;
				if (data){
					//Load the backup codes into the UI
					this.down("textarea[itemId='totpBackupCodes']").setValue(response.responseText);
				}
			},
			"failure": function(form, action){
				//Not sure what we need to do here
				//debugger;
			},
			"scope": panel
		});
	},
	
	"printBackupCodes": function(button){
		var totpBackupCodes = button.up("form").down("textarea[itemId='totpBackupCodes']").getValue();
		var winPrint = window.open();
		winPrint.document.write("<html><head><script type='text/javascript'>setTimeout(function(){window.print();}, 100);</script></head><body><pre>" + totpBackupCodes + "</pre></body></html>");
		winPrint.document.close();
		winPrint.focus();
	},
	
	"reloadPage": function(){
		window.location.reload();
	},
	
	"register": function(cmp, e) {
		if (!(e.getKey()) || e.getKey() == e.ENTER) {
			var form = cmp.up('form').getForm();
			if (form.isValid() == false) return;
			form.submit({
				"url": "authentication/register",
				"success": function() {
					cmp.up('form').up('panel').getLayout().next();
					cmp.up('form').up('panel').down('transientlabel[itemId=messageRegister2]').setDisappearingHtml("${i18n("ACTIVATION_KEY_SENT")?json_string}", 30000);
				},
				"failure": function(form, action) {
					var response = Ext.decode(action.response.responseText, true);
					var message = response && response.message ? response.message : "${i18n("UNKNOWN_ERROR_MESSAGE")?json_string}";
					cmp.up('form').down('transientlabel[itemId=messageRegister1]').setDisappearingHtml(message);
				}
			});
		}
	},
	
	"forgotPassword": function(cmp, e) {
		if (!(e.getKey()) || e.getKey() == e.ENTER) {
			var form = cmp.up('form').getForm();
			if (form.isValid() == false) return;
			form.submit({
				"url": "authentication/forgotPassword",
				"success": function() {
					cmp.up('form').up('panel').getLayout().next();
					cmp.up('form').up('panel').down('transientlabel[itemId=messageForgotPassword2]').setDisappearingHtml("${i18n("ACTIVATION_KEY_SENT")?json_string}", 30000);
				},
				"failure": function(form, action) {
					var response = Ext.decode(action.response.responseText, true);
					var message = response && response.message ? response.message : "${i18n("UNKNOWN_ERROR_MESSAGE")?json_string}";
					cmp.up('form').down('transientlabel[itemId=messageForgotPassword1]').setDisappearingHtml(message);
				}
			});
		}
	},
	
	"resetPassword": function(cmp, e) {
		if (!(e.getKey()) || e.getKey() == e.ENTER) {
			var form = cmp.up('form').getForm();
			if (form.isValid() == false) return;
			form.submit({
				"url": "authentication/resetPassword",
				"success": function() {
					window.location.reload();
				},
				"failure": function(form, action) {
					cmp.up('form').down('transientlabel[itemId=messageForgotPassword2]').setDisappearingHtml("${i18n("UNKNOWN_ERROR_MESSAGE")?json_string}");
				}
			});
		}
	},
	
	"forgotUsername": function(cmp, e) {
		if (!(e.getKey()) || e.getKey() == e.ENTER) {
			var form = cmp.up('form').getForm();
			if (form.isValid() == false) return;
			form.submit({
				"url": "authentication/forgotUsername",
				"success": function() {
					cmp.up('form').up('panel').down('transientlabel[itemId=messageForgotUsername1]').setDisappearingHtml("${i18n("USER_NAMES_SENT")?json_string}", 30000);
				},
				"failure": function(form, action) {
					var response = Ext.decode(action.response.responseText, true);
					var message = response && response.message ? response.message : "${i18n("UNKNOWN_ERROR_MESSAGE")?json_string}";
					cmp.up('form').down('transientlabel[itemId=messageForgotUsername1]').setDisappearingHtml(message);
				}
			});
		}
	}
});
