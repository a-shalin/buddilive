package ca.digitalcave.buddi.live.e2e.ui;

import okhttp3.OkHttpClient;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class TransactionSmokeIT extends BrowserBaseIT {

	private static final String EMAIL = "txn-smoke@example.com";
	private static final String EMAIL_EDIT = "txn-smoke-edit@example.com";
	private static final String EMAIL_PREF = "txn-smoke-pref@example.com";
	private static final String EMAIL_BUDGET_FORMAT = "txn-smoke-budget-format@example.com";
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
		waitForStoreLoad("transactionlist");

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

		assertGridRowsVisible("transactionlist");

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

		wait.until(d -> {
			try {
				return (Boolean) executeJs(
					"return Ext.ComponentQuery.query('preferenceseditor checkbox[itemId=showCurrencySymbol]').length > 0;");
			} catch (Exception e) {
				return false;
			}
		});

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

	@Test
	void testEditBudgetAmountWithCustomSeparators() throws Exception {
		helper.registerUser(EMAIL_BUDGET_FORMAT, PASSWORD, "en_US", "USD");
		OkHttpClient apiClient = helper.login(EMAIL_BUDGET_FORMAT, PASSWORD);
		int groceriesId = helper.createCategory(apiClient, "Groceries", "E", "MONTH");

		JSONObject update = new JSONObject();
		update.put("locale", "en_US");
		update.put("currency", "USD");
		update.put("showCurrencySymbol", false);
		update.put("currencyAfter", false);
		update.put("currencySpacing", true);
		update.put("decimalSeparator", ",");
		update.put("thousandSeparator", ".");
		update.put("negativeFormat", "N");
		update.put("showDeleted", true);
		helper.updateUserPreferences(apiClient, update);

		JSONObject initialBudget = helper.getCategories(apiClient, "MONTH");
		JSONObject setBudget = new JSONObject();
		setBudget.put("action", "set");
		setBudget.put("categoryId", groceriesId);
		setBudget.put("amount", "1111.00");
		setBudget.put("date", initialBudget.getString("date"));
		setBudget.put("periodType", "MONTH");
		setBudget.put("offset", 0);
		helper.postJson(apiClient, "/data/categories", setBudget);

		browserLogin(EMAIL_BUDGET_FORMAT, PASSWORD);

		executeJs("Ext.ComponentQuery.query('tabpanel[itemId=budditabpanel]')[0].setActiveTab(1);");
		waitForComponent("budgettree[itemId=MONTH]");

		Boolean edited = (Boolean) executeJs(
			"var categoryName = arguments[0];" +
			"var rawAmount = arguments[1];" +
			"var tree = Ext.ComponentQuery.query(\"budgettree[itemId='MONTH']\")[0] || Ext.ComponentQuery.query('budgettree')[0];" +
			"if (!tree) return false;" +
			"var record = null;" +
			"tree.getRootNode().cascadeBy(function(node) {" +
			"  if (node.get('name') === categoryName) record = node;" +
			"});" +
			"if (!record) return false;" +
			"var currentColumn = tree.columns[2];" +
			"var field = currentColumn.getEditor(record);" +
			"field.setRawValue(rawAmount);" +
			"var parsedValue = field.getValue();" +
			"BuddiLive.app.getController('budget.Tree').edit({cmp: tree}, {" +
			"  cmp: tree," +
			"  record: record," +
			"  originalValue: record.get('current')," +
			"  value: parsedValue" +
			"});" +
			"return true;",
			"Groceries", "1.234,56");
		assertThat(edited).isTrue();

		wait.until(d -> {
			try {
				JSONObject budget = helper.getCategories(apiClient, "MONTH");
				JSONObject groceries = findCategory(budget, "Groceries");
				return groceries != null && groceries.optString("current").contains("1.234,56");
			}
			catch (Exception e) {
				return false;
			}
		});

		JSONObject updatedBudget = helper.getCategories(apiClient, "MONTH");
		JSONObject groceries = findCategory(updatedBudget, "Groceries");
		assertThat(groceries).isNotNull();
		assertThat(groceries.optString("current")).contains("1.234,56");
	}

	@Test
	void testSwitchAccountAfterScrollShowsRows() throws Exception {
		final String email = "txn-scroll-switch@example.com";
		helper.registerUser(email, PASSWORD, "en_US", "USD");
		OkHttpClient apiClient = helper.login(email, PASSWORD);
		final int chequingId = helper.createAccount(apiClient, "Chequing", "D", "Chequing", "1000.00");
		final int savingsId = helper.createAccount(apiClient, "Savings", "D", "Savings", "2000.00");
		final int groceriesId = helper.createCategory(apiClient, "Groceries", "E", "MONTH");

		// Create >250 transactions in Chequing (pageSize=250) so the
		// BufferedStore pages and the rendered block is positioned deep.
		for (int t = 0; t < 300; t++) {
			final int day = (t % 28) + 1;
			final int month = (t / 28 % 12) + 1;
			final String date = String.format("2024-%02d-%02d", month, day);
			helper.createTransaction(apiClient, "Chq txn " + t, date, chequingId, groceriesId, "10.00");
		}

		// A few transactions in Savings
		for (int t = 0; t < 3; t++) {
			helper.createTransaction(apiClient, "Sav txn " + t, "2024-02-0" + (t + 1), savingsId, groceriesId, "100.00");
		}

		browserLogin(email, PASSWORD);

		// Open Chequing and wait for page 1 to load
		selectAccount("Chequing");
		waitForStoreLoad("transactionlist");

		// Scroll past page 1 boundary and wait for the rendered block to
		// include records beyond the first page (index >= 250).
		executeJs(
			"var list = Ext.ComponentQuery.query('transactionlist')[0];" +
			"var scroller = list.getView().getScrollable();" +
			"scroller.scrollTo(0, scroller.getMaxPosition().y);");
		wait.until(d -> {
			try {
				return (Boolean) executeJs(
					"var rows = Ext.ComponentQuery.query('transactionlist')[0].getView().all;" +
					"return rows.endIndex >= 250;");
			} catch (Exception e) {
				return false;
			}
		});

		// Now switch to Savings — reload() is called while the rendered
		// block is positioned deep in the scroll area (page 2+ territory).
		executeJs(
			"window.__switchLoadDone = false;" +
			"var list = Ext.ComponentQuery.query('transactionlist')[0];" +
			"list.getStore().on('load', function() { window.__switchLoadDone = true; }, null, {single: true});");
		selectAccount("Savings");

		wait.until(d -> {
			try {
				return (Boolean) executeJs("return window.__switchLoadDone === true;");
			} catch (Exception e) {
				return false;
			}
		});

		// Wait for the buffered renderer to finish rendering after load
		wait.until(d -> {
			try {
				return (Boolean) executeJs(
					"var list = Ext.ComponentQuery.query('transactionlist')[0];" +
					"var br = list.getView().bufferedRenderer;" +
					"return br && !br.refreshing;");
			} catch (Exception e) {
				return false;
			}
		});
		assertGridRowsVisible("transactionlist");
	}

	@Test
	void testBudgetToolbarLabelsAreTranslated() throws Exception {
		final String email = "txn-budget-i18n@example.com";
		helper.registerUser(email, PASSWORD, "en_US", "USD");
		helper.createCategory(helper.login(email, PASSWORD), "Groceries", "E", "MONTH");

		browserLogin(email, PASSWORD);

		executeJs("Ext.ComponentQuery.query('tabpanel[itemId=budditabpanel]')[0].setActiveTab(1);");
		waitForComponent("budgettree[itemId=MONTH]");

		String copyText = (String) executeJs(
			"return Ext.ComponentQuery.query('budgettree button[itemId=copyFromPreviousPeriod]')[0].getText();");
		assertThat(copyText)
			.as("COPY_FROM_PREVIOUS_BUDGET_PERIOD should be translated")
			.isNotEqualTo("COPY_FROM_PREVIOUS_BUDGET_PERIOD");

		String periodLabel = (String) executeJs(
			"var labels = Ext.ComponentQuery.query('budgettree label');" +
			"for (var i = 0; i < labels.length; i++) { if (labels[i].getText && labels[i].getText() !== '') return labels[i].getText(); }" +
			"return null;");
		assertThat(periodLabel)
			.as("CURRENT_BUDGET_PERIOD should be translated")
			.isNotEqualTo("CURRENT_BUDGET_PERIOD");
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

	private JSONObject findCategory(JSONObject tree, String name) {
		JSONArray children = tree.optJSONArray("children");
		if (children == null) return null;
		for (int i = 0; i < children.length(); i++) {
			JSONObject category = findCategoryNode(children.getJSONObject(i), name);
			if (category != null) return category;
		}
		return null;
	}

	private JSONObject findCategoryNode(JSONObject node, String name) {
		if (name.equals(node.optString("name"))) return node;
		JSONArray children = node.optJSONArray("children");
		if (children == null) return null;
		for (int i = 0; i < children.length(); i++) {
			JSONObject found = findCategoryNode(children.getJSONObject(i), name);
			if (found != null) return found;
		}
		return null;
	}
}
