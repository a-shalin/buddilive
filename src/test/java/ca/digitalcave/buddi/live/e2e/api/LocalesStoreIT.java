package ca.digitalcave.buddi.live.e2e.api;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import okhttp3.OkHttpClient;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import ca.digitalcave.buddi.live.e2e.BaseIT;
import ca.digitalcave.buddi.live.e2e.TestHelper;

import static org.assertj.core.api.Assertions.assertThat;

public class LocalesStoreIT extends BaseIT {

	private static TestHelper helper;
	private static OkHttpClient client;

	@BeforeAll
	static void setUp() {
		helper = new TestHelper(getBaseUrl(), getDbUrl());
		client = helper.newClient();
	}

	@Test
	void testLocalesStoreContainsOnlyConfiguredI18nLocales() throws Exception {
		JSONObject response = helper.getJson(client, "/stores/locales");
		assertThat(response.getBoolean("success")).isTrue();

		JSONArray data = response.getJSONArray("data");
		Set<String> returnedValues = extractLocaleValues(data);
		Set<String> supportedLocales = Set.copyOf(findConfiguredI18nLocales());

		assertThat(returnedValues)
			.as("Locales in /stores/locales should exactly match supported i18n bundle locales")
			.containsExactlyInAnyOrderElementsOf(supportedLocales);
	}

	@Test
	void testCurrenciesStoreContainsCommonCurrencies() throws Exception {
		JSONObject response = helper.getJson(client, "/stores/currencies");
		assertThat(response.getBoolean("success")).isTrue();

		JSONArray data = response.getJSONArray("data");
		Set<String> returnedValues = extractValues(data);
		assertThat(returnedValues).contains("CAD", "USD", "EUR", "GBP", "AUD");
	}

	private Set<String> extractLocaleValues(JSONArray data) {
		return extractValues(data);
	}

	private Set<String> extractValues(JSONArray data) {
		return data.toList().stream()
			.filter(item -> item instanceof java.util.Map<?, ?>)
			.map(item -> (java.util.Map<?, ?>) item)
			.map(item -> item.get("value"))
			.filter(String.class::isInstance)
			.map(String.class::cast)
			.filter(value -> !value.isBlank())
			.collect(Collectors.toSet());
	}

	private List<String> findConfiguredI18nLocales() throws IOException {
		try (var paths = Files.list(Path.of("src/main/resources"))) {
			return paths
				.map(path -> path.getFileName().toString())
				.filter(name -> name.startsWith("i18n_") && name.endsWith(".properties"))
				.map(name -> name.substring("i18n_".length(), name.length() - ".properties".length()))
				.sorted()
				.toList();
		}
	}
}
