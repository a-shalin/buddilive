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
public class DataManagementIT extends BaseIT {

	private static TestHelper helper;
	private static OkHttpClient client;
	private static final String EMAIL = "datamgmt-test@example.com";
	private static final String PASSWORD = "TestPassword123!";

	@BeforeAll
	static void setUp() throws Exception {
		helper = new TestHelper(getBaseUrl(), getDbUrl());
		helper.registerUser(EMAIL, PASSWORD, "en_US", "USD");
		client = helper.login(EMAIL, PASSWORD);
	}

	@Test
	@Order(1)
	void testBackup() throws Exception {
		helper.createAccount(client, "Backup Test Account", "D", "Chequing", "100.00");

		byte[] backup = helper.getBackup(client);
		assertThat(backup).isNotNull();
		assertThat(backup.length).isGreaterThan(0);
	}
}
