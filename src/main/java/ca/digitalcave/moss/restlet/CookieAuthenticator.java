package ca.digitalcave.moss.restlet;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.Semaphore;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.lang3.StringUtils;
import org.restlet.Context;
import org.restlet.Request;
import org.restlet.Response;
import org.restlet.data.ChallengeResponse;
import org.restlet.data.ChallengeScheme;
import org.restlet.data.Cookie;
import org.restlet.data.CookieSetting;
import org.restlet.data.Form;
import org.restlet.data.Status;
import org.restlet.security.ChallengeAuthenticator;
import org.restlet.security.Verifier;
import org.restlet.util.NamedValue;

import ca.digitalcave.moss.crypto.Crypto;
import ca.digitalcave.moss.restlet.plugin.AuthenticationHelper;

/**
 * The CookieAuthenticator is the core of this plugin.  It reads auth cookies sent with the request, and if they are valid, populates the 
 * ChallengeResponse object which will later be verified.
 */
public class CookieAuthenticator extends ChallengeAuthenticator {

	//These are field names.  They are used for reading POSTed forms (i.e. login credentials) as well as for ChallengeResponse parameter keys.
	public static final String FIELD_IDENTIFIER = "identifier";								//The user who we assume is logged in.  If impersonating, this will be the impersonated user.
	public static final String FIELD_AUTHENTICATOR = "authenticator";						//The user who has authenticated.  If impersonating, this will be the admin user; if not impersonating, it will be the same as the identifier.
	public static final String FIELD_IMPERSONATE = "impersonate";							//The user who is being impersonated.  This needs to be set in the form POST for the initial request, and will be added to the ChallengeResponse properties for convenience.
	public static final String FIELD_PASSWORD = "password";									//The password of the authenticator
	public static final String FIELD_REMEMBER = "remember";
	public static final String FIELD_DISABLE_IP_LOCK = "disableIpLock";
	public static final String FIELD_EMAIL = "email";										//The email address.  Used for 'Forgot username' request.
	public static final String FIELD_ACTIVATION_KEY = "activationKey";						//The activation key.  Used for 'Reset Password' request.
	public static final String FIELD_TIME_ISSUED = "timeIssued";
	public static final String FIELD_COOKIE_EXPIRY_MILLIS = "cookieExpires";
	public static final String FIELD_PASSWORD_EXPIRED = "passwordExpired";
	public static final String FIELD_CLIENT_ADDRESS = "clientAddress";
	
	public static final String FIELD_TOTP_TOKEN = "totpToken";								//The TOTP token submitted by the user
	public static final String FIELD_TOTP_SHARED_SECRET = "totpSharedSecret";				//The TOTP shared secret.  Used for sending the secret to the user at TOTP setup time
	public static final String FIELD_TOTP_SHARED_SECRET_QR = "totpSharedSecretQr";			//The TOTP shared secret in QR code format.  Used for sending the secret to the user at TOTP setup time
	
	public static final String FIELD_TWO_FACTOR_VALIDATED_IDENTIFIER = "twoFactorIdentifier";	//The user who has validated two factor.  Normally this will be the same as the logged in user, but if may be the authenticator if impersonating.
	public static final String FIELD_TWO_FACTOR_VALIDATED = "twoFactorValidated";			//The user has used two factor auth when logging in.  This may be TOTP (for password logins), or the SSO may have prompted for two factor.
	
	public static final String FIELD_TWO_FACTOR_REQUIRED = "twoFactorRequired";				//Is Two Factor auth needed for this user?  This is not persisted in the cookie, but is set in CookieVerifier every request.
	public static final String FIELD_TWO_FACTOR_SETUP = "twoFactorSetup";					//Is Two Factor auth setup for this user?  This is not persisted in the cookie, but is set in CookieVerifier every request.
	
	
	//These are the keys for storing objects in the request attributes
	public static final String ATTRIBUTE_AUTHENTICATION_HELPER = "authenticationHelper";
	
	private final static Map<String, Long> loggedInUsers = Collections.synchronizedMap(new HashMap<String, Long>());
	
	private final static Map<String, Semaphore> loginAttempts = Collections.synchronizedMap(new WeakHashMap<String, Semaphore>());

	private final AuthenticationHelper authenticationHelper;
	
	private volatile int delay = 1500;
	private volatile boolean secure = true;

	/**
	 * This authenticator will read an encrypted cookie and (if the decryption works) validate the username / password.
//	 * <p>If a verifier accepts alternate identifiers such as email address for login it MUST change the identifier in the challenge response to the canonical identifier.</p>
//	 * <p>The verifier MUST replace the identifier from activation key to the canonical identifier during activation.</p>
	 */
	public CookieAuthenticator(Context context, boolean optional, AuthenticationHelper authenticationHelper, Verifier verifier) {
		super(context, optional, ChallengeScheme.HTTP_COOKIE, null, verifier);
		this.authenticationHelper = authenticationHelper;
	}
	
	/**
	 * Helper method to get the authentication helper from the request attributes.
	 */
	public static AuthenticationHelper getAuthenticationHelper(Request request){
		return ((AuthenticationHelper) request.getAttributes().get(CookieAuthenticator.ATTRIBUTE_AUTHENTICATION_HELPER));
	}
	
	@Override
	public void challenge(Response response, boolean stale) {
		boolean loggable = response.getRequest().isLoggable() && getLogger().isLoggable(Level.FINE);
		if (loggable) {
			getLogger().log(Level.FINE, "An authentication challenge was requested.");
		}
		response.setStatus(Status.CLIENT_ERROR_UNAUTHORIZED);
	}
	
	@Override
	protected boolean authenticate(Request request, Response response) {
		final Cookie cookie = request.getCookies().getFirst(authenticationHelper.getCookieName());
		final ChallengeResponse cr = getChallengeResponseFromEncryptedCookie(cookie, request, authenticationHelper);
		if (cr != null || !isOptional()){
			request.setChallengeResponse(cr);
		}

		final boolean authenticated = super.authenticate(request, response);
		
		if (!authenticated && cookie != null && !CookieAuthenticator.isPasswordExpired(cr)){	//We don't want to discard the cookie if the password is expired; we need the cookie to change the password.
			//If the cookie was set, but not valid, delete it.
			CookieAuthenticator.setCookie(authenticationHelper, request, response, "", 0);
		}
		
		if (!authenticated && !isOptional() && delay > 0 && cr != null) {
			// delay to reduce the effectiveness of brute force attacks
			final String identifier = cr.getIdentifier();
			
			try {
				if (loginAttempts.get(identifier) == null) {
					loginAttempts.put(identifier, new Semaphore(1));
				}
				final Semaphore semaphore = loginAttempts.get(identifier);
				semaphore.acquire();
				Thread.sleep(delay);
				semaphore.release();
			}
			catch (InterruptedException e) {
				;
			}
		}
		
		if (authenticated){
			request.getAttributes().put(CookieAuthenticator.ATTRIBUTE_AUTHENTICATION_HELPER, authenticationHelper);
			final String clientInfoIdentifier = request.getClientInfo() != null && request.getClientInfo().getUser() != null ? request.getClientInfo().getUser().getIdentifier() : null;
			final String challengeResponseIdentifier = cr != null ? cr.getIdentifier() : null;
			if (clientInfoIdentifier != null){
				loggedInUsers.put(clientInfoIdentifier, System.currentTimeMillis());
			}
			else {
				loggedInUsers.put(challengeResponseIdentifier, System.currentTimeMillis());
			}
		}
		
		return authenticated;
	}

	@Override
	protected int authenticated(Request request, Response response) {
		final ChallengeResponse cr = request.getChallengeResponse();
		if (cr != null && cr.getScheme().equals(ChallengeScheme.HTTP_COOKIE)){
			final long expireTime = Long.parseLong(cr.getParameters().getFirstValue(FIELD_COOKIE_EXPIRY_MILLIS));
			if (StringUtils.isBlank(cr.getParameters().getFirstValue(FIELD_CLIENT_ADDRESS))							//If the client address is blank...
					|| (cr.getTimeIssued() < System.currentTimeMillis() - 300000)									// ... or the cookie has been issued more than 5 minutes ago...
					|| (expireTime - 300000 < System.currentTimeMillis())){											// ... or if the cookie will expire in the next 5 minutes ...
				setEncryptedCookieFromChallengeResponse(request, response, authenticationHelper);					// ... we refresh the cookie
			}
		}
		
		return super.authenticated(request, response);
	}
	
	public static Collection<String> getLoggedInUsers(){
		try {
			for (String identifier : loggedInUsers.keySet()) {
				if (loggedInUsers.get(identifier) < (System.currentTimeMillis() - (1000 * 60 * 7))){	//If the user made a request in the past 7 minutes we assume they are still logged in.
					loggedInUsers.remove(identifier);
				}
			}
		}
		catch (Throwable e){
			Logger.getLogger(CookieAuthenticator.class.getName()).log(Level.INFO, "Exception thrown when cleaning logged in users.  Proceeding.");
		}
		
		return Collections.unmodifiableCollection(loggedInUsers.keySet());
	}
	
	/**
	 * Given the supplied encrypted cookie value and key, return a ChallengeResponse object with all the required parameters populated.
	 */
	public static ChallengeResponse getChallengeResponseFromEncryptedCookie(Cookie cookie, Request request, AuthenticationHelper authenticationHelper) {
		if (cookie == null){
			return null;
		}
		
		final String encrypted = cookie.getValue();
		try {
			final String decrypted = Crypto.decrypt(authenticationHelper.getKey(), encrypted);
			
			final Form p = new Form(decrypted);
			
			long expires = Long.parseLong(p.getFirstValue(FIELD_COOKIE_EXPIRY_MILLIS));
			if (expires < System.currentTimeMillis()) {
				return null;
			}
			
			final boolean disableIpLock = Boolean.parseBoolean(p.getFirstValue(FIELD_DISABLE_IP_LOCK, "false"));
			final String clientAddress = p.getFirstValue(FIELD_CLIENT_ADDRESS, "");
			//Validate the IP lock, if applicable
			if (!disableIpLock && StringUtils.isNotBlank(clientAddress) && !StringUtils.equals(clientAddress, getClientAddress(request))){
				return null;
			}

			final ChallengeResponse result = new ChallengeResponse(ChallengeScheme.HTTP_COOKIE);
			result.setRawValue(decrypted);
			result.setTimeIssued(Long.parseLong(p.getFirstValue(FIELD_TIME_ISSUED)));
			result.getParameters().set(FIELD_COOKIE_EXPIRY_MILLIS, Long.toString(expires));
			
			result.setIdentifier(p.getFirstValue(FIELD_IDENTIFIER));
			result.setSecret(p.getFirstValue(FIELD_PASSWORD));
			
			if (p.getFirstValue(FIELD_AUTHENTICATOR) != null) {
				result.getParameters().set(FIELD_AUTHENTICATOR, p.getFirstValue(FIELD_AUTHENTICATOR));
			}
			result.getParameters().set(FIELD_REMEMBER, p.getFirstValue(FIELD_REMEMBER, "false"));
			result.getParameters().set(FIELD_DISABLE_IP_LOCK, p.getFirstValue(FIELD_DISABLE_IP_LOCK, "false"));
			result.getParameters().set(FIELD_CLIENT_ADDRESS, clientAddress);
			
			result.getParameters().set(FIELD_TWO_FACTOR_VALIDATED_IDENTIFIER, p.getFirstValue(FIELD_TWO_FACTOR_VALIDATED_IDENTIFIER));
			result.getParameters().set(FIELD_TWO_FACTOR_VALIDATED, Boolean.toString(Boolean.parseBoolean(p.getFirstValue(FIELD_TWO_FACTOR_VALIDATED, "false"))));
			result.getParameters().set(FIELD_TOTP_SHARED_SECRET, p.getFirstValue(FIELD_TOTP_SHARED_SECRET));	//This is used to store the generated secret when setting up TOTP.

			return result;
		}
		catch (Exception e) {
			Logger.getLogger(CookieAuthenticator.class.getName()).log(Level.INFO, "Unable to decrypt cookie credentials", e);
			return null;
		}
	}

	public static void setEncryptedCookieFromChallengeResponse(Request request, Response response, AuthenticationHelper helper) {
		setEncryptedCookieFromChallengeResponse(request, response, helper, true);
	}
	public static void setEncryptedCookieFromChallengeResponse(Request request, Response response, AuthenticationHelper helper, boolean ipLock) {
		final ChallengeResponse cr = request.getChallengeResponse();
		final Form p = new Form();
		long expiryTimeMillis;
		final boolean remember = Boolean.parseBoolean(cr.getParameters().getFirstValue(FIELD_REMEMBER, "false"));
		final boolean disableIpLock = Boolean.parseBoolean(cr.getParameters().getFirstValue(FIELD_DISABLE_IP_LOCK, "false"));
		p.set(FIELD_REMEMBER, Boolean.toString(remember));
		p.set(FIELD_DISABLE_IP_LOCK, Boolean.toString(disableIpLock));
		if (ipLock){
			//If ipLock is false, we don't set the client address field.  This means that the cookie will be valid for any IP.  This is a security risk, but it is mitigated by
			// the fact that the expiry timeout is 30 seconds in the future.
			if (!disableIpLock) {
				p.set(FIELD_CLIENT_ADDRESS, getClientAddress(request));
			}
			
			if (remember){
				expiryTimeMillis = 30l * 24 * 60 * 60 * 1000;	//If "Remember me" is set, the cookie is valid for 30 days
			}
			else {
				expiryTimeMillis = 60 * 60 * 1000;		//By default the cookie is valid for one hour
			}
		}
		else {
			expiryTimeMillis = 30 * 1000;	//If ipLock is false, we set the expiry date for 30 seconds in the future.
		}

		final long issued = System.currentTimeMillis();	// / cookieRenewIntervalMillis * cookieRenewIntervalMillis;		//The issue date is truncated to <cookieRenewIntervalMillis> intervals (defaults to 5 minutes).  This is what determines when the cookie is to be renewed.
		final long expires = issued + expiryTimeMillis;
		
		int maxAge = (int) (expiryTimeMillis / 1000);

		p.set(FIELD_TIME_ISSUED, Long.toString(issued));
		p.set(FIELD_COOKIE_EXPIRY_MILLIS, Long.toString(expires));
		p.set(FIELD_IDENTIFIER, cr.getIdentifier());
		if (cr.getSecret() != null){
			p.set(FIELD_PASSWORD, new String(cr.getSecret()));
		}
		final String authenticator = cr.getParameters().getFirstValue(FIELD_AUTHENTICATOR);
		if (authenticator != null){
			p.set(FIELD_AUTHENTICATOR, authenticator);
		}

		p.set(FIELD_TWO_FACTOR_VALIDATED_IDENTIFIER, cr.getParameters().getFirstValue(FIELD_TWO_FACTOR_VALIDATED_IDENTIFIER));
		p.set(FIELD_TWO_FACTOR_VALIDATED, Boolean.toString(Boolean.parseBoolean(cr.getParameters().getFirstValue(FIELD_TWO_FACTOR_VALIDATED, "false"))));
		p.set(FIELD_TOTP_SHARED_SECRET, cr.getParameters().getFirstValue(FIELD_TOTP_SHARED_SECRET));

		try {
			response.getCookieSettings().removeAll(helper.getCookieName());
			setCookie(helper, request, response, helper.getCrypto().encrypt(helper.getKey(), p.getQueryString()), maxAge);
		}
		catch (Exception e){
			Logger.getLogger(CookieAuthenticator.class.getName()).log(Level.WARNING, e.getMessage(), e);
		}
	}
	
	
	private static String getClientAddress(Request request){
		//We start with Restlet's client info upstream address.
		String clientAddress = request.getClientInfo().getUpstreamAddress();
		
		//If there are any x-forwarded-for headers, we use this to override.
		for (NamedValue<String> namedValue : request.getHeaders()) {
			if ("x-forwarded-for".equalsIgnoreCase(namedValue.getName())){
				clientAddress = namedValue.getValue();
			}
		}
		return clientAddress;
	}
	
	public static void setCookie(AuthenticationHelper helper, Request request, Response response, String value, int maxAge){
		response.getCookieSettings().removeAll(helper.getCookieName());
		final CookieSetting credentialsCookie = new CookieSetting(helper.getCookieName(), null);
		credentialsCookie.setValue(value);
		credentialsCookie.setAccessRestricted(true);
		credentialsCookie.setSecure(helper.isUseSecureCookies());
		credentialsCookie.setMaxAge(maxAge);

		if (request.getRootRef() != null) {
			final String path = helper.getCookiePath();
			credentialsCookie.setPath(path == null ? "/" : path);
		}

		response.getCookieSettings().add(credentialsCookie);
	}
	
	public int getDelay() {
		return delay;
	}
	/**
	 * Sets the number of milliseconds to delay after authentication failure.
	 * Defaults to 1500.
	 * Use this option to limit brute force attacks.
	 */
	public void setDelay(int delay) {
		this.delay = delay;
	}
	
	public boolean isSecure() {
		return secure;
	}
	/**
	 * Indicates if the cookie should only be transmitted from the browser to the server by secure means (https). 
	 * Default is false.
	 */
	public void setSecure(boolean secure) {
		this.secure = secure;
	}

	/**
	 * Returns the authenticated user; if impersonating another user, this will return the user who is doing the impersonating (i.e. the admin user)
	 */
	public static String getAuthenticator(ChallengeResponse challengeResponse) {
		if (challengeResponse == null){
			return null;
		}
		if (challengeResponse.getParameters().getFirstValue(FIELD_AUTHENTICATOR) != null) {
			return challengeResponse.getParameters().getFirstValue(FIELD_AUTHENTICATOR);
		} else {
			return challengeResponse.getIdentifier();
		}
	}
	
	public static boolean isPasswordExpired(ChallengeResponse challengeResponse){
		if (challengeResponse == null){
			return false;
		}
		return Boolean.parseBoolean(challengeResponse.getParameters().getFirstValue(FIELD_PASSWORD_EXPIRED, "false"));
	}
	public static void setPasswordExpired(ChallengeResponse challengeResponse) {
		if (challengeResponse == null){
			return;
		}
		challengeResponse.getParameters().set(FIELD_PASSWORD_EXPIRED, "true");
	}
	
	public static boolean isTwoFactorRequired(ChallengeResponse challengeResponse){
		if (challengeResponse == null){
			return false;
		}
		return Boolean.parseBoolean(challengeResponse.getParameters().getFirstValue(FIELD_TWO_FACTOR_REQUIRED, "false"));
	}
	public static void setTwoFactorRequired(ChallengeResponse challengeResponse) {
		if (challengeResponse == null){
			return;
		}
		challengeResponse.getParameters().set(FIELD_TWO_FACTOR_REQUIRED, "true");
	}
	public static boolean isTwoFactorSetup(ChallengeResponse challengeResponse){
		if (challengeResponse == null){
			return false;
		}
		return Boolean.parseBoolean(challengeResponse.getParameters().getFirstValue(FIELD_TWO_FACTOR_SETUP, "false"));
	}
	public static void setTwoFactorSetup(ChallengeResponse challengeResponse) {
		if (challengeResponse == null){
			return;
		}
		challengeResponse.getParameters().set(FIELD_TWO_FACTOR_SETUP, "true");
	}
	
	public static boolean isTwoFactorValidated(ChallengeResponse challengeResponse){
		if (challengeResponse == null){
			return false;
		}

		final boolean twoFactorValidated = Boolean.parseBoolean(challengeResponse.getParameters().getFirstValue(FIELD_TWO_FACTOR_VALIDATED, "false"));
		final boolean twoFactorValidatedByAuthenticator = getAuthenticator(challengeResponse).equals(challengeResponse.getParameters().getFirstValue(FIELD_TWO_FACTOR_VALIDATED_IDENTIFIER));
		
		return twoFactorValidated && twoFactorValidatedByAuthenticator;
	}
	public static void setTwoFactorValidated(ChallengeResponse challengeResponse){
		if (challengeResponse == null){
			return;
		}

		challengeResponse.getParameters().removeAll(CookieAuthenticator.FIELD_TOTP_SHARED_SECRET);	//If we have any shared secrets in the ChallengeResponse (from TOTP setup), delete them now.
		challengeResponse.getParameters().set(FIELD_TWO_FACTOR_VALIDATED, "true");
		challengeResponse.getParameters().set(FIELD_TWO_FACTOR_VALIDATED_IDENTIFIER, challengeResponse.getIdentifier());
	}
	
	public static void setTwoFactorInvalid(Request request, Response response) {
		final ChallengeResponse challengeResponse = request.getChallengeResponse();
		challengeResponse.getParameters().removeAll(FIELD_TWO_FACTOR_VALIDATED);
		challengeResponse.getParameters().removeAll(FIELD_TWO_FACTOR_VALIDATED_IDENTIFIER);

		CookieAuthenticator.setEncryptedCookieFromChallengeResponse(request, response, CookieAuthenticator.getAuthenticationHelper(request));
	}

	public static boolean isImpersonating(ChallengeResponse challengeResponse){
		if (challengeResponse == null){
			return false;
		}

		final String authenticator = challengeResponse.getParameters().getFirstValue(FIELD_AUTHENTICATOR);
		return authenticator != null && authenticator.trim().length() > 0;
	}
	
	public static boolean isAuthenticationValid(ChallengeResponse challengeResponse){
		if (challengeResponse == null){
			return false;
		}
		return isPrimaryAuthenticationValid(challengeResponse) && isSecondaryAuthenticationValid(challengeResponse);
	}
	
	/**
	 * Returns true if the primary authentication checks have returned true.  This means that one of the following has succeeded:
	 *  - Username / Password verified (and password not expired)
	 *  - SSO validated
	 */
	public static boolean isPrimaryAuthenticationValid(ChallengeResponse challengeResponse){
		if (challengeResponse == null || challengeResponse.getParameters() == null){
			return false;
		}
		
		if (StringUtils.isNotBlank(challengeResponse.getIdentifier()) && challengeResponse.getScheme().equals(ChallengeScheme.HTTP_COOKIE)){
			final boolean passwordExpired = Boolean.parseBoolean(challengeResponse.getParameters().getFirstValue(FIELD_PASSWORD_EXPIRED, "false"));
			if (passwordExpired){
				return false;
			}

			return true;
		}
		
		return false;
	}
	
	
	/**
	 * Returns true if all secondary authentication checks have returned true.  This includes:
	 *  - TOTP has either been validated or it is not required for this user
	 *  - Basic Auth does not support 2fa, and so is exempt from these checks.
	 */
	public static boolean isSecondaryAuthenticationValid(ChallengeResponse challengeResponse){
		if (challengeResponse == null){
			return false;
		}
		if (!challengeResponse.getScheme().equals(ChallengeScheme.HTTP_BASIC)){		//Basic auth doesn't need 2FA.  This is used for web services.
			if (challengeResponse == null || challengeResponse.getParameters() == null){
				return false;
			}

			if (isTwoFactorValidated(challengeResponse)){	//If two factor has been validated (and the user matches), then we are good to go.
				return true;
			}
			else if (CookieAuthenticator.isTwoFactorRequired(challengeResponse)) {	//Two factor is required  
				return false;	// ... and user needs it for SSO primary auth
			}
//			else if (CookieAuthenticator.isTwoFactorSetup(challengeResponse)){	//Two factor is set up, therefore we need to honour it.
//				return false;
//			}
		}
		
		return true;
	}
	
	

}
