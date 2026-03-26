package ca.digitalcave.buddi.live.e2e.api;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import org.json.JSONObject;

import okhttp3.FormBody;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import ca.digitalcave.buddi.live.e2e.BaseIT;
import ca.digitalcave.buddi.live.e2e.TestHelper;

import static org.assertj.core.api.Assertions.assertThat;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class AuthenticationIT extends BaseIT {

	private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

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

	@Test
	@Order(6)
	void testAuthenticatedIndexContainsSessionTimingConfig() throws Exception {
		final OkHttpClient client = helper.login(EMAIL, PASSWORD);
		final Request request = new Request.Builder()
			.url(getBaseUrl() + "/index")
			.get()
			.build();

		try (Response response = client.newCall(request).execute()) {
			assertThat(response.code()).isEqualTo(200);
			final String body = response.body().string();
			assertThat(body).contains("sessionTimeoutMillis");
			assertThat(body).contains("sessionRefreshWindowMillis");
		}
	}

	@Test
	@Order(7)
	void testChangePasswordApi() throws Exception {
		final String email = "auth-change-password@example.com";
		final String oldPassword = "OldPassword123!";
		final String newPassword = "NewPassword123!";

		helper.registerUser(email, oldPassword, "en_US", "USD");
		final OkHttpClient client = helper.login(email, oldPassword);

		final JSONObject requestBody = new JSONObject();
		requestBody.put("action", "update");
		requestBody.put("currentPassword", oldPassword);
		requestBody.put("newPassword", newPassword);

		final Request changePasswordRequest = new Request.Builder()
			.url(getBaseUrl() + "/data/changepassword")
			.post(RequestBody.create(requestBody.toString(), JSON))
			.build();

		try (Response response = client.newCall(changePasswordRequest).execute()) {
			assertThat(response.code()).isEqualTo(200);
			final JSONObject body = new JSONObject(response.body().string());
			assertThat(body.getBoolean("success")).isTrue();
		}

		final LoginResult oldLogin = login(helper.newClient(), email, oldPassword);
		assertThat(oldLogin.code).isEqualTo(400);
		assertThat(oldLogin.body.getBoolean("success")).isFalse();

		final LoginResult newLogin = login(helper.newClient(), email, newPassword);
		assertThat(newLogin.code).isEqualTo(200);
		assertThat(newLogin.body.getBoolean("success")).isTrue();
	}

	@Test
	@Order(8)
	void testTotpEnableDisableApi() throws Exception {
		final String email = "auth-totp-enable-disable@example.com";
		final String password = "TotpPassword123!";

		helper.registerUser(email, password, "en_US", "USD");
		final OkHttpClient preferencesClient = helper.login(email, password);

		final JSONObject prefs = helper.getUserPreferences(preferencesClient);
		prefs.put("useTwoFactor", true);
		helper.updateUserPreferences(preferencesClient, prefs);

		final LoginResult setupLogin = login(helper.newClient(), email, password);
		assertThat(setupLogin.code).isEqualTo(200);
		assertThat(setupLogin.body.getBoolean("success")).isFalse();
		assertThat(setupLogin.body.optString("next")).isEqualTo("totpSetup");

		final JSONObject prefsWithTotp = helper.getUserPreferences(preferencesClient);
		assertThat(prefsWithTotp.getBoolean("useTwoFactor")).isTrue();
		prefsWithTotp.put("useTwoFactor", false);
		helper.updateUserPreferences(preferencesClient, prefsWithTotp);

		final LoginResult postDisableLogin = login(helper.newClient(), email, password);
		assertThat(postDisableLogin.code).isEqualTo(200);
		assertThat(postDisableLogin.body.getBoolean("success")).isTrue();
		assertThat(postDisableLogin.body.has("next")).isFalse();
	}

	@Test
	@Order(9)
	void testLogoutEndpointClearsSession() throws Exception {
		final OkHttpClient client = helper.login(EMAIL, PASSWORD);
		final Request request = new Request.Builder()
			.url(getBaseUrl() + "/authentication/logout")
			.get()
			.build();

		try (Response response = client.newCall(request).execute()) {
			assertThat(response.code()).isEqualTo(302);
			assertThat(response.header("Location", "")).isEqualTo("/");
		}

		final int dataCode = helper.getResponseCode(client, "GET", "/data/accounts");
		assertThat(dataCode).isNotEqualTo(200);
	}

	@Test
	@Order(10)
	void testResetPasswordValidation() throws Exception {
		final OkHttpClient client = helper.newClient();
		final Request request = new Request.Builder()
			.url(getBaseUrl() + "/authentication/resetPassword")
			.post(new FormBody.Builder().build())
			.build();

		try (Response response = client.newCall(request).execute()) {
			assertThat(response.code()).isEqualTo(400);
			final JSONObject body = new JSONObject(response.body().string());
			assertThat(body.getBoolean("success")).isFalse();
		}
	}

	@Test
	@Order(11)
	void testForgotPasswordReturnsNoContent() throws Exception {
		final OkHttpClient client = helper.newClient();
		final Request request = new Request.Builder()
			.url(getBaseUrl() + "/authentication/forgotPassword")
			.post(new FormBody.Builder().add("identifier", EMAIL).build())
			.build();

		try (Response response = client.newCall(request).execute()) {
			assertThat(response.code()).isEqualTo(204);
		}
	}

	@Test
	@Order(12)
	void testForgotUsernameReturnsNoContent() throws Exception {
		final OkHttpClient client = helper.newClient();
		final Request request = new Request.Builder()
			.url(getBaseUrl() + "/authentication/forgotUsername")
			.post(new FormBody.Builder().add("email", EMAIL).build())
			.build();

		try (Response response = client.newCall(request).execute()) {
			assertThat(response.code()).isEqualTo(204);
		}
	}

	@Test
	@Order(13)
	void testCheckPasswordReturnsStrengthPayload() throws Exception {
		final OkHttpClient client = helper.newClient();
		final Request request = new Request.Builder()
			.url(getBaseUrl() + "/authentication/checkPassword")
			.post(new FormBody.Builder().add("identifier", EMAIL).add("secret", "Password123").build())
			.build();

		try (Response response = client.newCall(request).execute()) {
			assertThat(response.code()).isEqualTo(200);
			final JSONObject body = new JSONObject(response.body().string());
			assertThat(body.has("score")).isTrue();
			assertThat(body.has("passed")).isTrue();
		}
	}

	@Test
	@Order(14)
	void testPasswordExpiredRequiresAuthentication() throws Exception {
		final OkHttpClient client = helper.newClient();
		final Request request = new Request.Builder()
			.url(getBaseUrl() + "/authentication/passwordExpired")
			.post(new FormBody.Builder().add("password", "NewPassword123!").build())
			.build();

		try (Response response = client.newCall(request).execute()) {
			assertThat(response.code()).isEqualTo(401);
			final JSONObject body = new JSONObject(response.body().string());
			assertThat(body.getBoolean("success")).isFalse();
		}
	}

	@Test
	@Order(15)
	void testTotpSetupEndpointsRequireAuthentication() throws Exception {
		final OkHttpClient client = helper.newClient();

		final Request getRequest = new Request.Builder()
			.url(getBaseUrl() + "/authentication/totpSetup")
			.get()
			.build();
		try (Response response = client.newCall(getRequest).execute()) {
			assertThat(response.code()).isEqualTo(401);
		}

		final Request postRequest = new Request.Builder()
			.url(getBaseUrl() + "/authentication/totpSetup")
			.post(new FormBody.Builder().add("totpToken", "000000").build())
			.build();
		try (Response response = client.newCall(postRequest).execute()) {
			assertThat(response.code()).isEqualTo(401);
		}

		final Request deleteRequest = new Request.Builder()
			.url(getBaseUrl() + "/authentication/totpSetup")
			.delete()
			.build();
		try (Response response = client.newCall(deleteRequest).execute()) {
			assertThat(response.code()).isEqualTo(401);
		}
	}

	@Test
	@Order(16)
	void testTotpTokenRequiresAuthentication() throws Exception {
		final OkHttpClient client = helper.newClient();
		final Request request = new Request.Builder()
			.url(getBaseUrl() + "/authentication/totpToken")
			.post(new FormBody.Builder().add("totpToken", "000000").build())
			.build();

		try (Response response = client.newCall(request).execute()) {
			assertThat(response.code()).isEqualTo(401);
		}
	}

	@Test
	@Order(17)
	void testGenerateBackupCodesRequiresValidatedAuthentication() throws Exception {
		final OkHttpClient client = helper.newClient();
		final Request request = new Request.Builder()
			.url(getBaseUrl() + "/authentication/generateBackupCodes")
			.post(RequestBody.create("", JSON))
			.build();

		try (Response response = client.newCall(request).execute()) {
			assertThat(response.code()).isEqualTo(403);
		}
	}

	@Test
	@Order(18)
	void testImpersonateEndpointsRequireAuthentication() throws Exception {
		final OkHttpClient client = helper.newClient();

		final Request postRequest = new Request.Builder()
			.url(getBaseUrl() + "/authentication/impersonate")
			.post(new FormBody.Builder().add("impersonate", "someone@example.com").build())
			.build();
		try (Response response = client.newCall(postRequest).execute()) {
			assertThat(response.code()).isEqualTo(401);
		}

		final Request deleteRequest = new Request.Builder()
			.url(getBaseUrl() + "/authentication/impersonate")
			.delete()
			.build();
		try (Response response = client.newCall(deleteRequest).execute()) {
			assertThat(response.code()).isEqualTo(401);
		}
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

	private LoginResult login(final OkHttpClient client, final String email, final String password) throws Exception {
		final RequestBody formBody = new FormBody.Builder()
			.add("identifier", email)
			.add("password", password)
			.add("disableIpLock", "on")
			.build();
		final Request request = new Request.Builder()
			.url(getBaseUrl() + "/authentication/login")
			.post(formBody)
			.build();

		try (Response response = client.newCall(request).execute()) {
			final String body = response.body().string();
			return new LoginResult(response.code(), new JSONObject(body));
		}
	}

	private static class LoginResult {
		private final int code;
		private final JSONObject body;

		private LoginResult(final int code, final JSONObject body) {
			this.code = code;
			this.body = body;
		}
	}
}
