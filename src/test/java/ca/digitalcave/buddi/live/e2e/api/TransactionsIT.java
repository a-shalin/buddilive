package ca.digitalcave.buddi.live.e2e.api;

import okhttp3.OkHttpClient;

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
public class TransactionsIT extends BaseIT {

	private static TestHelper helper;
	private static OkHttpClient client;
	private static final String EMAIL = "transactions-test@example.com";
	private static final String PASSWORD = "TestPassword123!";
	private static int accountId;
	private static int account2Id;
	private static int categoryId;

	@BeforeAll
	static void setUp() throws Exception {
		helper = new TestHelper(getBaseUrl(), getDbUrl());
		helper.registerUser(EMAIL, PASSWORD, "en_US", "USD");
		client = helper.login(EMAIL, PASSWORD);

		accountId = helper.createAccount(client, "Chequing", "D", "Chequing", "1000.00");
		account2Id = helper.createAccount(client, "Savings", "D", "Savings", "5000.00");
		categoryId = helper.createCategory(client, "Food", "E", "MONTH");
	}

	@Test
	@Order(1)
	void testCreateTransaction() throws Exception {
		helper.createTransaction(client, "Grocery Store", "2024-03-15", accountId, categoryId, "50.00");

		JSONObject txns = helper.getTransactions(client, accountId);
		assertThat(txns.getBoolean("success")).isTrue();
		JSONArray data = txns.getJSONArray("data");
		assertThat(data.length()).isGreaterThanOrEqualTo(1);

		boolean found = false;
		for (int i = 0; i < data.length(); i++) {
			if ("Grocery Store".equals(data.getJSONObject(i).optString("description"))) {
				found = true;
				break;
			}
		}
		assertThat(found).as("Transaction should appear in list").isTrue();
	}

	@Test
	@Order(2)
	void testCreateTransfer() throws Exception {
		helper.createTransaction(client, "Transfer to Savings", "2024-03-16", accountId, account2Id, "200.00");

		JSONObject txns = helper.getTransactions(client, accountId);
		JSONArray data = txns.getJSONArray("data");
		boolean found = false;
		for (int i = 0; i < data.length(); i++) {
			if ("Transfer to Savings".equals(data.getJSONObject(i).optString("description"))) {
				found = true;
				break;
			}
		}
		assertThat(found).isTrue();
	}

	@Test
	@Order(3)
	void testUpdateTransaction() throws Exception {
		helper.createTransaction(client, "Old Description", "2024-03-17", accountId, categoryId, "25.00");

		JSONObject txns = helper.getTransactions(client, accountId);
		JSONArray data = txns.getJSONArray("data");
		long txnId = -1;
		for (int i = 0; i < data.length(); i++) {
			if ("Old Description".equals(data.getJSONObject(i).optString("description"))) {
				txnId = data.getJSONObject(i).getLong("id");
				break;
			}
		}
		assertThat(txnId).isGreaterThan(0);

		JSONObject split = new JSONObject();
		split.put("amount", "25.00");
		split.put("fromId", accountId);
		split.put("toId", categoryId);

		JSONArray splits = new JSONArray();
		splits.put(split);

		JSONObject update = new JSONObject();
		update.put("action", "update");
		update.put("id", String.valueOf(txnId));
		update.put("description", "Updated Description");
		update.put("date", "2024-03-17");
		update.put("splits", splits);

		helper.postJson(client, "/data/transactions", update);

		txns = helper.getTransactions(client, accountId);
		data = txns.getJSONArray("data");
		boolean found = false;
		for (int i = 0; i < data.length(); i++) {
			if ("Updated Description".equals(data.getJSONObject(i).optString("description"))) {
				found = true;
				break;
			}
		}
		assertThat(found).isTrue();
	}

	@Test
	@Order(4)
	void testDeleteTransaction() throws Exception {
		helper.createTransaction(client, "To Delete", "2024-03-18", accountId, categoryId, "10.00");

		JSONObject txns = helper.getTransactions(client, accountId);
		JSONArray data = txns.getJSONArray("data");
		long txnId = -1;
		for (int i = 0; i < data.length(); i++) {
			if ("To Delete".equals(data.getJSONObject(i).optString("description"))) {
				txnId = data.getJSONObject(i).getLong("id");
				break;
			}
		}
		assertThat(txnId).isGreaterThan(0);

		JSONObject delete = new JSONObject();
		delete.put("action", "delete");
		delete.put("id", txnId);

		helper.postJson(client, "/data/transactions", delete);

		txns = helper.getTransactions(client, accountId);
		data = txns.getJSONArray("data");
		boolean found = false;
		for (int i = 0; i < data.length(); i++) {
			if ("To Delete".equals(data.getJSONObject(i).optString("description"))) {
				found = true;
				break;
			}
		}
		assertThat(found).as("Deleted transaction should not appear").isFalse();
	}
}
