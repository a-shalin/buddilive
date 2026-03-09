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
 * This resource accepts an activation key and new password, and is used for resetting the user's password as well as finishing the registration step.
 */
public class ResetPasswordResource extends ServerResource {

	@Override
	protected void doInit() throws ResourceException {
		getVariants().add(new Variant(MediaType.APPLICATION_JSON));
	}


	@Override
	protected Representation post(Representation entity, Variant variant) throws ResourceException {
		try {
			final AuthenticationHelper helper = CookieAuthenticator.getAuthenticationHelper(getRequest());
			final Form form = new Form(entity.getText());

			final String activationKey = form.getFirstValue(CookieAuthenticator.FIELD_ACTIVATION_KEY);
			final String password = form.getFirstValue(CookieAuthenticator.FIELD_PASSWORD);
			
			if (activationKey == null || activationKey.trim().equals("")
					|| password == null || password.trim().equals("")) {
				throw new ResourceException(Status.CLIENT_ERROR_BAD_REQUEST, "Values for '" + CookieAuthenticator.FIELD_ACTIVATION_KEY +"' and '" + CookieAuthenticator.FIELD_PASSWORD +"' is required.");
			}
			else if (activationKey.equals(password)){
				throw new ResourceException(Status.CLIENT_ERROR_BAD_REQUEST, "Password cannot be set to activation key value");
			}
 
			if (helper.updatePasswordByActivationKey(activationKey, helper.getHash().generate(password))){
				return new StringRepresentation("{\"success\": true}", MediaType.APPLICATION_JSON);
			}
		}
		catch (IOException e){
			// Delay a random amount of time, between 0 and 1000 millis, so that user enumeration attacks relying on response time are more difficult
			try { Thread.sleep((long) (Math.random() * 1000));} catch (Throwable e2){}

			throw new ResourceException(Status.SERVER_ERROR_INTERNAL, e);
		}

		// Delay a random amount of time, between 0 and 1000 millis, so that user enumeration attacks relying on response time are more difficult
		try { Thread.sleep((long) (Math.random() * 1000));} catch (Throwable e){}

		getResponse().setStatus(Status.CLIENT_ERROR_BAD_REQUEST);
		return new StringRepresentation("{\"success\": false}", MediaType.APPLICATION_JSON);
	}
}
