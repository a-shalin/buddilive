package ca.digitalcave.buddi.live.api.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

public record StoreResponseDto(boolean success, List<StoreItemDto> data) {

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record StoreItemDto(String text, String value, String style) {
	}
}
