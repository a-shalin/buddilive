package ca.digitalcave.buddi.live.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record DescriptionsSyncResponseDto(boolean success, int version, Mode mode,
		List<TransactionSuggestionDto> items, List<Bucket> buckets) {

	public enum Mode { unchanged, buckets, full }

	public record Bucket(int id, String hash, List<TransactionSuggestionDto> items) {
	}
}
