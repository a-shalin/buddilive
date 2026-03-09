package ca.digitalcave.moss.restlet.resource;

import java.io.IOException;

import org.restlet.data.ChallengeResponse;
import org.restlet.data.Form;
import org.restlet.data.MediaType;
import org.restlet.data.Status;
import org.restlet.representation.EmptyRepresentation;
import org.restlet.representation.Representation;
import org.restlet.representation.StringRepresentation;
import org.restlet.representation.Variant;
import org.restlet.resource.ResourceException;
import org.restlet.resource.ServerResource;

import ca.digitalcave.moss.restlet.CookieAuthenticator;
import ca.digitalcave.moss.restlet.plugin.AuthenticationHelper;
import dev.samstevens.totp.code.CodeVerifier;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.code.DefaultCodeVerifier;
import dev.samstevens.totp.code.HashingAlgorithm;
import dev.samstevens.totp.exceptions.QrGenerationException;
import dev.samstevens.totp.qr.QrData;
import dev.samstevens.totp.qr.QrGenerator;
import dev.samstevens.totp.qr.ZxingPngQrGenerator;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.secret.SecretGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import dev.samstevens.totp.util.Utils;

/**
 * This resource generates a random secret key + QR code (GET), and confirms the token + saves the secret key (POST).  Success here will finish the TOTP setup for the user, and log them in.
 */
public class TotpSetupResource extends ServerResource {

	@Override
	protected void doInit() throws ResourceException {
		getVariants().add(new Variant(MediaType.APPLICATION_JSON));
	}

	@Override
	protected Representation get(Variant variant) throws ResourceException {
		final ChallengeResponse cr = getChallengeResponse();
		if (cr == null){
			throw new ResourceException(Status.CLIENT_ERROR_UNAUTHORIZED);
		}
		final AuthenticationHelper helper = CookieAuthenticator.getAuthenticationHelper(getRequest());
		
		// We create the TOTP secret here.
		final SecretGenerator secretGenerator = new DefaultSecretGenerator(32);
		final String sharedSecret = secretGenerator.generate();

		final QrData data = new QrData.Builder()
				.label(helper.getConfig().totpIssuer + ": " + cr.getIdentifier())
				.secret(sharedSecret)
				.issuer(helper.getConfig().totpIssuer)	//Authy does an image search to find the logo.
				.algorithm(HashingAlgorithm.SHA1)
				.digits(6)
				.period(30)
				.build();

		try {
			final QrGenerator generator = new ZxingPngQrGenerator();
			final byte[] smaredSecretQrImage = generator.generate(data);
			final String mimeType = generator.getImageMimeType();

			final String sharedSecretQrUri = Utils.getDataUriForImage(smaredSecretQrImage, mimeType);

			//Store the shared secret in the cookie, so that we can validate against it in the POST.  We DO NOT allow the user to POST us the shared secret, just the token for validation.
			cr.getParameters().set(CookieAuthenticator.FIELD_TOTP_SHARED_SECRET, sharedSecret);
			CookieAuthenticator.setEncryptedCookieFromChallengeResponse(getRequest(), getResponse(), helper);
			
			return new StringRepresentation("{\"success\": true, \"" + CookieAuthenticator.FIELD_TOTP_SHARED_SECRET + "\": \""+ sharedSecret + "\", \"" + CookieAuthenticator.FIELD_TOTP_SHARED_SECRET_QR + "\": \"" + sharedSecretQrUri + "\"}", MediaType.APPLICATION_JSON);
		}
		catch (QrGenerationException e){
			throw new ResourceException(Status.SERVER_ERROR_INTERNAL, e);
		}
	}
	
	@Override
	protected Representation post(Representation entity, Variant variant) throws ResourceException {
		try {
			final AuthenticationHelper helper = CookieAuthenticator.getAuthenticationHelper(getRequest());
			final Form form = new Form(entity.getText());

			final String totpToken = form.getFirstValue(CookieAuthenticator.FIELD_TOTP_TOKEN);
			
			if (totpToken == null){
				throw new ResourceException(Status.CLIENT_ERROR_BAD_REQUEST, "Value for '" + CookieAuthenticator.FIELD_TOTP_TOKEN +"' is required.");
			}

			final ChallengeResponse cr = getChallengeResponse();
			final String totpSecret = cr.getParameters().getFirstValue(CookieAuthenticator.FIELD_TOTP_SHARED_SECRET);

			if (totpToken != null){
				final CodeVerifier verifier = new DefaultCodeVerifier(new DefaultCodeGenerator(HashingAlgorithm.SHA1), new SystemTimeProvider());
				if (verifier.isValidCode(totpSecret, totpToken)){
					if (helper.insertTotpSecret(cr.getIdentifier(), totpSecret, cr)){
						CookieAuthenticator.setTwoFactorValidated(cr);
						CookieAuthenticator.setEncryptedCookieFromChallengeResponse(getRequest(), getResponse(), helper);
						return new StringRepresentation("{\"success\": true}", MediaType.APPLICATION_JSON);
					}
					else {
						//The update failed for some reason.
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
	
	@Override
	protected Representation delete(Variant variant) throws ResourceException {
		final ChallengeResponse cr = getChallengeResponse();
		final String identifier = cr.getIdentifier();
		if (identifier == null) {
			throw new ResourceException(Status.CLIENT_ERROR_NOT_FOUND);
		}
		
		final AuthenticationHelper helper = CookieAuthenticator.getAuthenticationHelper(getRequest());
		helper.disableTotp(identifier);
		CookieAuthenticator.setTwoFactorInvalid(getRequest(), getResponse());
		
		return new EmptyRepresentation();
	}
}
