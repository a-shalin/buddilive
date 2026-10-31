package ca.digitalcave.buddi.live.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Map;

public record TransactionsSyncResponseDto(boolean success, int version, List<Account> accounts, Map<Integer, String> sourceNames) {

	public enum Mode { unchanged, buckets, full }

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record Account(int sourceId, int total, String revision, Mode mode, List<TransactionRecordDto> items, List<Bucket> buckets) {
	}

	public record Bucket(int id, String hash, List<TransactionRecordDto> items) {
	}
}
