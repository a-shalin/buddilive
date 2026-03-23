package ca.digitalcave.buddi.live.security;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.lang3.StringUtils;

import ca.digitalcave.moss.crypto.Crypto;
import ca.digitalcave.moss.auth.service.AuthenticationHelper;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class CookieUtil {

	public static final String FIELD_IDENTIFIER = "identifier";
	public static final String FIELD_AUTHENTICATOR = "authenticator";
	public static final String FIELD_IMPERSONATE = "impersonate";
	public static final String FIELD_PASSWORD = "password";
	public static final String FIELD_REMEMBER = "remember";
	public static final String FIELD_DISABLE_IP_LOCK = "disableIpLock";
	public static final String FIELD_EMAIL = "email";
	public static final String FIELD_ACTIVATION_KEY = "activationKey";
	public static final String FIELD_TIME_ISSUED = "timeIssued";
	public static final String FIELD_COOKIE_EXPIRY_MILLIS = "cookieExpires";
	public static final String FIELD_PASSWORD_EXPIRED = "passwordExpired";
	public static final String FIELD_CLIENT_ADDRESS = "clientAddress";
	public static final long COOKIE_REFRESH_WINDOW_MILLIS = 300000L;
	public static final long COOKIE_EXPIRY_DEFAULT_MILLIS = 60L * 60 * 1000;
	public static final long COOKIE_EXPIRY_REMEMBER_MILLIS = 30L * 24 * 60 * 60 * 1000;
	public static final long COOKIE_EXPIRY_NO_IP_LOCK_MILLIS = 30L * 1000;

	public static final String FIELD_TOTP_TOKEN = "totpToken";
	public static final String FIELD_TOTP_SHARED_SECRET = "totpSharedSecret";
	public static final String FIELD_TOTP_SHARED_SECRET_QR = "totpSharedSecretQr";

	public static final String FIELD_TWO_FACTOR_VALIDATED_IDENTIFIER = "twoFactorIdentifier";
	public static final String FIELD_TWO_FACTOR_VALIDATED = "twoFactorValidated";
	public static final String FIELD_TWO_FACTOR_REQUIRED = "twoFactorRequired";
	public static final String FIELD_TWO_FACTOR_SETUP = "twoFactorSetup";

	public static Map<String, String> decryptCookie(AuthenticationHelper helper, String encrypted) {
		try {
			final String decrypted = Crypto.decrypt(helper.getKey(), encrypted);
			return parseQueryString(decrypted);
		}
		catch (Exception e) {
			Logger.getLogger(CookieUtil.class.getName()).log(Level.INFO, "Unable to decrypt cookie credentials", e);
			return null;
		}
	}

	public static String encryptCookie(AuthenticationHelper helper, Map<String, String> params) {
		try {
			return helper.getCrypto().encrypt(helper.getKey(), toQueryString(params));
		}
		catch (Exception e) {
			Logger.getLogger(CookieUtil.class.getName()).log(Level.WARNING, e.getMessage(), e);
			return null;
		}
	}

	public static void setCookie(AuthenticationHelper helper, HttpServletResponse response, String value, int maxAge) {
		final Cookie cookie = new Cookie(helper.getCookieName(), value);
		cookie.setHttpOnly(true);
		cookie.setSecure(helper.isUseSecureCookies());
		cookie.setMaxAge(maxAge);
		cookie.setPath(helper.getCookiePath() != null ? helper.getCookiePath() : "/");
		response.addCookie(cookie);
	}

	public static void deleteCookie(AuthenticationHelper helper, HttpServletResponse response) {
		setCookie(helper, response, "", 0);
	}

	public static String findCookieValue(HttpServletRequest request, String cookieName) {
		if (request.getCookies() == null) return null;
		for (Cookie cookie : request.getCookies()) {
			if (cookieName.equals(cookie.getName())) {
				return cookie.getValue();
			}
		}
		return null;
	}

	public static String getClientAddress(HttpServletRequest request) {
		final String forwarded = request.getHeader("x-forwarded-for");
		if (forwarded != null) {
			return forwarded;
		}
		return request.getRemoteAddr();
	}

	public static long getCookieTimeoutMillis(boolean remember) {
		return remember ? COOKIE_EXPIRY_REMEMBER_MILLIS : COOKIE_EXPIRY_DEFAULT_MILLIS;
	}

	public static boolean isPasswordExpired(Map<String, String> params) {
		return params != null && Boolean.parseBoolean(params.getOrDefault(FIELD_PASSWORD_EXPIRED, "false"));
	}

	public static boolean isTwoFactorRequired(Map<String, String> params) {
		return params != null && Boolean.parseBoolean(params.getOrDefault(FIELD_TWO_FACTOR_REQUIRED, "false"));
	}

	public static boolean isTwoFactorSetup(Map<String, String> params) {
		return params != null && Boolean.parseBoolean(params.getOrDefault(FIELD_TWO_FACTOR_SETUP, "false"));
	}

	public static boolean isTwoFactorValidated(Map<String, String> params) {
		if (params == null) return false;
		final boolean validated = Boolean.parseBoolean(params.getOrDefault(FIELD_TWO_FACTOR_VALIDATED, "false"));
		final String authenticator = getAuthenticator(params);
		final String validatedIdentifier = params.get(FIELD_TWO_FACTOR_VALIDATED_IDENTIFIER);
		return validated && authenticator != null && authenticator.equals(validatedIdentifier);
	}

	public static void setTwoFactorInvalid(HttpServletRequest request, HttpServletResponse response,
										   AuthenticationHelper helper) {
		final String cookieValue = findCookieValue(request, helper.getCookieName());
		if (cookieValue == null || cookieValue.isEmpty()) return;
		final Map<String, String> params = decryptCookie(helper, cookieValue);
		if (params == null) return;
		params.remove(FIELD_TWO_FACTOR_VALIDATED);
		params.remove(FIELD_TWO_FACTOR_VALIDATED_IDENTIFIER);
		setEncryptedCookie(request, response, helper, params, true);
	}

	public static boolean isImpersonating(Map<String, String> params) {
		if (params == null) return false;
		final String authenticator = params.get(FIELD_AUTHENTICATOR);
		return authenticator != null && !authenticator.trim().isEmpty();
	}

	public static String getAuthenticator(Map<String, String> params) {
		if (params == null) return null;
		final String authenticator = params.get(FIELD_AUTHENTICATOR);
		if (authenticator != null) return authenticator;
		return params.get(FIELD_IDENTIFIER);
	}

	public static boolean isPrimaryAuthenticationValid(Map<String, String> params) {
		if (params == null) return false;
		final String identifier = params.get(FIELD_IDENTIFIER);
		if (StringUtils.isBlank(identifier)) return false;
		return !Boolean.parseBoolean(params.getOrDefault(FIELD_PASSWORD_EXPIRED, "false"));
	}

	public static boolean isSecondaryAuthenticationValid(Map<String, String> params) {
		if (params == null) return false;
		if (isTwoFactorValidated(params)) return true;
		if (isTwoFactorRequired(params)) return false;
		return true;
	}

	public static boolean isAuthenticationValid(Map<String, String> params) {
		return isPrimaryAuthenticationValid(params) && isSecondaryAuthenticationValid(params);
	}

	public static void setEncryptedCookie(HttpServletRequest request, HttpServletResponse response,
										  AuthenticationHelper helper, Map<String, String> params, boolean ipLock) {
		long expiryTimeMillis;
		final boolean remember = Boolean.parseBoolean(params.getOrDefault(FIELD_REMEMBER, "false"));
		final boolean disableIpLock = Boolean.parseBoolean(params.getOrDefault(FIELD_DISABLE_IP_LOCK, "false"));

		final Map<String, String> cookieParams = new LinkedHashMap<>();
		cookieParams.put(FIELD_REMEMBER, Boolean.toString(remember));
		cookieParams.put(FIELD_DISABLE_IP_LOCK, Boolean.toString(disableIpLock));

		if (ipLock) {
			if (!disableIpLock) {
				cookieParams.put(FIELD_CLIENT_ADDRESS, getClientAddress(request));
			}
			expiryTimeMillis = getCookieTimeoutMillis(remember);
		}
		else {
			expiryTimeMillis = COOKIE_EXPIRY_NO_IP_LOCK_MILLIS;
		}

		final long issued = System.currentTimeMillis();
		final long expires = issued + expiryTimeMillis;
		int maxAge = (int) (expiryTimeMillis / 1000);

		cookieParams.put(FIELD_TIME_ISSUED, Long.toString(issued));
		cookieParams.put(FIELD_COOKIE_EXPIRY_MILLIS, Long.toString(expires));
		cookieParams.put(FIELD_IDENTIFIER, params.get(FIELD_IDENTIFIER));

		final String password = params.get(FIELD_PASSWORD);
		if (password != null) {
			cookieParams.put(FIELD_PASSWORD, password);
		}

		final String authenticator = params.get(FIELD_AUTHENTICATOR);
		if (authenticator != null) {
			cookieParams.put(FIELD_AUTHENTICATOR, authenticator);
		}

		cookieParams.put(FIELD_TWO_FACTOR_VALIDATED_IDENTIFIER, params.getOrDefault(FIELD_TWO_FACTOR_VALIDATED_IDENTIFIER, ""));
		cookieParams.put(FIELD_TWO_FACTOR_VALIDATED, Boolean.toString(Boolean.parseBoolean(params.getOrDefault(FIELD_TWO_FACTOR_VALIDATED, "false"))));
		final String totpSecret = params.get(FIELD_TOTP_SHARED_SECRET);
		if (totpSecret != null) {
			cookieParams.put(FIELD_TOTP_SHARED_SECRET, totpSecret);
		}

		final String encrypted = encryptCookie(helper, cookieParams);
		if (encrypted != null) {
			setCookie(helper, response, encrypted, maxAge);
		}
	}

	private static Map<String, String> parseQueryString(String queryString) {
		final Map<String, String> result = new LinkedHashMap<>();
		if (queryString == null || queryString.isEmpty()) return result;
		for (String pair : queryString.split("&")) {
			final int eq = pair.indexOf('=');
			if (eq >= 0) {
				final String key = URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8);
				final String value = URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
				result.put(key, value);
			}
			else {
				result.put(URLDecoder.decode(pair, StandardCharsets.UTF_8), "");
			}
		}
		return result;
	}

	private static String toQueryString(Map<String, String> params) {
		final StringBuilder sb = new StringBuilder();
		for (Map.Entry<String, String> entry : params.entrySet()) {
			if (sb.length() > 0) sb.append('&');
			sb.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8));
			sb.append('=');
			if (entry.getValue() != null) {
				sb.append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
			}
		}
		return sb.toString();
	}
}
