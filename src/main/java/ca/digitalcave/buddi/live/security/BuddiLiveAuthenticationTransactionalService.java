package ca.digitalcave.buddi.live.security;

import ca.digitalcave.buddi.live.db.BuddiSystem;
import ca.digitalcave.buddi.live.db.Users;
import ca.digitalcave.buddi.live.db.util.DatabaseException;
import ca.digitalcave.buddi.live.model.User;
import ca.digitalcave.buddi.live.util.LocaleUtil;
import ca.digitalcave.moss.crypto.Crypto;
import ca.digitalcave.moss.crypto.Crypto.Algorithm;
import ca.digitalcave.moss.crypto.Crypto.CryptoException;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.SecretKey;
import java.util.Currency;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class BuddiLiveAuthenticationTransactionalService {

	private final Users users;
	private final BuddiSystem buddiSystem;

	public BuddiLiveAuthenticationTransactionalService(final Users users, final BuddiSystem buddiSystem) {
		this.users = users;
		this.buddiSystem = buddiSystem;
	}

	@Transactional(readOnly = true)
	public User selectUserByHashedIdentifier(final String hashedIdentifier) {
		return users.selectUser(hashedIdentifier);
	}

	@Transactional
	public void upgradeLegacyUserSecret(final User user, final String hashedSecret) {
		users.updateUserSecret(user, hashedSecret);
	}

	@Transactional
	public void insertTotpSecret(final String hashedUsername, final String totpSharedSecret) {
		final User user = users.selectUser(hashedUsername);
		if (user == null) {
			throw new DatabaseException("User not found");
		}

		users.deleteUnusedBackupCodes(user);
		final int count = users.updateUserTotpSecret(user, totpSharedSecret);
		if (count != 1) {
			throw new DatabaseException(String.format("Update failed; expected 1 row, returned %s", count));
		}
	}

	@Transactional
	public void insertTotpBackupCodes(final String hashedUsername) {
		final User user = users.selectUser(hashedUsername);
		if (user == null) {
			throw new DatabaseException("User not found");
		}

		users.deleteUnusedBackupCodes(user);
		for (int i = 0; i < 10; i++) {
			final String backupCode = UUID.randomUUID().toString();
			users.insertTotpBackupCode(user, backupCode);
		}
	}

	@Transactional
	public void updateTotpBackupCodeMarkUsed(final String hashedUsername, final String backupCode) {
		final User user = users.selectUser(hashedUsername);
		if (user == null) {
			throw new DatabaseException("User not found");
		}

		final int count = users.updateUserTotpBackupCodeUsed(user, backupCode);
		if (count != 1) {
			throw new DatabaseException(String.format("Update failed; expected 1 row, returned %s", count));
		}
	}

	@Transactional
	public void disableTotp(final String hashedUsername) {
		final User user = users.selectUser(hashedUsername);
		if (user == null || !StringUtils.isBlank(user.getTwoFactorSecret())) {
			throw new DatabaseException("User does not meet disableTotp constraints");
		}

		user.setTwoFactorRequired(false);
		final int count = users.updateUser(user);
		if (count != 1) {
			throw new DatabaseException(String.format("Update failed; expected 1 row, returned %s", count));
		}

		users.updateUserTotpSecret(user, null);
		users.deleteUnusedBackupCodes(user);
	}

	@Transactional
	public void updateActivationKey(final String hashedIdentifier, final String activationKey) {
		final User user = users.selectUser(hashedIdentifier);
		if (user == null) {
			throw new DatabaseException("Could not find user with hashed identifier" + hashedIdentifier);
		}
		if (user.isEncrypted()) {
			throw new DatabaseException("Users with encrypted data cannot reset passwords.");
		}

		cleanupUsers(user);

		final Integer count = users.insertActivationKey(user, activationKey);
		if (count != 1) {
			throw new DatabaseException(String.format("Insert failed; expected 1 row, returned %s", count));
		}
	}

	@Transactional
	public void updatePasswordByActivationKey(final String activationKey, final String hashedPassword) {
		final User user = users.selectUserByActivationKey(activationKey);
		if (user == null) {
			throw new DatabaseException("Activation key is not valid");
		}
		final Integer count = users.updateUserSecret(user, hashedPassword);
		if (count != 1) {
			throw new DatabaseException(String.format("Update failed; expected 1 row, returned %s", count));
		}

		cleanupUsers(user);
	}

	@Transactional(rollbackFor = Exception.class)
	public void insertUser(final String hashedIdentifier, final String activationKey, final Map<String, String> formParams) throws Exception {
		if (!"on".equals(formParams.getOrDefault("agree", "off"))) {
			throw new IllegalArgumentException(LocaleUtil.getTranslation().getString("CREATE_USER_AGREEMENT_REQUIRED"));
		}

		final User newUser = new User();
		newUser.setIdentifier(hashedIdentifier);
		newUser.setUuid(UUID.randomUUID().toString());
		newUser.setCurrency(Currency.getInstance(formParams.getOrDefault("currency", "USD")));
		newUser.setLocale(LocaleUtil.parseLocale(formParams.getOrDefault("locale", "en_US"), Locale.US));

		final User existingUser = users.selectUser(newUser.getIdentifier());
		if (existingUser != null) {
			if (existingUser.getSecret() != null && existingUser.getSecret().length > 0) {
				throw new DatabaseException("The user name already exists");
			}
			users.deleteActivationKey(existingUser);
			final Integer insertActivationCount = users.insertActivationKey(existingUser, activationKey);
			if (insertActivationCount != 1) {
				throw new DatabaseException(String.format("Activation key insert failed; expected 1 row, returned %s", insertActivationCount));
			}
			return;
		}

		cleanupUsers(null);

		final Integer insertUserCount = users.insertUser(newUser);
		if (insertUserCount != 1) {
			throw new DatabaseException(String.format("User insert failed; expected 1 row, returned %s", insertUserCount));
		}
		final Integer insertActivationCount = users.insertActivationKey(newUser, activationKey);
		if (insertActivationCount != 1) {
			throw new DatabaseException(String.format("Activation key insert failed; expected 1 row, returned %s", insertActivationCount));
		}
	}

	@Transactional
	public SecretKey getOrCreateCookieEncryptionKey() throws CryptoException {
		SecretKey key;
		try {
			String keyEncoded = buddiSystem.selectCookieEncryptionKey();
			if (keyEncoded == null) {
				key = new Crypto().setAlgorithm(Algorithm.AES_256).generateSecretKey();
				keyEncoded = Crypto.encodeSecretKey(key);
				buddiSystem.deleteCookieEncryptionKey();
				buddiSystem.insertCookieEncryptionKey(keyEncoded);
			}
			key = Crypto.recoverSecretKey(keyEncoded);
		}
		catch (CryptoException e) {
			key = new Crypto().setAlgorithm(Algorithm.AES_256).generateSecretKey();
			final String keyEncoded = Crypto.encodeSecretKey(key);
			buddiSystem.updateCookieEncryptionKey(keyEncoded);
		}

		return key;
	}

	private void cleanupUsers(final User user) {
		if (user == null) {
			users.deleteActivationKey();
		}
		else {
			users.deleteActivationKey(user);
		}
		users.deleteInactiveUsers();
	}
}
