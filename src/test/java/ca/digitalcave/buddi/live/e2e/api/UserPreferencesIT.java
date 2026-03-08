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
	}

	@Test
	@Order(2)
	void testUpdatePreferences() throws Exception {
		JSONObject update = new JSONObject();
		update.put("showDeleted", true);

		helper.updateUserPreferences(client, update);

		JSONObject prefs = helper.getUserPreferences(client);
		assertThat(prefs.getBoolean("showDeleted")).isTrue();
	}
}
