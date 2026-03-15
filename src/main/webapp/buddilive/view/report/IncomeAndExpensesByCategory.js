Ext.define('BuddiLive.view.report.IncomeAndExpensesByCategory', {
	extend: "Ext.panel.Panel",
	alias: "widget.reportincomeandexpensesbycategory",
	
	requires: [
	],
	
	closable: true,
	layout: "fit",
	initComponent: function(){
		var me = this;
		this.dockedItems = BuddiLive.app.viewport.getDockedItems("report");
		
		this.title = BuddiLive.translate("REPORT_TABLE_INCOME_AND_EXPENSES_BY_CATEGORY") + " - " + this.initialConfig.options.dateRange;
		var styledRenderer = function(value, metaData, record){
			metaData.style = record.data[metaData.column.dataIndex + "Style"];
			return value;
		};

		this.items = [
			{
				xtype: "grid",
				store: Ext.create("Ext.data.Store", {
					autoLoad: true,
					fields: ["source", "actual", "budgeted", "difference", "transactions"],
					proxy: {
						type: "ajax",
						url: "data/report/incomeandexpensesbycategory.json?" + this.initialConfig.options.query,
						reader: {
							type: "json",
							rootProperty: "data"
						}
					}
				}),
				plugins: [
					{
						ptype: "rowexpander",
						expandOnEnter: false,
						rowBodyTpl: [
							"<table class='x-grid-table' style='width: 100%;'>",
							"<tpl if='transactions.length &gt; 0'>",
								"<tr>",
									"<td class='x-grid-cell x-grid-td' style='width: 10%; font-weight: bold;'>" + BuddiLive.translate("DATE") + "</td>",
									"<td class='x-grid-cell x-grid-td' style='width: 20%; font-weight: bold;'>" + BuddiLive.translate("DESCRIPTION") + "</td>",
									"<td class='x-grid-cell x-grid-td' style='width: 20%; font-weight: bold;'>" + BuddiLive.translate("FROM") + " &rarr; " + BuddiLive.translate("TO") + "</td>",
									"<td class='x-grid-cell x-grid-td' style='width: 20%; font-weight: bold;'>" + BuddiLive.translate("AMOUNT") + "</td>",
								"</tr>",
								"<tpl for='transactions'>",
									"<tr>",
										"<td class='x-grid-cell x-grid-td' style='{dateStyle}'>{date}</td>",
										"<td class='x-grid-cell x-grid-td' style='{descriptionStyle}'>{description}</td>",
										"<td class='x-grid-cell x-grid-td' style='{fromToStyle}'>{from} &rarr; {to}</td>",
										"<td class='x-grid-cell x-grid-td' style='{amountStyle}'>{amount}</td>",
									"</tr>",
								"</tpl>",
							"<tpl else>",
								"<tr>",
									"<td class='x-grid-cell x-grid-td' style='width: 100%; font-weight: bold;'>" + BuddiLive.translate("NO_TRANSACTIONS_IN_SELECTED_RANGE") + "</td>",
								"</tr>",
							"</tpl>",
							"</table>"
						],
						toggleRow: function(rowIdx, record) {
							if (record.get("source") == BuddiLive.translate("TOTAL")) return false;
							Ext.grid.plugin.RowExpander.prototype.toggleRow.apply(this, arguments);
						}
					}
				],
				columns: [
					{
						text: BuddiLive.translate("BUDGET_CATEGORY_NAME"),
						dataIndex: "source",
						hideable: false,
						sortable: false,
						flex: 3,
						renderer: styledRenderer
					},
					{
						text: BuddiLive.translate("ACTUAL"),
						dataIndex: "actual",
						hideable: false,
						sortable: false,
						flex: 2,
						renderer: styledRenderer
					},
					{
						text: BuddiLive.translate("BUDGETED"),
						dataIndex: "budgeted",
						hideable: false,
						sortable: false,
						flex: 2,
						renderer: styledRenderer
					},
					{
						text: BuddiLive.translate("DIFFERENCE"),
						dataIndex: "difference",
						hideable: false,
						sortable: false,
						flex: 2,
						renderer: styledRenderer
					}
				]
			}
		]
	
		this.callParent(arguments);
	}
});