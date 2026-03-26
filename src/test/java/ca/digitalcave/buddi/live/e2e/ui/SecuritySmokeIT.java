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
			"return Ext.ComponentQuery.query('buddiviewport').length > 0;");
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
		clickExtButton("login button[itemId=totpDisable]");

		waitForMainApp();

		logoutToLogin();
		submitLogin(email, password);
		waitForMainApp();

		final Boolean hasTotpTokenPrompt = (Boolean) executeJs(
			"var login = Ext.ComponentQuery.query('login')[0];" +
			"if (!login) return false;" +
			"var tab = login.getActiveTab();" +
			"if (!tab || !tab.getLayout) return false;" +
			"var active = tab.getLayout().getActiveItem();" +
			"return !!(active && active.getItemId && active.getItemId() === 'totpToken');");
		assertThat(hasTotpTokenPrompt).isFalse();
	}

	private void loginToMainApp(final String email, final String password) {
		driver.get(getBaseUrl() + "/index.html");
		waitForLoginPage();
		submitLogin(email, password);
		waitForMainApp();
	}

	private void submitLogin(final String email, final String password) {
		setExtFieldValue("login textfield[name=identifier]", email);
		setExtFieldValue("login textfield[name=password]", password);
		clickExtButton("login button[itemId=authenticate]");
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
		waitForExtJs();
		waitForComponent("login");
	}

	private void waitForLoginActiveItem(final String itemId) {
		wait.until(d -> {
			try {
				return (Boolean) executeJs(
					"var login = Ext.ComponentQuery.query('login')[0];" +
					"if (!login) return false;" +
					"var tab = login.getActiveTab();" +
					"if (!tab || !tab.getLayout) return false;" +
					"var active = tab.getLayout().getActiveItem();" +
					"return !!(active && active.getItemId && active.getItemId() === arguments[0]);",
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
					"var label = Ext.ComponentQuery.query(\"login transientlabel[itemId=messageLogin1]\")[0];" +
					"if (!label || !label.getEl()) return false;" +
					"var text = label.getEl().dom.innerText || label.getEl().dom.textContent || '';" +
					"text = text.replace(/\\u00a0/g, ' ').trim();" +
					"return text.length > 0;");
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
