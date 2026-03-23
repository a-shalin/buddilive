Ext.define("BuddiLive.view.component.GenericStoreBackedCombobox", {
	extend: "Ext.form.field.ComboBox",
	
	initComponent: function() {
		let combo = this;
		Ext.applyIf(this, this.initialConfig);

		if (Ext.isString(this.store) && !Ext.data.StoreManager.lookup(this.store)) {
			this.store = Ext.create("BuddiLive.store." + this.store, {autoLoad: true});
		}

		this.forceSelection = true;
		this.displayField = this.displayField || "text";
		this.valueField = this.valueField || "value";
		this.enableKeyEvents = true;
		this.editable = this.initialConfig.editable != null ? this.initialConfig.editable : true;
		this.queryMode = this.initialConfig.queryMode || "local";
		this.anyMatch = this.initialConfig.anyMatch != null ? this.initialConfig.anyMatch : true;

		this.listConfig = this.listConfig || {
			itemTpl: "<div style='{style}'>{text}</div>"
		};
	
		this.callParent(arguments);
		
		this.addListener("select", function() {
			//Don't let users select the separators.
			if (combo.getValue() == "") {
				combo.setValue();
			}
		});
		
		this.addListener("afterrender", function() {
			combo.setValue(this.initialConfig.value);
		});
	}
});
