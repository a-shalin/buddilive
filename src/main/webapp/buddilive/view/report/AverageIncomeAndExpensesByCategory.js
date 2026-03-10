Ext.define('BuddiLive.view.report.AverageIncomeAndExpensesByCategory', {
	"extend": "Ext.panel.Panel",
	"alias": "widget.reportaverageincomeandexpensesbycategory",
	
	"requires": [],
	
	"closable": true,
	"layout": "fit",
	"initComponent": function(){
		var me = this;
		this.dockedItems = BuddiLive.app.viewport.getDockedItems("report");
		
		this.title = BuddiLive.translate("REPORT_TABLE_AVERAGE_INCOME_AND_EXPENSES_BY_CATEGORY") + " - " + this.initialConfig.options.dateRange;
		var styledRenderer = function(value, metaData, record){
			metaData.style = record.data[metaData.column.dataIndex + "Style"];
			return value;
		};

		this.items = [
			{
				"xtype": "grid",
				"store": Ext.create("Ext.data.Store", {
					"autoLoad": true,
					"fields": ["source", "average"],
					"proxy": {
						"type": "ajax",
						"url": "data/report/averageincomeandexpensesbycategory.json?" + this.initialConfig.options.query,
						"reader": {
							"type": "json",
							"rootProperty": "data"
						}
					}
				}),
				"columns": [
					{
						"text": BuddiLive.translate("BUDGET_CATEGORY_NAME"),
						"dataIndex": "source",
						"hideable": false,
						"sortable": false,
						"flex": 3,
						"renderer": styledRenderer
					},
					{
						"text": BuddiLive.translate("AVERAGE_ACTUAL"),
						"dataIndex": "average",
						"hideable": false,
						"sortable": false,
						"flex": 2,
						"renderer": styledRenderer
					},
					{
						"text": BuddiLive.translate("AVERAGE_BUDGETED"),
						"dataIndex": "averageBudgeted",
						"hideable": false,
						"sortable": false,
						"flex": 2,
						"renderer": styledRenderer
					},
					{
						"text": BuddiLive.translate("DIFFERENCE"),
						"dataIndex": "difference",
						"hideable": false,
						"sortable": false,
						"flex": 2,
						"renderer": styledRenderer
					},
					{
						"text": BuddiLive.translate("BUDGET_CATEGORY_PERIOD_TYPE"),
						"dataIndex": "period",
						"hideable": false,
						"sortable": false,
						"flex": 2,
						"renderer": styledRenderer
					}
				]
			}
		]
	
		this.callParent(arguments);
	}
});