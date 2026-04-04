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
	void testLegacyIndexHtmlRedirectsToRootAndStaysCanonicalAfterLogin() throws Exception {
		final String canonicalEmail = "login-smoke-canonical@example.com";
		helper.registerUser(canonicalEmail, PASSWORD, "en_US", "USD");

		driver.get(getBaseUrl() + "/index.html");
		waitForLoginPageLite();
		assertThat(driver.getCurrentUrl()).isEqualTo(getBaseUrl() + "/");

		submitLiteLogin(canonicalEmail, PASSWORD);

		wait.until(d -> {
			try {
				return (Boolean) executeJs(
					"return typeof BuddiLive !== 'undefined' && BuddiLive.app != null && BuddiLive.app.viewport != null;");
			}
			catch (Exception e) {
				return false;
			}
		});

		assertThat(driver.getCurrentUrl()).isEqualTo(getBaseUrl() + "/");
	}

	@Test
	void testLoginWithBadPasswordStaysOnLoginPage() throws Exception {
		helper.registerUser("login-smoke-bad@example.com", PASSWORD, "en_US", "USD");

		driver.get(getBaseUrl() + "/");
		waitForLoginPageLite();

		submitLiteLogin("login-smoke-bad@example.com", "WrongPassword!");
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

		// Should still be on login page, not the main app
		Boolean stillOnLogin = (Boolean) executeJs(
			"return typeof BuddiLive === 'undefined' || BuddiLive.app == null;");
		assertThat(stillOnLogin).isTrue();

		// Login form should still be visible
		Boolean hasLoginForm = (Boolean) executeJs(
			"return document.querySelector(\"#loginform [data-card-container='login'] [data-card='authenticate']\") != null;");
		assertThat(hasLoginForm).isTrue();
	}

	@Test
	void testRegisterLocaleAndCurrencyUseSelectsWithWhitelistedValues() {
		driver.get(getBaseUrl() + "/");
		waitForLoginPageLite();
		executeJs(
			"var registerTab = document.querySelector(\"#loginform [data-tab-target='register']\");" +
				"if (registerTab) { registerTab.click(); }");

		wait.until(d -> {
			try {
				return (Boolean) executeJs(
					"var locale = document.querySelector(\"#loginform [data-tab-panel='register']:not(.auth-hidden) select[name='locale']\");" +
						"var currency = document.querySelector(\"#loginform [data-tab-panel='register']:not(.auth-hidden) select[name='currency']\");" +
						"return locale != null && currency != null;");
			}
			catch (Exception e) {
				return false;
			}
		});

		wait.until(d -> {
			try {
				return (Boolean) executeJs(
					"var locale = document.querySelector(\"#loginform [data-tab-panel='register']:not(.auth-hidden) select[name='locale']\");" +
						"var currency = document.querySelector(\"#loginform [data-tab-panel='register']:not(.auth-hidden) select[name='currency']\");" +
						"return locale != null && currency != null && locale.options.length > 0 && currency.options.length > 0;");
			}
			catch (Exception e) {
				return false;
			}
		});

		String fieldTags = (String) executeJs(
			"var locale = document.querySelector(\"#loginform [data-tab-panel='register']:not(.auth-hidden) select[name='locale']\");" +
				"var currency = document.querySelector(\"#loginform [data-tab-panel='register']:not(.auth-hidden) select[name='currency']\");" +
				"return String(locale.tagName) + '|' + String(currency.tagName);");
		assertThat(fieldTags).isEqualTo("SELECT|SELECT");

		Boolean hasCommonOptions = (Boolean) executeJs(
			"var locale = document.querySelector(\"#loginform [data-tab-panel='register']:not(.auth-hidden) select[name='locale']\");" +
				"var currency = document.querySelector(\"#loginform [data-tab-panel='register']:not(.auth-hidden) select[name='currency']\");" +
				"var hasLocale = Array.from(locale.options).some(function(opt) { return opt.value === 'en_US'; });" +
				"var hasCurrency = Array.from(currency.options).some(function(opt) { return opt.value === 'USD'; });" +
				"return hasLocale && hasCurrency;");
		assertThat(hasCommonOptions).isTrue();

		String invalidSelectionResult = (String) executeJs(
			"var locale = document.querySelector(\"#loginform [data-tab-panel='register']:not(.auth-hidden) select[name='locale']\");" +
				"var currency = document.querySelector(\"#loginform [data-tab-panel='register']:not(.auth-hidden) select[name='currency']\");" +
				"locale.value = 'zz_ZZ';" +
				"currency.value = 'ZZZ';" +
				"return String(locale.value) + '|' + String(currency.value);");
		assertThat(invalidSelectionResult).isEqualTo("|");
	}
}
