 Ext.application({
	"extend": "Ext.app.Application",

	"name": "Login",
	"appFolder": "${routerAttachPoint}",

	"views": ["LoginPanelMobile" <#list applicationViews! as view>,"${view?json_string}"</#list> ],
	"controllers": ["LoginController" <#list applicationControllers! as controller>,"${controller?json_string}"</#list>],
	"models": [<#list applicationModels! as model><#if model.first>,</#if>"${model?json_string}"</#list>],

	"mainView": "Login.view.LoginPanelMobile"
});