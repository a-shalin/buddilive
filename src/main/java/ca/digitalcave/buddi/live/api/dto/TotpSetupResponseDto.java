package ca.digitalcave.buddi.live.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TotpSetupResponseDto(boolean success, String totpSharedSecret, String totpSharedSecretQr) {

	public TotpSetupResponseDto(final boolean success) {
		this(success, null, null);
	}
}
