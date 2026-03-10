package ca.digitalcave.buddi.live.e2e.api;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;

import ca.digitalcave.buddi.live.e2e.BaseIT;
import ca.digitalcave.buddi.live.e2e.TestHelper;

import static org.assertj.core.api.Assertions.assertThat;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class AuthenticationIT extends BaseIT {

	private static TestHelper helper;
	private static final String EMAIL = "auth-test@example.com";
	private static final String PASSWORD = "TestPassword123!";

	@BeforeAll
	static void setUp() {
		helper = new TestHelper(getBaseUrl(), getDbUrl());
	}

	@Test
	@Order(1)
	void testReRegisterUnactivatedUser() throws Exception {
		String reregEmail = "reregister-test@example.com";
		String password = "ReregPassword123!";
		OkHttpClient client = helper.newClient();

		// First registration
		int code1 = postRegister(client, reregEmail);
		assertThat(code1).as("First registration should succeed").isEqualTo(200);

		// Read activation key from DB
		String firstKey = getLatestActivationKey();
		assertThat(firstKey).as("First activation key should exist").isNotNull();

		// Re-register without activating
		int code2 = postRegister(client, reregEmail);
		assertThat(code2).as("Re-registration of unactivated user should succeed").isEqualTo(200);

		// Read new activation key
		String secondKey = getLatestActivationKey();
		assertThat(secondKey).as("New activation key should exist").isNotNull();
		assertThat(secondKey).as("New activation key should differ from first").isNotEqualTo(firstKey);

		// Activate with new key
		RequestBody activateBody = new FormBody.Builder()
			.add("activationKey", secondKey)
			.add("password", password)
			.build();
		Request activateRequest = new Request.Builder()
			.url(getBaseUrl() + "/authentication/activate")
			.post(activateBody)
			.build();
		try (Response response = client.newCall(activateRequest).execute()) {
			assertThat(response.code()).as("Activation with new key should succeed").isEqualTo(200);
		}

		// Verify login works
		OkHttpClient loggedIn = helper.login(reregEmail, password);
		int dataCode = helper.getResponseCode(loggedIn, "GET", "/data/accounts");
		assertThat(dataCode).isEqualTo(200);
	}

	@Test
	@Order(2)
	void testReRegisterActivatedUserFails() throws Exception {
		String activatedEmail = "activated-rereg-test@example.com";
		String password = "ActivatedPassword123!";

		// Register and activate
		helper.registerUser(activatedEmail, password, "en_US", "USD");

		// Attempt re-registration of activated user
		OkHttpClient client = helper.newClient();
		int code = postRegister(client, activatedEmail);
		assertThat(code).as("Re-registration of activated user should fail").isNotEqualTo(200);
	}

	@Test
	@Order(3)
	void testRegisterAndLogin() throws Exception {
		helper.registerUser(EMAIL, PASSWORD, "en_US", "USD");
		OkHttpClient client = helper.login(EMAIL, PASSWORD);

		// Verify authenticated access works
		int code = helper.getResponseCode(client, "GET", "/data/accounts");
		assertThat(code).isEqualTo(200);
	}

	@Test
	@Order(4)
	void testLoginWrongPassword() throws Exception {
		OkHttpClient client = helper.newClient();
		int code = helper.getResponseCode(client, "POST", "/authentication/login");
		assertThat(code).isNotEqualTo(200);
	}

	@Test
	@Order(5)
	void testUnauthenticatedAccess() throws Exception {
		OkHttpClient client = helper.newClient();
		int code = helper.getResponseCode(client, "GET", "/data/accounts");
		// Unauthenticated requests get redirected to login
		assertThat(code).isNotEqualTo(200);
	}

	private int postRegister(OkHttpClient client, String email) throws Exception {
		RequestBody formBody = new FormBody.Builder()
			.add("email", email)
			.add("locale", "en_US")
			.add("currency", "USD")
			.add("agree", "on")
			.build();
		Request request = new Request.Builder()
			.url(getBaseUrl() + "/authentication/register")
			.post(formBody)
			.build();
		try (Response response = client.newCall(request).execute()) {
			response.body().string();
			return response.code();
		}
	}

	private String getLatestActivationKey() throws Exception {
		try (Connection conn = DriverManager.getConnection(getDbUrl());
			 Statement stmt = conn.createStatement();
			 ResultSet rs = stmt.executeQuery(
				 "SELECT activation_key FROM user_activations ORDER BY created DESC FETCH FIRST 1 ROWS ONLY")) {
			if (rs.next()) {
				return rs.getString(1);
			}
		}
		return null;
	}
}
