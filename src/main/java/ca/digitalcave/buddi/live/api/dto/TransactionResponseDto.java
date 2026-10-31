package ca.digitalcave.buddi.live.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TransactionResponseDto(boolean success, Long id, String uuid, SuggestionsPatchDto suggestions,
		TransactionRecordDto transaction, List<Integer> previousSourceIds) {
}
