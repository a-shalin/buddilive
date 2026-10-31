package ca.digitalcave.buddi.live.service;

import ca.digitalcave.buddi.live.api.dto.DescriptionsSyncResponseDto;
import ca.digitalcave.buddi.live.api.dto.TransactionSuggestionDto;
import ca.digitalcave.buddi.live.api.dto.request.DescriptionsSyncRequestDto;
import ca.digitalcave.buddi.live.model.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.zip.GZIPOutputStream;

import static ca.digitalcave.buddi.live.service.TransactionSuggestionBuckets.*;
import static org.assertj.core.api.Assertions.*;

class TransactionSuggestionBucketsTest {
	private static final String MANIFEST = "manifest";
	private static final String MODE = "mode";
	private final ObjectMapper mapper = new ObjectMapper();

	@Test
	void sharedGoldenManifest() throws Exception {
		final var fixture = mapper.readTree(getClass().getResourceAsStream("/transaction-suggestion-buckets.json"));
		final var items = mapper.readerForListOf(TransactionSuggestionDto.class).<List<TransactionSuggestionDto>>readValue(fixture.get("items"));
		final var buckets = partition(items);
		final ByteArrayOutputStream manifest = new ByteArrayOutputStream();
		for (int id = 0; id < COUNT; id++) manifest.write(hash(id, buckets.get(id)));
		assertThat(Base64.getEncoder().encodeToString(manifest.toByteArray())).isEqualTo(fixture.get(MANIFEST).asText());
		for (int index = 0; index < items.size(); index++) {
			assertThat(bucketId(items.get(index).description())).isEqualTo(fixture.get("bucketIds").get(index).asInt());
		}
		assertThat(manifest.size()).isEqualTo(8192);
		assertThat(fixture.get(MANIFEST).asText()).hasSize(10924);
	}

	@Test
	void unchangedAndSizeBasedFallbackUseOneProjection() throws Exception {
		final User user = new User();
		final List<TransactionSuggestionDto> initial = new ArrayList<>();
		for (int i = 0; i < 300; i++) initial.add(item("Suggestion " + i, i));
		final ProjectionService service = new ProjectionService(mapper, initial);
		final String manifest = manifest(initial);
		assertThat(mapper.readTree(service.sync(user, new DescriptionsSyncRequestDto(VERSION, manifest), true)).get(MODE).asText()).isEqualTo("unchanged");
		assertThat(service.reads).isEqualTo(1);
		final List<TransactionSuggestionDto> changed = new ArrayList<>(initial);
		changed.set(0, item(initial.getFirst().description(), 999));
		service.items = changed;
		final byte[] delta = service.sync(user, new DescriptionsSyncRequestDto(VERSION, manifest), true);
		final var response = mapper.readValue(delta, DescriptionsSyncResponseDto.class);
		assertThat(response.mode()).isEqualTo(DescriptionsSyncResponseDto.Mode.buckets);
		assertThat(response.buckets()).hasSize(1);
		assertThat(response.buckets().getFirst().id()).isEqualTo(bucketId(initial.getFirst().description()));
		final byte[] full = service.sync(user, new DescriptionsSyncRequestDto(VERSION, null), true);
		assertThat(compressedSize(delta)).isLessThan(compressedSize(full));
		service.items = List.of(item("Entirely new", 1));
		assertThat(mapper.readTree(service.sync(user, new DescriptionsSyncRequestDto(VERSION, manifest), true)).get(MODE).asText()).isEqualTo("full");
	}

	private static TransactionSuggestionDto item(final String name, final int amount) {
		return new TransactionSuggestionDto(name, List.of(new TransactionSuggestionDto.Split(BigDecimal.valueOf(amount), 1, 2, null, null)));
	}

	private static String manifest(final List<TransactionSuggestionDto> items) throws Exception {
		final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		final var buckets = partition(items);
		for (int id = 0; id < COUNT; id++) bytes.write(hash(id, buckets.get(id)));
		return Base64.getEncoder().encodeToString(bytes.toByteArray());
	}

	private static int compressedSize(final byte[] json) throws Exception {
		final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (final GZIPOutputStream output = new GZIPOutputStream(bytes)) {
			output.write(json);
		}
		return bytes.size();
	}
	private static class ProjectionService extends TransactionSuggestionsService {
		private List<TransactionSuggestionDto> items;
		private int reads;

		private ProjectionService(final ObjectMapper mapper, final List<TransactionSuggestionDto> items) {
			super(null, mapper);
			this.items = items;
		}

		@Override
		public List<TransactionSuggestionDto> current(final User user) {
			reads++;
			return items;
		}
	}

}
