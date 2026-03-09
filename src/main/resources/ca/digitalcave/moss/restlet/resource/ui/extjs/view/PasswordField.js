Ext.define("Login.view.PasswordField", {
	"extend": "Ext.form.FieldContainer",
	"alias": "widget.passwordfield",
	
	"layout": "vbox",
	
	"getValue": function(){
		var password = this.down("textfield[itemId='password']");
		if (password){
			return password.getValue();
		}
		return null;
	},
	
	"isValid": function(){
		return this.down("textfield[itemId='password']").isValid() && this.down("textfield[itemId='confirm']").isValid();
	},
	
	"getErrors": function(){
		return this.errors;
	},
	
	"required": true,
	
	"initComponent": function(){
		this.items = [
			{
				"xtype": "fieldcontainer",
				"layout": "hbox",
				"width": "100%",
				"items": [
					{
						"xtype": "textfield",
						"inputType": "password",
						"name": this.name,
						"itemId": "password",
						"options": this,
						"flex": 1,
						"validators": [
							function(){
								return this.errors == null ? true : this.errors;
							}
						],
						"connection": Ext.create('Ext.data.Connection', {
							"autoAbort": true,
							"method": "POST"
						})
					},
					{
						"xtype": "textfield",
						"inputType": "password",
						"itemId": "confirm",
						"flex": 1,
						"validators": [
							function(){
								var passwordField = this.up("rsgpasswordfield").down("textfield[itemId='password']");
								return passwordField.errors == null ? true : passwordField.errors;
							}
						],
						"margin": "0 0 0 7",
						"submitValue": false
					}
				]
			},
			{
				"xtype": "label",
				"height": 10,
				"itemId": "passwordbar",
				"width": "100%",
				"html": ""
			},
			{
				"xtype": "label",
				"margin": 10,
				"itemId": "passworderrors",
				"html": ""
			}
		];
		
		this.callParent(arguments);
		
		this.down("textfield[itemId='password']").addListener("blur", this.checkPassword);
		this.down("textfield[itemId='password']").addListener("focus", this.checkPassword);
		this.down("textfield[itemId='password']").addListener("change", this.checkPassword);
		this.down("textfield[itemId='confirm']").addListener("blur", this.checkPassword);
		this.down("textfield[itemId='confirm']").addListener("focus", this.checkPassword);
		this.down("textfield[itemId='confirm']").addListener("change", this.checkPassword);
	},
	
	"checkPassword": function(field){
		Ext.Ajax.request({
			"url": "${routerAttachPoint}/checkPassword",
			"params": {
				"identifier": field.up("passwordfield").identifier ? field.up("passwordfield").identifier : field.up("passwordfield").up("form").down("component[name=identifier]") ? field.up("passwordfield").up("form").down("component[name=identifier]").getValue() : "anonymous",
				"secret": field.up("passwordfield").getValue()
			},
			"scope": field.up("passwordfield"),
			"success": function(response){
				var lastCheck = Ext.decode(response.responseText, true);
				if (lastCheck == null) return;
				
				try {
					var passwordField = this.down("textfield[itemId='password']");
					var password = passwordField.getValue();
					var confirmField = this.down("textfield[itemId=confirm]");
					var confirmPassword = confirmField.getValue();
					
					var color;
					var strength = lastCheck.score;
	
					if (password == null || password.length == 0 || strength < 10) color = "#953131";
					else if (strength < 20) color = "#ab5e4a";
					else if (strength < 30) color = "#b17253";
					else if (strength < 40) color = "#b2894f";
					else if (strength < 50) color = "#b18c51";
					else if (strength < 60) color = "#bc9c45";
					else if (strength < 70) color = "#b5b557";
					else if (strength < 80) color = "#8cac4a";
					else if (strength < 90) color = "#74b254";
					else if (strength < 100) color = "#4aa94a";
					else { color = "#26a826"; strength = 100; }
					
					var passwordbar = this.down("label[itemId=passwordbar]");
					passwordbar.setHtml("<div style='width: " + strength + "%; background-color: " + color + ";'>&nbsp;</div>");
					
					//Populate the error text
					var passwordErrors = this.down("label[itemId='passworderrors']");
					
					//Nothing filled in, nothing required
					if (passwordField == null
							|| (!passwordField.initialConfig.required && !password)
							|| (password.length == 0 && confirmPassword.length == 0 && !passwordField.required)) {
						passwordField.errors = null;
						passwordErrors.setHtml("");
						return;
					}
					//Waiting for validation to return
					else if (lastCheck == null) {
						result = "${i18n("PASSWORD_UNVALIDATED")?json_string}";
						passwordField.errors = result;
						passwordErrors.setHtml(result);
						return result;
					}
					//Validation has returned, and is successful.  Clear errors and return true.
					else if (lastCheck.passed && confirmPassword == password) {
						passwordField.errors = null;
						passwordErrors.setHtml("");
						return true;
					}
					
					//There were errors returned.  Describe them in the text.
					var result = "<b>Problems</b>:<br/>";
					if (lastCheck.length === false) result += "${i18n("PASSWORD_LENGTH")?json_string}<br/>";
					if (lastCheck.strength === false) result += "${i18n("PASSWORD_STRENGTH")?json_string}<br/>";
					if (lastCheck.variance === false) result += "${i18n("PASSWORD_VARIANCE")?json_string}<br/>";
					if (lastCheck.classes === false) result += "${i18n("PASSWORD_CLASSES")?json_string}<br/>";
					if (lastCheck.history === false) result += "${i18n("PASSWORD_HISTORY")?json_string}<br/>";
					if (lastCheck.dictionary === false) result += "${i18n("PASSWORD_DICTIONARY")?json_string}<br/>";
					if (lastCheck.pattern === false) result += "${i18n("PASSWORD_PATTERN")?json_string}<br/>";
					if (lastCheck.custom === false) result += "${i18n("PASSWORD_CUSTOM")?json_string}<br/>";
					if (password && confirmPassword != password) result += "${i18n("PASSWORD_CONFIRMATION_MATCH")?json_string}";
					passwordField.errors = result;
					passwordErrors.setHtml(result);
				}
				finally {
					passwordField.validate();
					confirmField.validate();
				}
			}
		});
	}
});