package ca.digitalcave.buddi.live.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record SuccessResponseDto(boolean success, Long id, String uuid) {
	public SuccessResponseDto(final boolean success) {
		this(success, null, null);
	}
}
