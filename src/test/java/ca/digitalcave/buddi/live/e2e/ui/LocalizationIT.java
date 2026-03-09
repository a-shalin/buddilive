package ca.digitalcave.buddi.live.e2e.ui;

import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;

import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;

public class LocalizationIT extends BrowserBaseIT {

	private static final String PASSWORD = "TestPassword123!";

	private record LabelSpec(String query, String property, String i18nKey) {}

	private static final List<LabelSpec> VIEWPORT_LABELS = List.of(
		// Group 1 - Tab titles
		new LabelSpec("panel[itemId=myAccounts]", "title", "MY_ACCOUNTS"),
		new LabelSpec("tabpanel[itemId=budditabpanel]", "items.items[1].title", "MY_BUDGET"),

		// Account toolbar buttons
		new LabelSpec("button[itemId=addAccount]", "text", "NEW_ACCOUNT"),
		new LabelSpec("button[itemId=editAccount]", "text", "MODIFY_ACCOUNT"),
		new LabelSpec("button[itemId=deleteAccount]", "text", "DELETE_ACCOUNT"),

		// Logout
		new LabelSpec("button[itemId=logout]", "text", "LOGOUT"),

		// Reports/System button text (via child menu item)
		new LabelSpec("menuitem[itemId=showIncomeAndExpensesByCategoryTable]", "ownerCt.ownerCmp.text", "REPORTS"),
		new LabelSpec("menuitem[itemId=changePassword]", "ownerCt.ownerCmp.text", "SYSTEM")
	);

	private static final List<LabelSpec> REPORT_MENU_LABELS = List.of(
		new LabelSpec("menuitem[itemId=showIncomeAndExpensesByCategoryTable]", "text", "REPORT_TABLE_INCOME_AND_EXPENSES_BY_CATEGORY"),
		new LabelSpec("menuitem[itemId=showAverageIncomeAndExpensesByCategoryTable]", "text", "REPORT_TABLE_AVERAGE_INCOME_AND_EXPENSES_BY_CATEGORY"),
		new LabelSpec("menuitem[itemId=showInflowAndOutflowByAccountTable]", "text", "REPORT_TABLE_INFLOW_AND_OUTFLOW_BY_ACCOUNT"),
		new LabelSpec("menuitem[itemId=showInflowAndOutflowByPayeeTable]", "text", "REPORT_TABLE_INFLOW_AND_OUTFLOW_BY_PAYEE"),
		new LabelSpec("menuitem[itemId=showIncomeByCategoryPie]", "text", "REPORT_PIE_INCOME_BY_CATEGORY"),
		new LabelSpec("menuitem[itemId=showExpensesByCategoryPie]", "text", "REPORT_PIE_EXPENSES_BY_CATEGORY"),
		new LabelSpec("menuitem[itemId=showAccountBalancesOverTimeLine]", "text", "REPORT_ACCOUNT_BALANCES_OVER_TIME"),
		new LabelSpec("menuitem[itemId=showNetWorthOverTimeLine]", "text", "REPORT_NET_WORTH_OVER_TIME")
	);

	private static final List<LabelSpec> SYSTEM_MENU_LABELS = List.of(
		new LabelSpec("menuitem[itemId=changePassword]", "text", "CHANGE_PASSWORD"),
		new LabelSpec("menuitem[itemId=showPreferences]", "text", "PREFERENCES"),
		new LabelSpec("menuitem[itemId=showScheduled]", "text", "SCHEDULED_TRANSACTIONS"),
		new LabelSpec("menuitem[itemId=backup]", "text", "BACKUP"),
		new LabelSpec("menuitem[itemId=restore]", "text", "RESTORE"),
		new LabelSpec("menuitem[itemId=exportCsv]", "text", "EXPORT_CSV"),
		new LabelSpec("menuitem[itemId=gettingStarted]", "text", "HELP_GETTING_STARTED_TITLE"),
		new LabelSpec("menuitem[itemId=donate]", "text", "DONATE_TITLE"),
		new LabelSpec("menuitem[itemId=deleteUser]", "text", "DELETE_USER")
	);

	private static final List<LabelSpec> TRANSACTION_EDITOR_LABELS = List.of(
		new LabelSpec("button[itemId=deleteTransaction]", "text", "DELETE_TRANSACTION"),
		new LabelSpec("button[itemId=clearTransaction]", "text", "CLEAR_TRANSACTION"),
		new LabelSpec("button[itemId=recordTransaction]", "text", "RECORD_UPDATE_TRANSACTION"),
		new LabelSpec("datefield[itemId=date]", "emptyText", "DATE"),
		new LabelSpec("combobox[itemId=description]", "emptyText", "DESCRIPTION"),
		new LabelSpec("textfield[itemId=number]", "emptyText", "NUMBER")
	);

	private static final List<LabelSpec> SPLIT_EDITOR_LABELS = List.of(
		new LabelSpec("fromcombobox[itemId=from]", "emptyText", "FROM"),
		new LabelSpec("tocombobox[itemId=to]", "emptyText", "TO"),
		new LabelSpec("spliteditor textfield[itemId=memo]", "emptyText", "MEMO"),
		new LabelSpec("button[itemId=addSplit]", "tooltip", "ADD_SPLIT"),
		new LabelSpec("button[itemId=removeSplit]", "tooltip", "REMOVE_SPLIT")
	);

	private static final List<LabelSpec> TRANSACTION_LIST_LABELS = List.of(
		new LabelSpec("transactionlist", "columns[0].text", "DATE"),
		new LabelSpec("transactionlist", "columns[1].text", "DESCRIPTION"),
		new LabelSpec("transactionlist", "columns[2].text", "AMOUNT_FROM"),
		new LabelSpec("transactionlist", "columns[3].text", "AMOUNT_TO"),
		new LabelSpec("transactionlist", "columns[4].text", "BALANCE"),
		new LabelSpec("textfield[itemId=search]", "emptyText", "SEARCH")
	);

	private static final List<LabelSpec> BUDGET_TAB_LABELS = List.of(
		new LabelSpec("button[itemId=addCategory]", "text", "NEW_BUDGET_CATEGORY"),
		new LabelSpec("button[itemId=editCategory]", "text", "MODIFY_BUDGET_CATEGORY"),
		new LabelSpec("button[itemId=deleteCategory]", "text", "DELETE_BUDGET_CATEGORY")
	);

	private static final List<LabelSpec> ACCOUNT_EDITOR_LABELS = List.of(
		new LabelSpec("accounteditor", "title", "ADD_ACCOUNT"),
		new LabelSpec("accounteditor textfield[itemId=name]", "ownerCt.fieldLabel", "ACCOUNT_EDITOR_NAME"),
		new LabelSpec("accounteditor textfield[itemId=accountType]", "ownerCt.fieldLabel", "ACCOUNT_EDITOR_ACCOUNT_TYPE"),
		new LabelSpec("accounteditor combobox[itemId=type]", "ownerCt.fieldLabel", "ACCOUNT_EDITOR_TYPE"),
		new LabelSpec("accounteditor currencyfield[itemId=startBalance]", "ownerCt.fieldLabel", "ACCOUNT_EDITOR_STARTING_BALANCE"),
		new LabelSpec("accounteditor button[itemId=ok]", "text", "OK"),
		new LabelSpec("accounteditor button[itemId=cancel]", "text", "CANCEL")
	);

	@Test
	void testEnglishLabels() throws Exception {
		String email = "l10n-en@example.com";
		helper.registerUser(email, PASSWORD, "en_US", "USD");
		browserLogin(email, PASSWORD);
		verifyAllLabels(new Locale("en", "US"));
	}

	@Test
	void testRussianLabels() throws Exception {
		String email = "l10n-ru@example.com";
		helper.registerUser(email, PASSWORD, "ru", "RUB");
		browserLogin(email, PASSWORD);
		verifyAllLabels(new Locale("ru"));
	}

	private void verifyAllLabels(Locale locale) {
		ResourceBundle bundle = ResourceBundle.getBundle("i18n", locale);
		SoftAssertions softly = new SoftAssertions();

		// Groups 1-5: visible on My Accounts tab after login
		verifyLabels(softly, bundle, VIEWPORT_LABELS);
		verifyLabels(softly, bundle, REPORT_MENU_LABELS);
		verifyLabels(softly, bundle, SYSTEM_MENU_LABELS);
		verifyLabels(softly, bundle, TRANSACTION_EDITOR_LABELS);
		verifyLabels(softly, bundle, SPLIT_EDITOR_LABELS);
		verifyLabels(softly, bundle, TRANSACTION_LIST_LABELS);

		// Group 6: switch to Budget tab
		executeJs("Ext.ComponentQuery.query('tabpanel[itemId=budditabpanel]')[0].setActiveTab(1);");
		waitForComponent("button[itemId=addCategory]");
		verifyLabels(softly, bundle, BUDGET_TAB_LABELS);

		// Switch back to accounts tab for account editor
		executeJs("Ext.ComponentQuery.query('tabpanel[itemId=budditabpanel]')[0].setActiveTab(0);");
		waitForComponent("button[itemId=addAccount]");

		// Group 7: open account editor dialog
		clickExtButton("button[itemId=addAccount]");
		waitForComponent("accounteditor");
		verifyLabels(softly, bundle, ACCOUNT_EDITOR_LABELS);
		// Close the dialog
		clickExtButton("accounteditor button[itemId=cancel]");

		softly.assertAll();
	}

	private void verifyLabels(SoftAssertions softly, ResourceBundle bundle, List<LabelSpec> specs) {
		for (LabelSpec spec : specs) {
			String expected = bundle.getString(spec.i18nKey());
			String actual = getComponentProperty(spec.query(), spec.property());
			softly.assertThat(actual)
				.as("i18n key '%s' (query='%s', property='%s')", spec.i18nKey(), spec.query(), spec.property())
				.isEqualTo(expected);
		}
	}

	private String getComponentProperty(String query, String property) {
		String js;
		if (property.contains(".") || property.contains("[")) {
			js = "var cmp = Ext.ComponentQuery.query(arguments[0])[0];"
				+ "if (!cmp) return null;"
				+ "var val = cmp." + property + ";"
				+ "return (val !== undefined && val !== null) ? String(val) : null;";
		} else {
			js = "var cmp = Ext.ComponentQuery.query(arguments[0])[0];"
				+ "if (!cmp) return null;"
				+ "var val = cmp[arguments[1]];"
				+ "return (val !== undefined && val !== null) ? String(val) : null;";
		}
		Object result = executeJs(js, query, property);
		return result != null ? result.toString() : null;
	}
}
