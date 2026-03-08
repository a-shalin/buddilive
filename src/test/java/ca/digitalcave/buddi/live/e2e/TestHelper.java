package ca.digitalcave.buddi.live.e2e;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.Cookie;
import okhttp3.CookieJar;
import okhttp3.FormBody;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import org.json.JSONArray;
import org.json.JSONObject;

import static org.assertj.core.api.Assertions.assertThat;

public class TestHelper {

	private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

	private final String baseUrl;
	private final String dbUrl;

	public TestHelper(String baseUrl, String dbUrl) {
		this.baseUrl = baseUrl;
		this.dbUrl = dbUrl;
	}

	public OkHttpClient newClient() {
		return new OkHttpClient.Builder()
			.cookieJar(new InMemoryCookieJar())
			.followRedirects(false)
			.connectTimeout(30, TimeUnit.SECONDS)
			.readTimeout(30, TimeUnit.SECONDS)
			.writeTimeout(30, TimeUnit.SECONDS)
			.build();
	}

	public void registerUser(String email, String password, String locale, String currency) throws Exception {
		OkHttpClient client = newClient();

		// After Liquibase migration, c3p0 may have a stale connection in the pool.
		// The first request may fail; retry once to let the pool recover.
		String activationKey = null;
		for (int attempt = 0; attempt < 3; attempt++) {
			RequestBody formBody = new FormBody.Builder()
				.add("email", email)
				.add("locale", locale)
				.add("currency", currency)
				.add("agree", "on")
				.build();

			Request request = new Request.Builder()
				.url(baseUrl + "/authentication/register")
				.post(formBody)
				.build();

			try (Response response = client.newCall(request).execute()) {
				response.body().string(); // consume body
			}

			try (Connection conn = DriverManager.getConnection(dbUrl);
				 Statement stmt = conn.createStatement();
				 ResultSet rs = stmt.executeQuery(
					 "SELECT activation_key FROM user_activations ORDER BY created DESC FETCH FIRST 1 ROWS ONLY")) {
				if (rs.next()) {
					activationKey = rs.getString(1);
					break;
				}
			}
		}
		assertThat(activationKey).as("Activation key should exist in DB after registration").isNotNull();

		RequestBody activateBody = new FormBody.Builder()
			.add("activationKey", activationKey)
			.add("password", password)
			.build();

		Request activateRequest = new Request.Builder()
			.url(baseUrl + "/authentication/activate")
			.post(activateBody)
			.build();

		try (Response response = client.newCall(activateRequest).execute()) {
			assertThat(response.code()).as("Activation should succeed").isEqualTo(200);
		}
	}

	public OkHttpClient login(String email, String password) throws IOException {
		OkHttpClient client = newClient();

		RequestBody formBody = new FormBody.Builder()
			.add("identifier", email)
			.add("password", password)
			.add("disableIpLock", "on")
			.build();

		Request request = new Request.Builder()
			.url(baseUrl + "/authentication/login")
			.post(formBody)
			.build();

		try (Response response = client.newCall(request).execute()) {
			assertThat(response.code()).as("Login should succeed").isEqualTo(200);
			String body = response.body().string();
			JSONObject json = new JSONObject(body);
			assertThat(json.getBoolean("success")).as("Login response success").isTrue();
		}

		return client;
	}

	public int createAccount(OkHttpClient client, String name, String type, String accountType, String startBalance) throws IOException {
		JSONObject json = new JSONObject();
		json.put("action", "insert");
		json.put("name", name);
		json.put("type", type);
		json.put("accountType", accountType);
		json.put("startBalance", startBalance);
		json.put("startDate", "2024-01-01");

		String responseBody = postJson(client, "/data/accounts", json);
		JSONObject result = new JSONObject(responseBody);
		assertThat(result.getBoolean("success")).isTrue();
		return findAccountIdByName(client, name);
	}

	private int findAccountIdByName(OkHttpClient client, String name) throws IOException {
		JSONObject accounts = getAccounts(client);
		JSONArray children = accounts.optJSONArray("children");
		if (children != null) {
			for (int i = 0; i < children.length(); i++) {
				JSONObject group = children.getJSONObject(i);
				JSONArray groupChildren = group.optJSONArray("children");
				if (groupChildren == null) continue;
				for (int j = 0; j < groupChildren.length(); j++) {
					JSONObject account = groupChildren.getJSONObject(j);
					if (name.equals(account.optString("name"))) {
						return account.getInt("id");
					}
				}
			}
		}
		throw new AssertionError("Account not found: " + name);
	}

	public JSONObject getAccounts(OkHttpClient client) throws IOException {
		return getJson(client, "/data/accounts");
	}

	public int createCategory(OkHttpClient client, String name, String type, String periodType) throws IOException {
		JSONObject json = new JSONObject();
		json.put("action", "insert");
		json.put("name", name);
		json.put("type", type);
		json.put("periodType", periodType);

		String responseBody = postJson(client, "/data/categories", json);
		JSONObject result = new JSONObject(responseBody);
		assertThat(result.getBoolean("success")).isTrue();
		return findCategoryIdByName(client, name, periodType);
	}

	private int findCategoryIdByName(OkHttpClient client, String name, String periodType) throws IOException {
		JSONObject categories = getCategories(client, periodType);
		return findCategoryIdInTree(categories, name);
	}

	private int findCategoryIdInTree(JSONObject node, String name) {
		if (name.equals(node.optString("name"))) {
			return node.getInt("id");
		}
		JSONArray children = node.optJSONArray("children");
		if (children != null) {
			for (int i = 0; i < children.length(); i++) {
				int id = findCategoryIdInTree(children.getJSONObject(i), name);
				if (id > 0) return id;
			}
		}
		return -1;
	}

	public JSONObject getCategories(OkHttpClient client, String periodType) throws IOException {
		return getJson(client, "/data/categories?periodType=" + periodType);
	}

	public void createTransaction(OkHttpClient client, String description, String date, int fromId, int toId, String amount) throws IOException {
		JSONObject split = new JSONObject();
		split.put("amount", amount);
		split.put("fromId", fromId);
		split.put("toId", toId);

		JSONArray splits = new JSONArray();
		splits.put(split);

		JSONObject json = new JSONObject();
		json.put("action", "insert");
		json.put("description", description);
		json.put("date", date);
		json.put("splits", splits);

		String responseBody = postJson(client, "/data/transactions", json);
		JSONObject result = new JSONObject(responseBody);
		assertThat(result.getBoolean("success")).isTrue();
	}

	public JSONObject getTransactions(OkHttpClient client, int sourceId) throws IOException {
		return getJson(client, "/data/transactions?source=" + sourceId + "&start=0&limit=250");
	}

	public long createScheduledTransaction(OkHttpClient client, String name, String description,
			String repeat, int scheduleDay, String startDate, int fromId, int toId, String amount) throws IOException {
		JSONObject split = new JSONObject();
		split.put("amount", amount);
		split.put("fromId", fromId);
		split.put("toId", toId);

		JSONArray splits = new JSONArray();
		splits.put(split);

		JSONObject transaction = new JSONObject();
		transaction.put("description", description);
		transaction.put("splits", splits);

		JSONObject json = new JSONObject();
		json.put("action", "insert");
		json.put("name", name);
		json.put("description", description);
		json.put("repeat", repeat);
		json.put("scheduleDay", scheduleDay);
		json.put("scheduleWeek", 0);
		json.put("scheduleMonth", 0);
		json.put("start", startDate);
		json.put("end", "");
		json.put("lastCreatedDate", "");
		json.put("transaction", transaction);

		String responseBody = postJson(client, "/data/scheduledtransactions", json);
		JSONObject result = new JSONObject(responseBody);
		assertThat(result.getBoolean("success")).isTrue();
		return findScheduledTransactionIdByName(client, name);
	}

	private long findScheduledTransactionIdByName(OkHttpClient client, String name) throws IOException {
		JSONObject result = getScheduledTransactions(client);
		JSONArray data = result.getJSONArray("data");
		for (int i = 0; i < data.length(); i++) {
			JSONObject st = data.getJSONObject(i);
			if (name.equals(st.optString("name"))) {
				return st.getLong("id");
			}
		}
		throw new AssertionError("Scheduled transaction not found: " + name);
	}

	public JSONObject getScheduledTransactions(OkHttpClient client) throws IOException {
		return getJson(client, "/data/scheduledtransactions");
	}

	public JSONObject getUserPreferences(OkHttpClient client) throws IOException {
		return getJson(client, "/data/userpreferences");
	}

	public void updateUserPreferences(OkHttpClient client, JSONObject prefs) throws IOException {
		prefs.put("action", "update");
		String responseBody = postJson(client, "/data/userpreferences", prefs);
		JSONObject result = new JSONObject(responseBody);
		assertThat(result.getBoolean("success")).isTrue();
	}

	public byte[] getBackup(OkHttpClient client) throws IOException {
		Request request = new Request.Builder()
			.url(baseUrl + "/data/backup")
			.get()
			.build();

		try (Response response = client.newCall(request).execute()) {
			assertThat(response.code()).isEqualTo(200);
			return response.body().bytes();
		}
	}

	public String postJson(OkHttpClient client, String path, JSONObject json) throws IOException {
		RequestBody body = RequestBody.create(json.toString(), JSON);
		Request request = new Request.Builder()
			.url(baseUrl + path)
			.post(body)
			.build();

		try (Response response = client.newCall(request).execute()) {
			String responseBody = response.body().string();
			assertThat(response.code()).as("POST %s should succeed: %s", path, responseBody).isEqualTo(200);
			return responseBody;
		}
	}

	public JSONObject getJson(OkHttpClient client, String path) throws IOException {
		Request request = new Request.Builder()
			.url(baseUrl + path)
			.get()
			.build();

		try (Response response = client.newCall(request).execute()) {
			String responseBody = response.body().string();
			assertThat(response.code()).as("GET %s should succeed: %s", path, responseBody).isEqualTo(200);
			return new JSONObject(responseBody);
		}
	}

	public int getResponseCode(OkHttpClient client, String method, String path) throws IOException {
		Request.Builder builder = new Request.Builder().url(baseUrl + path);
		if ("POST".equals(method)) {
			builder.post(RequestBody.create("{}", JSON));
		}

		try (Response response = client.newCall(builder.build()).execute()) {
			return response.code();
		}
	}

	static class InMemoryCookieJar implements CookieJar {
		private final List<Cookie> cookies = new ArrayList<>();

		@Override
		public void saveFromResponse(HttpUrl url, List<Cookie> cookies) {
			for (Cookie c : cookies) {
				// Remove existing cookie with same name/domain/path before adding
				this.cookies.removeIf(existing ->
					existing.name().equals(c.name())
					&& existing.domain().equals(c.domain())
					&& existing.path().equals(c.path()));
				// Rebuild without the Secure flag so cookies work over HTTP in tests
				this.cookies.add(new Cookie.Builder()
					.name(c.name())
					.value(c.value())
					.domain(c.domain())
					.path(c.path())
					.expiresAt(c.expiresAt())
					.httpOnly()
					.build());
			}
		}

		@Override
		public List<Cookie> loadForRequest(HttpUrl url) {
			List<Cookie> result = new ArrayList<>();
			for (Cookie cookie : cookies) {
				if (cookie.expiresAt() >= System.currentTimeMillis()) {
					result.add(cookie);
				}
			}
			return result;
		}
	}
}
