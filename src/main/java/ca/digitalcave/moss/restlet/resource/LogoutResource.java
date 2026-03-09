package ca.digitalcave.moss.restlet.resource;

import org.restlet.data.MediaType;
import org.restlet.representation.EmptyRepresentation;
import org.restlet.representation.Representation;
import org.restlet.representation.Variant;
import org.restlet.resource.ResourceException;
import org.restlet.resource.ServerResource;

import ca.digitalcave.moss.restlet.CookieAuthenticator;
import ca.digitalcave.moss.restlet.plugin.AuthenticationHelper;
import ca.digitalcave.moss.restlet.util.RequestUtil;

/**
 * This resource accepts the GET to delete the auth cookie and log the user out.
 */
public class LogoutResource extends ServerResource {

	@Override
	protected void doInit() throws ResourceException {
		getVariants().add(new Variant(MediaType.APPLICATION_JSON));
	}

	@Override
	protected Representation get(Variant variant) throws ResourceException {
		final AuthenticationHelper helper = CookieAuthenticator.getAuthenticationHelper(getRequest());

		CookieAuthenticator.setCookie(helper, getRequest(), getResponse(), "", 0);
		getRequest().setChallengeResponse(null);

		//Redirect back to main page (no longer authenticated).
		getResponse().redirectSeeOther(getQuery().getFirstValue("relayState", RequestUtil.getRequestURL(getRequest()).replace("authentication/logout", "")));

		return new EmptyRepresentation();
	}
}
