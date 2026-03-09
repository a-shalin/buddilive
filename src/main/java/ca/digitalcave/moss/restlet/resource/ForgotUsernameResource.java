package ca.digitalcave.moss.restlet.resource;

import java.io.IOException;
import java.util.List;

import org.restlet.data.Form;
import org.restlet.data.MediaType;
import org.restlet.data.Status;
import org.restlet.representation.EmptyRepresentation;
import org.restlet.representation.Representation;
import org.restlet.representation.Variant;
import org.restlet.resource.ResourceException;
import org.restlet.resource.ServerResource;

import ca.digitalcave.moss.restlet.CookieAuthenticator;
import ca.digitalcave.moss.restlet.model.AuthUser;
import ca.digitalcave.moss.restlet.plugin.AuthenticationHelper;

/**
 * This resource accepts an unauthenticated POST with an email address, and sends an email to that address listing all usernames associated with that email.
 */
public class ForgotUsernameResource extends ServerResource {

	@Override
	protected void doInit() throws ResourceException {
		getVariants().add(new Variant(MediaType.APPLICATION_JSON));
	}


	@Override
	protected Representation post(Representation entity, Variant variant) throws ResourceException {
		try {
			final AuthenticationHelper helper = CookieAuthenticator.getAuthenticationHelper(getRequest());
			final Form form = new Form(entity.getText());

			final String email = form.getFirstValue(CookieAuthenticator.FIELD_EMAIL);
			
			if (email == null || email.trim().equals("")) {
				throw new ResourceException(Status.CLIENT_ERROR_BAD_REQUEST, "Value for '" + CookieAuthenticator.FIELD_EMAIL +"' is required.");
			}
 
			final List<AuthUser> users = helper.selectUsers(email);
			if (users != null && users.size() > 0){
				final StringBuilder sb = new StringBuilder();
				for (AuthUser user : users){
					if (sb.length() > 0){
						sb.append(", ");
					}
					sb.append(user.getIdentifier());
				}
				helper.sendEmail(email, "Forgot Username", "The following usernames are associated with this email: " + sb.toString() + "\nIf you did not request this information, please ignore this email.");
			}
		}
		catch (IOException e){
			throw new ResourceException(Status.SERVER_ERROR_INTERNAL, e);
		}

		return new EmptyRepresentation();
	}
}
