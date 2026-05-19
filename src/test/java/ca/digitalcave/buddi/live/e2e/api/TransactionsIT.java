package ca.digitalcave.buddi.live.e2e.api;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.InterruptedIOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import ca.digitalcave.buddi.live.controller.GlobalExceptionHandler;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterAll;
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
	private static final Logger globalExceptionLogger = Logger.getLogger(GlobalExceptionHandler.class.getName());
	private static final List<LogRecord> globalExceptionLogRecords = Collections.synchronizedList(new ArrayList<>());
	private static Handler globalExceptionLogHandler;

	@BeforeAll
	static void setUp() throws Exception {
		helper = new TestHelper(getBaseUrl(), getDbUrl());
		helper.registerUser(EMAIL, PASSWORD, "en_US", "USD");
		client = helper.login(EMAIL, PASSWORD);

		accountId = helper.createAccount(client, "Chequing", "D", "Chequing", "1000.00");
		account2Id = helper.createAccount(client, "Savings", "D", "Savings", "5000.00");
		categoryId = helper.createCategory(client, "Food", "E", "MONTH");

		globalExceptionLogHandler = new Handler() {
			@Override
			public void publish(final LogRecord record) {
				globalExceptionLogRecords.add(record);
			}

			@Override
			public void flush() {
			}

			@Override
			public void close() {
			}
		};
		globalExceptionLogger.addHandler(globalExceptionLogHandler);
	}

	@AfterAll
	static void tearDown() {
		if (globalExceptionLogHandler != null) {
			globalExceptionLogger.removeHandler(globalExceptionLogHandler);
		}
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
	void testCreateTransactionWithNumericAmount() throws Exception {
		JSONObject split = new JSONObject();
		split.put("amount", 100000);
		split.put("fromId", accountId);
		split.put("toId", categoryId);

		JSONArray splits = new JSONArray();
		splits.put(split);

		JSONObject json = new JSONObject();
		json.put("action", "insert");
		json.put("description", "Numeric Amount Test");
		json.put("date", "2024-03-15");
		json.put("splits", splits);

		String responseBody = helper.postJson(client, "/data/transactions", json);
		JSONObject result = new JSONObject(responseBody);
		assertThat(result.getBoolean("success")).isTrue();

		JSONObject txns = helper.getTransactions(client, accountId);
		JSONArray data = txns.getJSONArray("data");
		boolean found = false;
		for (int i = 0; i < data.length(); i++) {
			if ("Numeric Amount Test".equals(data.getJSONObject(i).optString("description"))) {
				found = true;
				break;
			}
		}
		assertThat(found).as("Transaction with numeric amount should appear in list").isTrue();
	}

	@Test
	@Order(3)
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
	@Order(4)
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
	@Order(5)
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

	@Test
	@Order(6)
	void testDescriptionsEndpointIncludesTransactionTemplate() throws Exception {
		final String description = "Descriptions Endpoint Smoke";
		helper.createTransaction(client, description, "2024-03-19", accountId, categoryId, "42.00");

		final JSONObject descriptions = helper.getJson(client, "/data/transactions/descriptions");
		assertThat(descriptions.getBoolean("success")).isTrue();
		final JSONArray data = descriptions.getJSONArray("data");

		JSONObject found = null;
		for (int i = 0; i < data.length(); i++) {
			final JSONObject item = data.getJSONObject(i);
			if (description.equals(item.optString("value"))) {
				found = item;
				break;
			}
		}

		assertThat(found).isNotNull();
		final JSONObject transaction = found.getJSONObject("transaction");
		assertThat(transaction.getString("description")).isEqualTo(description);
		assertThat(transaction.getJSONArray("splits").length()).isGreaterThan(0);
	}

	@Test
	@Order(7)
	void testDescriptionsEndpointStillWorksAfterEarlyClientClose() throws Exception {
		for (int i = 0; i < 300; i++) {
			helper.createTransaction(client, "Descriptions Abort " + i + " " + "x".repeat(256), "2024-03-20", accountId, categoryId, "1.00");
		}

		final int unhandledExceptionsBefore = countUnhandledExceptions();
		final Request request = new Request.Builder()
			.url(getBaseUrl() + "/data/transactions/descriptions")
			.header("Accept-Encoding", "gzip")
			.get()
			.build();

		final OkHttpClient abortClient = client.newBuilder()
			.readTimeout(1, TimeUnit.MILLISECONDS)
			.build();

		for (int i = 0; i < 25; i++) {
			try (Response response = abortClient.newCall(request).execute()) {
				if (response.body() != null) {
					response.body().byteStream().readNBytes(64);
				}
			}
			catch (InterruptedIOException ignored) {
			}
		}

		final JSONObject descriptions = helper.getJson(client, "/data/transactions/descriptions");
		assertThat(descriptions.getBoolean("success")).isTrue();
		assertThat(descriptions.getJSONArray("data").length()).isGreaterThan(0);
		assertThat(countUnhandledExceptions()).isEqualTo(unhandledExceptionsBefore);
	}

	@Test
	@Order(8)
	void testTransactionPagesDoNotOverlap() throws Exception {
		final String suffix = String.valueOf(System.currentTimeMillis());
		final int pagedAccountId = helper.createAccount(client, "Paging Account " + suffix, "D", "Chequing", "0.00");
		for (int i = 0; i < 205; i++) {
			helper.createTransaction(client, "Paging Tx " + suffix + " #" + i, "2024-03-20", pagedAccountId, categoryId, "1.00");
		}

		final JSONObject firstPage = helper.getJson(client, "/data/transactions?source=" + pagedAccountId + "&start=0&limit=100");
		final JSONObject secondPage = helper.getJson(client, "/data/transactions?source=" + pagedAccountId + "&start=100&limit=100");

		assertThat(firstPage.getBoolean("success")).isTrue();
		assertThat(secondPage.getBoolean("success")).isTrue();
		assertThat(firstPage.getInt("total")).isEqualTo(205);
		assertThat(secondPage.getInt("total")).isEqualTo(205);
		assertThat(firstPage.getJSONArray("data").length()).isEqualTo(100);
		assertThat(secondPage.getJSONArray("data").length()).isEqualTo(100);

		final Set<Long> firstPageIds = extractTransactionIds(firstPage.getJSONArray("data"));
		final Set<Long> secondPageIds = extractTransactionIds(secondPage.getJSONArray("data"));
		firstPageIds.retainAll(secondPageIds);
		assertThat(firstPageIds).as("Transaction IDs should not overlap across pages").isEmpty();
	}

	@Test
	@Order(9)
	void testCreateTransactionWithClientUuidIsIdempotent() throws Exception {
		final String uuid = UUID.randomUUID().toString();
		final JSONObject json = new JSONObject();
		json.put("action", "insert");
		json.put("uuid", uuid);
		json.put("description", "Client UUID Transaction");
		json.put("date", "2024-03-21");

		final JSONObject split = new JSONObject();
		split.put("amount", "33.00");
		split.put("fromId", accountId);
		split.put("toId", categoryId);

		final JSONArray splits = new JSONArray();
		splits.put(split);
		json.put("splits", splits);

		final JSONObject firstResponse = new JSONObject(helper.postJson(client, "/data/transactions", json));
		final JSONObject secondResponse = new JSONObject(helper.postJson(client, "/data/transactions", json));

		assertThat(firstResponse.getBoolean("success")).isTrue();
		assertThat(firstResponse.getString("uuid")).isEqualTo(uuid);
		assertThat(firstResponse.getLong("id")).isPositive();
		assertThat(secondResponse.getBoolean("success")).isTrue();
		assertThat(secondResponse.getString("uuid")).isEqualTo(uuid);
		assertThat(secondResponse.getLong("id")).isEqualTo(firstResponse.getLong("id"));

		final JSONArray data = helper.getTransactions(client, accountId).getJSONArray("data");
		int matchingTransactions = 0;
		for (int i = 0; i < data.length(); i++) {
			final JSONObject transaction = data.getJSONObject(i);
			if (uuid.equals(transaction.optString("uuid"))) {
				matchingTransactions++;
				assertThat(transaction.getLong("id")).isEqualTo(firstResponse.getLong("id"));
			}
		}
		assertThat(matchingTransactions).isEqualTo(1);
	}

	private static int countUnhandledExceptions() {
		int count = 0;
		synchronized (globalExceptionLogRecords) {
			for (LogRecord record : globalExceptionLogRecords) {
				if ("Unhandled exception".equals(record.getMessage()) && record.getLevel().intValue() >= Level.WARNING.intValue()) {
					count++;
				}
			}
		}
		return count;
	}

	private static Set<Long> extractTransactionIds(final JSONArray data) {
		final Set<Long> ids = new HashSet<>();
		for (int i = 0; i < data.length(); i++) {
			ids.add(data.getJSONObject(i).getLong("id"));
		}
		return ids;
	}
}
