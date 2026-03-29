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
		waitForExtJs();
		waitForComponent("login");
		assertThat(driver.getCurrentUrl()).isEqualTo(getBaseUrl() + "/");

		setExtFieldValue("login textfield[name=identifier]", canonicalEmail);
		setExtFieldValue("login textfield[name=password]", PASSWORD);
		clickExtButton("login button[itemId=authenticate]");

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

	@Test
	void testRegisterComboboxesAreSearchable() {
		driver.get(getBaseUrl() + "/");
		waitForExtJs();
		waitForComponent("login");

		executeJs("Ext.ComponentQuery.query('login')[0].setActiveTab(1);");
		waitForComponent("login combobox[name=locale]");
		waitForComponent("login combobox[name=currency]");

		wait.until(d -> {
			try {
				return (Boolean) executeJs(
					"var locale = Ext.ComponentQuery.query('login combobox[name=locale]')[0];" +
					"var currency = Ext.ComponentQuery.query('login combobox[name=currency]')[0];" +
					"return locale && currency && locale.getStore().isLoaded() && currency.getStore().isLoaded();");
			} catch (Exception e) {
				return false;
			}
		});

		String localeConfig = (String) executeJs(
			"var combo = Ext.ComponentQuery.query('login combobox[name=locale]')[0];" +
			"return String(combo.editable) + '|' + String(combo.queryMode);");
		assertThat(localeConfig)
			.as("Locale combobox should be editable and use local query mode")
			.isEqualTo("true|local");

		String currencyConfig = (String) executeJs(
			"var combo = Ext.ComponentQuery.query('login combobox[name=currency]')[0];" +
			"return String(combo.editable) + '|' + String(combo.queryMode);");
		assertThat(currencyConfig)
			.as("Currency combobox should be editable and use local query mode")
			.isEqualTo("true|local");

		Boolean localeFiltersUnmatchedQuery = (Boolean) executeJs(
			"var combo = Ext.ComponentQuery.query('login combobox[name=locale]')[0];" +
			"combo.getStore().clearFilter();" +
			"var before = combo.getStore().getCount();" +
			"combo.setRawValue('zzzzzzzz');" +
			"combo.doQuery(combo.getRawValue(), false, true);" +
			"var after = combo.getStore().getCount();" +
			"combo.getStore().clearFilter();" +
			"combo.setRawValue('');" +
			"return before > 0 && after === 0;");
		assertThat(localeFiltersUnmatchedQuery)
			.as("Locale combobox should filter out unmatched input")
			.isTrue();

		Boolean currencyFiltersUnmatchedQuery = (Boolean) executeJs(
			"var combo = Ext.ComponentQuery.query('login combobox[name=currency]')[0];" +
			"combo.getStore().clearFilter();" +
			"var before = combo.getStore().getCount();" +
			"combo.setRawValue('zzzzzzzz');" +
			"combo.doQuery(combo.getRawValue(), false, true);" +
			"var after = combo.getStore().getCount();" +
			"combo.getStore().clearFilter();" +
			"combo.setRawValue('');" +
			"return before > 0 && after === 0;");
		assertThat(currencyFiltersUnmatchedQuery)
			.as("Currency combobox should filter out unmatched input")
			.isTrue();
	}
}
