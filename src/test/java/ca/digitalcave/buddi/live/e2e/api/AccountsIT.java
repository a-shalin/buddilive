package ca.digitalcave.buddi.live.e2e.api;

import okhttp3.OkHttpClient;

import org.json.JSONArray;
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
public class AccountsIT extends BaseIT {

	private static TestHelper helper;
	private static OkHttpClient client;
	private static final String EMAIL = "accounts-test@example.com";
	private static final String PASSWORD = "TestPassword123!";
	private static int createdAccountId;

	@BeforeAll
	static void setUp() throws Exception {
		helper = new TestHelper(getBaseUrl(), getDbUrl());
		helper.registerUser(EMAIL, PASSWORD, "en_US", "USD");
		client = helper.login(EMAIL, PASSWORD);
	}

	@Test
	@Order(1)
	void testCreateDebitAccount() throws Exception {
		createdAccountId = helper.createAccount(client, "Chequing Account", "D", "Chequing", "1000.00");
		assertThat(createdAccountId).isGreaterThan(0);

		JSONObject accounts = helper.getAccounts(client);
		assertThat(accounts.getBoolean("success")).isTrue();
		assertThat(findAccountInTree(accounts, "Chequing Account")).isTrue();
	}

	@Test
	@Order(2)
	void testCreateCreditAccount() throws Exception {
		int id = helper.createAccount(client, "Visa Card", "C", "Credit Card", "0.00");
		assertThat(id).isGreaterThan(0);

		JSONObject accounts = helper.getAccounts(client);
		assertThat(findAccountInTree(accounts, "Visa Card")).isTrue();
	}

	@Test
	@Order(3)
	void testUpdateAccount() throws Exception {
		JSONObject json = new JSONObject();
		json.put("action", "update");
		json.put("id", createdAccountId);
		json.put("name", "Savings Account");
		json.put("type", "D");
		json.put("accountType", "Savings");
		json.put("startBalance", "1000.00");
		json.put("startDate", "2024-01-01");

		helper.postJson(client, "/data/accounts", json);

		JSONObject accounts = helper.getAccounts(client);
		assertThat(findAccountInTree(accounts, "Savings Account")).isTrue();
	}

	@Test
	@Order(4)
	void testDeleteAccount() throws Exception {
		int tempId = helper.createAccount(client, "Temp Account", "D", "Chequing", "0.00");

		JSONObject json = new JSONObject();
		json.put("action", "delete");
		json.put("id", tempId);

		helper.postJson(client, "/data/accounts", json);

		JSONObject accounts = helper.getAccounts(client);
		assertThat(findAccountInTree(accounts, "Temp Account")).isFalse();
	}

	private boolean findAccountInTree(JSONObject tree, String name) {
		JSONArray children = tree.optJSONArray("children");
		if (children == null) return false;
		for (int i = 0; i < children.length(); i++) {
			JSONObject group = children.getJSONObject(i);
			JSONArray groupChildren = group.optJSONArray("children");
			if (groupChildren == null) continue;
			for (int j = 0; j < groupChildren.length(); j++) {
				JSONObject account = groupChildren.getJSONObject(j);
				if (name.equals(account.optString("name"))) {
					return true;
				}
			}
		}
		return false;
	}
}
