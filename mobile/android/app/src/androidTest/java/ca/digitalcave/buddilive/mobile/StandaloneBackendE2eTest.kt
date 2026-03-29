package ca.digitalcave.buddilive.mobile

import android.content.Context
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class StandaloneBackendE2eTest {

	@get:Rule
	val composeTestRule = createAndroidComposeRule<MainActivity>()

	private val backend = BackendClient("http://10.0.2.2:8080")

	private lateinit var email: String
	private lateinit var password: String
	private lateinit var primaryAccountName: String
	private lateinit var secondaryAccountName: String
	private lateinit var reserveAccountName: String
	private lateinit var transactionOneDescription: String
	private lateinit var transactionTwoDescription: String

	@Before
	fun setUp() {
		clearMobileCookies()

		val suffix = System.currentTimeMillis()
		email = "android-e2e-$suffix@example.com"
		password = "AndroidE2ePass123!"
		primaryAccountName = "Android E2E Primary $suffix"
		secondaryAccountName = "Android E2E Secondary $suffix"
		reserveAccountName = "Android E2E Reserve $suffix"
		transactionOneDescription = "Android E2E Groceries $suffix"
		transactionTwoDescription = "Android E2E Transfer $suffix"

		backend.registerUser(email, password)
		val apiClient = backend.login(email, password)
		val primaryAccountId = backend.createAccount(apiClient, primaryAccountName, "1500.00")
		val secondaryAccountId = backend.createAccount(apiClient, secondaryAccountName, "500.00")
		val reserveAccountId = backend.createAccount(apiClient, reserveAccountName, "700.00")

		backend.createTransaction(
			client = apiClient,
			description = transactionOneDescription,
			date = "2026-03-20",
			fromId = primaryAccountId,
			toId = secondaryAccountId,
			amount = "25.75"
		)
		backend.createTransaction(
			client = apiClient,
			description = transactionTwoDescription,
			date = "2026-03-21",
			fromId = primaryAccountId,
			toId = reserveAccountId,
			amount = "40.00"
		)
	}

	@Test
	fun loginShowsAccountsAndTransactionsFromStandaloneBackend() {
		waitForLoginFields()

		val fields = composeTestRule.onAllNodes(hasSetTextAction(), useUnmergedTree = true)
		fields[0].performTextInput(email)
		fields[1].performTextInput(password)
		composeTestRule.onNodeWithText("Sign In").performClick()

		assertEventuallyVisible(primaryAccountName)
		assertEventuallyVisible(secondaryAccountName)
		assertEventuallyVisible(reserveAccountName)

		composeTestRule.onNodeWithText(primaryAccountName).performClick()

		assertEventuallyVisible("Transactions: $primaryAccountName")
		assertEventuallyVisible(transactionOneDescription)
		assertEventuallyVisible(transactionTwoDescription)
	}

	private fun waitForLoginFields() {
		composeTestRule.waitUntil(timeoutMillis = 20_000) {
			composeTestRule.onAllNodes(hasSetTextAction(), useUnmergedTree = true)
				.fetchSemanticsNodes().size >= 2
		}
	}

	private fun assertEventuallyVisible(text: String) {
		composeTestRule.waitUntil(timeoutMillis = 20_000) {
			composeTestRule.onAllNodes(hasText(text), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
		}
		assertTrue(
			"Expected text not visible: $text",
			composeTestRule.onAllNodes(hasText(text), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
		)
	}

	private fun clearMobileCookies() {
		val context = ApplicationProvider.getApplicationContext<Context>()
		context.getSharedPreferences("buddilive_mobile_cookies", Context.MODE_PRIVATE)
			.edit()
			.clear()
			.commit()
	}
}

private class BackendClient(private val baseUrl: String) {

	fun registerUser(email: String, password: String) {
		val form = FormBody.Builder()
			.add("email", email)
			.add("password", password)
			.add("locale", "en_US")
			.add("currency", "USD")
			.add("agree", "on")
			.build()

		execute(
			newClient().newCall(
				Request.Builder()
					.url("$baseUrl/authentication/register")
					.post(form)
					.build()
			).execute()
		) { response ->
			assertEquals(200, response.code)
			assertSuccess(response)
		}
	}

	fun login(email: String, password: String): OkHttpClient {
		val client = newClient()
		val form = FormBody.Builder()
			.add("identifier", email)
			.add("password", password)
			.add("disableIpLock", "on")
			.build()

		execute(
			client.newCall(
				Request.Builder()
					.url("$baseUrl/authentication/login")
					.post(form)
					.build()
			).execute()
		) { response ->
			assertEquals(200, response.code)
			assertSuccess(response)
		}
		return client
	}

	fun createAccount(client: OkHttpClient, name: String, startBalance: String): Int {
		val payload = JSONObject()
			.put("action", "insert")
			.put("name", name)
			.put("type", "D")
			.put("accountType", "Chequing")
			.put("startBalance", startBalance)
			.put("startDate", "2026-01-01")

		postJson(client, "/data/accounts", payload)
		return findAccountIdByName(client, name)
	}

	fun createTransaction(
		client: OkHttpClient,
		description: String,
		date: String,
		fromId: Int,
		toId: Int,
		amount: String
	) {
		val split = JSONObject()
			.put("amount", amount)
			.put("fromId", fromId)
			.put("toId", toId)

		val payload = JSONObject()
			.put("action", "insert")
			.put("description", description)
			.put("date", date)
			.put("splits", JSONArray().put(split))

		postJson(client, "/data/transactions", payload)
	}

	private fun findAccountIdByName(client: OkHttpClient, name: String): Int {
		val response = getJson(client, "/data/accounts")
		val found = findAccountId(response, name)
		assertNotNull("Account not found: $name", found)
		return found ?: error("Account not found: $name")
	}

	private fun findAccountId(node: JSONObject, name: String): Int? {
		if (node.optString("nodeType") == "account" && node.optString("name") == name) {
			return node.optInt("id")
		}

		val children = node.optJSONArray("children") ?: return null
		for (index in 0 until children.length()) {
			val child = children.optJSONObject(index) ?: continue
			val found = findAccountId(child, name)
			if (found != null) {
				return found
			}
		}
		return null
	}

	private fun postJson(client: OkHttpClient, path: String, payload: JSONObject): JSONObject {
		val request = Request.Builder()
			.url("$baseUrl$path")
			.post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
			.build()
		return execute(client.newCall(request).execute()) { response ->
			assertEquals(200, response.code)
			assertSuccess(response)
		}
	}

	private fun getJson(client: OkHttpClient, path: String): JSONObject {
		val request = Request.Builder()
			.url("$baseUrl$path")
			.get()
			.build()
		return execute(client.newCall(request).execute()) { response ->
			assertEquals(200, response.code)
			JSONObject(response.body?.string().orEmpty())
		}
	}

	private fun assertSuccess(response: Response): JSONObject {
		val body = JSONObject(response.body?.string().orEmpty())
		assertEquals(true, body.optBoolean("success"))
		return body
	}

	private fun newClient(): OkHttpClient {
		return OkHttpClient.Builder()
			.cookieJar(InMemoryCookieJar())
			.followRedirects(false)
			.connectTimeout(30, TimeUnit.SECONDS)
			.readTimeout(30, TimeUnit.SECONDS)
			.writeTimeout(30, TimeUnit.SECONDS)
			.build()
	}

	private fun <T> execute(response: Response, block: (Response) -> T): T {
		response.use {
			return block(it)
		}
	}

	private class InMemoryCookieJar : CookieJar {

		private val cookies = mutableListOf<Cookie>()

		override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
			for (cookie in cookies) {
				this.cookies.removeAll {
					it.name == cookie.name && it.domain == cookie.domain && it.path == cookie.path
				}
				this.cookies.add(
					Cookie.Builder()
						.name(cookie.name)
						.value(cookie.value)
						.domain(cookie.domain)
						.path(cookie.path)
						.expiresAt(cookie.expiresAt)
						.httpOnly()
						.build()
				)
			}
		}

		override fun loadForRequest(url: HttpUrl): List<Cookie> {
			val now = System.currentTimeMillis()
			return cookies.filter { it.expiresAt >= now }
		}
	}

	companion object {
		private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
	}
}
