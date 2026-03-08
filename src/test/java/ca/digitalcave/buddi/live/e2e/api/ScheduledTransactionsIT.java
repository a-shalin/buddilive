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
public class ScheduledTransactionsIT extends BaseIT {

	private static TestHelper helper;
	private static OkHttpClient client;
	private static final String EMAIL = "scheduled-test@example.com";
	private static final String PASSWORD = "TestPassword123!";
	private static int accountId;
	private static int categoryId;
	private static long scheduledTxnId;

	@BeforeAll
	static void setUp() throws Exception {
		helper = new TestHelper(getBaseUrl(), getDbUrl());
		helper.registerUser(EMAIL, PASSWORD, "en_US", "USD");
		client = helper.login(EMAIL, PASSWORD);

		accountId = helper.createAccount(client, "Main Account", "D", "Chequing", "1000.00");
		categoryId = helper.createCategory(client, "Rent", "E", "MONTH");
	}

	@Test
	@Order(1)
	void testCreateScheduledTransaction() throws Exception {
		scheduledTxnId = helper.createScheduledTransaction(client, "Monthly Rent", "Rent Payment",
			"SCHEDULE_FREQUENCY_MONTHLY_BY_DATE", 1, "2024-01-01",
			accountId, categoryId, "1500.00");
		assertThat(scheduledTxnId).isGreaterThan(0);

		JSONObject result = helper.getScheduledTransactions(client);
		assertThat(result.getBoolean("success")).isTrue();
		JSONArray data = result.getJSONArray("data");
		boolean found = false;
		for (int i = 0; i < data.length(); i++) {
			if ("Monthly Rent".equals(data.getJSONObject(i).optString("name"))) {
				found = true;
				break;
			}
		}
		assertThat(found).isTrue();
	}

	@Test
	@Order(2)
	void testDeleteScheduledTransaction() throws Exception {
		JSONObject delete = new JSONObject();
		delete.put("action", "delete");
		delete.put("id", scheduledTxnId);

		helper.postJson(client, "/data/scheduledtransactions", delete);

		JSONObject result = helper.getScheduledTransactions(client);
		JSONArray data = result.optJSONArray("data");
		boolean found = false;
		if (data != null) {
			for (int i = 0; i < data.length(); i++) {
				if ("Monthly Rent".equals(data.getJSONObject(i).optString("name"))) {
					found = true;
					break;
				}
			}
		}
		assertThat(found).isFalse();
	}
}
