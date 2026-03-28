package ca.digitalcave.buddi.live.controller;

import ca.digitalcave.buddi.live.api.converter.UserPreferencesResponseConverter;
import ca.digitalcave.buddi.live.api.dto.SuccessResponseDto;
import ca.digitalcave.buddi.live.api.dto.UserPreferencesResponseDto;
import ca.digitalcave.buddi.live.api.dto.request.UserPreferencesRequestDto;
import ca.digitalcave.buddi.live.service.UserPreferencesTransactionalService;
import ca.digitalcave.buddi.live.db.util.DatabaseException;
import ca.digitalcave.buddi.live.model.User;
import ca.digitalcave.buddi.live.security.CookieUtil;
import ca.digitalcave.buddi.live.util.LocaleUtil;
import ca.digitalcave.moss.auth.service.AuthenticationHelper;
import ca.digitalcave.moss.crypto.Crypto.CryptoException;
import ca.digitalcave.moss.crypto.DefaultHash;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/data/userpreferences")
public class UserPreferencesController {

	@Autowired
	private UserPreferencesTransactionalService userPreferencesTransactionalService;

	@Autowired
	private AuthenticationHelper authenticationHelper;

	@Autowired
	private UserPreferencesResponseConverter userPreferencesResponseConverter;

	@GetMapping
	public UserPreferencesResponseDto get(@AuthenticationPrincipal final User user) {
		return userPreferencesResponseConverter.convert(user);
	}

	@PostMapping
	public SuccessResponseDto post(@AuthenticationPrincipal final User user,
			@RequestBody final UserPreferencesRequestDto dto,
			final HttpServletRequest request,
			final HttpServletResponse response) {
		try {
			final Action action = dto.action();

			if (Action.UPDATE == action) {
				final boolean toggleEncryption = Boolean.TRUE.equals(dto.encrypt()) != user.isEncrypted();
				if (toggleEncryption) {
					final String encryptPassword = dto.encryptPassword();
					if (!DefaultHash.verify(new String(user.getSecret()), encryptPassword)) {
						throw new ResponseStatusException(HttpStatus.FORBIDDEN, LocaleUtil.getTranslation(user).getString("INCORRECT_PASSWORD"));
					}
				}

				final boolean invalidateTwoFactorCookie = userPreferencesTransactionalService.updatePreferences(user, dto, toggleEncryption);
				if (invalidateTwoFactorCookie) {
					CookieUtil.setTwoFactorInvalid(request, response, authenticationHelper);
				}
			}
			else if (Action.INVALIDATE_TOTP_BACKUPS == action) {
				userPreferencesTransactionalService.invalidateTotpBackups(user);
			}
			else if (Action.DELETE == action) {
				userPreferencesTransactionalService.deleteUserData(user);
			}
			else {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, LocaleUtil.getTranslation(user).getString("ACTION_PARAMETER_MUST_BE_SPECIFIED"));
			}

			return new SuccessResponseDto(true);
		}
		catch (final DatabaseException e) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
		}
		catch (final CryptoException e) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage(), e);
		}
	}
}
