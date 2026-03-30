package ca.digitalcave.buddi.live.e2e.api;

import java.net.URI;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import ca.digitalcave.buddi.live.e2e.BaseIT;
import ca.digitalcave.buddi.live.e2e.TestHelper;

import static org.assertj.core.api.Assertions.assertThat;

public class StaticResourceCachingIT extends BaseIT {

	private static final Pattern ASSET_VERSION_PATTERN = Pattern.compile("window\\.__assetVersion\\s*=\\s*\"?(\\d+)\"?");

	private static TestHelper helper;

	@BeforeAll
	static void setUp() {
		helper = new TestHelper(getBaseUrl(), getDbUrl());
	}

	@Test
	void testIndexExposesVersionedAssetUrlsAndNoCacheHtml() throws Exception {
		final OkHttpClient client = helper.newClient();
		final Request request = new Request.Builder()
			.url(getBaseUrl() + "/")
			.get()
			.build();

		try (Response response = client.newCall(request).execute()) {
			assertThat(response.code()).isEqualTo(200);

			final String cacheControl = response.header("Cache-Control", "");
			assertThat(cacheControl).contains("no-cache");
			assertThat(cacheControl).contains("no-store");

			final String body = response.body().string();
			final String assetVersion = extractAssetVersion(body);

			assertThat(body).doesNotContain("Build Date: <span>N/A</span>");
			assertThat(body).doesNotContain("Version: <a href='doc/changelog.html' target='_blank'>N/A</a>");

			assertThat(body).contains("/lib/extjs/ext-all-debug.js?v=" + assetVersion);
			assertThat(body).contains("/lib/extjs/charts.js?v=" + assetVersion);
			assertThat(body).contains("/css/buddilive.css?v=" + assetVersion);
			assertThat(body).contains("/authentication/app/Application.js?v=" + assetVersion);
			assertThat(body).contains("/mobile/buddilive-android.apk?v=" + assetVersion);
		}
	}

	@Test
	void testStaticAssetsHaveLongLivedCacheHeaders() throws Exception {
		final OkHttpClient client = helper.newClient();
		final String assetVersion = fetchIndexAssetVersion(client);

		assertStaticAssetCacheHeaders(client, "/lib/extjs/ext-all-debug.js?v=" + assetVersion);
		assertStaticAssetCacheHeaders(client, "/css/buddilive.css?v=" + assetVersion);
	}

	@Test
	void testLegacyEntryUrlsRedirectToRoot() throws Exception {
		final OkHttpClient client = helper.newClient().newBuilder().followRedirects(false).build();

		assertRedirectToRoot(client, "/index");
		assertRedirectToRoot(client, "/index.html");
		assertRedirectToRoot(client, "/buddilive");
	}

	private String fetchIndexAssetVersion(final OkHttpClient client) throws Exception {
		final Request request = new Request.Builder()
			.url(getBaseUrl() + "/")
			.get()
			.build();

		try (Response response = client.newCall(request).execute()) {
			assertThat(response.code()).isEqualTo(200);
			return extractAssetVersion(response.body().string());
		}
	}

	private void assertRedirectToRoot(final OkHttpClient client, final String path) throws Exception {
		final Request request = new Request.Builder()
			.url(getBaseUrl() + path)
			.get()
			.build();

		try (Response response = client.newCall(request).execute()) {
			assertThat(response.code()).isEqualTo(302);
			final String location = response.header("Location", "");
			assertThat(location).isNotBlank();
			assertThat(URI.create(location).getPath()).isEqualTo("/");
		}
	}

	private void assertStaticAssetCacheHeaders(final OkHttpClient client, final String path) throws Exception {
		final Request request = new Request.Builder()
			.url(getBaseUrl() + path)
			.get()
			.build();

		try (Response response = client.newCall(request).execute()) {
			assertThat(response.code()).isEqualTo(200);
			final String cacheControl = response.header("Cache-Control", "");
			assertThat(cacheControl).contains("public");
			assertThat(cacheControl).contains("max-age=31536000");
		}
	}

	private String extractAssetVersion(final String body) {
		final Matcher matcher = ASSET_VERSION_PATTERN.matcher(body);
		assertThat(matcher.find()).as("Index should include window.__assetVersion").isTrue();

		final String assetVersion = matcher.group(1);
		assertThat(assetVersion).matches("\\d+");
		return assetVersion;
	}
}
