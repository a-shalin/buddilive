package ca.digitalcave.buddilive.mobile.data

import android.content.Context
import ca.digitalcave.buddilive.mobile.BuildConfig
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import retrofit2.Retrofit
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
		@Field("password") password: String
	): AuthenticationFlowResponseDto

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
		return createWithTimeoutSeconds(context = context, timeoutSeconds = DEFAULT_TIMEOUT_SECONDS)
	}

	fun createDescriptionsApi(context: Context): BuddiApi {
		return createWithTimeoutSeconds(
			context = context,
			timeoutSeconds = DEFAULT_TIMEOUT_SECONDS * DESCRIPTIONS_TIMEOUT_MULTIPLIER
		)
	}

	private fun createWithTimeoutSeconds(context: Context, timeoutSeconds: Long): BuddiApi {
		val client = OkHttpClient.Builder()
			.cookieJar(PersistentCookieJar(context))
			.connectTimeout(timeoutSeconds, TimeUnit.SECONDS)
			.readTimeout(timeoutSeconds, TimeUnit.SECONDS)
			.writeTimeout(timeoutSeconds, TimeUnit.SECONDS)
			.callTimeout(timeoutSeconds, TimeUnit.SECONDS)
			.build()

		return Retrofit.Builder()
			.baseUrl(BuildConfig.BASE_URL)
			.client(client)
			.addConverterFactory(GsonConverterFactory.create())
			.build()
			.create(BuddiApi::class.java)
	}

	private const val DEFAULT_TIMEOUT_SECONDS = 10L
	private const val DESCRIPTIONS_TIMEOUT_MULTIPLIER = 5L
}
