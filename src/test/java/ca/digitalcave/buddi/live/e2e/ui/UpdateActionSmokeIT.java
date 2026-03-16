package ca.digitalcave.buddi.live.e2e.ui;

import java.time.LocalDate;

import okhttp3.OkHttpClient;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class UpdateActionSmokeIT extends BrowserBaseIT {

	private static final String PASSWORD = "TestPassword123!";
	private static final String ACCOUNT_EMAIL = "update-account-ui@example.com";
	private static final String BUDGET_EMAIL = "update-budget-ui@example.com";
	private static final String SCHEDULED_EMAIL = "update-scheduled-ui@example.com";
	private static final String TRANSACTION_EMAIL = "update-transaction-ui@example.com";

	@Test
	void testUpdateAccountDoesNotThrowJsError() throws Exception {
		final String originalName = "Account Original";
		final String updatedName = "Account Updated";

		helper.registerUser(ACCOUNT_EMAIL, PASSWORD, "en_US", "USD");
		final OkHttpClient apiClient = helper.login(ACCOUNT_EMAIL, PASSWORD);
		helper.createAccount(apiClient, originalName, "D", "Chequing", "1000.00");

		browserLogin(ACCOUNT_EMAIL, PASSWORD);
		installJsErrorCollector();

		selectAccount(originalName);
		wait.until(d -> {
			try {
				return (Boolean) executeJs(
					"var btn = Ext.ComponentQuery.query(\"button[itemId='editAccount']\")[0];" +
					"return !!btn && !btn.disabled;");
			}
			catch (Exception e) {
				return false;
			}
		});

		clickExtButton("button[itemId=editAccount]");
		waitForComponent("accounteditor");
		setExtFieldValue("accounteditor textfield[itemId=name]", updatedName);
		executeJs("Ext.ComponentQuery.query(\"accounteditor button[itemId='ok']\")[0].setDisabled(false);");
		clickExtButton("accounteditor button[itemId=ok]");

		Thread.sleep(500);
		assertNoJsErrors();
		waitForAppReload();

		wait.until(d -> {
			try {
				return hasAccountNamed(helper.getAccounts(apiClient), updatedName);
			}
			catch (Exception e) {
				return false;
			}
		});

		assertThat(hasAccountNamed(helper.getAccounts(apiClient), updatedName)).isTrue();
	}

	@Test
	void testUpdateBudgetCategoryDoesNotThrowJsError() throws Exception {
		final String originalName = "Budget Original";
		final String updatedName = "Budget Updated";

		helper.registerUser(BUDGET_EMAIL, PASSWORD, "en_US", "USD");
		final OkHttpClient apiClient = helper.login(BUDGET_EMAIL, PASSWORD);
		helper.createCategory(apiClient, originalName, "E", "MONTH");

		browserLogin(BUDGET_EMAIL, PASSWORD);
		installJsErrorCollector();

		executeJs("Ext.ComponentQuery.query(\"tabpanel[itemId='budditabpanel']\")[0].setActiveTab(1);");
		waitForComponent("budgettree[itemId=MONTH]");

		wait.until(d -> {
			try {
				return (Boolean) executeJs(
					"var target = arguments[0];" +
					"var tree = Ext.ComponentQuery.query(\"budgettree[itemId='MONTH']\")[0];" +
					"if (!tree) return false;" +
					"var found = null;" +
					"tree.getRootNode().cascadeBy(function(node) {" +
					"  if (node.get('name') === target) found = node;" +
					"});" +
					"if (found) { tree.getSelectionModel().select(found); return true; }" +
					"return false;",
					originalName);
			}
			catch (Exception e) {
				return false;
			}
		});

		wait.until(d -> {
			try {
				return (Boolean) executeJs(
					"var btn = Ext.ComponentQuery.query(\"button[itemId='editCategory']\")[0];" +
					"return !!btn && !btn.disabled;");
			}
			catch (Exception e) {
				return false;
			}
		});

		clickExtButton("button[itemId=editCategory]");
		waitForComponent("budgeteditor");
		setExtFieldValue("budgeteditor textfield[itemId=name]", updatedName);
		executeJs("Ext.ComponentQuery.query(\"budgeteditor button[itemId='ok']\")[0].setDisabled(false);");
		clickExtButton("budgeteditor button[itemId=ok]");

		Thread.sleep(500);
		assertNoJsErrors();

		wait.until(d -> {
			try {
				return findCategoryByName(helper.getCategories(apiClient, "MONTH"), updatedName) != null;
			}
			catch (Exception e) {
				return false;
			}
		});

		assertThat(findCategoryByName(helper.getCategories(apiClient, "MONTH"), updatedName)).isNotNull();
	}

	@Test
	void testUpdateScheduledTransactionDoesNotThrowJsError() throws Exception {
		final String originalName = "Scheduled Original";
		final String updatedName = "Scheduled Updated";
		final String startDate = LocalDate.now().toString();

		helper.registerUser(SCHEDULED_EMAIL, PASSWORD, "en_US", "USD");
		final OkHttpClient apiClient = helper.login(SCHEDULED_EMAIL, PASSWORD);
		final int accountId = helper.createAccount(apiClient, "Chequing", "D", "Chequing", "1000.00");
		final int categoryId = helper.createCategory(apiClient, "Rent", "E", "MONTH");
		helper.createScheduledTransaction(
			apiClient,
			originalName,
			"Rent Payment",
			"SCHEDULE_FREQUENCY_MONTHLY_BY_DATE",
			1,
			startDate,
			accountId,
			categoryId,
			"1200.00");

		browserLogin(SCHEDULED_EMAIL, PASSWORD);
		installJsErrorCollector();

		executeJs(
			"var item = Ext.ComponentQuery.query(\"menuitem[itemId='showScheduled']\")[0];" +
			"item.fireEvent('click', item);");
		waitForComponent("scheduledlist grid");

		wait.until(d -> {
			try {
				return (Boolean) executeJs(
					"var target = arguments[0];" +
					"var grid = Ext.ComponentQuery.query('scheduledlist grid')[0];" +
					"if (!grid) return false;" +
					"var idx = grid.getStore().findExact('name', target);" +
					"if (idx >= 0) { grid.getSelectionModel().select(idx); return true; }" +
					"return false;",
					originalName);
			}
			catch (Exception e) {
				return false;
			}
		});

		wait.until(d -> {
			try {
				return (Boolean) executeJs(
					"var btn = Ext.ComponentQuery.query(\"button[itemId='editScheduled']\")[0];" +
					"return !!btn && !btn.disabled;");
			}
			catch (Exception e) {
				return false;
			}
		});

		clickExtButton("button[itemId=editScheduled]");
		waitForComponent("schedulededitor");
		setExtFieldValue("schedulededitor textfield[itemId=name]", updatedName);
		executeJs("Ext.ComponentQuery.query(\"schedulededitor button[itemId='ok']\")[0].setDisabled(false);");
		clickExtButton("schedulededitor button[itemId=ok]");

		Thread.sleep(500);
		assertNoJsErrors();

		wait.until(d -> {
			try {
				return hasScheduledNamed(helper.getScheduledTransactions(apiClient), updatedName);
			}
			catch (Exception e) {
				return false;
			}
		});

		assertThat(hasScheduledNamed(helper.getScheduledTransactions(apiClient), updatedName)).isTrue();
	}

	@Test
	void testUpdateTransactionDoesNotThrowJsError() throws Exception {
		final String originalDescription = "Txn Original";
		final String updatedDescription = "Txn Updated";
		final String transactionDate = LocalDate.now().minusDays(1).toString();

		helper.registerUser(TRANSACTION_EMAIL, PASSWORD, "en_US", "USD");
		final OkHttpClient apiClient = helper.login(TRANSACTION_EMAIL, PASSWORD);
		final int accountId = helper.createAccount(apiClient, "Chequing", "D", "Chequing", "1000.00");
		final int categoryId = helper.createCategory(apiClient, "Groceries", "E", "MONTH");
		helper.createTransaction(apiClient, originalDescription, transactionDate, accountId, categoryId, "45.50");

		browserLogin(TRANSACTION_EMAIL, PASSWORD);
		installJsErrorCollector();

		selectAccount("Chequing");
		waitForComponent("transactioneditor");

		wait.until(d -> {
			try {
				return (Boolean) executeJs(
					"var target = arguments[0];" +
					"var list = Ext.ComponentQuery.query('transactionlist')[0];" +
					"if (!list) return false;" +
					"var idx = list.getStore().findExact('description', target);" +
					"if (idx >= 0) { list.getSelectionModel().select(idx); return true; }" +
					"return false;",
					originalDescription);
			}
			catch (Exception e) {
				return false;
			}
		});

		setExtFieldValue("transactionlist transactioneditor combobox[itemId=description]", updatedDescription);
		executeJs(
			"var editor = Ext.ComponentQuery.query('transactionlist transactioneditor')[0];" +
			"editor.lastTransaction = null;");
		executeJs(
			"var btn = Ext.ComponentQuery.query('transactionlist transactioneditor button[itemId=recordTransaction]')[0];" +
			"btn.setDisabled(false);");
		clickExtButton("transactionlist transactioneditor button[itemId=recordTransaction]");

		Thread.sleep(500);
		assertNoJsErrors();

		wait.until(d -> {
			try {
				return hasTransactionDescription(helper.getTransactions(apiClient, accountId), updatedDescription);
			}
			catch (Exception e) {
				return false;
			}
		});

		assertThat(hasTransactionDescription(helper.getTransactions(apiClient, accountId), updatedDescription)).isTrue();
	}

	private void selectAccount(final String accountName) {
		wait.until(d -> {
			try {
				return (Boolean) executeJs(
					"var target = arguments[0];" +
					"var tree = Ext.ComponentQuery.query('accounttree')[0];" +
					"if (!tree) return false;" +
					"var root = tree.getRootNode();" +
					"var found = null;" +
					"root.cascadeBy(function(node) {" +
					"  if (node.get('name') === target && node.get('nodeType') === 'account') found = node;" +
					"});" +
					"if (found) { tree.getSelectionModel().select(found); return true; }" +
					"return false;",
					accountName);
			}
			catch (Exception e) {
				return false;
			}
		});
	}

	private boolean hasAccountNamed(final JSONObject accounts, final String name) {
		final JSONArray children = accounts.optJSONArray("children");
		if (children == null) return false;
		for (int i = 0; i < children.length(); i++) {
			final JSONObject group = children.getJSONObject(i);
			final JSONArray groupChildren = group.optJSONArray("children");
			if (groupChildren == null) continue;
			for (int j = 0; j < groupChildren.length(); j++) {
				final JSONObject account = groupChildren.getJSONObject(j);
				if (name.equals(account.optString("name"))) {
					return true;
				}
			}
		}
		return false;
	}

	private JSONObject findCategoryByName(final JSONObject tree, final String name) {
		if (name.equals(tree.optString("name"))) return tree;
		final JSONArray children = tree.optJSONArray("children");
		if (children == null) return null;
		for (int i = 0; i < children.length(); i++) {
			final JSONObject found = findCategoryByName(children.getJSONObject(i), name);
			if (found != null) return found;
		}
		return null;
	}

	private boolean hasScheduledNamed(final JSONObject scheduledTransactions, final String name) {
		final JSONArray data = scheduledTransactions.optJSONArray("data");
		if (data == null) return false;
		for (int i = 0; i < data.length(); i++) {
			final JSONObject scheduled = data.getJSONObject(i);
			if (name.equals(scheduled.optString("name"))) {
				return true;
			}
		}
		return false;
	}

	private boolean hasTransactionDescription(final JSONObject transactions, final String description) {
		final JSONArray data = transactions.optJSONArray("data");
		if (data == null) return false;
		for (int i = 0; i < data.length(); i++) {
			final JSONObject transaction = data.getJSONObject(i);
			if (description.equals(transaction.optString("description"))) {
				return true;
			}
		}
		return false;
	}
}
