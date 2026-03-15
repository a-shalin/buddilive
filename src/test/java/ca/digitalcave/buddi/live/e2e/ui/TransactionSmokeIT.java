package ca.digitalcave.buddi.live.e2e.ui;

import okhttp3.OkHttpClient;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class TransactionSmokeIT extends BrowserBaseIT {

	private static final String EMAIL = "txn-smoke@example.com";
	private static final String EMAIL_EDIT = "txn-smoke-edit@example.com";
	private static final String EMAIL_PREF = "txn-smoke-pref@example.com";
	private static final String PASSWORD = "TestPassword123!";

	@Test
	void testCreateTransactionAppearsInGrid() throws Exception {
		helper.registerUser(EMAIL, PASSWORD, "en_US", "USD");
		OkHttpClient apiClient = helper.login(EMAIL, PASSWORD);
		helper.createAccount(apiClient, "Chequing", "D", "Chequing", "1000.00");
		helper.createCategory(apiClient, "Groceries", "E", "MONTH");

		browserLogin(EMAIL, PASSWORD);

		selectAccount("Chequing");

		waitForComponent("transactioneditor");
		Thread.sleep(1000);

		// Fill in the transaction editor
		executeJs(
			"Ext.ComponentQuery.query('transactioneditor')[0]" +
			".down('datefield[itemId=date]').setValue(new Date());");
		executeJs(
			"Ext.ComponentQuery.query('transactioneditor')[0]" +
			".down('combobox[itemId=description]').setValue('Weekly Groceries');");
		executeJs(
			"Ext.ComponentQuery.query('spliteditor')[0]" +
			".down('currencyfield[itemId=amount]').setValue(75.50);");

		// Set From combo — store text has leading non-breaking spaces
		executeJs(
			"var split = Ext.ComponentQuery.query('spliteditor')[0];" +
			"var combo = split.down('combo[itemId=from]');" +
			"combo.getStore().clearFilter();" +
			"var idx = combo.getStore().findBy(function(r) {" +
			"  return r.get('text').indexOf('Chequing') !== -1 && r.get('value') !== '';" +
			"});" +
			"if (idx >= 0) combo.setValue(combo.getStore().getAt(idx).get('value'));");

		// Set To combo
		executeJs(
			"var split = Ext.ComponentQuery.query('spliteditor')[0];" +
			"var combo = split.down('combo[itemId=to]');" +
			"combo.getStore().clearFilter();" +
			"var idx = combo.getStore().findBy(function(r) {" +
			"  return r.get('text').indexOf('Groceries') !== -1 && r.get('value') !== '';" +
			"});" +
			"if (idx >= 0) combo.setValue(combo.getStore().getAt(idx).get('value'));");

		// Submit the transaction via XHR (Ext.data.Connection doesn't fire from executeScript context)
		executeJs(
			"var editor = Ext.ComponentQuery.query('transactioneditor')[0];" +
			"var request = editor.getTransaction();" +
			"request.action = 'insert';" +
			"window.__txnPostDone = false;" +
			"var xhr = new XMLHttpRequest();" +
			"xhr.open('POST', 'data/transactions', true);" +
			"xhr.setRequestHeader('Content-Type', 'application/json');" +
			"xhr.setRequestHeader('Accept', 'application/json');" +
			"xhr.onload = function() { window.__txnPostDone = true; };" +
			"xhr.onerror = function() { window.__txnPostDone = true; };" +
			"xhr.send(JSON.stringify(request));");

		wait.until(d -> {
			try {
				return (Boolean) executeJs("return window.__txnPostDone === true;");
			} catch (Exception e) {
				return false;
			}
		});

		// Reload the transaction list store and wait for load
		executeJs(
			"window.__storeLoaded = false;" +
			"var list = Ext.ComponentQuery.query('transactionlist')[0];" +
			"list.getStore().on('load', function() { window.__storeLoaded = true; }, null, {single: true});" +
			"list.reload();");

		wait.until(d -> {
			try {
				return (Boolean) executeJs("return window.__storeLoaded === true;");
			} catch (Exception e) {
				return false;
			}
		});

		// Verify the transaction appears in the grid
		Boolean found = (Boolean) executeJs(
			"var el = Ext.ComponentQuery.query('transactionlist')[0].getEl();" +
			"return el.dom.innerHTML.indexOf('Weekly Groceries') !== -1;");
		assertThat(found).as("Transaction 'Weekly Groceries' should appear in grid").isTrue();
	}

	@Test
	void testSelectingTransactionPopulatesEditorFields() throws Exception {
		helper.registerUser(EMAIL_EDIT, PASSWORD, "en_US", "USD");
		OkHttpClient apiClient = helper.login(EMAIL_EDIT, PASSWORD);
		int chequingId = helper.createAccount(apiClient, "Chequing", "D", "Chequing", "1000.00");
		int groceriesId = helper.createCategory(apiClient, "Groceries", "E", "MONTH");

		org.json.JSONObject split = new org.json.JSONObject();
		split.put("amount", "45.50");
		split.put("fromId", chequingId);
		split.put("toId", groceriesId);
		split.put("memo", "Original Memo");
		org.json.JSONArray splits = new org.json.JSONArray();
		splits.put(split);
		org.json.JSONObject transaction = new org.json.JSONObject();
		transaction.put("action", "insert");
		transaction.put("description", "Editable Transaction");
		transaction.put("number", "INV-1");
		transaction.put("date", "2024-03-20");
		transaction.put("splits", splits);
		helper.postJson(apiClient, "/data/transactions", transaction);

		browserLogin(EMAIL_EDIT, PASSWORD);
		selectAccount("Chequing");

		wait.until(d -> {
			try {
				return (Boolean) executeJs(
					"var list = Ext.ComponentQuery.query('transactionlist')[0];" +
					"var idx = list.getStore().findExact('description', 'Editable Transaction');" +
					"if (idx >= 0) { list.getSelectionModel().select(idx); return true; }" +
					"return false;");
			} catch (Exception e) {
				return false;
			}
		});

		String selectedDate = (String) executeJs(
			"var e = Ext.ComponentQuery.query('transactioneditor')[0];" +
			"return Ext.Date.format(e.down('datefield[itemId=date]').getValue(), 'Y-m-d');");
		String selectedDescription = (String) executeJs(
			"return Ext.ComponentQuery.query('transactioneditor')[0].down('combobox[itemId=description]').getValue();");
		String selectedNumber = (String) executeJs(
			"return Ext.ComponentQuery.query('transactioneditor')[0].down('textfield[itemId=number]').getValue();");
		String selectedAmount = String.valueOf(executeJs(
			"return Ext.ComponentQuery.query('transactioneditor')[0].down('currencyfield[itemId=amount]').getValue();"));
		String selectedFromId = String.valueOf(executeJs(
			"return Ext.ComponentQuery.query('transactioneditor')[0].down('combo[itemId=from]').getValue();"));
		String selectedToId = String.valueOf(executeJs(
			"return Ext.ComponentQuery.query('transactioneditor')[0].down('combo[itemId=to]').getValue();"));
		String selectedMemo = (String) executeJs(
			"return Ext.ComponentQuery.query('transactioneditor')[0].down('textfield[itemId=memo]').getValue();");

		assertThat(selectedDate).isEqualTo("2024-03-20");
		assertThat(selectedDescription).isEqualTo("Editable Transaction");
		assertThat(selectedNumber).isEqualTo("INV-1");
		assertThat(Double.parseDouble(selectedAmount)).isEqualTo(45.50);
		assertThat(Integer.parseInt(selectedFromId)).isEqualTo(chequingId);
		assertThat(Integer.parseInt(selectedToId)).isEqualTo(groceriesId);
		assertThat(selectedMemo).isEqualTo("Original Memo");

	}

	@Test
	void testPreferencesCurrencySymbolLabelTracksSelectedCurrency() throws Exception {
		helper.registerUser(EMAIL_PREF, PASSWORD, "en_US", "USD");
		browserLogin(EMAIL_PREF, PASSWORD);

		executeJs(
			"var item = Ext.ComponentQuery.query('menuitem[itemId=showPreferences]')[0];" +
			"item.fireEvent('click', item);");
		waitForComponent("preferenceseditor");

		String initialLabel = (String) executeJs(
			"return Ext.ComponentQuery.query('preferenceseditor checkbox[itemId=showCurrencySymbol]')[0].boxLabel;");
		assertThat(initialLabel).contains("$");

		executeJs(
			"var combo = Ext.ComponentQuery.query('preferenceseditor combobox[itemId=currency]')[0];" +
			"combo.setValue('EUR');");

		wait.until(d -> {
			try {
				String label = (String) executeJs(
					"return Ext.ComponentQuery.query('preferenceseditor checkbox[itemId=showCurrencySymbol]')[0].boxLabel;");
				return label != null && label.contains("€");
			} catch (Exception e) {
				return false;
			}
		});
	}

	private void selectAccount(String accountName) {
		wait.until(d -> {
			try {
				return (Boolean) executeJs(
					"var target = arguments[0];" +
					"var tree = Ext.ComponentQuery.query('accounttree')[0];" +
					"var root = tree.getRootNode();" +
					"var found = null;" +
					"root.cascadeBy(function(node) {" +
					"  if (node.get('name') === target && node.get('nodeType') === 'account') found = node;" +
					"});" +
					"if (found) { tree.getSelectionModel().select(found); return true; }" +
					"return false;",
					accountName);
			} catch (Exception e) {
				return false;
			}
		});
	}
}
