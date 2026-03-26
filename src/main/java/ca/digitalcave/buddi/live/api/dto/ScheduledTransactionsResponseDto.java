package ca.digitalcave.buddi.live.api.dto;

import java.math.BigDecimal;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

public record ScheduledTransactionsResponseDto(
		boolean success,
		int total,
		List<ScheduledTransactionDto> data) {

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record ScheduledTransactionDto(
			long id,
			String name,
			String description,
			String number,
			int scheduleDay,
			int scheduleWeek,
			int scheduleMonth,
			String start,
			String end,
			String repeat,
			String lastCreatedDate,
			String message,
			List<ScheduledSplitDto> splits) {
	}

	public record ScheduledSplitDto(
			long id,
			String amount,
			BigDecimal amountNumber,
			int fromId,
			int toId,
			String memo) {
	}
}
