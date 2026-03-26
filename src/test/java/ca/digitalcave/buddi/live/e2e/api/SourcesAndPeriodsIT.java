package ca.digitalcave.buddi.live.e2e.api;

import okhttp3.OkHttpClient;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import ca.digitalcave.buddi.live.e2e.BaseIT;
import ca.digitalcave.buddi.live.e2e.TestHelper;

import static org.assertj.core.api.Assertions.assertThat;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class SourcesAndPeriodsIT extends BaseIT {

	private static TestHelper helper;
	private static OkHttpClient client;
	private static final String EMAIL = "sources-periods-test@example.com";
	private static final String PASSWORD = "TestPassword123!";
	private static int accountId;
	private static int incomeCategoryId;
	private static int expenseCategoryId;

	@BeforeAll
	static void setUp() throws Exception {
		helper = new TestHelper(getBaseUrl(), getDbUrl());
		helper.registerUser(EMAIL, PASSWORD, "en_US", "USD");
		client = helper.login(EMAIL, PASSWORD);
		accountId = helper.createAccount(client, "Sources Account", "D", "Chequing", "100.00");
		incomeCategoryId = helper.createCategory(client, "Sources Salary", "I", "MONTH");
		expenseCategoryId = helper.createCategory(client, "Sources Groceries", "E", "YEAR");
	}

	@Test
	@Order(1)
	void testGetPeriodsIncludesCreatedPeriodTypes() throws Exception {
		final JSONObject periods = helper.getJson(client, "/data/categories/periods");
		assertThat(periods.getBoolean("success")).isTrue();

		final JSONArray data = periods.getJSONArray("data");
		assertThat(hasPeriod(data, "MONTH")).isTrue();
		assertThat(hasPeriod(data, "YEAR")).isTrue();
		assertThat(findPeriodText(data, "MONTH")).isNotBlank();
		assertThat(findPeriodText(data, "YEAR")).isNotBlank();
	}

	@Test
	@Order(2)
	void testGetSourcesFromAndToFilterCategoryTypes() throws Exception {
		final JSONObject from = helper.getJson(client, "/data/sources/from");
		assertThat(from.getBoolean("success")).isTrue();
		final JSONArray fromData = from.getJSONArray("data");

		assertThat(hasTextEntry(fromData, "--- Accounts ---")).isTrue();
		assertThat(hasTextEntry(fromData, "--- Budget Categories ---")).isTrue();
		assertThat(findByValue(fromData, accountId)).isNotNull();
		assertThat(findByValue(fromData, incomeCategoryId)).isNotNull();
		assertThat(findByValue(fromData, expenseCategoryId)).isNull();

		final JSONObject to = helper.getJson(client, "/data/sources/to");
		assertThat(to.getBoolean("success")).isTrue();
		final JSONArray toData = to.getJSONArray("data");

		assertThat(hasTextEntry(toData, "--- Accounts ---")).isTrue();
		assertThat(hasTextEntry(toData, "--- Budget Categories ---")).isTrue();
		assertThat(findByValue(toData, accountId)).isNotNull();
		assertThat(findByValue(toData, incomeCategoryId)).isNull();
		assertThat(findByValue(toData, expenseCategoryId)).isNotNull();
	}

	@Test
	@Order(3)
	void testGetParentsContainsTopLevelAndCategories() throws Exception {
		final JSONObject parents = helper.getJson(client, "/data/categories/parents");
		assertThat(parents.getBoolean("success")).isTrue();
		final JSONArray data = parents.getJSONArray("data");

		assertThat(hasTextEntry(data, "Top Level")).isTrue();
		assertThat(findByValue(data, incomeCategoryId)).isNotNull();
		assertThat(findByValue(data, expenseCategoryId)).isNotNull();
	}

	private boolean hasPeriod(final JSONArray data, final String value) {
		for (int i = 0; i < data.length(); i++) {
			final JSONObject item = data.getJSONObject(i);
			if (value.equals(item.optString("value"))) {
				return true;
			}
		}
		return false;
	}

	private String findPeriodText(final JSONArray data, final String value) {
		for (int i = 0; i < data.length(); i++) {
			final JSONObject item = data.getJSONObject(i);
			if (value.equals(item.optString("value"))) {
				return item.optString("text");
			}
		}
		return "";
	}

	private JSONObject findByValue(final JSONArray data, final int value) {
		for (int i = 0; i < data.length(); i++) {
			final JSONObject item = data.getJSONObject(i);
			final Object itemValue = item.opt("value");
			if (itemValue instanceof Number && ((Number) itemValue).intValue() == value) {
				return item;
			}
			if (itemValue != null && String.valueOf(value).equals(String.valueOf(itemValue))) {
				return item;
			}
		}
		return null;
	}

	private boolean hasTextEntry(final JSONArray data, final String text) {
		for (int i = 0; i < data.length(); i++) {
			final JSONObject item = data.getJSONObject(i);
			if (text.equals(item.optString("text"))) {
				return true;
			}
		}
		return false;
	}
}
