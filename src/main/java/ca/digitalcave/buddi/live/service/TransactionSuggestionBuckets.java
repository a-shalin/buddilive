package ca.digitalcave.buddi.live.service;

import ca.digitalcave.buddi.live.api.dto.TransactionSuggestionDto;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class TransactionSuggestionBuckets {
	public static final int VERSION = 1;
	public static final int COUNT = 256;
	public static final int HASH_LENGTH = 32;

	private TransactionSuggestionBuckets() {
	}

	public static int bucketId(final String description) {
		return digest(description.getBytes(StandardCharsets.UTF_8))[0] & 0xff;
	}

	public static List<List<TransactionSuggestionDto>> partition(final List<TransactionSuggestionDto> items) {
		final List<List<TransactionSuggestionDto>> buckets = new ArrayList<>(COUNT);
		for (int id = 0; id < COUNT; id++) buckets.add(new ArrayList<>());
		for (final TransactionSuggestionDto item : items) buckets.get(bucketId(item.description())).add(item);
		buckets.forEach(bucket -> bucket.sort(Comparator.comparing(TransactionSuggestionDto::description)));
		return buckets;
	}

	public static byte[] hash(final int id, final List<TransactionSuggestionDto> items) {
		try {
			final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
			final DataOutputStream output = new DataOutputStream(bytes);
			output.writeInt(VERSION);
			output.writeInt(id);
			output.writeInt(items.size());
			for (final TransactionSuggestionDto item : items.stream().sorted(Comparator.comparing(TransactionSuggestionDto::description)).toList()) {
				writeString(output, item.description());
				output.writeInt(item.splits().size());
				for (final TransactionSuggestionDto.Split split : item.splits()) {
					writeString(output, split.amountNumber().stripTrailingZeros().toPlainString());
					output.writeInt(split.fromId());
					output.writeInt(split.toId());
					writeString(output, split.fromType());
					writeString(output, split.toType());
				}
			}
			return digest(bytes.toByteArray());
		}
		catch (final IOException e) {
			throw new IllegalStateException(e);
		}
	}

	private static void writeString(final DataOutputStream output, final String value) throws IOException {
		if (value == null) {
			output.writeInt(-1);
			return;
		}
		final byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
		output.writeInt(bytes.length);
		output.write(bytes);
	}

	private static byte[] digest(final byte[] bytes) {
		try {
			return MessageDigest.getInstance("SHA-256").digest(bytes);
		}
		catch (final NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}
}
