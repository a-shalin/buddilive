package ca.digitalcave.buddi.live.api.dto.request;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record SplitRequestDto(
		Long id,
		Long transactionId,
		Object amount,
		int fromId,
		int toId,
		String memo) {
}