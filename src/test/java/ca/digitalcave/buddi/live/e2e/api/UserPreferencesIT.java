package ca.digitalcave.buddi.live.e2e.api;

import okhttp3.OkHttpClient;

import org.json.JSONObject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;

import ca.digitalcave.buddi.live.e2e.BaseIT;
import ca.digitalcave.buddi.live.e2e.TestHelper;

import static org.assertj.core.api.Assertions.assertThat;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class UserPreferencesIT extends BaseIT {

	private static TestHelper helper;
	private static OkHttpClient client;
	private static final String EMAIL = "prefs-test@example.com";
	private static final String PASSWORD = "TestPassword123!";

	@BeforeAll
	static void setUp() throws Exception {
		helper = new TestHelper(getBaseUrl(), getDbUrl());
		helper.registerUser(EMAIL, PASSWORD, "en_US", "USD");
		client = helper.login(EMAIL, PASSWORD);
	}

	@Test
	@Order(1)
	void testGetPreferences() throws Exception {
		JSONObject prefs = helper.getUserPreferences(client);
		assertThat(prefs.getBoolean("success")).isTrue();
		assertThat(prefs.getString("currency")).isEqualTo("USD");
		assertThat(prefs.getString("locale")).isEqualTo("en_US");
		assertThat(prefs.getBoolean("showCurrencySymbol")).isFalse();
		assertThat(prefs.getBoolean("currencySpacing")).isTrue();
	}

	@Test
	@Order(2)
	void testUpdatePreferences() throws Exception {
		JSONObject update = new JSONObject();
		update.put("showDeleted", true);
		update.put("showCurrencySymbol", true);
		update.put("currencySpacing", false);
		update.put("currencyAfter", true);
		update.put("decimalSeparator", ",");
		update.put("thousandSeparator", " ");
		update.put("negativeFormat", "B");

		helper.updateUserPreferences(client, update);

		JSONObject prefs = helper.getUserPreferences(client);
		assertThat(prefs.getBoolean("showDeleted")).isTrue();
		assertThat(prefs.getBoolean("showCurrencySymbol")).isTrue();
		assertThat(prefs.getBoolean("currencySpacing")).isFalse();
		assertThat(prefs.getBoolean("currencyAfter")).isTrue();
		assertThat(prefs.getString("decimalSeparator")).isEqualTo(",");
		assertThat(prefs.getString("thousandSeparator")).isEqualTo(" ");
		assertThat(prefs.getString("negativeFormat")).isEqualTo("B");
	}
}
