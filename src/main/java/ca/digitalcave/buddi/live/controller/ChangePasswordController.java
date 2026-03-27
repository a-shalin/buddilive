package ca.digitalcave.buddi.live.controller;

import javax.crypto.SecretKey;

import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import ca.digitalcave.buddi.live.api.dto.SuccessResponseDto;
import ca.digitalcave.buddi.live.db.Users;
import ca.digitalcave.buddi.live.db.util.DatabaseException;
import ca.digitalcave.buddi.live.model.User;
import ca.digitalcave.buddi.live.util.LocaleUtil;
import ca.digitalcave.moss.crypto.Crypto;
import ca.digitalcave.moss.crypto.Crypto.CryptoException;
import ca.digitalcave.moss.crypto.DefaultHash;
import ca.digitalcave.moss.auth.password.PasswordChecker;

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
	public SuccessResponseDto post(@AuthenticationPrincipal User user, @RequestBody String body) {
		try {
			final JSONObject json = new JSONObject(body);
			final Action action = Action.fromString(json.optString("action"));

			if (Action.UPDATE == action) {
				final String currentPassword = json.getString("currentPassword");
				final String newPassword = json.getString("newPassword");

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
