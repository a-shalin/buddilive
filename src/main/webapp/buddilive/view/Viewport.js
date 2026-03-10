Ext.define("BuddiLive.view.Viewport", {
	"extend": "Ext.container.Viewport",
	"alias": "widget.buddiviewport",

	"requires": [
		"BuddiLive.view.account.Tree",
		"BuddiLive.view.budget.Panel",
		"BuddiLive.view.budget.Tree",
		"Login.view.SelfDocumentingField",
		"BuddiLive.view.restore.Form",
		"BuddiLive.view.preferences.PreferencesEditor",
		"BuddiLive.view.preferences.ChangePasswordEditor",
		"BuddiLive.view.scheduled.List",
		"BuddiLive.view.transaction.List",
		"BuddiLive.view.transaction.Editor"
	],

	"layout": "border",
	"height": "100%",
	"width": "100%",

	"initComponent": function() {
		var isPremium = BuddiLive.util.UserConfig.get('premium');
		var isEncrypted = BuddiLive.util.UserConfig.get('encrypted');

		var northHtml = isPremium ? "" : "<iframe id='adsensetop' src='buddilive/view/ads/top.html' scrolling='no' width='468' height='60' marginheight='0' marginwidth='0' seamless='seamless' frameborder='0'></iframe>";
		northHtml += "<img src='img/logo-title-small.png' style='position: absolute; top: 2px; right: 30px'/>";

		var northConfig = {
			"xtype": "panel",
			"region": "north",
			"height": 60,
			"border": false,
			"html": northHtml
		};

		if (!isPremium) {
			northConfig.listeners = {
				"afterrender": function(){
					window.setInterval(function(){
						var iframe = document.getElementById('adsensetop');
						if (iframe != null) iframe.src += "";
					}, 1000 * 60 * 20);
				}
			};
		}

		this.items = [
			northConfig,
			{
				"xtype": "tabpanel",
				"itemId": "budditabpanel",
				"layout": "fit",
				"region": "center",
				"items": [
					{
						"xtype": "panel",
						"layout": "border",
						"title": BuddiLive.translate("MY_ACCOUNTS"),
						"itemId": "myAccounts",
						"items": [
							{
								"xtype": "accounttree",
								"region": "west",
								"width": 300,
								"split": true
							},
							{
								"xtype": "transactionlist",
								"region": "center"
							}
						],
						"dockedItems": this.getDockedItems("accounts")
					},
					{
						"xtype": "panel",
						"layout": "fit",
						"region": "center",
						"title": BuddiLive.translate("MY_BUDGET"),
						"items": [
							{
								"xtype": "budgetpanel",
								"itemId": "myBudget"
							}
						],
						"dockedItems": this.getDockedItems("categories")
					}
				]
			}
		];

		this.callParent();
	},

	"reload": function(){
		location.reload();
	},

	"getDockedItems": function(type){
		var isPremium = BuddiLive.util.UserConfig.get('premium');
		var isEncrypted = BuddiLive.util.UserConfig.get('encrypted');
		var items = [];

		if (type == "accounts"){
			items.push(
				{
					"text": BuddiLive.translate("NEW_ACCOUNT"),
					"icon": "img/bank--plus.png",
					"itemId": "addAccount"
				},
				{
					"text": BuddiLive.translate("MODIFY_ACCOUNT"),
					"icon": "img/bank--pencil.png",
					"itemId": "editAccount",
					"disabled": true
				},
				{
					"text": BuddiLive.translate("DELETE_ACCOUNT"),
					"icon": "img/bank--minus.png",
					"itemId": "deleteAccount",
					"disabled": true
				}
			);
		}
		else if (type == "categories"){
			items.push(
				{
					"text": BuddiLive.translate("NEW_BUDGET_CATEGORY"),
					"icon": "img/table--plus.png",
					"itemId": "addCategory"
				},
				{
					"text": BuddiLive.translate("MODIFY_BUDGET_CATEGORY"),
					"icon": "img/table--pencil.png",
					"itemId": "editCategory",
					"disabled": true
				},
				{
					"text": BuddiLive.translate("DELETE_BUDGET_CATEGORY"),
					"icon": "img/table--minus.png",
					"itemId": "deleteCategory",
					"disabled": true
				}
			);
		}
		else if (type == "scheduled"){
			items.push(
				{
					"text": BuddiLive.translate("NEW_SCHEDULED_TRANSACTION"),
					"icon": "img/alarm-clock--plus.png",
					"itemId": "addScheduled"
				},
				{
					"text": BuddiLive.translate("MODIFY_SCHEDULED_TRANSACTION"),
					"icon": "img/alarm-clock--pencil.png",
					"itemId": "editScheduled",
					"disabled": true
				},
				{
					"text": BuddiLive.translate("DELETE_SCHEDULED_TRANSACTION"),
					"icon": "img/alarm-clock--minus.png",
					"itemId": "deleteScheduled",
					"disabled": true
				}
			);
		}
		else if (type == "report"){
			items.push(
				{
					"text": BuddiLive.translate("REFRESH"),
					"icon": "img/refresh.gif",
					"itemId": "refreshReport"
				}
			);
		}


		items.push(
			"->",
			isEncrypted ? {
				"icon": "img/lock.png",
				"overCls": "",
				"tooltip": BuddiLive.translate("DATA_ENCRYPTED")
			} : "",
			isEncrypted ? " " : "",
			isPremium ? {
				"icon": "img/medal-premium.png",
				"overCls": "",
				"tooltip": BuddiLive.translate("PREMIUM_THANKS")
			} : "",
			isPremium ? " " : "",
			{
				"text": BuddiLive.translate("REPORTS"),
				"icon": "img/chart.png",
				"menu": [
					{
						"text": BuddiLive.translate("REPORT_TABLE_INCOME_AND_EXPENSES_BY_CATEGORY"),
						"icon": "img/table-sum.png",
						"itemId": "showIncomeAndExpensesByCategoryTable"
					},
					{
						"text": BuddiLive.translate("REPORT_TABLE_AVERAGE_INCOME_AND_EXPENSES_BY_CATEGORY"),
						"icon": "img/table-sum.png",
						"itemId": "showAverageIncomeAndExpensesByCategoryTable"
					},
					{
						"text": BuddiLive.translate("REPORT_TABLE_INFLOW_AND_OUTFLOW_BY_ACCOUNT"),
						"icon": "img/table-sum" + (isPremium ? "" : "-disabled") + ".png",
						"itemId": "showInflowAndOutflowByAccountTable"
					},
					{
						"text": BuddiLive.translate("REPORT_TABLE_INFLOW_AND_OUTFLOW_BY_PAYEE"),
						"icon": "img/table-sum" + (isPremium ? "" : "-disabled") + ".png",
						"itemId": "showInflowAndOutflowByPayeeTable"
					},
					{
						"text": BuddiLive.translate("REPORT_PIE_INCOME_BY_CATEGORY"),
						"icon": "img/chart-pie.png",
						"itemId": "showIncomeByCategoryPie"
					},
					{
						"text": BuddiLive.translate("REPORT_PIE_EXPENSES_BY_CATEGORY"),
						"icon": "img/chart-pie.png",
						"itemId": "showExpensesByCategoryPie"
					},
					{
						"text": BuddiLive.translate("REPORT_ACCOUNT_BALANCES_OVER_TIME"),
						"icon": "img/chart-up.png",
						"itemId": "showAccountBalancesOverTimeLine"
					},
					{
						"text": BuddiLive.translate("REPORT_NET_WORTH_OVER_TIME"),
						"icon": "img/chart-up.png",
						"itemId": "showNetWorthOverTimeLine"
					}
				]
			},
			{
				"text": BuddiLive.translate("SYSTEM"),
				"icon": "img/switch.png",
				"menu": [
					{
						"text": BuddiLive.translate("CHANGE_PASSWORD"),
						"icon": "img/ui-text-field-password.png",
						"itemId": "changePassword"
					},
					{
						"text": BuddiLive.translate("PREFERENCES"),
						"icon": "img/gear.png",
						"itemId": "showPreferences"
					},
					{
						"text": BuddiLive.translate("SCHEDULED_TRANSACTIONS"),
						"icon": "img/alarm-clock.png",
						"itemId": "showScheduled"
					},
					"-",
					{
						"text": BuddiLive.translate("BACKUP"),
						"icon": "img/drive-download.png",
						"itemId": "backup"
					},
					{
						"text": BuddiLive.translate("RESTORE"),
						"icon": "img/drive-upload.png",
						"itemId": "restore"
					},
					{
						"text": BuddiLive.translate("EXPORT_CSV"),
						"icon": "img/blue-document-excel-csv" + (isPremium ? "" : "-disabled") + ".png",
						"itemId": "exportCsv"
					},
					"-",
					{
						"text": BuddiLive.translate("HELP_GETTING_STARTED_TITLE"),
						"icon": "img/question.png",
						"itemId": "gettingStarted"
					},
					{
						"text": BuddiLive.translate("DONATE_TITLE"),
						"icon": "img/money-coin.png",
						"itemId": "donate"
					},
					"-",
					{
						"text": BuddiLive.translate("DELETE_USER"),
						"icon": "img/minus-octagon.png",
						"itemId": "deleteUser"
					}
				]
			},
			{
				"text": BuddiLive.translate("LOGOUT"),
				"icon": "img/control-power.png",
				"itemId": "logout"
			}
		);

		return [
			{
				"xtype": "toolbar",
				"dock": "top",
				"items": items
			}
		];
	}
});
