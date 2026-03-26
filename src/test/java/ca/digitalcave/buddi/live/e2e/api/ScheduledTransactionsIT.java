package ca.digitalcave.buddi.live.e2e.api;

import okhttp3.MediaType;
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
public class ScheduledTransactionsIT extends BaseIT {

	private static final MediaType TEXT = MediaType.get("text/plain; charset=utf-8");

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
	void testExecuteScheduledTransactionsEndpoint() throws Exception {
		final Request request = new Request.Builder()
			.url(getBaseUrl() + "/data/scheduledtransactions/execute")
			.post(RequestBody.create("2024-03-20", TEXT))
			.build();

		try (Response response = client.newCall(request).execute()) {
			assertThat(response.code()).isEqualTo(200);
			final JSONObject body = new JSONObject(response.body().string());
			assertThat(body.getBoolean("success")).isTrue();
			assertThat(body.has("messages")).isTrue();
		}
	}

	@Test
	@Order(3)
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
