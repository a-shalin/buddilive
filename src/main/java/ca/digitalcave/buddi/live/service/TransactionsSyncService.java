package ca.digitalcave.buddi.live.service;

import ca.digitalcave.buddi.live.api.dto.TransactionRecordDto;
import ca.digitalcave.buddi.live.api.dto.TransactionsSyncResponseDto;
import ca.digitalcave.buddi.live.api.dto.TransactionsSyncResponseDto.Account;
import ca.digitalcave.buddi.live.api.dto.TransactionsSyncResponseDto.Bucket;
import ca.digitalcave.buddi.live.api.dto.TransactionsSyncResponseDto.Mode;
import ca.digitalcave.buddi.live.api.dto.request.TransactionsSyncRequestDto;
import ca.digitalcave.buddi.live.db.Sources;
import ca.digitalcave.buddi.live.db.Transactions;
import ca.digitalcave.buddi.live.model.Split;
import ca.digitalcave.buddi.live.model.Transaction;
import ca.digitalcave.buddi.live.model.User;
import ca.digitalcave.buddi.live.util.CryptoUtil;
import ca.digitalcave.buddi.live.util.FormatUtil;
import ca.digitalcave.moss.crypto.Crypto.CryptoException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.GZIPOutputStream;
import java.util.stream.Collectors;

import static ca.digitalcave.buddi.live.service.TransactionBuckets.*;

@Service
public class TransactionsSyncService {
	private final Sources sources;
	private final Transactions transactions;
	private final ObjectMapper mapper;

	public TransactionsSyncService(final Sources sources, final Transactions transactions, final ObjectMapper mapper) {
		this.sources = sources;
		this.transactions = transactions;
		this.mapper = mapper;
	}

	@Transactional(readOnly = true)
	public byte[] sync(final User user, final TransactionsSyncRequestDto request, final boolean gzip) throws CryptoException, IOException {
		if (request.version() != VERSION || request.accounts() == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported transaction synchronization request");
		}
		final var ids = new HashSet<Integer>();
		final List<byte[]> manifests = new ArrayList<>();
		for (final var account : request.accounts()) {
			if (account == null || !ids.add(account.sourceId())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Duplicate or invalid account");
			manifests.add(decodeManifest(account.bucketHashes()));
			final var source = sources.selectSource(user, account.sourceId());
			if (source == null || !source.isAccount()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown account");
		}
		final List<Transaction> all = ids.isEmpty() ? List.of() : transactions.selectTransactions(user);
		// Interleaved split IDs can produce multiple mapper objects for one transaction.
		final var grouped = all.stream().collect(Collectors.groupingBy(Transaction::getId));
		final List<Account> accounts = new ArrayList<>();
		final Map<Integer, String> sourceNames = new TreeMap<>();
		for (int index = 0; index < request.accounts().size(); index++) {
			final int sourceId = request.accounts().get(index).sourceId();
			final List<TransactionRecordDto> records = new ArrayList<>();
			for (final List<Transaction> fragments : grouped.values()) {
				final Transaction transaction = fragments.getFirst();
				final List<Split> matching = fragments.stream().flatMap(fragment -> fragment.getSplits().stream())
						.filter(split -> split.getFromSource() == sourceId || split.getToSource() == sourceId)
						.sorted(Comparator.comparing(Split::getId).reversed()).toList();
				if (matching.isEmpty()) continue;
				records.add(record(user, transaction, matching));
			}
			records.sort(Comparator.comparing(TransactionRecordDto::dateIso)
					.thenComparingLong(item -> item.splits().stream().map(TransactionRecordDto.Split::id).mapToLong(Long::longValue).max().orElse(0))
					.thenComparingLong(TransactionRecordDto::id).reversed());
			accounts.add(reconcile(sourceId, records, manifests.get(index), gzip));
			for (final var item : records.stream().limit(LIMIT).toList()) {
				for (final var split : item.splits()) {
					for (final int id : new int[] {split.fromId(), split.toId()}) {
						if (!sourceNames.containsKey(id)) {
							final var source = sources.selectSource(user, id);
							if (source != null) sourceNames.put(id, CryptoUtil.decryptWrapper(source.getName(), user));
						}
					}
				}
			}
		}
		return mapper.writeValueAsBytes(new TransactionsSyncResponseDto(true, VERSION, accounts, sourceNames));
	}

	TransactionRecordDto record(final User user, final Transaction transaction, final List<Split> matching) throws CryptoException {
		final List<TransactionRecordDto.Split> splits = new ArrayList<>();
		for (final Split split : matching.stream().sorted(Comparator.comparing(Split::getId).reversed()).toList()) {
			splits.add(new TransactionRecordDto.Split(split.getId(), CryptoUtil.decryptWrapperBigDecimal(split.getAmount(), user, false),
					split.getFromSource(), split.getToSource(), CryptoUtil.decryptWrapper(split.getMemo(), user)));
		}
		return new TransactionRecordDto(transaction.getId(), transaction.getUuid(), FormatUtil.formatDateInternal(transaction.getDate()),
				CryptoUtil.decryptWrapper(transaction.getDescription(), user), CryptoUtil.decryptWrapper(transaction.getNumber(), user), splits);
	}

	Account reconcile(final int sourceId, final List<TransactionRecordDto> records, final byte[] manifest, final boolean gzip) throws IOException {
		final String revision = Base64.getEncoder().encodeToString(hash(sourceId, -1, records));
		final List<TransactionRecordDto> recent = records.stream().limit(LIMIT).toList();
		final Account full = new Account(sourceId, records.size(), revision, Mode.full, recent, null);
		if (manifest == null) return full;
		final var buckets = partition(recent);
		final List<Bucket> changed = new ArrayList<>();
		for (int id = 0; id < COUNT; id++) {
			final byte[] hash = hash(sourceId, id, buckets.get(id));
			if (!Arrays.equals(hash, Arrays.copyOfRange(manifest, id * HASH_LENGTH, (id + 1) * HASH_LENGTH))) {
				changed.add(new Bucket(id, Base64.getEncoder().encodeToString(hash), buckets.get(id)));
			}
		}
		if (changed.isEmpty()) return new Account(sourceId, records.size(), revision, Mode.unchanged, null, null);
		final Account delta = new Account(sourceId, records.size(), revision, Mode.buckets, null, changed);
		return size(delta, gzip) < size(full, gzip) ? delta : full;
	}

	private static byte[] decodeManifest(final String encoded) {
		if (encoded == null) return null;
		try {
			final byte[] bytes = Base64.getDecoder().decode(encoded);
			if (bytes.length != COUNT * HASH_LENGTH) throw new IllegalArgumentException();
			return bytes;
		}
		catch (final IllegalArgumentException e) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid transaction manifest", e);
		}
	}

	private int size(final Account response, final boolean gzip) throws IOException {
		final byte[] json = mapper.writeValueAsBytes(response);
		if (!gzip) return json.length;
		final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (final GZIPOutputStream output = new GZIPOutputStream(bytes)) {
			output.write(json);
		}
		return bytes.size();
	}
}
