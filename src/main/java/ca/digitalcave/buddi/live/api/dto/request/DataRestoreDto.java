package ca.digitalcave.buddi.live.api.dto.request;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record DataRestoreDto(
		List<RestoreAccountDto> accounts,
		List<RestoreCategoryDto> categories,
		List<RestoreEntryDto> entries,
		List<RestoreTransactionDto> transactions,
		List<RestoreScheduledTransactionDto> scheduledTransactions) {

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record RestoreAccountDto(
			String uuid,
			String name,
			String startDate,
			Boolean deleted,
			String type,
			String startBalance,
			String accountType) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record RestoreCategoryDto(
			String uuid,
			String name,
			Boolean deleted,
			String type,
			String parent,
			String periodType,
			List<RestoreCategoryDto> categories) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record RestoreEntryDto(
			String category,
			String date,
			String amount) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record RestoreTransactionDto(
			String uuid,
			String description,
			String number,
			String date,
			Boolean deleted,
			List<RestoreSplitDto> splits) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record RestoreScheduledTransactionDto(
			String uuid,
			String description,
			String number,
			String scheduleName,
			int scheduleDay,
			int scheduleWeek,
			int scheduleMonth,
			String frequencyType,
			String startDate,
			String endDate,
			String lastCreatedDate,
			String message,
			List<RestoreSplitDto> splits) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record RestoreSplitDto(
			String amount,
			String from,
			String to,
			String memo) {
	}
}