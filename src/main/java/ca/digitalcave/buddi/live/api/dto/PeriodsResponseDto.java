package ca.digitalcave.buddi.live.api.dto;

import java.util.List;

public record PeriodsResponseDto(boolean success, List<PeriodItemDto> data) {

	public record PeriodItemDto(String value, String text) {
	}
}
