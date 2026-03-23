package ca.digitalcave.buddi.live.security;

import java.util.Collections;
import java.util.Map;

import org.springframework.security.authentication.AbstractAuthenticationToken;

import ca.digitalcave.buddi.live.model.User;

public class CookieAuthenticationToken extends AbstractAuthenticationToken {

	private final User user;
	private final Map<String, String> cookieParams;

	public CookieAuthenticationToken(User user, Map<String, String> cookieParams) {
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
