package ca.digitalcave.buddi.live.api.dto.request;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import ca.digitalcave.buddi.live.controller.Action;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ScheduledTransactionRequestDto(
		Action action,
		Long id,
		String uuid,
		String name,
		int scheduleDay,
		int scheduleWeek,
		int scheduleMonth,
		String repeat,
		String start,
		String end,
		String lastCreatedDate,
		String message,
		TransactionInnerDto transaction) {

	public record TransactionInnerDto(
			String description,
			String number,
			List<SplitRequestDto> splits) {
	}
}