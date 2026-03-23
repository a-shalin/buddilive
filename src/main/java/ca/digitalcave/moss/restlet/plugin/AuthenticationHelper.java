package ca.digitalcave.moss.restlet.plugin;

import java.security.Key;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.crypto.SecretKey;

import ca.digitalcave.moss.crypto.Crypto;
import ca.digitalcave.moss.crypto.Crypto.CryptoException;
import ca.digitalcave.moss.crypto.DefaultHash;
import ca.digitalcave.moss.crypto.Hash;
import ca.digitalcave.moss.restlet.model.AuthUser;
import ca.digitalcave.moss.restlet.util.PasswordChecker;

public abstract class AuthenticationHelper {

	private final AuthenticationConfiguration config;

	public AuthenticationHelper(AuthenticationConfiguration config) {
		this.config = config;
	}

	public AuthenticationConfiguration getConfig() {
		return config;
	}

	//******************* Authentication / User Section *******************//

	public abstract AuthUser authenticate(String applicationName, String identifier, String secret);

	public abstract AuthUser selectUser(String username);

	public abstract List<AuthUser> selectUsers(String email);

	//*******************TOTP Section *******************//

	public abstract void insertTotpBackupCodes(String username);

	public abstract void updateTotpBackupCodeMarkUsed(String username, String backupCode);

	public boolean insertTotpSecret(String username, String totpSharedSecret) {
		throw new RuntimeException("Not implemented");
	}

	public abstract void disableTotp(String username);

	//******************* Forgot Password Section *******************//

	public abstract String updateActivationKey(String username, String activationKey) throws Exception;

	public abstract boolean updatePasswordByActivationKey(String activationKey, String hashedPassword);

	//******************* Register User Section *******************//

	public abstract void insertUser(String email, String activationKey, Map<String, String> formParams) throws Exception;

	//******************* Expired Password Section *******************//

	public abstract boolean updatePassword(String username, String hashedPassword);

	//******************* Cookie Encryption Section *******************//

	public Crypto getCrypto() {
		return new Crypto();
	}

	protected Key key = null;

	public Key getKey() {
		if (key == null) {
			synchronized (this) {
				try {
					String keyEncoded = selectKey();
					if (keyEncoded != null) {
						key = Crypto.recoverSecretKey(keyEncoded);
						try {
							getCrypto().encrypt(key, "foo");
						}
						catch (CryptoException e) {
							key = null;
							Logger.getLogger(this.getClass().getName()).log(Level.WARNING, "Error when testing stored encryption key; discarding stored key and re-generating.", e);
						}
					}

					if (key == null) {
						key = getCrypto().generateSecretKey();
						keyEncoded = Crypto.encodeSecretKey((SecretKey) key);
						updateKey(keyEncoded);
					}
				}
				catch (Exception e) {
					throw new RuntimeException(e);
				}
				return key;
			}
		}

		return key;
	}

	public String selectKey() throws Exception {
		return Crypto.encodeSecretKey(getCrypto().generateSecretKey());
	}

	public void updateKey(String encodedKey) {
		;
	}

	public String getCookiePath() {
		return "/";
	}

	public String getCookieName() {
		return "enrich_auth";
	}

	public boolean isUseSecureCookies() {
		return true;
	}

	//******************* General Section *******************//

	public abstract void sendEmail(final String toEmail, final String subject, final String body);

	public PasswordChecker getPasswordChecker() {
		return new PasswordChecker();
	}

	public Hash getHash() {
		return new DefaultHash();
	}
}
