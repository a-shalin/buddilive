package ca.digitalcave.buddi.live.e2e.api;

import java.nio.charset.StandardCharsets;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;

import ca.digitalcave.buddi.live.e2e.BaseIT;
import ca.digitalcave.buddi.live.e2e.TestHelper;

import static org.assertj.core.api.Assertions.assertThat;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class DataManagementIT extends BaseIT {

	private static TestHelper helper;
	private static OkHttpClient client;
	private static final String EMAIL = "datamgmt-test@example.com";
	private static final String PASSWORD = "TestPassword123!";

	@BeforeAll
	static void setUp() throws Exception {
		helper = new TestHelper(getBaseUrl(), getDbUrl());
		helper.registerUser(EMAIL, PASSWORD, "en_US", "USD");
		client = helper.login(EMAIL, PASSWORD);
	}

	@Test
	@Order(1)
	void testBackupRestoreRoundTrip() throws Exception {
		final int chequingId = helper.createAccount(client, "Chequing Account", "D", "Chequing", "500.00");
		final int savingsId = helper.createAccount(client, "Savings Account", "D", "Savings", "1000.00");
		final int visaId = helper.createAccount(client, "Visa", "C", "CreditCard", "0.00");
		final int groceriesId = helper.createCategory(client, "Groceries", "E", "MONTH");
		final int salaryId = helper.createCategory(client, "Salary", "I", "MONTH");

		helper.setBudgetEntry(client, groceriesId, "400.00", "2024-01-01", "MONTH");
		helper.setBudgetEntry(client, salaryId, "3000.00", "2024-01-01", "MONTH");

		helper.createTransaction(client, "Weekly groceries", "2024-01-15", chequingId, groceriesId, "85.50");
		helper.createTransaction(client, "Visa payment", "2024-01-20", chequingId, visaId, "200.00");
		helper.createTransaction(client, "Transfer to savings", "2024-01-25", chequingId, savingsId, "300.00");

		helper.createScheduledTransaction(client, "Monthly salary", "Salary deposit",
				"MONTH_DATE", 1, "2024-01-01", salaryId, chequingId, "3000.00");

		final JSONObject backupBefore = helper.getBackupJson(client);
		assertThat(backupBefore.getJSONArray("accounts").length()).isEqualTo(3);
		assertThat(backupBefore.getJSONArray("categories").length()).isGreaterThanOrEqualTo(2);
		assertThat(backupBefore.getJSONArray("transactions").length()).isEqualTo(3);
		assertThat(backupBefore.getJSONArray("scheduledTransactions").length()).isEqualTo(1);
		assertThat(backupBefore.getJSONArray("entries").length()).isEqualTo(2);

		helper.restore(client, new JSONObject(), true);

		final JSONObject backupEmpty = helper.getBackupJson(client);
		assertThat(backupEmpty.getJSONArray("accounts")).isEmpty();
		assertThat(backupEmpty.getJSONArray("categories")).isEmpty();
		assertThat(backupEmpty.getJSONArray("transactions")).isEmpty();
		assertThat(backupEmpty.getJSONArray("scheduledTransactions")).isEmpty();

		helper.restore(client, backupBefore, true);

		final JSONObject backupAfter = helper.getBackupJson(client);
		assertThat(backupAfter.getJSONArray("accounts").length())
			.isEqualTo(backupBefore.getJSONArray("accounts").length());
		assertThat(backupAfter.getJSONArray("categories").length())
			.isEqualTo(backupBefore.getJSONArray("categories").length());
		assertThat(backupAfter.getJSONArray("transactions").length())
			.isEqualTo(backupBefore.getJSONArray("transactions").length());
		assertThat(backupAfter.getJSONArray("scheduledTransactions").length())
			.isEqualTo(backupBefore.getJSONArray("scheduledTransactions").length());
		assertThat(backupAfter.getJSONArray("entries").length())
			.isEqualTo(backupBefore.getJSONArray("entries").length());

		assertAccountsMatch(backupBefore.getJSONArray("accounts"), backupAfter.getJSONArray("accounts"));
		assertCategoriesMatch(backupBefore.getJSONArray("categories"), backupAfter.getJSONArray("categories"));
		assertTransactionsMatch(backupBefore.getJSONArray("transactions"), backupAfter.getJSONArray("transactions"));
		assertScheduledTransactionsMatch(
			backupBefore.getJSONArray("scheduledTransactions"),
			backupAfter.getJSONArray("scheduledTransactions"));
		assertEntriesMatch(backupBefore.getJSONArray("entries"), backupAfter.getJSONArray("entries"));
	}

	@Test
	@Order(2)
	void testRestoreIgnoresDeletedTransactions() throws Exception {
		String accountUuid = "11111111-1111-1111-1111-111111111111";
		String categoryUuid = "22222222-2222-2222-2222-222222222222";
		String deletedTransactionUuid = "84ed058d-98c3-47fb-af6c-9fced9c3932e";
		String activeTransactionUuid = "84ed058d-98c3-47fb-af6c-9fced9c3932f";

		JSONObject split = new JSONObject();
		split.put("amount", "10000");
		split.put("memo", "");
		split.put("from", accountUuid);
		split.put("to", categoryUuid);

		JSONObject deletedTransaction = new JSONObject();
		deletedTransaction.put("date", "2026-03-12");
		deletedTransaction.put("number", "");
		deletedTransaction.put("deleted", true);
		deletedTransaction.put("description", "to tkf from halva sav");
		deletedTransaction.put("uuid", deletedTransactionUuid);
		deletedTransaction.put("splits", new JSONArray().put(split));

		JSONObject activeTransaction = new JSONObject();
		activeTransaction.put("date", "2026-03-12");
		activeTransaction.put("number", "");
		activeTransaction.put("deleted", false);
		activeTransaction.put("description", "active restore tx");
		activeTransaction.put("uuid", activeTransactionUuid);
		activeTransaction.put("splits", new JSONArray().put(split));

		JSONObject account = new JSONObject();
		account.put("uuid", accountUuid);
		account.put("name", "Restore Test Account");
		account.put("startDate", "2026-03-12");
		account.put("type", "D");
		account.put("accountType", "Chequing");
		account.put("startBalance", "0");

		JSONObject category = new JSONObject();
		category.put("uuid", categoryUuid);
		category.put("name", "Restore Test Category");
		category.put("type", "E");
		category.put("periodType", "MONTH");

		JSONObject restoreData = new JSONObject();
		restoreData.put("accounts", new JSONArray().put(account));
		restoreData.put("categories", new JSONArray().put(category));
		restoreData.put("transactions", new JSONArray().put(deletedTransaction).put(activeTransaction));

		RequestBody file = RequestBody.create(restoreData.toString(), MediaType.get("application/json"));
		RequestBody multipart = new MultipartBody.Builder()
			.setType(MultipartBody.FORM)
			.addFormDataPart("file", "restore.json", file)
			.build();
		Request request = new Request.Builder()
			.url(getBaseUrl() + "/data/restore?deleteData=true")
			.post(multipart)
			.build();

		try (Response response = client.newCall(request).execute()) {
			assertThat(response.code()).isEqualTo(200);
			JSONObject result = new JSONObject(response.body().string());
			assertThat(result.getBoolean("success")).isTrue();
		}

		JSONObject accounts = helper.getAccounts(client);
		int accountId = findAccountIdByName(accounts, "Restore Test Account");
		assertThat(accountId).isGreaterThan(0);

		JSONObject transactions = helper.getTransactions(client, accountId);
		assertThat(transactions.getBoolean("success")).isTrue();
		assertThat(transactions.getInt("total")).isEqualTo(1);
		JSONArray data = transactions.getJSONArray("data");
		assertThat(data).hasSize(1);
		assertThat(data.getJSONObject(0).getString("description")).isEqualTo("active restore tx");
		assertThat(data.getJSONObject(0).getBoolean("deleted")).isFalse();
	}

	@Test
	@Order(3)
	void testRestoreAcceptsLargeUpload() throws Exception {
		JSONObject restoreData = new JSONObject();
		restoreData.put("padding", "x".repeat(50 * 1024 * 1024));

		byte[] restoreBytes = restoreData.toString().getBytes(StandardCharsets.UTF_8);
		assertThat(restoreBytes.length).isGreaterThan(49 * 1024 * 1024);

		RequestBody file = RequestBody.create(restoreBytes, MediaType.get("application/json"));
		RequestBody multipart = new MultipartBody.Builder()
			.setType(MultipartBody.FORM)
			.addFormDataPart("file", "large-restore.json", file)
			.build();
		Request request = new Request.Builder()
			.url(getBaseUrl() + "/data/restore")
			.post(multipart)
			.build();

		try (Response response = client.newCall(request).execute()) {
			assertThat(response.code()).isEqualTo(200);
			JSONObject result = new JSONObject(response.body().string());
			assertThat(result.getBoolean("success")).isTrue();
		}
	}

	@Test
	@Order(4)
	void testExportRequiresPremium() throws Exception {
		final Request request = new Request.Builder()
			.url(getBaseUrl() + "/data/export?interval=PLUGIN_FILTER_THIS_YEAR&type=csv")
			.get()
			.build();

		try (Response response = client.newCall(request).execute()) {
			assertThat(response.code()).isEqualTo(401);
		}
	}

	private int findAccountIdByName(JSONObject accounts, String name) {
		JSONArray groups = accounts.optJSONArray("children");
		if (groups == null) return -1;
		for (int i = 0; i < groups.length(); i++) {
			JSONArray groupChildren = groups.getJSONObject(i).optJSONArray("children");
			if (groupChildren == null) continue;
			for (int j = 0; j < groupChildren.length(); j++) {
				JSONObject account = groupChildren.getJSONObject(j);
				if (name.equals(account.optString("name"))) {
					return account.getInt("id");
				}
			}
		}
		return -1;
	}

	private static JSONObject findByUuid(JSONArray array, String uuid) {
		for (int i = 0; i < array.length(); i++) {
			final JSONObject obj = array.getJSONObject(i);
			if (uuid.equals(obj.optString("uuid"))) {
				return obj;
			}
		}
		return null;
	}

	private static void assertAccountsMatch(JSONArray expected, JSONArray actual) {
		for (int i = 0; i < expected.length(); i++) {
			final JSONObject exp = expected.getJSONObject(i);
			final JSONObject act = findByUuid(actual, exp.getString("uuid"));
			assertThat(act).as("Account uuid=%s should exist after restore", exp.getString("uuid")).isNotNull();
			assertThat(act.getString("name")).isEqualTo(exp.getString("name"));
			assertThat(act.getString("type")).isEqualTo(exp.getString("type"));
			assertThat(act.getString("accountType")).isEqualTo(exp.getString("accountType"));
			assertThat(act.getString("startBalance")).isEqualTo(exp.getString("startBalance"));
			assertThat(act.getString("startDate")).isEqualTo(exp.getString("startDate"));
		}
	}

	private static void assertCategoriesMatch(JSONArray expected, JSONArray actual) {
		for (int i = 0; i < expected.length(); i++) {
			final JSONObject exp = expected.getJSONObject(i);
			final JSONObject act = findByUuid(actual, exp.getString("uuid"));
			assertThat(act).as("Category uuid=%s should exist after restore", exp.getString("uuid")).isNotNull();
			assertThat(act.getString("name")).isEqualTo(exp.getString("name"));
			assertThat(act.getString("type")).isEqualTo(exp.getString("type"));
			assertThat(act.getString("periodType")).isEqualTo(exp.getString("periodType"));
		}
	}

	private static void assertTransactionsMatch(JSONArray expected, JSONArray actual) {
		for (int i = 0; i < expected.length(); i++) {
			final JSONObject exp = expected.getJSONObject(i);
			final JSONObject act = findByUuid(actual, exp.getString("uuid"));
			assertThat(act).as("Transaction uuid=%s should exist after restore", exp.getString("uuid")).isNotNull();
			assertThat(act.getString("description")).isEqualTo(exp.getString("description"));
			assertThat(act.getString("date")).isEqualTo(exp.getString("date"));

			final JSONArray expSplits = exp.getJSONArray("splits");
			final JSONArray actSplits = act.getJSONArray("splits");
			assertThat(actSplits.length()).isEqualTo(expSplits.length());
			for (int j = 0; j < expSplits.length(); j++) {
				assertThat(actSplits.getJSONObject(j).getString("amount"))
					.isEqualTo(expSplits.getJSONObject(j).getString("amount"));
				assertThat(actSplits.getJSONObject(j).getString("from"))
					.isEqualTo(expSplits.getJSONObject(j).getString("from"));
				assertThat(actSplits.getJSONObject(j).getString("to"))
					.isEqualTo(expSplits.getJSONObject(j).getString("to"));
			}
		}
	}

	private static void assertScheduledTransactionsMatch(JSONArray expected, JSONArray actual) {
		for (int i = 0; i < expected.length(); i++) {
			final JSONObject exp = expected.getJSONObject(i);
			final JSONObject act = findByUuid(actual, exp.getString("uuid"));
			assertThat(act).as("Scheduled transaction uuid=%s should exist after restore", exp.getString("uuid")).isNotNull();
			assertThat(act.getString("scheduleName")).isEqualTo(exp.getString("scheduleName"));
			assertThat(act.getString("description")).isEqualTo(exp.getString("description"));
			assertThat(act.getString("frequencyType")).isEqualTo(exp.getString("frequencyType"));
			assertThat(act.getInt("scheduleDay")).isEqualTo(exp.getInt("scheduleDay"));
			assertThat(act.getString("startDate")).isEqualTo(exp.getString("startDate"));

			final JSONArray expSplits = exp.getJSONArray("splits");
			final JSONArray actSplits = act.getJSONArray("splits");
			assertThat(actSplits.length()).isEqualTo(expSplits.length());
			for (int j = 0; j < expSplits.length(); j++) {
				assertThat(actSplits.getJSONObject(j).getString("amount"))
					.isEqualTo(expSplits.getJSONObject(j).getString("amount"));
			}
		}
	}

	private static void assertEntriesMatch(JSONArray expected, JSONArray actual) {
		for (int i = 0; i < expected.length(); i++) {
			final JSONObject exp = expected.getJSONObject(i);
			boolean found = false;
			for (int j = 0; j < actual.length(); j++) {
				final JSONObject act = actual.getJSONObject(j);
				if (exp.getString("category").equals(act.getString("category"))
						&& exp.getString("date").equals(act.getString("date"))) {
					assertThat(act.getString("amount"))
						.as("Entry amount for category=%s date=%s", exp.getString("category"), exp.getString("date"))
						.isEqualTo(exp.getString("amount"));
					found = true;
					break;
				}
			}
			assertThat(found)
				.as("Entry for category=%s date=%s should exist after restore", exp.getString("category"), exp.getString("date"))
				.isTrue();
		}
	}
}
