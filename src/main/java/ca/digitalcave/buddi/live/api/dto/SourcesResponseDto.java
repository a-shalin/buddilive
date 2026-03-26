package ca.digitalcave.buddi.live.api.dto;

import java.util.List;

public record SourcesResponseDto(boolean success, List<SourceItemDto> data) {

	public record SourceItemDto(Object value, String text, String style, String type) {
	}
}
