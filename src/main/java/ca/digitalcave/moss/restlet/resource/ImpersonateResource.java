package ca.digitalcave.moss.restlet.resource;

import java.io.IOException;

import org.restlet.data.ChallengeResponse;
import org.restlet.data.Form;
import org.restlet.data.MediaType;
import org.restlet.data.Status;
import org.restlet.representation.Representation;
import org.restlet.representation.StringRepresentation;
import org.restlet.representation.Variant;
import org.restlet.resource.ResourceException;
import org.restlet.resource.ServerResource;

import ca.digitalcave.moss.restlet.CookieAuthenticator;
import ca.digitalcave.moss.restlet.model.AuthUser;
import ca.digitalcave.moss.restlet.plugin.AuthenticationHelper;

/**
 * This resource accepts a POSTed form with an impersonate UID and begins impersonation, if the logged in user is allowed to impersonate others.
 * Call DELETE to stop impersonating.
 */
public class ImpersonateResource extends ServerResource {

	@Override
	protected void doInit() throws ResourceException {
		getVariants().add(new Variant(MediaType.APPLICATION_JSON));
	}


	@Override
	protected Representation post(Representation entity, Variant variant) throws ResourceException {
		try {
			final AuthenticationHelper helper = CookieAuthenticator.getAuthenticationHelper(getRequest());
			final Form form = new Form(entity.getText());

			final String impersonate = form.getFirstValue(CookieAuthenticator.FIELD_IMPERSONATE);
			if (impersonate == null 
					|| impersonate.trim().equals("")) {
				throw new ResourceException(Status.CLIENT_ERROR_BAD_REQUEST, "Value for '" + CookieAuthenticator.FIELD_IMPERSONATE +"' is required.");
			}
 
			final ChallengeResponse cr = getChallengeResponse();
			if (cr != null){
				final AuthUser user = (AuthUser) getRequest().getClientInfo().getUser();
				if (user.isImpersonateAllowed(impersonate)){
					cr.getParameters().set(CookieAuthenticator.FIELD_AUTHENTICATOR, cr.getIdentifier());
					cr.getParameters().set(CookieAuthenticator.FIELD_IMPERSONATE, impersonate);
					cr.setIdentifier(impersonate);
					CookieAuthenticator.setEncryptedCookieFromChallengeResponse(getRequest(), getResponse(), helper);
					return new StringRepresentation("{\"success\": true}", MediaType.APPLICATION_JSON);
				}
			}
		}
		catch (IOException e){
			throw new ResourceException(Status.SERVER_ERROR_INTERNAL, e);
		}
		
		getResponse().setStatus(Status.CLIENT_ERROR_BAD_REQUEST);
		return new StringRepresentation("{\"success\": false}", MediaType.APPLICATION_JSON);
	}

	@Override
	protected Representation delete(Variant variant) throws ResourceException {
		final AuthenticationHelper helper = CookieAuthenticator.getAuthenticationHelper(getRequest());
		final ChallengeResponse cr = getChallengeResponse();

		final String authenticator = cr.getParameters().getFirstValue("authenticator");
		if (authenticator == null) {
			//We are not impersonating.
			throw new ResourceException(Status.CLIENT_ERROR_BAD_REQUEST);
		}
		else {
			// user is currently impersonating another user so restore them to their original credentials
			cr.setIdentifier(authenticator);
			cr.getParameters().removeAll(CookieAuthenticator.FIELD_AUTHENTICATOR);
			cr.getParameters().removeAll(CookieAuthenticator.FIELD_IMPERSONATE);
			CookieAuthenticator.setEncryptedCookieFromChallengeResponse(getRequest(), getResponse(), helper);
			return new StringRepresentation("{\"success\": true}", MediaType.APPLICATION_JSON);
		}
	}
}
