package ca.digitalcave.buddi.live.e2e.ui;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AccountSmokeIT extends BrowserBaseIT {

	private static final String EMAIL = "account-smoke@example.com";
	private static final String PASSWORD = "TestPassword123!";

	@Test
	void testCreateAccountAppearsInTree() throws Exception {
		helper.registerUser(EMAIL, PASSWORD, "en_US", "USD");
		browserLogin(EMAIL, PASSWORD);

		// Click "Add Account" button
		waitForComponent("button[itemId=addAccount]");
		clickExtButton("button[itemId=addAccount]");

		// Wait for the account editor dialog
		waitForComponent("accounteditor");

		// Fill in the account form fields
		setExtFieldValue("accounteditor textfield[itemId=name]", "My Chequing");
		setExtFieldValue("accounteditor textfield[itemId=accountType]", "Chequing");
		// Type defaults to Debit ("D"), which is correct
		executeJs(
			"Ext.ComponentQuery.query('accounteditor numberfield[itemId=startBalance]')[0].setValue(500);");

		// Enable OK button and click
		executeJs(
			"Ext.ComponentQuery.query('accounteditor')[0].down('button[itemId=ok]').setDisabled(false);");
		clickExtButton("accounteditor button[itemId=ok]");

		// Account creation triggers location.reload() — wait for the app to reinitialize
		waitForAppReload();

		// Verify the account appears in the tree
		Boolean found = (Boolean) wait.until(d -> {
			try {
				return (Boolean) executeJs(
					"var tree = Ext.ComponentQuery.query('accounttree')[0];" +
					"var store = tree.getStore();" +
					"var found = false;" +
					"store.each(function(r) { if (r.get('name') === 'My Chequing') found = true; });" +
					"return found;");
			} catch (Exception e) {
				return false;
			}
		});
		assertThat(found).isTrue();
	}
}
