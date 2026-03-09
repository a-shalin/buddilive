<#if applicationLoaderPaths??>
Ext.Loader.setConfig({
	"enabled": true,
	"paths": {
	<#list applicationLoaderPaths!?keys as path>
		"${path!?json_string}": "${applicationLoaderPaths[path]!?json_string}"
	</#list>
	}
});
</#if>

Ext.application({
	"name": "Login",
	"appFolder": "${routerAttachPoint}",
	
	"views": ["LoginPanel" <#list applicationViews! as view>,"${view?json_string}"</#list> ],
	"controllers": ["LoginController" <#list applicationControllers! as controller>,"${controller?json_string}"</#list>],
	"models": [<#list applicationModels! as model><#if model.first>,</#if>"${model?json_string}"</#list>],
	
	"launch": function() {
		Ext.create("Login.view.LoginPanel");
	}
});
