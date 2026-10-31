package ca.digitalcave.buddi.live.service;

import ca.digitalcave.buddi.live.api.dto.TransactionRecordDto;
import ca.digitalcave.buddi.live.api.dto.TransactionsSyncResponseDto;
import ca.digitalcave.buddi.live.api.dto.request.TransactionsSyncRequestDto;
import ca.digitalcave.buddi.live.db.Sources;
import ca.digitalcave.buddi.live.db.Transactions;
import ca.digitalcave.buddi.live.model.Source;
import ca.digitalcave.buddi.live.model.Split;
import ca.digitalcave.buddi.live.model.Transaction;
import ca.digitalcave.buddi.live.model.User;
import java.lang.reflect.Proxy;
import java.sql.Date;
import ca.digitalcave.buddi.live.api.dto.TransactionsSyncResponseDto.Mode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import static ca.digitalcave.buddi.live.service.TransactionBuckets.*;
import static org.assertj.core.api.Assertions.assertThat;

class TransactionBucketsTest {
	private static final String TRANSACTION_DATE = "2026-10-31";
	private final ObjectMapper mapper = new ObjectMapper();

	@Test
	void joinedFragmentsAreCombinedWithoutMutatingMapperObjects() throws Exception {
		final Transaction first = fragment(1);
		final Transaction second = fragment(3);
		final Transactions transactions = (Transactions) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {Transactions.class},
				(proxy, method, args) -> List.of(first, second));
		final Sources sources = (Sources) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {Sources.class}, (proxy, method, args) -> {
			final Source source = new Source();
			source.setId((Integer) args[1]);
			source.setType("D");
			source.setName("Account");
			return source;
		});
		final var service = new TransactionsSyncService(sources, transactions, mapper);
		final var request = new TransactionsSyncRequestDto(VERSION, List.of(new TransactionsSyncRequestDto.Account(1, null)));
		final var response = mapper.readValue(service.sync(new User(), request, false), TransactionsSyncResponseDto.class);
		assertThat(response.accounts().getFirst().total()).isEqualTo(1);
		assertThat(response.accounts().getFirst().items()).hasSize(1);
		assertThat(response.accounts().getFirst().items().getFirst().splits()).extracting(TransactionRecordDto.Split::id).containsExactly(3L, 1L);
		assertThat(first.getSplits()).hasSize(1);
		assertThat(second.getSplits()).hasSize(1);
	}

	@Test
	void sharedGoldenManifest() throws Exception {
		final var fixture = mapper.readTree(getClass().getResourceAsStream("/transaction-buckets.json"));
		final List<TransactionRecordDto> items = mapper.readerForListOf(TransactionRecordDto.class).readValue(fixture.get("items"));
		final String expected = fixture.get("manifest").asText();
		assertThat(manifest(1, items)).isEqualTo(expected).hasSize(684);
		assertThat(Base64.getDecoder().decode(expected)).hasSize(512);
		for (int index = 0; index < items.size(); index++) {
			assertThat(bucketId(items.get(index).id())).isEqualTo(fixture.get("bucketIds").get(index).asInt());
		}
		assertThat(manifest(2, items)).isNotEqualTo(expected);
	}

	@Test
	void onlyChangedBucketsAreSentAndLargeChangesUseFull() throws Exception {
		final var service = new TransactionsSyncService(null, null, mapper);
		final List<TransactionRecordDto> items = new ArrayList<>();
		for (int id = 1; id <= LIMIT; id++) items.add(item(id, BigDecimal.ONE));
		final byte[] previous = Base64.getDecoder().decode(manifest(1, items));
		assertThat(service.reconcile(1, items, previous, true).mode()).isEqualTo(Mode.unchanged);
		items.set(0, item(1, BigDecimal.TEN));
		for (final boolean gzip : new boolean[] {false, true}) {
			final var response = service.reconcile(1, items, previous, gzip);
			assertThat(response.mode()).isEqualTo(Mode.buckets);
			assertThat(response.buckets()).hasSize(1);
			assertThat(response.buckets().getFirst().id()).isEqualTo(bucketId(1));
			assertThat(response.buckets().getFirst().items()).allMatch(record -> bucketId(record.id()) == bucketId(1));
		}
		items.replaceAll(record -> item(record.id(), BigDecimal.valueOf(record.id() * 123)));
		assertThat(service.reconcile(1, items, previous, true).mode()).isEqualTo(Mode.full);
	}

	@Test
	void emptyBucketsAndOlderChangesAreDetected() throws Exception {
		final var service = new TransactionsSyncService(null, null, mapper);
		final List<TransactionRecordDto> items = new ArrayList<>();
		for (int id = LIMIT + 1; id > 0; id--) items.add(item(id, BigDecimal.ONE));
		final byte[] previous = Base64.getDecoder().decode(manifest(1, items.subList(0, LIMIT)));
		final String revision = service.reconcile(1, items, previous, true).revision();
		items.set(LIMIT, item(1, BigDecimal.TEN));
		final var older = service.reconcile(1, items, previous, true);
		assertThat(older.mode()).isEqualTo(Mode.unchanged);
		assertThat(older.revision()).isNotEqualTo(revision);
		final int removed = bucketId(2);
		items.removeIf(record -> bucketId(record.id()) == removed);
		final var deleted = service.reconcile(1, items, previous, true);
		assertThat(deleted.mode()).isEqualTo(Mode.buckets);
		assertThat(deleted.buckets().stream().filter(bucket -> bucket.id() == removed).findFirst().orElseThrow().items()).isEmpty();
	}

	private static Transaction fragment(final long splitId) {
		final Split split = new Split();
		split.setId(splitId);
		split.setAmount("1.00");
		split.setFromSource(1);
		split.setToSource(2);
		final Transaction transaction = new Transaction();
		transaction.setId(1L);
		transaction.setUuid("uuid");
		transaction.setDescription("Split transaction");
		transaction.setDate(Date.valueOf(TRANSACTION_DATE));
		transaction.setSplits(List.of(split));
		return transaction;
	}

	private static TransactionRecordDto item(final long id, final BigDecimal amount) {
		return new TransactionRecordDto(id, Long.toString(id), TRANSACTION_DATE, "Transaction " + id, null,
				List.of(new TransactionRecordDto.Split(id, amount, 1, 2, null)));
	}
}
