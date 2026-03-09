package ca.digitalcave.moss.restlet.plugin;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

/**
 * The configuration class is used for static data, such as i18n, hardcoded application properties, etc.  For things that can be changed
 * at runtime, AuthenticationHelper should be used instead.
 */
public class AuthenticationConfiguration implements Cloneable {

	/**
	 * Show the 'forgot password' panel on the login screen.
	 */
	public boolean showForgotPassword = true;
	
	/**
	 * Show the 'forgot username' panel on the login screen.
	 */
	public boolean showForgotUsername = false;
	/**
	 * Show the 'login' panel on the login screen.
	 */
	public boolean showLogin = true;
	/**
	 * Show the 'register' panel on the login screen.
	 */
	public boolean showRegister = false;
	
	/**
	 * Show the 'remember me' checkbox on login panel
	 */
	public boolean showRemember = true;
	
	/**
	 * Show the 'disable IP lock' on login panel
	 */
	public boolean showDisableIpLock = false;
	
	
	/**
	 * Show the 'Mobile version' link on the login page.
	 */
	public boolean showMobile = false;
	
	/**
	 * Allow impersonate functionality.  The users' authority is checked in the AuthenticationHelper.
	 */
	public boolean showImpersonate = false;
	

	/**
	 * Require that users accept cookies before continuing
	 */
	public boolean showCookieWarning = true;
	
	
	/**
	 * The organization who is issuing the 2FA secret.  Some authenticator apps (Authy) use this to google image search a company logo.
	 */
	public String totpIssuer = "TOTP Issuer";
	
	/**
	 * Allow SSO authentication.
	 */
	public boolean showSSO = true;


	/**
	 * A list of extra EXT JS controller class names, to be instantiated by Login's application.
	 */
	public String[] applicationControllers;
	/**
	 * A list of extra EXT JS model class names, to be instantiated by Login's application.
	 */
	public String[] applicationModels;
	/**
	 * A list of extra EXT JS view class names, to be instantiated by Login's application.
	 */
	public String[] applicationViews;
	/**
	 * A mapping between application names (e.g. MyApplication) and top level paths (e.g. app).
	 * This is only needed when you integrate components from a different application into the 
	 * login application.
	 */
	public Map<String, String> applicationLoaderPaths;
	
	
	public ExtraFieldsDirective extraRegisterStep1Fields;
	public ExtraFieldsDirective extraRegisterStep2Fields;
	public ExtraFieldsDirective extraforgotPasswordStep1PanelFields;
	public ExtraFieldsDirective extraforgotPasswordStep2PanelFields;
	public ExtraFieldsDirective extraForgotUsernameStep1PanelFields;

	/**
	 * Specify the custom resource bundle file for custom i18n.  This is 
	 * used when implementing the extraXStepYFields; the custom translator 
	 * is passed into the {@link ExtraFieldsDirective} abstract method.
	 */
	public String i18nBaseCustom;	
	
	public Map<String, Object> getMap(){
		final Map<String, Object> result = new HashMap<String, Object>();
		
		for (Field field : this.getClass().getFields()){
			try {
				result.put(field.getName(), field.get(this));
			}
			catch (IllegalAccessException e){
				e.printStackTrace();
			}
		}
		
		return result;
	}
}