package ca.digitalcave.buddi.live.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TransactionResponseDto(boolean success, Long id, String uuid, SuggestionsPatchDto suggestions) {
}
