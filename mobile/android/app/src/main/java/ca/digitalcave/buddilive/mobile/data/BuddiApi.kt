package ca.digitalcave.buddilive.mobile.data

import android.content.Context
import ca.digitalcave.buddilive.mobile.BuildConfig
import okhttp3.OkHttpClient
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

	@POST("data/transactions")
	suspend fun mutateTransaction(@Body request: TransactionMutationRequestDto): SuccessResponseDto

	@GET("data/sources/{direction}")
	suspend fun getSources(@Path("direction") direction: String): SourcesResponseDto
}

object BuddiApiFactory {

	fun create(context: Context): BuddiApi {
		val client = OkHttpClient.Builder()
			.cookieJar(PersistentCookieJar(context))
			.build()

		return Retrofit.Builder()
			.baseUrl(BuildConfig.BASE_URL)
			.client(client)
			.addConverterFactory(GsonConverterFactory.create())
			.build()
			.create(BuddiApi::class.java)
	}
}

