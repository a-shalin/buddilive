Ext.define('BuddiLive.view.report.NetWorthOverTime', {
	extend: "Ext.panel.Panel",
	alias: "widget.reportnetworthovertime",
	
	requires: [],
	
	closable: true,
	layout: "fit",
	initComponent: function(){
		var me = this;
		this.dockedItems = BuddiLive.app.viewport.getDockedItems("report");
		
		this.title = BuddiLive.translate("REPORT_NET_WORTH_OVER_TIME") + " - " + this.initialConfig.options.dateRange;
		this.items = [
			{
				xtype: "chart",
				store: Ext.create("Ext.data.Store", {
					autoLoad: true,
					proxy: {
						type: "ajax",
						url: "data/report/balancesovertime.json?netWorthOnly=true&" + this.initialConfig.options.query,
						reader: {
							type: "json",
							rootProperty: "data"
						}
					},
					listeners: {
						beforeload: function(store, operation, eOpts){
							me.mask(BuddiLive.translate("LOADING"));
						},
						load: function(store, records, successful, operation, eOpts){
							me.unmask();
						}
					}
				}),
				legend: {
					docked: "right"
				},
				axes: [
					{
						type: "numeric",
						position: "left",
						fields: ["netWorth"],
						title: BuddiLive.translate("NET_WORTH"),
						grid: true
					},
					{
						type: "category",
						position: "bottom",
						label: {
							rotate: {
								degrees: -90
							}
						},
						fields: ["date"],
						title: BuddiLive.translate("DATE")
					}
				],
				series: [
					{
						type: "line",
						axis: "left",
						showMarkers: false,
						style: {
							"stroke-width": 2
						},
						title: BuddiLive.translate("NET_WORTH"),
						xField: "date",
						yField: "netWorth"
					}
				]
			}
		]
	
		this.callParent(arguments);
	}
});