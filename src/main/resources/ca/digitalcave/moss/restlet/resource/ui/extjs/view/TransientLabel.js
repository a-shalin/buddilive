Ext.define("Login.view.TransientLabel", {
	"extend": "Ext.form.Label",
	"alias": "widget.transientlabel",
	"text": "\xA0",	//non breaking space to reserve space for text and not collapse empty label
	
	"setDisappearingHtml": function(value, timeout){
		var label = this;
		label.setHtml(value);
		if (!timeout){
			timeout = 10000;	//Default to 10 seconds
		}
		Ext.defer(function(){
			label.setHtml("\xA0");
		}, timeout);
	}
});