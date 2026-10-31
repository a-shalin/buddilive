package ca.digitalcave.buddi.live.service;

import ca.digitalcave.buddi.live.api.dto.TransactionRecordDto;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;

public final class TransactionBuckets {
	public static final int VERSION = 1;
	public static final int COUNT = 16;
	public static final int LIMIT = 100;
	public static final int HASH_LENGTH = 32;

	private TransactionBuckets() {
	}

	public static int bucketId(final long id) {
		return digest(Long.toString(id).getBytes(StandardCharsets.UTF_8))[0] & (COUNT - 1);
	}

	public static List<List<TransactionRecordDto>> partition(final List<TransactionRecordDto> items) {
		final List<List<TransactionRecordDto>> buckets = new ArrayList<>(COUNT);
		for (int id = 0; id < COUNT; id++) buckets.add(new ArrayList<>());
		for (final TransactionRecordDto item : items) buckets.get(bucketId(item.id())).add(item);
		return buckets;
	}

	public static byte[] hash(final int sourceId, final int bucketId, final List<TransactionRecordDto> items) {
		try {
			final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
			final DataOutputStream output = new DataOutputStream(bytes);
			output.writeInt(VERSION);
			output.writeInt(sourceId);
			output.writeInt(bucketId);
			output.writeInt(items.size());
			for (final TransactionRecordDto item : items.stream().sorted(Comparator.comparingLong(TransactionRecordDto::id)).toList()) {
				output.writeLong(item.id());
				writeString(output, item.uuid());
				writeString(output, item.dateIso());
				writeString(output, item.description());
				writeString(output, item.number());
				output.writeInt(item.splits().size());
				for (final TransactionRecordDto.Split split : item.splits()) {
					output.writeLong(split.id() == null ? -1 : split.id());
					writeString(output, split.amountNumber().stripTrailingZeros().toPlainString());
					output.writeInt(split.fromId());
					output.writeInt(split.toId());
					writeString(output, split.memo());
				}
			}
			return digest(bytes.toByteArray());
		}
		catch (final IOException e) {
			throw new IllegalStateException(e);
		}
	}

	public static String manifest(final int sourceId, final List<TransactionRecordDto> items) {
		final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		final var buckets = partition(items);
		for (int id = 0; id < COUNT; id++) bytes.writeBytes(hash(sourceId, id, buckets.get(id)));
		return Base64.getEncoder().encodeToString(bytes.toByteArray());
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
