package ca.digitalcave.buddi.live.security;

import ca.digitalcave.buddi.live.model.User;
import ca.digitalcave.moss.auth.model.AuthUser;
import ca.digitalcave.moss.auth.service.AuthenticationHelper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.Semaphore;

public class CookieAuthenticationFilter extends OncePerRequestFilter {

	private static final Map<String, Semaphore> loginAttempts = Collections.synchronizedMap(new WeakHashMap<>());
	private static final Map<String, Long> loggedInUsers = Collections.synchronizedMap(new HashMap<>());

	private final AuthenticationHelper authenticationHelper;
	private final int delay;

	public CookieAuthenticationFilter(final AuthenticationHelper authenticationHelper) {
		this.authenticationHelper = authenticationHelper;
		this.delay = 1500;
	}

	@Override
	protected void doFilterInternal(final HttpServletRequest request, final HttpServletResponse response, final FilterChain filterChain)
			throws ServletException, IOException {

		final String cookieValue = CookieUtil.findCookieValue(request, authenticationHelper.getCookieName());
		if (cookieValue == null || cookieValue.isEmpty()) {
			filterChain.doFilter(request, response);
			return;
		}

		final Map<String, String> params = CookieUtil.decryptCookie(authenticationHelper, cookieValue);
		if (params == null) {
			CookieUtil.deleteCookie(authenticationHelper, response);
			filterChain.doFilter(request, response);
			return;
		}

		// Check expiry
		final long expires = Long.parseLong(params.getOrDefault(CookieUtil.FIELD_COOKIE_EXPIRY_MILLIS, "0"));
		if (expires < System.currentTimeMillis()) {
			CookieUtil.deleteCookie(authenticationHelper, response);
			filterChain.doFilter(request, response);
			return;
		}

		// Validate IP lock
		final boolean disableIpLock = Boolean.parseBoolean(params.getOrDefault(CookieUtil.FIELD_DISABLE_IP_LOCK, "false"));
		final String clientAddress = params.getOrDefault(CookieUtil.FIELD_CLIENT_ADDRESS, "");
		if (!disableIpLock && StringUtils.isNotBlank(clientAddress) && !StringUtils.equals(clientAddress, CookieUtil.getClientAddress(request))) {
			CookieUtil.deleteCookie(authenticationHelper, response);
			filterChain.doFilter(request, response);
			return;
		}

		// Authenticate against DB
		final String identifier = params.get(CookieUtil.FIELD_IDENTIFIER);
		final String password = params.get(CookieUtil.FIELD_PASSWORD);
		final String authenticator = CookieUtil.getAuthenticator(params);

		final AuthUser authUser = authenticationHelper.authenticate("", authenticator, password);
		if (authUser == null) {
			if (!CookieUtil.isPasswordExpired(params)) {
				CookieUtil.deleteCookie(authenticationHelper, response);
			}

			// Brute force delay
			if (delay > 0 && identifier != null) {
				try {
					if (loginAttempts.get(identifier) == null) {
						loginAttempts.put(identifier, new Semaphore(1));
					}
					final Semaphore semaphore = loginAttempts.get(identifier);
					if (semaphore != null) {
						semaphore.acquire();
						Thread.sleep(delay);
						semaphore.release();
					}
				}
				catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
			}

			filterChain.doFilter(request, response);
			return;
		}

		// Set 2FA flags from user state
		if (authUser.isTwoFactorSetup()) {
			params.put(CookieUtil.FIELD_TWO_FACTOR_SETUP, "true");
		}
		if (authUser.isTwoFactorRequired()) {
			params.put(CookieUtil.FIELD_TWO_FACTOR_REQUIRED, "true");
		}

		if (CookieUtil.isAuthenticationValid(params)) {
			// Fully authenticated
			final User user = (User) authUser;
			if (CookieUtil.isImpersonating(params)) {
				user.setImpersonatedIdentifier(identifier);
			}

			final CookieAuthenticationToken token = new CookieAuthenticationToken(user, params);
			SecurityContextHolder.getContext().setAuthentication(token);

			loggedInUsers.put(identifier, System.currentTimeMillis());

			// Cookie refresh: if cookie is old or near expiry
			final long timeIssued = Long.parseLong(params.getOrDefault(CookieUtil.FIELD_TIME_ISSUED, "0"));
			if (StringUtils.isBlank(clientAddress)
					|| (timeIssued < System.currentTimeMillis() - CookieUtil.COOKIE_REFRESH_WINDOW_MILLIS)
					|| (expires - CookieUtil.COOKIE_REFRESH_WINDOW_MILLIS < System.currentTimeMillis())) {
				CookieUtil.setEncryptedCookie(request, response, authenticationHelper, params, true);
			}
		}
		else if (CookieUtil.isPrimaryAuthenticationValid(params)) {
			// Primary auth valid but 2FA pending — set a limited authentication so auth endpoints work
			final User user = (User) authUser;
			final CookieAuthenticationToken token = new CookieAuthenticationToken(user, params);
			SecurityContextHolder.getContext().setAuthentication(token);
		}

		filterChain.doFilter(request, response);
	}
}
