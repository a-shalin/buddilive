package ca.digitalcave.buddi.live.e2e.api;

import java.time.LocalDate;

import okhttp3.OkHttpClient;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import ca.digitalcave.buddi.live.e2e.BaseIT;
import ca.digitalcave.buddi.live.e2e.TestHelper;

import static org.assertj.core.api.Assertions.assertThat;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class FormattingIT extends BaseIT {

	private static TestHelper helper;
	private static OkHttpClient client;
	private static int accountId;
	private static int expenseCategoryId;
	private static final String EMAIL = "formatting-test@example.com";
	private static final String PASSWORD = "TestPassword123!";

	@BeforeAll
	static void setUp() throws Exception {
		helper = new TestHelper(getBaseUrl(), getDbUrl());
		helper.registerUser(EMAIL, PASSWORD, "en_US", "USD");
		client = helper.login(EMAIL, PASSWORD);

		accountId = helper.createAccount(client, "Chequing", "D", "Chequing", "1111.00");
		expenseCategoryId = helper.createCategory(client, "Groceries", "E", "MONTH");
		int incomeCategoryId = helper.createCategory(client, "Salary", "I", "MONTH");

		LocalDate now = LocalDate.now();
		helper.createTransaction(client, "Grocery Store", now.minusDays(2).toString(), accountId, expenseCategoryId, "50.00");
		helper.createTransaction(client, "Negative Grocery", now.minusDays(1).toString(), accountId, expenseCategoryId, "-25.00");
		helper.createTransaction(client, "Paycheck", now.minusDays(3).toString(), incomeCategoryId, accountId, "1000.00");

		JSONObject initialBudget = helper.getCategories(client, "MONTH");
		JSONObject setBudget = new JSONObject();
		setBudget.put("action", "set");
		setBudget.put("categoryId", expenseCategoryId);
		setBudget.put("amount", "1234.50");
		setBudget.put("date", initialBudget.getString("date"));
		setBudget.put("periodType", "MONTH");
		setBudget.put("offset", 0);
		helper.postJson(client, "/data/categories", setBudget);
	}

	@Test
	@Order(1)
	void testDefaultIsoSpacingInAccountsAndTransactions() throws Exception {
		JSONObject accounts = helper.getAccounts(client);
		JSONObject account = findAccount(accounts, "Chequing");
		assertThat(account).isNotNull();
		assertThat(account.getString("balance")).startsWith("USD ");

		JSONObject txns = helper.getTransactions(client, accountId);
		JSONObject txn = findTransaction(txns, "Grocery Store");
		assertThat(txn).isNotNull();
		JSONArray splits = txn.getJSONArray("splits");
		assertThat(splits.length()).isGreaterThan(0);
		JSONObject split = splits.getJSONObject(0);
		assertThat(split.getString("amount")).startsWith("USD ");
		assertThat(split.getString("balance")).startsWith("USD ");
	}

	@Test
	@Order(2)
	void testBudgetAndReportsUseConfiguredFormatting() throws Exception {
		JSONObject update = new JSONObject();
		update.put("locale", "en_US");
		update.put("currency", "USD");
		update.put("showCurrencySymbol", true);
		update.put("currencyAfter", true);
		update.put("currencySpacing", true);
		update.put("decimalSeparator", ",");
		update.put("thousandSeparator", " ");
		update.put("negativeFormat", "B");
		update.put("showDeleted", true);
		helper.updateUserPreferences(client, update);

		JSONObject budget = helper.getCategories(client, "MONTH");
		JSONObject groceries = findCategory(budget, "Groceries");
		assertThat(groceries).isNotNull();
		assertSymbolAfterWithSpace(groceries.getString("current"));
		assertSymbolAfterWithSpace(groceries.getString("actual"));
		assertSymbolAfterWithSpace(groceries.getString("difference"));

		JSONObject pie = helper.getJson(client, "/data/report/pietotalsbycategory?type=E&interval=PLUGIN_FILTER_THIS_YEAR");
		assertThat(pie.getBoolean("success")).isTrue();
		JSONObject pieRow = findPieRow(pie.getJSONArray("data"), "Groceries");
		assertThat(pieRow).isNotNull();
		assertSymbolAfterWithSpace(pieRow.getString("formattedAmount"));
		assertThat(pieRow.getString("label")).contains(pieRow.getString("formattedAmount"));

		JSONObject incomeExpense = helper.getJson(client, "/data/report/incomeandexpensesbycategory?interval=PLUGIN_FILTER_THIS_YEAR");
		assertThat(incomeExpense.getBoolean("success")).isTrue();
		JSONObject groceriesRow = findReportRow(incomeExpense.getJSONArray("data"), "Groceries");
		assertThat(groceriesRow).isNotNull();
		assertSymbolAfterWithSpace(groceriesRow.getString("actual"));
		assertSymbolAfterWithSpace(groceriesRow.getString("budgeted"));
		assertSymbolAfterWithSpace(groceriesRow.getString("difference"));

		JSONObject average = helper.getJson(client, "/data/report/averageincomeandexpensesbycategory?interval=PLUGIN_FILTER_THIS_YEAR");
		assertThat(average.getBoolean("success")).isTrue();
		JSONObject averageRow = findReportRow(average.getJSONArray("data"), "Groceries");
		assertThat(averageRow).isNotNull();
		assertSymbolAfterWithSpace(averageRow.getString("average"));
		assertSymbolAfterWithSpace(averageRow.getString("averageBudgeted"));
		assertSymbolAfterWithSpace(averageRow.getString("difference"));

		JSONObject byAccount = helper.getJson(client, "/data/report/inflowandoutflowbyaccount?interval=PLUGIN_FILTER_THIS_YEAR");
		assertThat(byAccount.getBoolean("success")).isTrue();
		JSONObject chequingRow = findReportRow(byAccount.getJSONArray("data"), "Chequing");
		assertThat(chequingRow).isNotNull();
		assertSymbolAfterWithSpace(chequingRow.getString("inflow"));
		assertSymbolAfterWithSpace(chequingRow.getString("outflow"));
		assertSymbolAfterWithSpace(chequingRow.getString("difference"));

		JSONObject byPayee = helper.getJson(client, "/data/report/inflowandoutflowbypayee?interval=PLUGIN_FILTER_THIS_YEAR");
		assertThat(byPayee.getBoolean("success")).isTrue();
		JSONObject payeeRow = findReportRow(byPayee.getJSONArray("data"), "Grocery Store");
		assertThat(payeeRow).isNotNull();
		assertSymbolAfterWithSpace(payeeRow.getString("inflow"));
		assertSymbolAfterWithSpace(payeeRow.getString("outflow"));
		assertSymbolAfterWithSpace(payeeRow.getString("difference"));

		JSONObject balancesOverTime = helper.getJson(client, "/data/report/balancesovertime?interval=PLUGIN_FILTER_THIS_YEAR");
		assertThat(balancesOverTime.getBoolean("success")).isTrue();
		JSONArray balancesData = balancesOverTime.getJSONArray("data");
		assertThat(balancesData.length()).isGreaterThan(0);
		JSONObject accountPoint = findFirstDataPointWithKey(balancesData, "a" + accountId);
		assertThat(accountPoint).isNotNull();
		assertThat(accountPoint.has("date")).isTrue();
		assertThat(accountPoint.get("a" + accountId)).isNotInstanceOf(String.class);

		JSONObject netWorth = helper.getJson(client, "/data/report/balancesovertime?netWorthOnly=true&interval=PLUGIN_FILTER_THIS_YEAR");
		assertThat(netWorth.getBoolean("success")).isTrue();
		JSONArray netWorthData = netWorth.getJSONArray("data");
		assertThat(netWorthData.length()).isGreaterThan(0);
		JSONObject netWorthPoint = findFirstDataPointWithKey(netWorthData, "netWorth");
		assertThat(netWorthPoint).isNotNull();
		assertThat(netWorthPoint.has("date")).isTrue();
		assertThat(netWorthPoint.get("netWorth")).isNotInstanceOf(String.class);
	}

	private JSONObject findAccount(JSONObject tree, String name) {
		JSONArray children = tree.optJSONArray("children");
		if (children == null) return null;
		for (int i = 0; i < children.length(); i++) {
			JSONObject group = children.getJSONObject(i);
			JSONArray groupChildren = group.optJSONArray("children");
			if (groupChildren == null) continue;
			for (int j = 0; j < groupChildren.length(); j++) {
				JSONObject account = groupChildren.getJSONObject(j);
				if (name.equals(account.optString("name"))) return account;
			}
		}
		return null;
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

	private JSONObject findTransaction(JSONObject txns, String description) {
		JSONArray data = txns.optJSONArray("data");
		if (data == null) return null;
		for (int i = 0; i < data.length(); i++) {
			JSONObject txn = data.getJSONObject(i);
			if (description.equals(txn.optString("description"))) return txn;
		}
		return null;
	}

	private JSONObject findReportRow(JSONArray rows, String source) {
		for (int i = 0; i < rows.length(); i++) {
			JSONObject row = rows.getJSONObject(i);
			if (source.equals(row.optString("source"))) return row;
		}
		return null;
	}

	private JSONObject findPieRow(JSONArray rows, String categoryName) {
		for (int i = 0; i < rows.length(); i++) {
			JSONObject row = rows.getJSONObject(i);
			if (row.optString("label").startsWith(categoryName + " - ")) return row;
		}
		return null;
	}

	private JSONObject findFirstDataPointWithKey(JSONArray rows, String key) {
		for (int i = 0; i < rows.length(); i++) {
			JSONObject row = rows.getJSONObject(i);
			if (row.has(key) && !row.isNull(key)) return row;
		}
		return null;
	}

	private void assertSymbolAfterWithSpace(String value) {
		assertThat(value).doesNotContain("USD");
		assertThat(value).contains(" $");
		assertThat(value).doesNotContain("$ ");
	}
}
