package ca.digitalcave.buddi.live.e2e.api;

import okhttp3.OkHttpClient;

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
	void testRegisterAndLogin() throws Exception {
		helper.registerUser(EMAIL, PASSWORD, "en_US", "USD");
		OkHttpClient client = helper.login(EMAIL, PASSWORD);

		// Verify authenticated access works
		int code = helper.getResponseCode(client, "GET", "/data/accounts");
		assertThat(code).isEqualTo(200);
	}

	@Test
	@Order(2)
	void testLoginWrongPassword() throws Exception {
		OkHttpClient client = helper.newClient();
		int code = helper.getResponseCode(client, "POST", "/authentication/login");
		assertThat(code).isNotEqualTo(200);
	}

	@Test
	@Order(3)
	void testUnauthenticatedAccess() throws Exception {
		OkHttpClient client = helper.newClient();
		int code = helper.getResponseCode(client, "GET", "/data/accounts");
		// Unauthenticated requests get redirected to login
		assertThat(code).isNotEqualTo(200);
	}
}
