package ca.digitalcave.buddi.live.api.dto.request;

import ca.digitalcave.buddi.live.controller.Action;

public record ChangePasswordRequestDto(
		Action action,
		String currentPassword,
		String newPassword) {
}