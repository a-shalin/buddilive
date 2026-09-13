package ca.digitalcave.buddilive.mobile

import android.content.Context
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import ca.digitalcave.buddilive.mobile.data.*
import ca.digitalcave.buddilive.mobile.ui.BuddiMobileApp
import ca.digitalcave.buddilive.mobile.ui.theme.AppTheme
import com.google.gson.Gson
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import retrofit2.Response
import java.math.BigDecimal
import java.util.UUID

class OfflineStartupTest {

	@get:Rule
	val compose = createComposeRule()

	@Test
	fun cachedScreensAndLocalCreationWorkWhileRefreshIsSuspended() = runBlocking {
		val context = ApplicationProvider.getApplicationContext<Context>()
		val databaseName = "offline-test-${UUID.randomUUID()}.db"
		val api = OfflineTestApi()
		val root = AndroidOfflineStore(context, databaseName)
		val seed = BuddiRepository(api, offlineStore = root, autoSyncPendingTransactions = false, backendIdentity = databaseName)
		seed.login("offline-test", "password", true)
		seed.synchronize().getOrThrow()
		root.close()
		context.getSharedPreferences("buddilive_mobile_auth", Context.MODE_PRIVATE).edit()
			.putBoolean("stay_logged_in", true).commit()
		api.suspendReads = true
		val repository = BuddiRepository(api, offlineStore = AndroidOfflineStore(context, databaseName), backendIdentity = databaseName)
		val networkMonitor = NetworkMonitor(context)
		try {
			compose.setContent {
				AppTheme { BuddiMobileApp(repository, networkMonitor) }
			}
			withTimeout(5_000) { api.readStarted.await() }
			compose.waitUntil(5_000) { compose.onAllNodesWithText("Assets").fetchSemanticsNodes().isNotEmpty() }
			compose.onNodeWithContentDescription("Refresh").assertIsEnabled()
			compose.onNodeWithText("Assets").performClick()
			compose.onNodeWithText("Checking").performClick()
			compose.waitUntil(5_000) { compose.onAllNodesWithText("Cached transaction").fetchSemanticsNodes().isNotEmpty() }
			compose.onNodeWithContentDescription("New").assertIsEnabled().performClick()
			compose.onNodeWithTag("transactionEditorDescription").performTextReplacement("Offline coffee")
			compose.onNodeWithTag("transactionEditorAmount").performTextReplacement("25.00")
			compose.onNodeWithTag("transactionEditorTo").performClick()
			compose.onNodeWithText("Groceries").performClick()
			compose.onNodeWithTag("transactionEditorSave").assertIsEnabled().performClick()
			compose.waitUntil(5_000) { compose.onAllNodesWithText("Offline coffee").fetchSemanticsNodes().isNotEmpty() }
			assertEquals(BigDecimal("75.00"), repository.readCachedAccountData(1).account?.balanceNumber)
			assertEquals(0, api.mutationCount)
			assertTrue(repository.syncState.value.isSyncing)
			val restarted = BuddiRepository(api, offlineStore = AndroidOfflineStore(context, databaseName), backendIdentity = databaseName)
			assertTrue(restarted.restoreLocalSession())
			assertEquals("Offline coffee", restarted.fetchPendingTransactions(1).single().description)
		} finally {
			repository.setForeground(false)
			api.releaseReads.complete(Unit)
			context.getSharedPreferences("buddilive_mobile_auth", Context.MODE_PRIVATE).edit().clear().commit()
		}
	}

	@Test
	fun versionOneDatabaseUpgradePreservesCacheAndOutbox() {
		val context = ApplicationProvider.getApplicationContext<Context>()
		val databaseName = "offline-migration-${UUID.randomUUID()}.db"
		context.openOrCreateDatabase(databaseName, Context.MODE_PRIVATE, null).use { db ->
			db.execSQL("create table offline_cache (cache_key text primary key, cache_value text, updated_at integer)")
			db.execSQL("""
				create table pending_transactions (
					local_uuid text primary key, status text, date_iso text, description text, number text,
					amount text, from_id integer, to_id integer, memo text, created_at integer,
					modified_at integer, last_error text, attempt_count integer
				)
			""".trimIndent())
			db.execSQL("insert into offline_cache values ('accounts', '{}', 1)")
			db.execSQL("insert into pending_transactions values ('legacy', 'PENDING', '2026-09-13', 'Coffee', '', '25', 1, 2, '', 1, 1, null, 0)")
			db.version = 1
		}
		AndroidOfflineStore(context, databaseName).use { store ->
			assertEquals("{}", store.loadCache("accounts"))
			val pending = store.listPendingTransactions().single()
			assertEquals("legacy", pending.localUuid)
			assertEquals(PendingSyncState(), pending.sync)
			assertEquals(2, store.readableDatabase.version)
		}
		context.deleteDatabase(databaseName)
	}
}

private class OfflineTestApi : BuddiApi {
	@Volatile
	var suspendReads = false
	val readStarted = CompletableDeferred<Unit>()
	val releaseReads = CompletableDeferred<Unit>()
	var mutationCount = 0
	private val gson = Gson()

	private suspend fun awaitBackend() {
		if (suspendReads) {
			readStarted.complete(Unit)
			releaseReads.await()
		}
	}

	override suspend fun login(identifier: String, password: String, remember: String?): AuthenticationFlowResponseDto =
		AuthenticationFlowResponseDto(true, null)

	override suspend fun logout(): Response<Void> = Response.success(null)

	override suspend fun getAccounts(): AccountsResponseDto {
		awaitBackend()
		return gson.fromJson("""{"success":true,"children":[{"nodeType":"type","name":"Assets","balance":"USD 100.00","balanceNumber":100,"children":[{"nodeType":"account","id":1,"name":"Checking","balance":"USD 100.00","balanceNumber":100,"debit":true}]}]}""", AccountsResponseDto::class.java)
	}

	override suspend fun getTransactions(sourceId: Long, start: Int, limit: Int, search: String?): TransactionsResponseDto {
		awaitBackend()
		return gson.fromJson("""{"success":true,"total":1,"data":[{"id":1,"uuid":"cached","date":"2026-09-12","dateIso":"2026-09-12","description":"Cached transaction","number":"","splits":[]}]}""", TransactionsResponseDto::class.java)
	}

	override suspend fun getTransactionDescriptions(): TransactionDescriptionsResponseDto {
		awaitBackend()
		return TransactionDescriptionsResponseDto(true, emptyList())
	}

	override suspend fun mutateTransaction(request: TransactionMutationRequestDto): SuccessResponseDto {
		mutationCount++
		return SuccessResponseDto(true, 2, request.uuid)
	}

	override suspend fun getUserPreferences(): UserPreferencesResponseDto {
		awaitBackend()
		return gson.fromJson("""{"success":true,"locale":"en-US","currency":"USD","dateFormat":"yyyy-MM-dd","currencyAfter":false,"decimalSeparator":".","thousandSeparator":",","negativeFormat":"N","showCurrencySymbol":false,"currencySpacing":true}""", UserPreferencesResponseDto::class.java)
	}

	override suspend fun getSources(direction: String): SourcesResponseDto {
		awaitBackend()
		return gson.fromJson("""{"success":true,"data":[{"value":1,"text":"Checking","type":"D"},{"value":2,"text":"Groceries","type":"E"}]}""", SourcesResponseDto::class.java)
	}
}
