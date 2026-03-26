package ca.digitalcave.buddi.live.api.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

public record ParentsResponseDto(boolean success, List<ParentItemDto> data) {

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record ParentItemDto(Object value,
			String text,
			String style,
			Boolean income,
			String type,
			String periodType) {
	}
}
