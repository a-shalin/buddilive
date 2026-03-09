package ca.digitalcave.buddi.live.e2e.ui;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class LoginSmokeIT extends BrowserBaseIT {

	private static final String EMAIL = "login-smoke@example.com";
	private static final String PASSWORD = "TestPassword123!";

	@Test
	void testLoginShowsMainApp() throws Exception {
		helper.registerUser(EMAIL, PASSWORD, "en_US", "USD");

		browserLogin(EMAIL, PASSWORD);

		// Verify the main viewport is visible with account tree and tab panel
		Boolean hasViewport = (Boolean) executeJs(
			"return Ext.ComponentQuery.query('buddiviewport').length > 0;");
		assertThat(hasViewport).isTrue();

		Boolean hasAccountTree = (Boolean) executeJs(
			"return Ext.ComponentQuery.query('accounttree').length > 0;");
		assertThat(hasAccountTree).isTrue();

		Boolean hasTabPanel = (Boolean) executeJs(
			"return Ext.ComponentQuery.query('tabpanel[itemId=budditabpanel]').length > 0;");
		assertThat(hasTabPanel).isTrue();
	}

	@Test
	void testLoginWithBadPasswordStaysOnLoginPage() throws Exception {
		helper.registerUser("login-smoke-bad@example.com", PASSWORD, "en_US", "USD");

		driver.get(getBaseUrl() + "/index.html");
		waitForExtJs();
		waitForComponent("login");

		setExtFieldValue("login textfield[name=identifier]", "login-smoke-bad@example.com");
		setExtFieldValue("login textfield[name=password]", "WrongPassword!");
		clickExtButton("login button[itemId=authenticate]");

		// Wait a moment for the AJAX response
		Thread.sleep(1000);

		// Should still be on login page, not the main app
		Boolean stillOnLogin = (Boolean) executeJs(
			"return typeof BuddiLive === 'undefined' || BuddiLive.app == null;");
		assertThat(stillOnLogin).isTrue();

		// Login form should still be visible
		Boolean hasLoginForm = (Boolean) executeJs(
			"return Ext.ComponentQuery.query('login').length > 0;");
		assertThat(hasLoginForm).isTrue();
	}
}
