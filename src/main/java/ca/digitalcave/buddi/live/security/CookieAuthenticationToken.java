package ca.digitalcave.buddi.live.security;

import ca.digitalcave.buddi.live.model.User;
import org.springframework.security.authentication.AbstractAuthenticationToken;

import java.util.Collections;
import java.util.Map;

public class CookieAuthenticationToken extends AbstractAuthenticationToken {

	private final User user;
	private final Map<String, String> cookieParams;

	public CookieAuthenticationToken(final User user, final Map<String, String> cookieParams) {
		super(Collections.emptyList());
		this.user = user;
		this.cookieParams = cookieParams;
		setAuthenticated(true);
	}

	@Override
	public Object getCredentials() {
		return null;
	}

	@Override
	public Object getPrincipal() {
		return user;
	}

	public Map<String, String> getCookieParams() {
		return cookieParams;
	}
}
