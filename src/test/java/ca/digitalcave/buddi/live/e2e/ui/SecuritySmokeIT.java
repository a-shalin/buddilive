package ca.digitalcave.buddi.live.e2e.ui;

import org.junit.jupiter.api.Test;

import okhttp3.OkHttpClient;

import static org.assertj.core.api.Assertions.assertThat;

public class SecuritySmokeIT extends BrowserBaseIT {

	@Test
	void testChangePasswordFlow() throws Exception {
		final String email = "security-password-ui@example.com";
		final String oldPassword = "UiOldPassword123!";
		final String newPassword = "UiNewPassword123!";

		helper.registerUser(email, oldPassword, "en_US", "USD");
		final OkHttpClient apiClient = helper.login(email, oldPassword);
		helper.createAccount(apiClient, "Security Password Account", "D", "Checking", "0");

		loginToMainApp(email, oldPassword);
		openChangePasswordEditor();
		waitForComponent("changepasswordeditor");

		setExtFieldValue("changepasswordeditor textfield[itemId=currentPassword]", oldPassword);
		setExtFieldValue("changepasswordeditor passwordfield[itemId=newPassword] textfield[itemId=password]", newPassword);
		setExtFieldValue("changepasswordeditor passwordfield[itemId=newPassword] textfield[itemId=confirm]", newPassword);
		clickExtButton("changepasswordeditor button[itemId=ok]");
		wait.until(d -> {
			try {
				return (Boolean) executeJs("return Ext.ComponentQuery.query('changepasswordeditor').length === 0;");
			}
			catch (Exception e) {
				return false;
			}
		});

		logoutToLogin();
		submitLogin(email, oldPassword);
		waitForLoginErrorMessage();

		final Boolean hasViewportAfterOldPassword = (Boolean) executeJs(
			"return typeof Ext !== 'undefined' && Ext.ComponentQuery.query('buddiviewport').length > 0;");
		assertThat(hasViewportAfterOldPassword).isFalse();

		submitLogin(email, newPassword);
		waitForMainApp();
	}

	@Test
	void testTotpEnableAndDisableFlow() throws Exception {
		final String email = "security-totp-ui@example.com";
		final String password = "UiTotpPassword123!";

		helper.registerUser(email, password, "en_US", "USD");
		final OkHttpClient apiClient = helper.login(email, password);
		helper.createAccount(apiClient, "Security TOTP Account", "D", "Checking", "0");

		loginToMainApp(email, password);
		openPreferencesEditor();
		setPreferencesTwoFactor(true);
		clickExtButton("preferenceseditor button[itemId=ok]");

		waitForLoginPage();
		waitForLoginActiveItem("totpSetup");
		clickLiteAction("totpDisable");

		waitForMainApp();

		logoutToLogin();
		submitLogin(email, password);
		waitForMainApp();

		final Boolean hasTotpTokenPrompt = (Boolean) executeJs(
			"return document.querySelector(\"#loginform [data-card='totpToken']:not(.auth-hidden)\") != null;");
		assertThat(hasTotpTokenPrompt).isFalse();
	}

	private void loginToMainApp(final String email, final String password) {
		driver.get(getBaseUrl() + "/");
		waitForLoginPage();
		submitLogin(email, password);
		waitForMainApp();
	}

	private void submitLogin(final String email, final String password) {
		submitLiteLogin(email, password);
	}

	private void waitForMainApp() {
		wait.until(d -> {
			try {
				return (Boolean) executeJs(
					"return Ext.ComponentQuery.query('buddiviewport').length > 0 && Ext.ComponentQuery.query('accounttree').length > 0;");
			}
			catch (Exception e) {
				return false;
			}
		});
	}

	private void waitForLoginPage() {
		waitForLoginPageLite();
	}

	private void waitForLoginActiveItem(final String itemId) {
		wait.until(d -> {
			try {
				return (Boolean) executeJs(
					"return document.querySelector(\"#loginform [data-card-container='login'] [data-card='\" + arguments[0] + \"']:not(.auth-hidden)\") != null;",
					itemId);
			}
			catch (Exception e) {
				return false;
			}
		});
	}

	private void waitForLoginErrorMessage() {
		wait.until(d -> {
			try {
				return (Boolean) executeJs(
					"var message = document.querySelector(\"#loginform [data-message='messageLogin1']\");" +
						"return message != null && (message.textContent || '').trim().length > 0;");
			}
			catch (Exception e) {
				return false;
			}
		});
	}

	private void openChangePasswordEditor() {
		executeJs(
			"var viewport = Ext.ComponentQuery.query('buddiviewport')[0];" +
			"Ext.widget('changepasswordeditor', {panel: viewport}).show();"
		);
	}

	private void openPreferencesEditor() {
		executeJs(
			"window.__prefsOpened = false;" +
			"var viewport = Ext.ComponentQuery.query('buddiviewport')[0];" +
			"Ext.Ajax.request({" +
			"  url: 'data/userpreferences'," +
			"  method: 'GET'," +
			"  success: function(response) {" +
			"    var data = Ext.decode(response.responseText);" +
			"    Ext.widget('preferenceseditor', {panel: viewport, data: data}).show();" +
			"    window.__prefsOpened = true;" +
			"  }," +
			"  failure: function() { window.__prefsOpened = 'failed'; }" +
			"});"
		);
		wait.until(d -> {
			try {
				return (Boolean) executeJs("return window.__prefsOpened === true;");
			}
			catch (Exception e) {
				return false;
			}
		});
		waitForComponent("preferenceseditor");
	}

	private void setPreferencesTwoFactor(final boolean value) {
		executeJs(
			"var checkbox = Ext.ComponentQuery.query(\"preferenceseditor checkbox[itemId=useTwoFactor]\")[0];" +
			"if (checkbox) { checkbox.setValue(arguments[0]); }",
			value
		);
	}

	private void logoutToLogin() {
		driver.get(getBaseUrl() + "/authentication/logout");
		waitForLoginPage();
	}
}
