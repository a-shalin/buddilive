package ca.digitalcave.moss.restlet.resource;

import java.io.IOException;
import java.util.UUID;

import org.restlet.data.Form;
import org.restlet.data.MediaType;
import org.restlet.data.Status;
import org.restlet.representation.EmptyRepresentation;
import org.restlet.representation.Representation;
import org.restlet.representation.Variant;
import org.restlet.resource.ResourceException;
import org.restlet.resource.ServerResource;

import ca.digitalcave.moss.restlet.CookieAuthenticator;
import ca.digitalcave.moss.restlet.plugin.AuthenticationHelper;

/**
 * This resource accepts an unauthenticated POST from the login window with an identifier, and sends an activation key to the email associated with that account.
 */
public class ForgotPasswordResource extends ServerResource {

	@Override
	protected void doInit() throws ResourceException {
		getVariants().add(new Variant(MediaType.APPLICATION_JSON));
	}


	@Override
	protected Representation post(Representation entity, Variant variant) throws ResourceException {
		try {
			final AuthenticationHelper helper = CookieAuthenticator.getAuthenticationHelper(getRequest());
			final Form form = new Form(entity.getText());

			final String identifier = form.getFirstValue(CookieAuthenticator.FIELD_IDENTIFIER);
			
			if (identifier == null || identifier.trim().equals("")) {
				throw new ResourceException(Status.CLIENT_ERROR_BAD_REQUEST, "Value for '" + CookieAuthenticator.FIELD_IDENTIFIER +"' is required.");
			}
 
			final String activationKey = UUID.randomUUID().toString();
			
			// Delay a random amount of time, between 0 and 1000 millis, so that user enumeration attacks relying on response time are more difficult
			try { Thread.sleep((long) (Math.random() * 1000));} catch (Throwable e){}

			final String userEmailAddress = helper.updateActivationKey(identifier, activationKey);
			if (userEmailAddress != null) {
				helper.sendEmail(userEmailAddress, "Account Activation", "Here is the activation key you requested: " + activationKey + "\nIf you did not request this activation key please ignore this email.");
			}
		}
		catch (IOException e){
			throw new ResourceException(Status.SERVER_ERROR_INTERNAL, e);
		}
		catch (Exception e){
			throw new ResourceException(Status.CLIENT_ERROR_BAD_REQUEST, e);
		}

		return new EmptyRepresentation();
	}
}
