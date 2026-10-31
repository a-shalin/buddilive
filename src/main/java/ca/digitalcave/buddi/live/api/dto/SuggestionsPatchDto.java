package ca.digitalcave.buddi.live.api.dto;

import java.util.List;

public record SuggestionsPatchDto(List<TransactionSuggestionDto> upserts, List<String> deletes) {
}
