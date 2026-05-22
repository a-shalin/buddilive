package ca.digitalcave.buddilive.mobile.data

import android.content.Context
import ca.digitalcave.buddilive.mobile.BuildConfig
import okhttp3.CookieJar
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import retrofit2.Retrofit
import retrofit2.Response
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query
import retrofit2.http.Path

interface BuddiApi {

	@FormUrlEncoded
	@POST("authentication/login")
	suspend fun login(
		@Field("identifier") identifier: String,
		@Field("password") password: String,
		@Field("remember") remember: String? = null
	): AuthenticationFlowResponseDto

	@GET("authentication/logout")
	suspend fun logout(): Response<Void>

	@GET("data/accounts")
	suspend fun getAccounts(): AccountsResponseDto

	@GET("data/transactions")
	suspend fun getTransactions(
		@Query("source") sourceId: Long,
		@Query("start") start: Int,
		@Query("limit") limit: Int,
		@Query("search") search: String? = null
	): TransactionsResponseDto

	@GET("data/transactions/descriptions.json")
	suspend fun getTransactionDescriptions(): TransactionDescriptionsResponseDto

	@POST("data/transactions")
	suspend fun mutateTransaction(@Body request: TransactionMutationRequestDto): SuccessResponseDto

	@GET("data/userpreferences")
	suspend fun getUserPreferences(): UserPreferencesResponseDto

	@GET("data/sources/{direction}")
	suspend fun getSources(@Path("direction") direction: String): SourcesResponseDto
}

object BuddiApiFactory {

	fun create(context: Context): BuddiApi {
		return createWithTimeoutSeconds(
			baseUrl = BuildConfig.BASE_URL,
			cookieJar = PersistentCookieJar(context),
			timeoutSeconds = DEFAULT_TIMEOUT_SECONDS
		)
	}

	fun createDescriptionsApi(context: Context): BuddiApi {
		return createWithTimeoutSeconds(
			baseUrl = BuildConfig.BASE_URL,
			cookieJar = PersistentCookieJar(context),
			timeoutSeconds = DESCRIPTIONS_TIMEOUT_SECONDS
		)
	}

	internal fun createForTest(baseUrl: String): BuddiApi {
		return createWithTimeoutSeconds(
			baseUrl = baseUrl,
			cookieJar = CookieJar.NO_COOKIES,
			timeoutSeconds = DEFAULT_TIMEOUT_SECONDS
		)
	}

	private fun createWithTimeoutSeconds(baseUrl: String, cookieJar: CookieJar, timeoutSeconds: Long): BuddiApi {
		val client = OkHttpClient.Builder()
			.cookieJar(cookieJar)
			.connectTimeout(timeoutSeconds, TimeUnit.SECONDS)
			.readTimeout(timeoutSeconds, TimeUnit.SECONDS)
			.writeTimeout(timeoutSeconds, TimeUnit.SECONDS)
			.callTimeout(timeoutSeconds, TimeUnit.SECONDS)
			.build()

		return Retrofit.Builder()
			.baseUrl(baseUrl)
			.client(client)
			.addConverterFactory(GsonConverterFactory.create())
			.build()
			.create(BuddiApi::class.java)
	}

	private const val DEFAULT_TIMEOUT_SECONDS = 60L
	private const val DESCRIPTIONS_TIMEOUT_SECONDS = 120L
}
