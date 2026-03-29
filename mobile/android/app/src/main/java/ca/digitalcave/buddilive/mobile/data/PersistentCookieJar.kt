package ca.digitalcave.buddilive.mobile.data

import android.content.Context
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

class PersistentCookieJar(context: Context) : CookieJar {

	private val sharedPreferences = context.getSharedPreferences("buddilive_mobile_cookies", Context.MODE_PRIVATE)

	override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
		synchronized(this) {
			val allCookies = loadAllCookies(url).associateBy { cookieKey(it) }.toMutableMap()
			for (cookie in cookies) {
				val normalized = normalizeCookie(cookie)
				allCookies[cookieKey(normalized)] = normalized
			}
			persistCookies(allCookies.values.toList())
		}
	}

	override fun loadForRequest(url: HttpUrl): List<Cookie> {
		synchronized(this) {
			val now = System.currentTimeMillis()
			val validCookies = loadAllCookies(url)
				.filter { it.expiresAt >= now }
			persistCookies(validCookies)
			return validCookies.filter { it.matches(url) }
		}
	}

	private fun loadAllCookies(url: HttpUrl): List<Cookie> {
		val persisted = sharedPreferences.getStringSet(KEY_COOKIES, emptySet()) ?: emptySet()
		return persisted.mapNotNull { Cookie.parse(url, it) }
	}

	private fun persistCookies(cookies: List<Cookie>) {
		sharedPreferences.edit()
			.putStringSet(KEY_COOKIES, cookies.map { it.toString() }.toSet())
			.apply()
	}

	private fun cookieKey(cookie: Cookie): String {
		return "${cookie.name}|${cookie.domain}|${cookie.path}"
	}

	private fun normalizeCookie(cookie: Cookie): Cookie {
		val builder = Cookie.Builder()
			.name(cookie.name)
			.value(cookie.value)
			.path(cookie.path)
			.expiresAt(cookie.expiresAt)

		if (cookie.hostOnly) {
			builder.hostOnlyDomain(cookie.domain)
		}
		else {
			builder.domain(cookie.domain)
		}

		if (cookie.httpOnly) {
			builder.httpOnly()
		}

		return builder.build()
	}

	companion object {
		private const val KEY_COOKIES = "cookies"
	}
}
