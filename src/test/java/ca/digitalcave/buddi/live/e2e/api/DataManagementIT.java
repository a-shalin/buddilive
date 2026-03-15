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
	void testBackup() throws Exception {
		helper.createAccount(client, "Backup Test Account", "D", "Chequing", "100.00");

		byte[] backup = helper.getBackup(client);
		assertThat(backup).isNotNull();
		assertThat(backup.length).isGreaterThan(0);
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
}
