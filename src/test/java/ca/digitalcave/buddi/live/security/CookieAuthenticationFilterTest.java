package ca.digitalcave.buddi.live.security;

import ca.digitalcave.buddi.live.model.User;
import ca.digitalcave.moss.auth.service.AuthenticationHelper;
import ca.digitalcave.moss.crypto.Crypto;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.security.Key;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

public class CookieAuthenticationFilterTest {

	private static final String COOKIE_NAME = "enrich_auth";
	private static final String IDENTIFIER = "user@example.com";
	private static final String PASSWORD = "secret";
	private static final String ORIGINAL_CLIENT_ADDRESS = "203.0.113.1";
	private static final String NEW_CLIENT_ADDRESS = "198.51.100.10";

	private AuthenticationHelper authenticationHelper;

	@BeforeEach
	public void setUp() throws Exception {
		final Key key = new Crypto().generateSecretKey();
		final User user = new User();
		user.setIdentifier(IDENTIFIER);

		authenticationHelper = mock(AuthenticationHelper.class);
		when(authenticationHelper.getCookieName()).thenReturn(COOKIE_NAME);
		when(authenticationHelper.getKey()).thenReturn(key);
		when(authenticationHelper.getCrypto()).thenReturn(new Crypto());
		when(authenticationHelper.getCookiePath()).thenReturn("/");
		when(authenticationHelper.isUseSecureCookies()).thenReturn(false);
		when(authenticationHelper.authenticate("", IDENTIFIER, PASSWORD)).thenReturn(user);

		SecurityContextHolder.clearContext();
	}

	@AfterEach
	public void tearDown() {
		SecurityContextHolder.clearContext();
	}

	@Test
	public void ipLockMismatchRejectsCookieWhenGlobalDisableOff() throws Exception {
		final CookieAuthenticationFilter filter = new CookieAuthenticationFilter(authenticationHelper, false);
		final MockHttpServletRequest request = new MockHttpServletRequest();
		final MockHttpServletResponse response = new MockHttpServletResponse();
		final MockFilterChain chain = new MockFilterChain();

		request.setRemoteAddr(NEW_CLIENT_ADDRESS);
		request.setCookies(new Cookie(COOKIE_NAME, buildCookieValue(ORIGINAL_CLIENT_ADDRESS)));

		filter.doFilter(request, response, chain);

		assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
		assertThat(response.getCookies())
			.anySatisfy(cookie -> {
				assertThat(cookie.getName()).isEqualTo(COOKIE_NAME);
				assertThat(cookie.getMaxAge()).isZero();
			});
		verify(authenticationHelper, never()).authenticate(anyString(), anyString(), anyString());
	}

	@Test
	public void ipLockMismatchAllowedWhenGlobalDisableOn() throws Exception {
		final CookieAuthenticationFilter filter = new CookieAuthenticationFilter(authenticationHelper, true);
		final MockHttpServletRequest request = new MockHttpServletRequest();
		final MockHttpServletResponse response = new MockHttpServletResponse();
		final MockFilterChain chain = new MockFilterChain();

		request.setRemoteAddr(NEW_CLIENT_ADDRESS);
		request.setCookies(new Cookie(COOKIE_NAME, buildCookieValue(ORIGINAL_CLIENT_ADDRESS)));

		filter.doFilter(request, response, chain);

		assertThat(SecurityContextHolder.getContext().getAuthentication()).isInstanceOf(CookieAuthenticationToken.class);
		verify(authenticationHelper, times(1)).authenticate("", IDENTIFIER, PASSWORD);
	}

	private String buildCookieValue(final String clientAddress) {
		final long issued = System.currentTimeMillis();
		final long expires = issued + CookieUtil.COOKIE_EXPIRY_DEFAULT_MILLIS;
		final Map<String, String> params = new LinkedHashMap<>();
		params.put(CookieUtil.FIELD_REMEMBER, "false");
		params.put(CookieUtil.FIELD_DISABLE_IP_LOCK, "false");
		params.put(CookieUtil.FIELD_TIME_ISSUED, Long.toString(issued));
		params.put(CookieUtil.FIELD_COOKIE_EXPIRY_MILLIS, Long.toString(expires));
		params.put(CookieUtil.FIELD_IDENTIFIER, IDENTIFIER);
		params.put(CookieUtil.FIELD_PASSWORD, PASSWORD);
		params.put(CookieUtil.FIELD_CLIENT_ADDRESS, clientAddress);
		return CookieUtil.encryptCookie(authenticationHelper, params);
	}
}
