package ca.digitalcave.moss.restlet.plugin;

import java.security.Key;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.crypto.SecretKey;

import org.restlet.data.ChallengeResponse;
import org.restlet.data.Form;

import ca.digitalcave.moss.crypto.Crypto;
import ca.digitalcave.moss.crypto.Crypto.CryptoException;
import ca.digitalcave.moss.crypto.DefaultHash;
import ca.digitalcave.moss.crypto.Hash;
import ca.digitalcave.moss.restlet.model.AuthUser;
import ca.digitalcave.moss.restlet.util.PasswordChecker;

/**
 * Implement this class to provide hooks for the authentication plugin to use when performing different actions.
 * 
 * Required methods are marked as abstract; optional methods will throw an exception if used.
 */
public abstract class AuthenticationHelper {
	
	private final AuthenticationConfiguration config;
	
	public AuthenticationHelper(AuthenticationConfiguration config) {
		this.config = config;
	}
	
	
	/**
	 * Return the config object.  The properties on this object should be modified by the Application startup code prior to routes being added.
	 */
	public AuthenticationConfiguration getConfig() {
		return config;
	}
	
	//******************* Authentication / User Section *******************//

	/**
	 * Authenticate the supplied ChallengeResponse.  If you want to check for 2FA, expired passwords, etc, this is the place to do it.
	 * 
	 * The 'applicationName' parameter will be passed in as the context path.
	 * 
	 * If credentials are valid, true; otherwise false
	 * 
	 * If credentials are valid, but further action is needed before login can complete, call the appropriate methods on CookieAuthenticator as follows:
	 * 		TOTP Required for user: CookieAuthenticator.setTwoFactorNeeded()
	 * 		TOTP Required for user but not yet set up: CookieAuthenticator.setTwoFactorSetupNeeded() 
	 * 		Password expired / force change: CookieAuthenticator.setPasswordExpired()
	 * 
	 * At a minimum, you MUST return the AuthUser if authenticated, or null otherwise.  If your system supports other features, you MUST call the appropriate helpers on CookieAuthenticator.
	 * If your system supports SSO, you MUST call setSamlSessionIndexVerified.
	 * 
	 * It is STRONGLY RECOMMENDED that you hook this into your Verifier (or better yet, use the CookieVerifier which calls this directly) so that the same code is run for login as 
	 * for normal authentication.
	 */
	public abstract AuthUser authenticate(String applicationName, ChallengeResponse challengeResponse, Form form);
	
	/**
	 * Returns the user associated with the username.
	 */
	public abstract AuthUser selectUser(String username);
	
	/**
	 * Returns the list of users associated with the email address.
	 */
	public abstract List<AuthUser> selectUsers(String email);

	//*******************TOTP Section *******************//
	/**
	 * Generates a list of backup codes for the given user and persists them.  Use 'getBackupCodes' to get the newly created list.
	 */
	public abstract void insertTotpBackupCodes(String username, ChallengeResponse cr);
	
	/**
	 * Disables the supplied TOTP backup code.  Called from TotpTokenResource, after someone uses a backup code to log in.
	 */
	public abstract void updateTotpBackupCodeMarkUsed(String username, String backupCode, ChallengeResponse cr);
	
	/**
	 * Implement this to store the two factor secret for the provided identifier.  The return value of this
	 * method MUST return true if the secret was successfully stored, false otherwise.
	 */
	public boolean insertTotpSecret(String username, String totpSharedSecret, ChallengeResponse cr){
		throw new RuntimeException("Not implemented");
	}
	
	/**
	 * Disables TOTP for the specified user.  This should include setting the 'totp required' flag to false at a minimum.  
	 * This is called from the "TOTP Setup" panel, and allows a user to cancel their TOTP request.
	 * It is recommended that implementing code verifies that the user which is disabling TOTP does not have a secret
	 * set up (i.e. TOTP is not yet set up, and is in the process of being enabled).
	 */
	public abstract void disableTotp(String username) ;

	//******************* Forgot Password Section *******************//
	
	/**
	 * Implement this to set an activation key for the provided identifier.  The return value of this method MUST return 
	 * the email address for the username, or null if the user is invalid / activation key generation failed.
	 */
	public abstract String updateActivationKey(String username, String activationKey) throws Exception;
	
	/**
	 * Implement this to validate the activation key and (re)set the user's password.  MUST return true if the activation key 
	 * is valid and the new password is successfully set, false otherwise.
	 */
	public abstract boolean updatePasswordByActivationKey(String activationKey, String hashedPassword);
	
	//******************* Register User Section *******************//
	
	/**
	 * Registers a new user given an email and activation key.  The form will include the POSTed form, so if there are extra registration fields they can be used.
	 * The return value of this method MUST return true if the registration is successful, or false otherwise.
	 */
	public abstract void insertUser(String email, String activationKey, Form form) throws Exception;
	
	//******************* Expired Password Section *******************//
	
	/**
	 * Implement this to set password for the specified user.  MUST return true if the new password is successfully
	 * set, false otherwise
	 */
	public abstract boolean updatePassword(String username, String hashedPassword);
	
	//******************* Cookie Encryption Section *******************//
	
	/**
	 * Returns the crypto object.  By default this is AES128 with 16 bytes salt and one iteration.
	 */
	public Crypto getCrypto(){
		return new Crypto();
	}
	
	protected Key key = null;
	/**
	 * Returns the key which is used to encrypt the authentication cookie.  By default this uses the 'selectKey' and 'updateKey' methods on helper to persist the key across server restarts.
	 */
	public Key getKey() {
		if (key == null){
			synchronized(this){
				try {
					String keyEncoded = selectKey();
					if (keyEncoded != null) {
						key = Crypto.recoverSecretKey(keyEncoded);
						//Test the stored encryption key; if it doesn't work, discard it.
						try {
							getCrypto().encrypt(key, "foo");
						}
						catch (CryptoException e){
							key = null;
							Logger.getLogger(this.getClass().getName()).log(Level.WARNING, "Error when testing stored encryption key; discarding stored key and re-generating.", e);
						}
					}
					
					if (key == null){
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
	
	/**
	 * Selects the server key.  By default we just generate a new one, which will result in cookies being invalidated after server reboot.
	 */
	public String selectKey() throws Exception {
		return Crypto.encodeSecretKey(getCrypto().generateSecretKey());
	}
	
	/**
	 * Persists the cookie encryption key.  By default we do nothing, which will result in cookies being invalidated after server reboot.
	 */
	public void updateKey(String encodedKey){
		;
	}
	
	/**
	 * Set the cookie path.  Multiple applications with the same host and same path can share cookies.
	 */
	public String getCookiePath(){
		return "/";
	}
	
	public String getCookieName(){
		return "enrich_auth";
	}
	
	/**
	 * Use the 'secure cookies' flag.
	 */
	public boolean isUseSecureCookies(){
		return true;
	}
	
	//******************* General Section *******************//
	
	/**
	 * Implement this to send an email
	 */
	public abstract void sendEmail(final String toEmail, final String subject, final String body);
	
	
	/**
	 * Returns the PasswordChecker class.  By default we return a reasonably lenient password checker.
	 */
	public PasswordChecker getPasswordChecker(){
		return new PasswordChecker();
	}
	
	/**
	 * Return the hash algorithm to be used for this application.  By default this hash is SHA-256 with 16 bytes of salt and one iteration.
	 */
	public Hash getHash(){
		return new DefaultHash();
	}
}
