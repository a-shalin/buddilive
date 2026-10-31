package ca.digitalcave.buddi.live.e2e.api;

import ca.digitalcave.buddi.live.api.dto.TransactionRecordDto;
import ca.digitalcave.buddi.live.api.dto.TransactionResponseDto;
import ca.digitalcave.buddi.live.api.dto.TransactionsSyncResponseDto;
import ca.digitalcave.buddi.live.api.dto.TransactionsSyncResponseDto.Mode;
import ca.digitalcave.buddi.live.api.dto.request.SplitRequestDto;
import ca.digitalcave.buddi.live.api.dto.request.TransactionRequestDto;
import ca.digitalcave.buddi.live.api.dto.request.TransactionsSyncRequestDto;
import ca.digitalcave.buddi.live.controller.Action;
import ca.digitalcave.buddi.live.e2e.BaseIT;
import ca.digitalcave.buddi.live.e2e.TestHelper;
import ca.digitalcave.buddi.live.service.TransactionBuckets;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TransactionsSyncIT extends BaseIT {
	private static final String MONTH = "MONTH";
	private static final String FIRST_DATE = "2024-01-01";
	private static final String SYNC = "/data/transactions/sync";
	private static final String TRANSACTIONS = "/data/transactions";
	private static final String PASSWORD = "TestPassword123!";
	private static final String EMAIL_DOMAIN = "@example.com";
	private static final String SAVINGS = "Savings";
	private static final String ACCOUNT_NAME = "Checking";
	private static final String SOURCE_NAME = "Food";
	private static final String UPDATED_NAME = "Updated";
	private static final String INITIAL_BALANCE = "1000.00";
	private static final String ZERO_BALANCE = "0.00";
	private static final String INITIAL_AMOUNT = "1.00";
	private static final String UPDATED_AMOUNT = "10.00";
	private static final String EN_US = "en_US";
	private static final String USD = "USD";
	private static final ObjectMapper MAPPER = new ObjectMapper();

	@Test
	void mutationAcknowledgementsMatchAccountHashesAndPreserveReplayIdentity() throws Exception {
		final TestHelper helper = new TestHelper(getBaseUrl(), getDbUrl());
		final String email = "transaction-ack-" + UUID.randomUUID() + EMAIL_DOMAIN;
		helper.registerUser(email, PASSWORD, EN_US, USD);
		final OkHttpClient client = helper.login(email, PASSWORD);
		final int account = helper.createAccount(client, ACCOUNT_NAME, SourceType.DEBIT.code, ACCOUNT_NAME, INITIAL_BALANCE);
		final int other = helper.createAccount(client, SAVINGS, SourceType.DEBIT.code, SAVINGS, ZERO_BALANCE);
		final int category = helper.createCategory(client, SOURCE_NAME, SourceType.EXPENSE.code, MONTH);
		final String uuid = UUID.randomUUID().toString();
		final var insert = new TransactionRequestDto(Action.INSERT, null, uuid, SOURCE_NAME, null, FIRST_DATE, null,
				List.of(new SplitRequestDto(null, null, INITIAL_AMOUNT, account, category, SOURCE_NAME),
						new SplitRequestDto(null, null, UPDATED_AMOUNT, other, account, SAVINGS)), null);
		final var acknowledged = acknowledge(helper, client, insert);
		final var record = acknowledged.transaction();
		assertThat(acknowledged.previousSourceIds()).isEmpty();
		assertThat(record.id()).isEqualTo(acknowledged.id());
		assertThat(record.uuid()).isEqualTo(uuid);
		assertThat(record.splits()).hasSize(2).allSatisfy(split -> assertThat(split.id()).isPositive());
		assertThat(record.splits().getFirst().id()).isGreaterThan(record.splits().getLast().id());
		assertThat(record.splits().getLast().amountNumber()).isEqualByComparingTo(INITIAL_AMOUNT);
		assertThat(record.splits().getLast().memo()).isEqualTo(SOURCE_NAME);
		final var otherRecord = new TransactionRecordDto(record.id(), record.uuid(), record.dateIso(), record.description(), record.number(),
				record.splits().stream().filter(split -> split.fromId() == other || split.toId() == other).toList());
		final var matched = sync(helper, client, List.of(
				new TransactionsSyncRequestDto.Account(account, TransactionBuckets.manifest(account, List.of(record))),
				new TransactionsSyncRequestDto.Account(other, TransactionBuckets.manifest(other, List.of(otherRecord)))));
		assertThat(matched.accounts()).allSatisfy(page -> {
			assertThat(page.mode()).isEqualTo(Mode.unchanged);
			assertThat(page.total()).isEqualTo(1);
			assertThat(page.items()).isNull();
			assertThat(page.buckets()).isNull();
		});
		assertThat(acknowledge(helper, client, insert).transaction()).isEqualTo(record);
		final var update = new TransactionRequestDto(Action.UPDATE, record.id(), null, UPDATED_NAME, null, FIRST_DATE, null,
				List.of(new SplitRequestDto(null, null, UPDATED_AMOUNT, account, category, null)), null);
		final var edited = acknowledge(helper, client, update);
		assertThat(edited.previousSourceIds()).containsExactlyInAnyOrder(account, other, category);
		assertThat(edited.transaction().uuid()).isEqualTo(uuid);
		assertThat(edited.transaction().splits()).hasSize(1);
		assertThat(edited.transaction().splits().getFirst().id()).isPositive();
		final var afterEdit = sync(helper, client, List.of(
				new TransactionsSyncRequestDto.Account(account, TransactionBuckets.manifest(account, List.of(edited.transaction()))),
				new TransactionsSyncRequestDto.Account(other, TransactionBuckets.manifest(other, List.of()))));
		assertThat(afterEdit.accounts()).allSatisfy(page -> assertThat(page.mode()).isEqualTo(Mode.unchanged));
		assertThat(afterEdit.accounts().getLast().total()).isZero();
		final var deleted = acknowledge(helper, client,
				new TransactionRequestDto(Action.DELETE, record.id(), null, null, null, null, null, null, null));
		assertThat(deleted.transaction()).isNull();
		assertThat(deleted.previousSourceIds()).containsExactlyInAnyOrder(account, category);
		final var empty = sync(helper, client, List.of(new TransactionsSyncRequestDto.Account(account, TransactionBuckets.manifest(account, List.of()))));
		assertThat(empty.accounts().getFirst().mode()).isEqualTo(Mode.unchanged);
		assertThat(empty.accounts().getFirst().total()).isZero();
		final var legacy = new JSONObject(helper.postJson(client, TRANSACTIONS, new JSONObject(MAPPER.writeValueAsString(insert))));
		assertThat(legacy.has("transaction")).isFalse();
		assertThat(legacy.has("previousSourceIds")).isFalse();
	}

	@Test
	void batchesAccountsAndHandlesWindowChangesAndOlderEdits() throws Exception {
		final TestHelper helper = new TestHelper(getBaseUrl(), getDbUrl());
		final String email = "transaction-sync-" + UUID.randomUUID() + EMAIL_DOMAIN;
		helper.registerUser(email, PASSWORD, EN_US, USD);
		final OkHttpClient client = helper.login(email, PASSWORD);
		final int account = helper.createAccount(client, ACCOUNT_NAME, SourceType.DEBIT.code, ACCOUNT_NAME, INITIAL_BALANCE);
		final int other = helper.createAccount(client, SAVINGS, SourceType.DEBIT.code, SAVINGS, ZERO_BALANCE);
		final int category = helper.createCategory(client, SOURCE_NAME, SourceType.EXPENSE.code, MONTH);
		final List<Long> ids = new ArrayList<>();
		for (int index = 0; index <= TransactionBuckets.LIMIT; index++) {
			ids.add(mutate(helper, client, Action.INSERT, null, "Transaction " + index,
					LocalDate.of(2024, 1, 1).plusDays(index).toString(), INITIAL_AMOUNT, account, category).id());
		}
		final var initial = sync(helper, client, List.of(new TransactionsSyncRequestDto.Account(account, null), new TransactionsSyncRequestDto.Account(other, null)));
		assertThat(initial.accounts()).hasSize(2);
		final var page = initial.accounts().getFirst();
		assertThat(page.mode()).isEqualTo(Mode.full);
		assertThat(page.items()).hasSize(TransactionBuckets.LIMIT);
		assertThat(page.total()).isEqualTo(101);
		assertThat(initial.accounts().getLast().items()).isEmpty();
		assertThat(initial.sourceNames().get(category)).isEqualTo(SOURCE_NAME);
		final String manifest = TransactionBuckets.manifest(account, page.items());
		assertThat(manifest).hasSize(684);
		final var unchanged = sync(helper, client, List.of(new TransactionsSyncRequestDto.Account(account, manifest))).accounts().getFirst();
		assertThat(unchanged.mode()).isEqualTo(Mode.unchanged);
		assertThat(unchanged.items()).isNull();

		helper.postJson(client, "/data/categories", new JSONObject().put("action", Action.UPDATE.getValue()).put("id", category)
				.put("name", UPDATED_NAME).put("type", SourceType.EXPENSE.code).put("periodType", MONTH));
		final var renamed = sync(helper, client, List.of(new TransactionsSyncRequestDto.Account(account, manifest)));
		assertThat(renamed.accounts().getFirst().mode()).isEqualTo(Mode.unchanged);
		assertThat(renamed.sourceNames().get(category)).isEqualTo(UPDATED_NAME);

		// Updating an older amount changes later running balances, but no recent transaction content.
		mutate(helper, client, Action.UPDATE, ids.getFirst(), UPDATED_NAME, FIRST_DATE, UPDATED_AMOUNT, account, category);
		final var older = sync(helper, client, List.of(new TransactionsSyncRequestDto.Account(account, manifest))).accounts().getFirst();
		assertThat(older.mode()).isEqualTo(Mode.unchanged);
		assertThat(older.revision()).isNotEqualTo(page.revision());
		mutate(helper, client, Action.UPDATE, ids.getLast(), UPDATED_NAME, "2024-04-10", UPDATED_AMOUNT, account, category);
		final var edited = sync(helper, client, List.of(new TransactionsSyncRequestDto.Account(account, manifest))).accounts().getFirst();
		assertThat(edited.mode()).isEqualTo(Mode.buckets);
		assertThat(edited.buckets()).hasSize(1);
		assertThat(edited.buckets().getFirst().id()).isEqualTo(TransactionBuckets.bucketId(ids.getLast()));
		final var canonical = sync(helper, client, List.of(new TransactionsSyncRequestDto.Account(account, null))).accounts().getFirst();
		final String beforeInsert = TransactionBuckets.manifest(account, canonical.items());
		final long inserted = mutate(helper, client, Action.INSERT, null, "Newest", "2026-10-31", INITIAL_AMOUNT, account, category).id();
		final var insertion = sync(helper, client, List.of(new TransactionsSyncRequestDto.Account(account, beforeInsert))).accounts().getFirst();
		assertThat(insertion.total()).isEqualTo(102);
		assertThat(insertion.mode()).isEqualTo(Mode.buckets);
		final var withInserted = sync(helper, client, List.of(new TransactionsSyncRequestDto.Account(account, null))).accounts().getFirst();
		assertThat(withInserted.items().getFirst().id()).isEqualTo(inserted);
		mutate(helper, client, Action.DELETE, inserted, null, null, null, account, category);
		final var afterDelete = sync(helper, client, List.of(new TransactionsSyncRequestDto.Account(account, TransactionBuckets.manifest(account, withInserted.items())))).accounts().getFirst();
		assertThat(afterDelete.total()).isEqualTo(101);
		assertThat(afterDelete.mode()).isEqualTo(Mode.buckets);
		assertThat(helper.getJson(client, TRANSACTIONS + "?source=" + account + "&start=100&limit=100").getJSONArray("data").length()).isEqualTo(1);
		assertStatus(client, new TransactionsSyncRequestDto(1, List.of()), 200);
		assertStatus(client, new TransactionsSyncRequestDto(99, List.of()), 400);
		assertStatus(client, new TransactionsSyncRequestDto(1, List.of(new TransactionsSyncRequestDto.Account(account, "invalid"))), 400);
		assertStatus(client, new TransactionsSyncRequestDto(1, List.of(new TransactionsSyncRequestDto.Account(account, null), new TransactionsSyncRequestDto.Account(account, null))), 400);
		assertStatus(new OkHttpClient(), new TransactionsSyncRequestDto(1, List.of()), 401);
		final String secondEmail = "other-transaction-sync-" + UUID.randomUUID() + EMAIL_DOMAIN;
		helper.registerUser(secondEmail, PASSWORD, EN_US, USD);
		assertStatus(helper.login(secondEmail, PASSWORD), new TransactionsSyncRequestDto(1, List.of(new TransactionsSyncRequestDto.Account(account, null))), 400);
	}

	private static TransactionResponseDto acknowledge(final TestHelper helper, final OkHttpClient client, final TransactionRequestDto request) throws Exception {
		return MAPPER.readValue(helper.postJson(client, TRANSACTIONS + "?includeTransaction=true", new JSONObject(MAPPER.writeValueAsString(request))), TransactionResponseDto.class);
	}

	private static TransactionResponseDto mutate(final TestHelper helper, final OkHttpClient client, final Action action, final Long id,
			final String description, final String date, final String amount, final int from, final int to) throws Exception {
		final var request = new TransactionRequestDto(action, id, UUID.randomUUID().toString(), description, null, date, null,
				amount == null ? null : List.of(new SplitRequestDto(null, null, amount, from, to, null)), null);
		return MAPPER.readValue(helper.postJson(client, TRANSACTIONS, new JSONObject(MAPPER.writeValueAsString(request))), TransactionResponseDto.class);
	}

	private static TransactionsSyncResponseDto sync(final TestHelper helper, final OkHttpClient client, final List<TransactionsSyncRequestDto.Account> accounts) throws Exception {
		final var request = new TransactionsSyncRequestDto(1, accounts);
		return MAPPER.readValue(helper.postJson(client, SYNC, new JSONObject(MAPPER.writeValueAsString(request))), TransactionsSyncResponseDto.class);
	}

	private static void assertStatus(final OkHttpClient client, final TransactionsSyncRequestDto request, final int status) throws Exception {
		try (final Response response = client.newCall(new Request.Builder().url(getBaseUrl() + SYNC)
				.post(RequestBody.create(MAPPER.writeValueAsBytes(request), MediaType.get("application/json"))).build()).execute()) {
			assertThat(response.code()).isEqualTo(status);
			if (status == 200) assertThat(response.header("Cache-Control")).contains("no-store");
		}
	}

	private enum SourceType {
		DEBIT("D"), EXPENSE("E");

		private final String code;

		SourceType(final String code) {
			this.code = code;
		}
	}
}
