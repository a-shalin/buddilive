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
import dev.samstevens.totp.code.CodeVerifier;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.code.DefaultCodeVerifier;
import dev.samstevens.totp.code.HashingAlgorithm;
import dev.samstevens.totp.time.SystemTimeProvider;

/**
 * This resource accepts the POST from the TOTP token validation window and validates the TOTP token.  Success completes login.
 */
public class TotpTokenResource extends ServerResource {

	@Override
	protected void doInit() throws ResourceException {
		getVariants().add(new Variant(MediaType.APPLICATION_JSON));
	}


	@Override
	protected Representation post(Representation entity, Variant variant) throws ResourceException {
		try {
			final AuthenticationHelper helper = CookieAuthenticator.getAuthenticationHelper(getRequest());
			final Form form = new Form(entity.getText());

			final String totpToken = form.getFirstValue(CookieAuthenticator.FIELD_TOTP_TOKEN);
			
			if (totpToken == null){
				throw new ResourceException(Status.CLIENT_ERROR_BAD_REQUEST, "Value for \"" + CookieAuthenticator.FIELD_TOTP_TOKEN +"\" is required.");
			}

			
			final ChallengeResponse cr = getChallengeResponse();
			final AuthUser user = helper.selectUser(cr.getIdentifier());

			if (totpToken != null && user.getTwoFactorSecret() != null){
				final CodeVerifier verifier = new DefaultCodeVerifier(new DefaultCodeGenerator(HashingAlgorithm.SHA1), new SystemTimeProvider());
				if (verifier.isValidCode(user.getTwoFactorSecret(), totpToken)){
					CookieAuthenticator.setTwoFactorValidated(cr);
					CookieAuthenticator.setEncryptedCookieFromChallengeResponse(getRequest(), getResponse(), helper);
					if (CookieAuthenticator.isPasswordExpired(cr)){
						return new StringRepresentation("{\"success\": false, \"next\": \"" + CookieAuthenticator.FIELD_PASSWORD_EXPIRED + "\"}", MediaType.APPLICATION_JSON);
					}
					else if (user.getTwoFactorBackupCodes().size() == 0){
						return new StringRepresentation("{\"success\": false, \"next\": \"totpBackupCodesNeeded\"}", MediaType.APPLICATION_JSON);
					}
					return new StringRepresentation("{\"success\": true}", MediaType.APPLICATION_JSON);
				}
				else {
					//If the token didn\"t validate as a proper TOTP value, check if it is in the valid backup codes.
					if (user.getTwoFactorBackupCodes().contains(totpToken)){
						//We disable the backup code that was used
						helper.updateTotpBackupCodeMarkUsed(cr.getIdentifier(), totpToken, cr);
						
						CookieAuthenticator.setTwoFactorValidated(cr);
						CookieAuthenticator.setEncryptedCookieFromChallengeResponse(getRequest(), getResponse(), helper);
						if (user.getTwoFactorBackupCodes().size() <= 1){	//Since we just used one of them to get here, we need to check if size is 1 (technically should never be less, but doesn\"t hurt).
							return new StringRepresentation("{\"success\": false, \"next\": \"totpBackupCodesNeeded\"}", MediaType.APPLICATION_JSON);
						}
						return new StringRepresentation("{\"success\": true}", MediaType.APPLICATION_JSON);
					}
				}
			}
		}
		catch (IOException e){
			throw new ResourceException(Status.SERVER_ERROR_INTERNAL, e);
		}
		
		getResponse().setStatus(Status.CLIENT_ERROR_BAD_REQUEST);
		return new StringRepresentation("{\"success\": false}", MediaType.APPLICATION_JSON);
	}
}
