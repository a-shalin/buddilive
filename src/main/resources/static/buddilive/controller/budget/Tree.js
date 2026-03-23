Ext.define("BuddiLive.controller.budget.Tree", {
	extend: "Ext.app.Controller",

	init: function() {
		this.control({
			budgettree: {
				edit: this.edit,
				selectionchange: this.selectionChange
			},
			"budgettree button[itemId='copyFromPreviousPeriod']": { click: this.clickCopyFromPreviousPeriod },
			"budgettree button[itemId='previousPeriod']": { click: this.clickChangePeriod },
			"budgettree button[itemId='nextPeriod']": { click: this.clickChangePeriod }
		});
	},
	
	edit: function(editor, data) {
		//If nothing has changed, no point in reloading
		if (data.originalValue == data.value) return;
		if (data.value == "") data.value = 0;
		if (data.value == null) {
			data.record.reject();
			return;
		}
		
		let budgetTree = editor.cmp;
		let request = {action: "set"};
		request.categoryId = data.record.data.id;
		request.date = data.record.data.date;
		request.periodType = data.record.data.type;
		request.amount = String(data.value);
	
		let conn = new Ext.data.Connection();
		conn.request({
			url: "data/categories",
			headers: {
				Accept: "application/json"
			},
			method: "POST",
			jsonData: request,

			success: function(response) {
				let v = Ext.decode(response.responseText);
				if (v && v.data) {
					v = v.data;
					data.record.set("current", v.current);
					data.record.set("currentStyle", v.currentStyle);
					data.record.set("actual", v.actual);
					data.record.set("actualStyle", v.actualStyle);
					data.record.set("difference", v.difference);
					data.record.set("differenceStyle", v.differenceStyle);
				}
				data.record.commit();
			},

			failure: function(response) {
				data.record.reject();
				BuddiLive.app.error(response);
			}
		});
	},
	
	selectionChange: function(selectionModel, selected) {
		let enabled = selected && selected.length > 0;

		let viewport = selectionModel.view.panel.up("buddiviewport");
		viewport.down("button[itemId='editCategory']").setDisabled(!enabled);
		viewport.down("button[itemId='deleteCategory']").setDisabled(!enabled);
		if (selected && selected.length > 0 && selected[0].data.deleted) {
			viewport.down("button[itemId='deleteCategory']").setText(BuddiLive.translate("UNDELETE_BUDGET_CATEGORY"));
		}
		else {
			viewport.down("button[itemId='deleteCategory']").setText(BuddiLive.translate("DELETE_BUDGET_CATEGORY"));
		}
	},

	clickCopyFromPreviousPeriod: function(component) {
		let budgetTree = component.up("budgettree");
		let request = {action: "copyFromPrevious"};
		request.type = budgetTree.periodValue;
		request.date = budgetTree.currentDate;
		let conn = new Ext.data.Connection();
		let mask = new Ext.LoadMask({msg: BuddiLive.translate("PROCESSING"), target: budgetTree});
		mask.show();
		conn.request({
			url: "data/categories",
			headers: {
				Accept: "application/json"
			},
			method: "POST",
			jsonData: request,

			success: function(response) {
				mask.hide();
				budgetTree.getStore().reload();
			},

			failure: function(response) {
				mask.hide();
				BuddiLive.app.error(response);
			}
		});
	},
		
	clickChangePeriod: function(component) {
		let budgetTree = component.up("budgettree");
		let offset = (component.itemId == "previousPeriod" ? -1 : 1);
		budgetTree.getStore().load({params: {date: budgetTree.currentDate, offset: offset}});
	}
});
