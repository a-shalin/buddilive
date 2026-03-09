package ca.digitalcave.moss.restlet.security;

import org.restlet.Request;
import org.restlet.Response;
import org.restlet.data.ChallengeResponse;
import org.restlet.data.Protocol;
import org.restlet.security.Verifier;

import ca.digitalcave.moss.restlet.CookieAuthenticator;
import ca.digitalcave.moss.restlet.model.AuthUser;
import ca.digitalcave.moss.restlet.plugin.AuthenticationHelper;

public abstract class CookieVerifier implements Verifier {

	public final static String COOKIE_NAME = "ebie";

	private AuthenticationHelper helper;

	public CookieVerifier(AuthenticationHelper helper) {
		this.helper = helper;
	}

	public int verify(Request request, Response response) {
		if (Protocol.RIAP.equals(request.getProtocol())) {
			return verifyRiap(request, response);
		}
		else {
			final ChallengeResponse cr = request.getChallengeResponse();
			if (cr == null){
				return RESULT_INVALID;
			}
			else {
				final String applicationName = request.getRootRef().getPath().replaceFirst("^/", "");
				final AuthUser user = helper.authenticate(applicationName, cr, null);
				
				if (user != null){
					//We need to set some fields on the CR, to cover the cases where the user is not fully authenticated but we still want to show the right next step
					if (user.isTwoFactorSetup()){
						CookieAuthenticator.setTwoFactorSetup(cr);
					}
					if (user.isTwoFactorRequired()){
						CookieAuthenticator.setTwoFactorRequired(cr);
					}
				}
				
				if (user != null && CookieAuthenticator.isAuthenticationValid(cr)) {
					//User is completely logged in (including second factor, if needed).  This is the normal 'logged in' operation.
					if (CookieAuthenticator.isImpersonating(cr)){
						user.setImpersonatedIdentifier(cr.getIdentifier());
					}
					request.getClientInfo().setUser(user);
					addPrincipals(request, user);
					return RESULT_VALID;
				}
				else if (CookieAuthenticator.isPrimaryAuthenticationValid(cr)){
					//User has completed primary authentication, but has not yet completed the (required) second factor auth.  We return valid, but don't set the user in the client info.
					return RESULT_VALID;
				}
				else {
					return RESULT_INVALID;
				}
			}
		}
	}
	
	/**
	 * Add principals as needed.  By default does nothing.
	 */
	protected void addPrincipals(Request request, AuthUser user){
		//request.getClientInfo().getPrincipals().add(new UserIdPrincipal(user.getIdentifier()));
	}
	
	/**
	 * Handle the RIAP protocol.  By default we block all RIAP access; override to allow.
	 */
	protected int verifyRiap(Request request, Response response){
		return RESULT_INVALID;
	}
	
	public AuthenticationHelper getHelper() {
		return helper;
	}
}
