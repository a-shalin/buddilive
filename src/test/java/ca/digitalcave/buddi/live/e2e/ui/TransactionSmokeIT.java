package ca.digitalcave.buddi.live.e2e.ui;

import okhttp3.OkHttpClient;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class TransactionSmokeIT extends BrowserBaseIT {

	private static final String EMAIL = "txn-smoke@example.com";
	private static final String PASSWORD = "TestPassword123!";

	@Test
	void testCreateTransactionAppearsInGrid() throws Exception {
		helper.registerUser(EMAIL, PASSWORD, "en_US", "USD");
		OkHttpClient apiClient = helper.login(EMAIL, PASSWORD);
		helper.createAccount(apiClient, "Chequing", "D", "Chequing", "1000.00");
		helper.createCategory(apiClient, "Groceries", "E", "MONTH");

		browserLogin(EMAIL, PASSWORD);

		// Select the account in the tree
		wait.until(d -> {
			try {
				return (Boolean) executeJs(
					"var tree = Ext.ComponentQuery.query('accounttree')[0];" +
					"var root = tree.getRootNode();" +
					"var found = null;" +
					"root.cascadeBy(function(node) {" +
					"  if (node.get('name') === 'Chequing' && node.get('nodeType') === 'account') found = node;" +
					"});" +
					"if (found) { tree.getSelectionModel().select(found); return true; }" +
					"return false;");
			} catch (Exception e) {
				return false;
			}
		});

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
}
