package ca.digitalcave.moss.restlet.resource;

import java.io.IOException;

import org.restlet.data.Form;
import org.restlet.data.MediaType;
import org.restlet.data.Status;
import org.restlet.representation.Representation;
import org.restlet.representation.StringRepresentation;
import org.restlet.representation.Variant;
import org.restlet.resource.ResourceException;
import org.restlet.resource.ServerResource;

import ca.digitalcave.moss.restlet.CookieAuthenticator;
import ca.digitalcave.moss.restlet.plugin.AuthenticationHelper;

/**
 * This resource is used during the login process, if the user's password has expired.  It requires the ChallengeResponse to be set with the identifier (done via login).
 */
public class PasswordExpiredResource extends ServerResource {

	@Override
	protected void doInit() throws ResourceException {
		getVariants().add(new Variant(MediaType.APPLICATION_JSON));
	}


	@Override
	protected Representation post(Representation entity, Variant variant) throws ResourceException {
		if (getChallengeResponse() == null || getChallengeResponse().getIdentifier() == null){
			throw new ResourceException(Status.CLIENT_ERROR_UNAUTHORIZED);
		}
		
		try {
			final AuthenticationHelper helper = CookieAuthenticator.getAuthenticationHelper(getRequest());
			final Form form = new Form(entity.getText());

			final String username = getChallengeResponse().getIdentifier();
			final String password = form.getFirstValue(CookieAuthenticator.FIELD_PASSWORD);
			
			if (password == null || password.trim().equals("")) {
				throw new ResourceException(Status.CLIENT_ERROR_BAD_REQUEST, "Value for '" + CookieAuthenticator.FIELD_PASSWORD + "' is required.");
			}
 
			if (helper.updatePassword(username, helper.getHash().generate(password))){
				return new StringRepresentation("{\"success\": true}", MediaType.APPLICATION_JSON);
			}
		}
		catch (IOException e){
			// Delay a random amount of time, between 0 and 1000 millis, so that user enumeration attacks relying on response time are more difficult
			try { Thread.sleep((long) (Math.random() * 1000));} catch (Throwable e2){}

			throw new ResourceException(Status.SERVER_ERROR_INTERNAL, e);
		}

		getResponse().setStatus(Status.CLIENT_ERROR_BAD_REQUEST);
		return new StringRepresentation("{\"success\": false}", MediaType.APPLICATION_JSON);
	}
}
