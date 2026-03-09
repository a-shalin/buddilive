package ca.digitalcave.moss.restlet.router;

import org.restlet.Application;
import org.restlet.Request;
import org.restlet.Response;
import org.restlet.Restlet;
import org.restlet.routing.Router;
import org.restlet.security.Verifier;

import ca.digitalcave.moss.restlet.CookieAuthenticator;
import ca.digitalcave.moss.restlet.plugin.AuthenticationHelper;
import ca.digitalcave.moss.restlet.resource.ForgotPasswordResource;
import ca.digitalcave.moss.restlet.resource.ForgotUsernameResource;
import ca.digitalcave.moss.restlet.resource.GenerateBackupCodesResource;
import ca.digitalcave.moss.restlet.resource.ImpersonateResource;
import ca.digitalcave.moss.restlet.resource.LoginResource;
import ca.digitalcave.moss.restlet.resource.LogoutResource;
import ca.digitalcave.moss.restlet.resource.PasswordCheckResource;
import ca.digitalcave.moss.restlet.resource.PasswordExpiredResource;
import ca.digitalcave.moss.restlet.resource.RegisterResource;
import ca.digitalcave.moss.restlet.resource.ResetPasswordResource;
import ca.digitalcave.moss.restlet.resource.TotpSetupResource;
import ca.digitalcave.moss.restlet.resource.TotpTokenResource;
import ca.digitalcave.moss.restlet.resource.ui.UIFreemarkerResource;


public class AuthenticationRouter extends Router {
	private final AuthenticationHelper helper;
	
	public AuthenticationRouter(Application application, AuthenticationHelper helper, Verifier verifier) {
		this.helper = helper;
		
		this.attach("/login", LoginResource.class);						//POST credentials to create a cookie; there may be additional steps needed.
		this.attach("/logout", LogoutResource.class);					//POST to delete the cookie (and if logged in via SAML, redirect to SLO endpoint of IdP)
		this.attach("/passwordExpired", PasswordExpiredResource.class);	//POST to reset password after it has expired
		this.attach("/totpToken", TotpTokenResource.class);				//POST to validate TOTP token and finish login process
		this.attach("/totpSetup", TotpSetupResource.class);				//GET to generate random secret; POST to validate code and finish TOTP setup
		this.attach("/generateBackupCodes", GenerateBackupCodesResource.class);		//POST to generate and commit random backup codes for the user.
		
		this.attach("/impersonate", ImpersonateResource.class);			//POST to modify cookie and add impersonation info, DELETE to stop impersonating
		
		if (helper.getConfig().showRegister){
			this.attach("/register", RegisterResource.class);				//POST to register new user
			this.attach("/activate", ResetPasswordResource.class);			//POST to activate account
		}
		
		if (helper.getConfig().showForgotPassword){
			this.attach("/forgotPassword", ForgotPasswordResource.class);	//POST identifier to send an activation key to the email associated with the account.  This activation key can be used in 'resetPassword' to reset the password without needing to log in first.
			this.attach("/resetPassword", ResetPasswordResource.class);		//POST activation key and new password to reset the user's password
		}
		if (helper.getConfig().showForgotUsername){
			this.attach("/forgotUsername", ForgotUsernameResource.class);	//POST email address to send list of user name(s) associated with the email address to the email.
		}
		this.attach("/checkPassword", PasswordCheckResource.class);
		
		this.attachDefault(UIFreemarkerResource.class);
	}
	
	@Override
	protected void doHandle(Restlet next, Request request, Response response) {
		request.getAttributes().put(CookieAuthenticator.ATTRIBUTE_AUTHENTICATION_HELPER, helper);	//We need to have access to the helper in all resources under this router
		
		super.doHandle(next, request, response);
	}
	

}
