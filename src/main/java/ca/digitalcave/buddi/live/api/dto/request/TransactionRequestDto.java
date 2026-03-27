package ca.digitalcave.buddi.live.api.dto.request;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import ca.digitalcave.buddi.live.controller.Action;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TransactionRequestDto(
		Action action,
		Long id,
		String uuid,
		String description,
		String number,
		String date,
		Boolean deleted,
		List<SplitRequestDto> splits) {
}