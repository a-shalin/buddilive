package ca.digitalcave.buddi.live.service;

import ca.digitalcave.buddi.live.db.Users;
import ca.digitalcave.buddi.live.db.util.DatabaseException;
import ca.digitalcave.buddi.live.model.User;
import ca.digitalcave.moss.crypto.Crypto;
import ca.digitalcave.moss.crypto.Crypto.CryptoException;
import ca.digitalcave.moss.crypto.DefaultHash;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.SecretKey;

@Service
public class ChangePasswordTransactionalService {

	private final Users users;
	private final Crypto crypto;

	public ChangePasswordTransactionalService(final Users users, final Crypto crypto) {
		this.users = users;
		this.crypto = crypto;
	}

	@Transactional
	public void updateUserPassword(final User user, final String newPassword) throws CryptoException {
		user.setSecret(new DefaultHash().generate(newPassword).toCharArray());
		int count = users.updateUser(user);
		if (count != 1) throw new DatabaseException(String.format("Update failed; expected 1 row, returned %s", count));
		if (user.isEncrypted()) {
			final SecretKey encryptionKey = user.getDecryptedSecretKey();
			user.setEncryptionKey(crypto.encrypt(newPassword, Crypto.encodeSecretKey(encryptionKey)));
			count = users.updateUserEncryptionKey(user);
			if (count != 1) throw new DatabaseException(String.format("Encryption key update failed; expected 1 row, returned %s", count));
		}
	}
}
