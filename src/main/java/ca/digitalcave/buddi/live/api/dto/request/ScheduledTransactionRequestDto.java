package ca.digitalcave.buddi.live.api.dto.request;

import ca.digitalcave.buddi.live.controller.Action;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ScheduledTransactionRequestDto(
		Action action,
		Long id,
		String uuid,
		String name,
		Integer scheduleDay,
		Integer scheduleWeek,
		Integer scheduleMonth,
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
