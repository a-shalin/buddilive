package ca.digitalcave.buddi.live.controller;

import ca.digitalcave.buddi.live.api.dto.SuccessResponseDto;
import ca.digitalcave.buddi.live.api.dto.request.ChangePasswordRequestDto;
import ca.digitalcave.buddi.live.db.Users;
import ca.digitalcave.buddi.live.db.util.DatabaseException;
import ca.digitalcave.buddi.live.model.User;
import ca.digitalcave.buddi.live.util.LocaleUtil;
import ca.digitalcave.moss.auth.password.PasswordChecker;
import ca.digitalcave.moss.crypto.Crypto;
import ca.digitalcave.moss.crypto.Crypto.CryptoException;
import ca.digitalcave.moss.crypto.DefaultHash;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.SecretKey;

@RestController
@RequestMapping("/data/changepassword")
public class ChangePasswordController {

	@Autowired
	private Users users;

	@Autowired
	private Crypto crypto;

	@Autowired
	private PasswordChecker passwordChecker;

	@PostMapping
	@Transactional
	public SuccessResponseDto post(@AuthenticationPrincipal User user, @RequestBody final ChangePasswordRequestDto request) {
		try {
			final Action action = request.action();

			if (Action.UPDATE == action) {
				final String currentPassword = request.currentPassword();
				final String newPassword = request.newPassword();

				if (DefaultHash.verify(new String(user.getSecret()), currentPassword)) {
					if (passwordChecker.isValid("", newPassword)) {
						user.setSecret(new DefaultHash().generate(newPassword).toCharArray());
						int count = users.updateUser(user);
						if (count != 1) throw new DatabaseException(String.format("Update failed; expected 1 row, returned %s", count));
						if (user.isEncrypted()) {
							final SecretKey encryptionKey = user.getDecryptedSecretKey();
							user.setEncryptionKey(crypto.encrypt(newPassword, Crypto.encodeSecretKey(encryptionKey)));
							count = users.updateUserEncryptionKey(user);
							if (count != 1) throw new DatabaseException(String.format("Encryption key update failed; expected 1 row, returned %s", count));
						}

						return new SuccessResponseDto(true);
					}
					throw new ResponseStatusException(HttpStatus.BAD_REQUEST, LocaleUtil.getTranslation(user).getString("PASSWORD_CHECK_FAILED"));
				}
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, LocaleUtil.getTranslation(user).getString("INCORRECT_PASSWORD"));
			}
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, LocaleUtil.getTranslation(user).getString("ACTION_PARAMETER_MUST_BE_SPECIFIED"));
		}
		catch (DatabaseException e) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
		}
		catch (CryptoException e) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage(), e);
		}
	}
}
