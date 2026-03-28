package ca.digitalcave.buddi.live.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AuthenticationFlowResponseDto(boolean success, String next) {

	public AuthenticationFlowResponseDto(final boolean success) {
		this(success, null);
	}
}
