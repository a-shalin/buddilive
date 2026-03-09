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
public class CategoriesIT extends BaseIT {

	private static TestHelper helper;
	private static OkHttpClient client;
	private static final String EMAIL = "categories-test@example.com";
	private static final String PASSWORD = "TestPassword123!";
	private static int incomeCategoryId;
	private static int expenseCategoryId;

	@BeforeAll
	static void setUp() throws Exception {
		helper = new TestHelper(getBaseUrl(), getDbUrl());
		helper.registerUser(EMAIL, PASSWORD, "en_US", "USD");
		client = helper.login(EMAIL, PASSWORD);
	}

	@Test
	@Order(1)
	void testCreateIncomeCategory() throws Exception {
		incomeCategoryId = helper.createCategory(client, "Salary", "I", "MONTH");
		assertThat(incomeCategoryId).isGreaterThan(0);

		JSONObject categories = helper.getCategories(client, "MONTH");
		assertThat(categories.getBoolean("success")).isTrue();
		assertThat(findCategoryInTree(categories, "Salary")).isTrue();
	}

	@Test
	@Order(2)
	void testCreateExpenseCategory() throws Exception {
		expenseCategoryId = helper.createCategory(client, "Groceries", "E", "MONTH");
		assertThat(expenseCategoryId).isGreaterThan(0);

		JSONObject categories = helper.getCategories(client, "MONTH");
		assertThat(findCategoryInTree(categories, "Groceries")).isTrue();
	}

	@Test
	@Order(3)
	void testCreateChildCategory() throws Exception {
		int childId = helper.createCategory(client, "Fruit", "E", "MONTH", expenseCategoryId);
		assertThat(childId).isGreaterThan(0);

		JSONObject categories = helper.getCategories(client, "MONTH");
		assertThat(findCategoryInTree(categories, "Fruit")).isTrue();
	}

	@Test
	@Order(4)
	void testSetBudgetEntry() throws Exception {
		JSONObject json = new JSONObject();
		json.put("action", "set");
		json.put("categoryId", expenseCategoryId);
		json.put("amount", "500.00");
		json.put("date", "2024-03-01");
		json.put("periodType", "MONTH");
		json.put("offset", 0);

		helper.postJson(client, "/data/categories", json);

		JSONObject categories = helper.getCategories(client, "MONTH");
		assertThat(categories.getBoolean("success")).isTrue();
	}

	private boolean findCategoryInTree(JSONObject tree, String name) {
		JSONArray children = tree.optJSONArray("children");
		if (children == null) return false;
		for (int i = 0; i < children.length(); i++) {
			JSONObject item = children.getJSONObject(i);
			if (name.equals(item.optString("name"))) return true;
			if (findCategoryInTree(item, name)) return true;
		}
		return false;
	}
}
