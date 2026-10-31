package ca.digitalcave.buddi.live.e2e.api;

import ca.digitalcave.buddi.live.api.dto.TransactionSuggestionDto;
import ca.digitalcave.buddi.live.controller.Action;
import ca.digitalcave.buddi.live.e2e.BaseIT;
import ca.digitalcave.buddi.live.e2e.TestHelper;
import ca.digitalcave.buddi.live.service.TransactionSuggestionBuckets;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DescriptionsSyncIT extends BaseIT {
	private static final String EMAIL_DOMAIN = "@example.com";
	private static final String LATEST_DATE = "2024-03-01";
	private static final String LATEST_AMOUNT = "20.00";
	private static final String INITIAL_AMOUNT = "10.00";
	private static final String EN_US = "en_US";
	private static final String USD = "USD";
	private static final String CHECKING = "Checking";
	private static final String MODE = "mode";
	private static final String FULL = "full";
	private static final String VERSION = "version";
	private static final String BUCKET_HASHES = "bucketHashes";
	private static final String ID = "id";
	private static final String ACTION = "action";
	private static final String DESCRIPTION = "description";
	private static final String APPLICATION_JSON = "application/json";
	private static final String SPLITS = "splits";
	private static final String SYNC = "/data/transactions/descriptions/sync";
	private static final String MUTATE = "/data/transactions?includeSuggestions=true";
	private static final String COFFEE = "Coffee";
	private static final String TEA = "Tea";
	private static final String PASSWORD = "TestPassword123!";
	private static final String ITEMS = "items";
	private static final String SUGGESTIONS = "suggestions";
	private static final String UPSERTS = "upserts";
	private static final String AMOUNT = "amountNumber";

	@Test
	void syncAndMutationPatchesPreserveCanonicalRepresentativesAndIsolation() throws Exception {
		final TestHelper helper = new TestHelper(getBaseUrl(), getDbUrl());
		final String email = "suggestions-" + UUID.randomUUID() + EMAIL_DOMAIN;
		helper.registerUser(email, PASSWORD, EN_US, USD);
		final OkHttpClient client = helper.login(email, PASSWORD);
		final int account = helper.createAccount(client, CHECKING, "D", CHECKING, "1000.00");
		final int category = helper.createCategory(client, "Food", "E", "MONTH");
		final JSONObject bootstrap = sync(helper, client, null);
		assertThat(bootstrap.getString(MODE)).isEqualTo(FULL);
		assertThat(bootstrap.getJSONArray(ITEMS).length()).isZero();
		assertThat(sync(helper, client, manifest(bootstrap.getJSONArray(ITEMS))).getString(MODE)).isEqualTo("unchanged");
		assertStatus(client, new JSONObject().put(VERSION, 99), 400);
		assertStatus(client, new JSONObject().put(VERSION, 1).put(BUCKET_HASHES, "invalid"), 400);
		assertStatus(new OkHttpClient(), new JSONObject().put(VERSION, 1), 401);

		final JSONObject oldest = new JSONObject(helper.postJson(client, "/data/transactions",
				mutation(COFFEE, "2023-01-01", "5.00", account, category)));
		assertThat(oldest.has(SUGGESTIONS)).isFalse();

		final JSONObject original = mutation(COFFEE, "2024-01-01", INITIAL_AMOUNT, account, category);
		final JSONObject first = post(helper, client, original);
		assertAmount(first, INITIAL_AMOUNT);
		final JSONObject latest = mutation(COFFEE, LATEST_DATE, LATEST_AMOUNT, account, category);
		final JSONObject second = post(helper, client, latest);
		assertAmount(second, LATEST_AMOUNT);
		assertThat(post(helper, client, latest).getLong(ID)).isEqualTo(second.getLong(ID));
		final JSONObject olderEdit = mutation(COFFEE, "2024-02-01", "15.00", account, category)
				.put(ACTION, Action.UPDATE.getValue()).put(ID, first.getLong(ID));
		assertAmount(post(helper, client, olderEdit), LATEST_AMOUNT);
		final JSONObject beforeRename = sync(helper, client, null);
		final String beforeManifest = manifest(beforeRename.getJSONArray(ITEMS));
		final JSONObject rename = mutation(TEA, LATEST_DATE, "30.00", account, category)
				.put(ACTION, Action.UPDATE.getValue()).put(ID, second.getLong(ID))
				.put("suggestionKeys", new JSONArray(List.of(COFFEE, TEA)));
		assertThat(post(helper, client, rename).getJSONObject(SUGGESTIONS).getJSONArray(UPSERTS).length()).isEqualTo(2);
		final JSONObject replay = post(helper, client, rename);
		assertThat(replay.getJSONObject(SUGGESTIONS).getJSONArray(UPSERTS).length()).isEqualTo(2);
		final JSONObject changed = sync(helper, client, beforeManifest);
		assertThat(changed.getString(MODE)).isIn(FULL, "buckets");
		final JSONObject deletion = post(helper, client, new JSONObject().put(ACTION, Action.DELETE.getValue()).put(ID, second.getLong(ID)));
		assertThat(deletion.getJSONObject(SUGGESTIONS).getJSONArray("deletes").toList()).containsExactly(TEA);
		final JSONObject restored = post(helper, client, new JSONObject().put(ACTION, Action.DELETE.getValue()).put(ID, first.getLong(ID)));
		assertThat(restored.getJSONObject(SUGGESTIONS).getJSONArray(UPSERTS).getJSONObject(0).getJSONArray(SPLITS)
				.getJSONObject(0).getBigDecimal(AMOUNT)).isEqualByComparingTo("5");
		assertThat(sync(helper, client, null).getJSONArray(ITEMS).getJSONObject(0).getString(DESCRIPTION)).isEqualTo(COFFEE);
		assertThat(helper.getJson(client, "/data/transactions/descriptions").getJSONArray("data").length()).isEqualTo(1);

		final String otherEmail = "suggestions-other-" + UUID.randomUUID() + EMAIL_DOMAIN;
		helper.registerUser(otherEmail, PASSWORD, EN_US, USD);
		final OkHttpClient other = helper.login(otherEmail, PASSWORD);
		assertThat(sync(helper, other, beforeManifest).getJSONArray(ITEMS).length()).isZero();
		try (final Response response = client.newCall(new Request.Builder().url(getBaseUrl() + SYNC)
				.post(RequestBody.create("{\"version\":1}", MediaType.get(APPLICATION_JSON))).build()).execute()) {
			assertThat(response.header("Cache-Control")).contains("no-store");
		}
	}

	private static JSONObject mutation(final String description, final String date, final String amount, final int from, final int to) {
		return new JSONObject().put(ACTION, Action.INSERT.getValue()).put("uuid", UUID.randomUUID().toString())
				.put(DESCRIPTION, description).put("date", date).put("number", "")
				.put(SPLITS, new JSONArray().put(new JSONObject().put("amount", amount).put("fromId", from).put("toId", to)));
	}

	private static JSONObject post(final TestHelper helper, final OkHttpClient client, final JSONObject mutation) throws Exception {
		return new JSONObject(helper.postJson(client, MUTATE, mutation));
	}

	private static void assertAmount(final JSONObject response, final String amount) {
		assertThat(response.getJSONObject(SUGGESTIONS).getJSONArray(UPSERTS).getJSONObject(0).getJSONArray(SPLITS)
				.getJSONObject(0).getBigDecimal(AMOUNT)).isEqualByComparingTo(amount);
	}

	private static JSONObject sync(final TestHelper helper, final OkHttpClient client, final String manifest) throws Exception {
		return new JSONObject(helper.postJson(client, SYNC, new JSONObject().put(VERSION, 1).put(BUCKET_HASHES, manifest)));
	}

	private static String manifest(final JSONArray items) throws Exception {
		final var records = new ObjectMapper().readerForListOf(TransactionSuggestionDto.class).<List<TransactionSuggestionDto>>readValue(items.toString());
		final var buckets = TransactionSuggestionBuckets.partition(records);
		final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		for (int id = 0; id < TransactionSuggestionBuckets.COUNT; id++) bytes.write(TransactionSuggestionBuckets.hash(id, buckets.get(id)));
		return Base64.getEncoder().encodeToString(bytes.toByteArray());
	}

	private static void assertStatus(final OkHttpClient client, final JSONObject json, final int status) throws Exception {
		try (final Response response = client.newCall(new Request.Builder().url(getBaseUrl() + SYNC)
				.post(RequestBody.create(json.toString(), MediaType.get(APPLICATION_JSON))).build()).execute()) {
			assertThat(response.code()).isEqualTo(status);
		}
	}
}
