package ca.digitalcave.buddi.live.service;

import ca.digitalcave.buddi.live.api.dto.DescriptionsSyncResponseDto;
import ca.digitalcave.buddi.live.api.dto.DescriptionsSyncResponseDto.Bucket;
import ca.digitalcave.buddi.live.api.dto.DescriptionsSyncResponseDto.Mode;
import ca.digitalcave.buddi.live.api.dto.SuggestionsPatchDto;
import ca.digitalcave.buddi.live.api.dto.TransactionSuggestionDto;
import ca.digitalcave.buddi.live.api.dto.request.DescriptionsSyncRequestDto;
import ca.digitalcave.buddi.live.db.Transactions;
import ca.digitalcave.buddi.live.model.Transaction;
import ca.digitalcave.buddi.live.model.User;
import ca.digitalcave.buddi.live.util.CryptoUtil;
import ca.digitalcave.moss.crypto.Crypto.CryptoException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.zip.GZIPOutputStream;
import java.util.stream.Collectors;

import static ca.digitalcave.buddi.live.service.TransactionSuggestionBuckets.*;

@Service
public class TransactionSuggestionsService {
	private final Transactions transactions;
	private final ObjectMapper objectMapper;

	public TransactionSuggestionsService(final Transactions transactions, final ObjectMapper objectMapper) {
		this.transactions = transactions;
		this.objectMapper = objectMapper;
	}

	public Map<String, Transaction> select(final User user, final Integer source) throws CryptoException {
		final Map<String, Transaction> selected = new TreeMap<>();
		for (final Transaction transaction : transactions.selectDescriptions(user)) {
			if (source != null && (transaction.getSplits() == null || transaction.getSplits().stream()
					.noneMatch(split -> split.getFromSource() == source || split.getToSource() == source))) continue;
			selected.putIfAbsent(CryptoUtil.decryptWrapper(transaction.getDescription(), user), transaction);
		}
		return selected;
	}

	public List<TransactionSuggestionDto> current(final User user) throws CryptoException {
		final List<TransactionSuggestionDto> items = new ArrayList<>();
		for (final Map.Entry<String, Transaction> entry : select(user, null).entrySet()) {
			final List<TransactionSuggestionDto.Split> splits = new ArrayList<>();
			for (final var split : entry.getValue().getSplits()) {
				splits.add(new TransactionSuggestionDto.Split(CryptoUtil.decryptWrapperBigDecimal(split.getAmount(), user, false),
						split.getFromSource(), split.getToSource(), split.getFromType(), split.getToType()));
			}
			items.add(new TransactionSuggestionDto(entry.getKey(), splits));
		}
		return items;
	}

	public SuggestionsPatchDto patch(final User user, final Set<String> keys) throws CryptoException {
		final List<TransactionSuggestionDto> upserts = current(user).stream().filter(item -> keys.contains(item.description())).toList();
		final Set<String> found = upserts.stream().map(TransactionSuggestionDto::description).collect(Collectors.toSet());
		return new SuggestionsPatchDto(upserts, keys.stream().filter(key -> !found.contains(key)).sorted().toList());
	}

	public byte[] sync(final User user, final DescriptionsSyncRequestDto request, final boolean gzip) throws CryptoException, IOException {
		if (request.version() != VERSION) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported suggestions protocol version");
		byte[] clientHashes = null;
		if (request.bucketHashes() != null) {
			try {
				clientHashes = Base64.getDecoder().decode(request.bucketHashes());
				if (clientHashes.length != COUNT * HASH_LENGTH) throw new IllegalArgumentException();
			}
			catch (final IllegalArgumentException e) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid suggestions manifest", e);
			}
		}
		final List<TransactionSuggestionDto> items = current(user);
		final byte[] full = objectMapper.writeValueAsBytes(new DescriptionsSyncResponseDto(true, VERSION, Mode.full, items, null));
		if (clientHashes == null) return full;
		final List<List<TransactionSuggestionDto>> partitioned = partition(items);
		final List<Bucket> changed = new ArrayList<>();
		for (int id = 0; id < COUNT; id++) {
			final byte[] hash = hash(id, partitioned.get(id));
			if (!Arrays.equals(hash, Arrays.copyOfRange(clientHashes, id * HASH_LENGTH, (id + 1) * HASH_LENGTH))) {
				changed.add(new Bucket(id, Base64.getEncoder().encodeToString(hash), partitioned.get(id)));
			}
		}
		if (changed.isEmpty()) return objectMapper.writeValueAsBytes(new DescriptionsSyncResponseDto(true, VERSION, Mode.unchanged, null, null));
		final byte[] delta = objectMapper.writeValueAsBytes(new DescriptionsSyncResponseDto(true, VERSION, Mode.buckets, null, changed));
		return size(delta, gzip) < size(full, gzip) ? delta : full;
	}

	private static int size(final byte[] json, final boolean gzip) throws IOException {
		if (!gzip) return json.length;
		final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (final GZIPOutputStream output = new GZIPOutputStream(bytes)) {
			output.write(json);
		}
		return bytes.size();
	}
}
