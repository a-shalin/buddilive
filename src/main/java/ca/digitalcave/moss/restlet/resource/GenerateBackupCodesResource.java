package ca.digitalcave.moss.restlet.resource;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import org.restlet.data.ChallengeResponse;
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
 * This resource accepts a POST to re-generate and store backup codes for a user.  The user must be fully authenticated (including 2FA) before this can be called.
 */
public class GenerateBackupCodesResource extends ServerResource {

	@Override
	protected void doInit() throws ResourceException {
		getVariants().add(new Variant(MediaType.TEXT_PLAIN));
	}

	@Override
	protected Representation post(Representation entity, Variant variant) throws ResourceException {
		final AuthenticationHelper helper = CookieAuthenticator.getAuthenticationHelper(getRequest());
		
		
		final ChallengeResponse cr = getChallengeResponse();
		if (CookieAuthenticator.isAuthenticationValid(cr)){
			final String username = CookieAuthenticator.getAuthenticator(cr);
			helper.insertTotpBackupCodes(username, cr);
			final AuthUser updatedUser = helper.selectUser(username);
			if (updatedUser != null){
				final List<String> backupCodes = updatedUser.getTwoFactorBackupCodes();
				
				final StringBuilder sb = new StringBuilder();
				sb.append("Backup codes generated for user '").append(username).append("' on ").append(new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date())).append(":\n");
				for (String backupCode : backupCodes){
					sb.append(backupCode).append("\n");
				}
				
				return new StringRepresentation(sb.toString());
			}
		}
		
		throw new ResourceException(Status.CLIENT_ERROR_FORBIDDEN);
	}
}
