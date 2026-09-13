package ca.digitalcave.buddilive.mobile.data

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response
import retrofit2.HttpException
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.IOException
import java.math.BigDecimal

class BuddiRepositoryTest {

	@Test
	fun changingBackendDoesNotRestoreAnotherBackendsSession() = runBlocking {
		val users = mutableMapOf<String, OfflineStore>()
		val root = object : OfflineStore by InMemoryOfflineStore() {
			override fun forOwner(owner: String): OfflineStore = users.getOrPut(owner) { InMemoryOfflineStore() }
		}
		val api = LedgerApi()
		val first = BuddiRepository(api, offlineStore = root, backendIdentity = "first", autoSyncPendingTransactions = false)
		first.login("user", "password", true)
		first.synchronize().getOrThrow()
		first.createTransaction(testInput()).getOrThrow()
		val second = BuddiRepository(api, offlineStore = root, backendIdentity = "second", autoSyncPendingTransactions = false)
		assertFalse(second.restoreLocalSession())
	}

	@Test
	fun editorOpenedBeforeUploadCanSaveUsingTheResolvedServerId() = runBlocking {
		val api = LedgerApi()
		val repository = BuddiRepository(api, offlineStore = InMemoryOfflineStore(), autoSyncPendingTransactions = false)
		repository.synchronize().getOrThrow()
		repository.createTransaction(testInput()).getOrThrow()
		val editorId = repository.fetchPendingTransactions(1).single().id
		repository.synchronize().getOrThrow()
		repository.updateTransaction(editorId, testInput().copy(amount = "30.00")).getOrThrow()
		assertEquals(api.transactions.keys.single(), api.requests.last().id)
		repository.synchronize().getOrThrow()
		assertEquals(BigDecimal("70.00"), repository.loadCachedAccount(1)?.balanceNumber)
	}

	@Test
	fun partialUploadFailurePreservesBothTransactionsAndDoesNotDoubleCountOnRetry() = runBlocking {
		val api = LedgerApi()
		val store = InMemoryOfflineStore()
		val repository = BuddiRepository(api, offlineStore = store, autoSyncPendingTransactions = false)
		repository.synchronize().getOrThrow()
		repository.createTransaction(testInput()).getOrThrow()
		repository.createTransaction(testInput().copy(description = "Lunch")).getOrThrow()
		api.afterMutation = { if (api.requests.size == 2) throw IOException("Response lost") }
		assertTrue(repository.synchronize().isFailure)
		assertEquals(BigDecimal("50.00"), repository.loadCachedAccount(1)?.balanceNumber)
		assertEquals(2, repository.readCachedAccountData(1).page?.items?.size)
		api.afterMutation = { }
		repository.synchronize().getOrThrow()
		assertEquals(3, api.requests.size)
		assertEquals(2, api.transactions.size)
		assertTrue(store.listPendingTransactions().isEmpty())
		assertEquals(BigDecimal("50.00"), repository.loadCachedAccount(1)?.balanceNumber)
	}

	@Test
	fun foregroundRefreshRequestsCoalesceAndStopWhenBackgrounded(): Unit = runBlocking {
		val firstRead = CompletableDeferred<Unit>()
		val secondRead = CompletableDeferred<Unit>()
		val releaseFirst = CompletableDeferred<Unit>()
		val cancelled = CompletableDeferred<Unit>()
		var requests = 0
		val api = object : BuddiApi by FakeBuddiApi() {
			override suspend fun getAccounts(): AccountsResponseDto {
				requests++
				if (requests == 1) {
					firstRead.complete(Unit)
					releaseFirst.await()
				} else {
					secondRead.complete(Unit)
					try { CompletableDeferred<Unit>().await() } finally { cancelled.complete(Unit) }
				}
				return accountsResponse()
			}
		}
		val repository = BuddiRepository(api, offlineStore = InMemoryOfflineStore())
		try {
			repository.setForeground(true)
			withTimeout(2_000) { firstRead.await() }
			repeat(20) { repository.requestSync() }
			releaseFirst.complete(Unit)
			withTimeout(2_000) { secondRead.await() }
			assertEquals(2, requests)
			repository.setForeground(false)
			withTimeout(2_000) { cancelled.await(); repository.syncState.first { !it.isSyncing } }
		} finally {
			repository.setForeground(false)
		}
	}

	@Test
	fun legacyOutboxRequiresExplicitOwnershipConfirmationAndCanOnlyBeImportedOnce() = runBlocking {
		val users = mutableMapOf<String, OfflineStore>()
		val root = object : OfflineStore by InMemoryOfflineStore() {
			override fun forOwner(owner: String): OfflineStore = users.getOrPut(owner) { InMemoryOfflineStore() }
		}
		val api = LedgerApi()
		val legacy = BuddiRepository(api, offlineStore = root, autoSyncPendingTransactions = false)
		legacy.synchronize().getOrThrow()
		legacy.createTransaction(testInput()).getOrThrow()
		assertTrue(legacy.restoreLocalSession())
		assertTrue(legacy.syncState.value.needsLogin)
		legacy.login("owner", "password", true)
		legacy.synchronize().getOrThrow()
		assertTrue(api.requests.isEmpty())
		assertEquals(1, legacy.legacyPendingCount())
		legacy.restoreLegacyTransactions().getOrThrow()
		legacy.restoreLegacyTransactions().getOrThrow()
		assertEquals(1, legacy.fetchPendingTransactions(1).size)
		legacy.synchronize().getOrThrow()
		assertEquals(1, api.requests.size)
		assertEquals(0, legacy.legacyPendingCount())
		legacy.login("different-owner", "password", true)
		assertEquals(0, legacy.legacyPendingCount())
		assertTrue(legacy.fetchPendingTransactions(1).isEmpty())
	}

	@Test
	fun loginReturnsBeforePreparationAndSyncCachesEveryAccount() = runBlocking {
		val api = LedgerApi()
		val repository = BuddiRepository(api, offlineStore = InMemoryOfflineStore(), autoSyncPendingTransactions = false)
		assertTrue(repository.login("user", "password", true) is LoginResult.Success)
		assertEquals(0, api.accountRequests)
		repository.synchronize().getOrThrow()
		assertEquals(setOf(1L, 2L), api.transactionSources.toSet())
		assertTrue(repository.syncState.value.isPrepared)
		for (id in listOf(1L, 2L)) {
			val data = repository.readCachedAccountData(id)
			assertEquals(0, data.page?.total)
			assertEquals(2, data.fromSources.size)
			assertEquals(2, data.toSources.size)
		}
	}

	@Test
	fun lostUploadResponseRetainsBalanceAndRetriesOriginalUuidBeforeConcurrentEdit() = runBlocking {
		val api = LedgerApi()
		val store = InMemoryOfflineStore()
		val repository = BuddiRepository(api, offlineStore = store, autoSyncPendingTransactions = false)
		repository.synchronize().getOrThrow()
		repository.createTransaction(testInput()).getOrThrow()
		api.afterMutation = { throw IOException("Response lost") }
		assertTrue(repository.synchronize().isFailure)
		assertEquals(BigDecimal("75.00"), repository.loadCachedAccount(1)?.balanceNumber)
		val localId = repository.fetchPendingTransactions(1).single().id
		repository.updateTransaction(localId, testInput().copy(amount = "30.00")).getOrThrow()
		api.afterMutation = { }
		repository.synchronize().getOrThrow()
		assertEquals(BigDecimal("70.00"), repository.loadCachedAccount(1)?.balanceNumber)
		repository.synchronize().getOrThrow()
		assertEquals(listOf("insert", "insert", "update"), api.requests.map { it.action })
		assertEquals(api.requests[0].uuid, api.requests[1].uuid)
		assertEquals("25.00", api.requests[1].splits?.single()?.amount)
		assertEquals(1, api.transactions.size)
		assertTrue(store.listPendingTransactions().isEmpty())
		assertEquals(BigDecimal("70.00"), repository.loadCachedAccount(1)?.balanceNumber)
		assertEquals(1, repository.readCachedAccountData(1).page?.items?.size)
	}

	@Test
	fun deletingWhileUploadIsInFlightQueuesDeletionWithoutLosingIt() = runBlocking {
		val api = LedgerApi()
		val store = InMemoryOfflineStore()
		val repository = BuddiRepository(api, offlineStore = store, autoSyncPendingTransactions = false)
		repository.synchronize().getOrThrow()
		repository.createTransaction(testInput()).getOrThrow()
		val entered = CompletableDeferred<Unit>()
		val release = CompletableDeferred<Unit>()
		api.afterMutation = { entered.complete(Unit); release.await() }
		val upload = async { repository.synchronize() }
		withTimeout(2_000) { entered.await() }
		val localId = repository.fetchPendingTransactions(1).single().id
		withTimeout(500) { repository.deleteTransaction(localId).getOrThrow() }
		assertTrue(repository.readCachedAccountData(1).page!!.items.isEmpty())
		assertEquals(BigDecimal("100.00"), repository.loadCachedAccount(1)?.balanceNumber)
		release.complete(Unit)
		upload.await().getOrThrow()
		api.afterMutation = { }
		repository.synchronize().getOrThrow()
		assertEquals(listOf("insert", "delete"), api.requests.map { it.action })
		assertTrue(store.listPendingTransactions().isEmpty())
		assertTrue(api.transactions.isEmpty())
		assertEquals(BigDecimal("100.00"), repository.loadCachedAccount(1)?.balanceNumber)
	}

	@Test
	fun failedRefreshAndExpiredSessionKeepLocalDataAndChanges() = runBlocking {
		val api = LedgerApi()
		val repository = BuddiRepository(api, offlineStore = InMemoryOfflineStore(), autoSyncPendingTransactions = false)
		repository.synchronize().getOrThrow()
		api.accountsError = HttpException(Response.error<Any>(503, "Unavailable".toResponseBody()))
		assertTrue(repository.synchronize().isFailure)
		assertTrue(repository.hasValidSession())
		assertFalse(repository.syncState.value.isSyncing)
		api.accountsError = HttpException(Response.error<Any>(401, "Expired".toResponseBody()))
		assertTrue(repository.synchronize().isFailure)
		assertTrue(repository.syncState.value.needsLogin)
		repository.createTransaction(testInput()).getOrThrow()
		assertEquals(BigDecimal("75.00"), repository.loadCachedAccount(1)?.balanceNumber)
		assertTrue(repository.synchronize().isFailure)
		assertTrue(api.requests.isEmpty())
		api.accountsError = null
		repository.login("user", "password", true)
		repository.synchronize().getOrThrow()
		assertFalse(repository.syncState.value.needsLogin)
		assertEquals(1, api.transactions.size)
	}

	@Test
	fun logoutAndUserSwitchKeepOutboxesIsolated() = runBlocking {
		val users = mutableMapOf<String, OfflineStore>()
		val root = object : OfflineStore by InMemoryOfflineStore() {
			override fun forOwner(owner: String): OfflineStore = users.getOrPut(owner) { InMemoryOfflineStore() }
		}
		val api = LedgerApi()
		val repository = BuddiRepository(api, offlineStore = root, autoSyncPendingTransactions = false)
		repository.login("first", "password", true)
		repository.synchronize().getOrThrow()
		repository.createTransaction(testInput()).getOrThrow()
		repository.logout()
		assertFalse(repository.restoreLocalSession())
		repository.login("second", "password", true)
		assertEquals(null, repository.readCachedAccountsOverview())
		assertTrue(repository.fetchPendingTransactions(1).isEmpty())
		repository.synchronize().getOrThrow()
		assertTrue(api.requests.isEmpty())
		repository.login("first", "password", true)
		assertEquals(1, repository.fetchPendingTransactions(1).size)
		val restarted = BuddiRepository(api, offlineStore = root, autoSyncPendingTransactions = false)
		assertTrue(restarted.restoreLocalSession())
		assertEquals(BigDecimal("75.00"), restarted.readCachedAccountData(1).account?.balanceNumber)
	}

	@Test
	fun cachedStartupDoesNotWaitForBackend() = runBlocking {
		val store = InMemoryOfflineStore()
		val seed = BuddiRepository(api = FakeBuddiApi(accountsResponse = accountsResponse()), offlineStore = store)
		seed.fetchAccountsOverview().getOrThrow()
		seed.fetchTransactions(1, 0, 100).getOrThrow()
		val unavailable = object : BuddiApi by FakeBuddiApi() {
			override suspend fun getAccounts(): AccountsResponseDto = CompletableDeferred<AccountsResponseDto>().await()

			override suspend fun getTransactions(sourceId: Long, start: Int, limit: Int, search: String?): TransactionsResponseDto =
				CompletableDeferred<TransactionsResponseDto>().await()
		}
		val repository = BuddiRepository(api = unavailable, offlineStore = store, autoSyncPendingTransactions = false)
		withTimeout(500) {
			assertTrue(repository.hasValidSession())
			assertTrue(repository.fetchTransactions(1, 0, 100).isSuccess)
		}
	}

	@Test
	fun createTransactionUpdatesExistingDescriptionTemplateAmount() = runBlocking {
		val description = "Coffee"
		val api = FakeBuddiApi(descriptionsResponse = descriptionsResponse(description, "10.00"))
		val repository = BuddiRepository(api = api, descriptionsApi = api)

		val initial = repository.fetchTransactionDescriptionTemplates().getOrThrow()
		assertEquals(BigDecimal("10.00"), initial.single().splits.single().amountNumber)

		val result = repository.createTransaction(
			TransactionEditInput(
				dateIso = "2026-04-23",
				description = description,
				number = "",
				amount = "25.75",
				fromId = 1,
				toId = 2,
				memo = ""
			)
		)

		assertTrue(result.isSuccess)
		assertEquals(
			BigDecimal("25.75"),
			repository.transactionDescriptionTemplates.value.single().splits.single().amountNumber
		)
	}

	@Test
	fun fetchTransactionDescriptionTemplatesKeepsNewerLocalTemplateWhenRemoteLoadFinishesLater() = runBlocking {
		val description = "Coffee"
		val repository = BuddiRepository(
			api = FakeBuddiApi(),
			descriptionsApi = FakeBuddiApi(descriptionsResponse = descriptionsResponse(description, "10.00"))
		)

		val mutationResult = repository.createTransaction(
			TransactionEditInput(
				dateIso = "2026-04-23",
				description = description,
				number = "",
				amount = "25.75",
				fromId = 1,
				toId = 2,
				memo = ""
			)
		)

		assertTrue(mutationResult.isSuccess)

		val merged = repository.fetchTransactionDescriptionTemplates().getOrThrow()
		assertEquals(BigDecimal("25.75"), merged.single().splits.single().amountNumber)
	}

	@Test
	fun offlineCreateEditAndDeleteUsesPendingOutbox() = runBlocking {
		val store = InMemoryOfflineStore()
		val repository = BuddiRepository(
			api = FakeBuddiApi(
				mutateException = IOException("offline"),
				transactionsResponse = TransactionsResponseDto(success = true, data = emptyList(), total = 0)
			),
			offlineStore = store,
			autoSyncPendingTransactions = false
		)
		val input = TransactionEditInput(
			dateIso = "2026-04-23",
			description = "Coffee",
			number = "",
			amount = "25.75",
			fromId = 1,
			toId = 2,
			memo = ""
		)

		val createResult = repository.createTransaction(input)

		assertTrue(createResult.isSuccess)
		assertEquals(1, store.listPendingTransactions().size)

		var page = repository.fetchTransactions(sourceId = 1, start = 0, limit = 100).getOrThrow()
		var pendingTransaction = page.items.single()
		assertTrue(pendingTransaction.isPending)
		assertEquals(BigDecimal("25.75"), pendingTransaction.split?.amountNumber)

		val updateResult = repository.updateTransaction(pendingTransaction.id, input.copy(amount = "30.00"))

		assertTrue(updateResult.isSuccess)
		assertEquals("30.00", store.listPendingTransactions().single().amount)
		page = repository.fetchTransactions(sourceId = 1, start = 0, limit = 100).getOrThrow()
		pendingTransaction = page.items.single()
		assertEquals(BigDecimal("30.00"), pendingTransaction.split?.amountNumber)

		val deleteResult = repository.deleteTransaction(pendingTransaction.id)

		assertTrue(deleteResult.isSuccess)
		assertTrue(store.listPendingTransactions().isEmpty())
	}

	@Test
	fun fetchTransactionsWithPendingOutboxDoesNotWaitForNetworkList() = runBlocking {
		val store = InMemoryOfflineStore()
		val api = FakeBuddiApi(
			transactionsResponse = TransactionsResponseDto(success = true, data = emptyList(), total = 0)
		)
		val repository = BuddiRepository(
			api = api,
			offlineStore = store,
			autoSyncPendingTransactions = false
		)

		val createResult = repository.createTransaction(
			TransactionEditInput(
				dateIso = "2026-04-23",
				description = "Coffee",
				number = "",
				amount = "25.75",
				fromId = 1,
				toId = 2,
				memo = ""
			)
		)

		assertTrue(createResult.isSuccess)
		val page = repository.fetchTransactions(sourceId = 1, start = 0, limit = 100).getOrThrow()
		assertEquals(0, api.transactionsRequestCount)
		assertEquals(1, page.items.size)
		assertTrue(page.items.single().isPending)
	}

	@Test
	fun loadCachedAccountProjectsPendingBalanceWithoutNetworkRefresh() = runBlocking {
		val store = InMemoryOfflineStore()
		val api = FakeBuddiApi(
			accountsResponse = accountsResponse(
				AccountSummary(
					id = 1,
					name = "Checking",
					balance = "USD 100.00",
					deleted = false,
					balanceNumber = BigDecimal("100.00")
				),
				AccountSummary(
					id = 2,
					name = "Groceries",
					balance = "USD 0.00",
					deleted = false,
					balanceNumber = BigDecimal.ZERO
				)
			)
		)
		val repository = BuddiRepository(
			api = api,
			offlineStore = store,
			autoSyncPendingTransactions = false
		)

		repository.fetchUserDatePreferences().getOrThrow()
		repository.fetchAccounts().getOrThrow()
		val createResult = repository.createTransaction(
			TransactionEditInput(
				dateIso = "2026-04-23",
				description = "Coffee",
				number = "",
				amount = "25.00",
				fromId = 1,
				toId = 2,
				memo = ""
			)
		)

		assertTrue(createResult.isSuccess)
		val projectedAccount = repository.loadCachedAccount(1)
		assertEquals(1, api.accountsRequestCount)
		assertEquals("USD 75.00", projectedAccount?.balance)
		assertTrue(projectedAccount?.hasPending == true)
	}

	@Test
	fun syncPendingTransactionsSendsStableUuidAndRemovesSyncedRow() = runBlocking {
		val store = InMemoryOfflineStore()
		val api = FakeBuddiApi()
		val repository = BuddiRepository(
			api = api,
			offlineStore = store,
			autoSyncPendingTransactions = false
		)

		repository.createTransaction(
			TransactionEditInput(
				dateIso = "2026-04-23",
				description = "Coffee",
				number = "",
				amount = "25.75",
				fromId = 1,
				toId = 2,
				memo = ""
			)
		)
		val localUuid = store.listPendingTransactions().single().localUuid

		val syncResult = repository.syncPendingTransactions()

		assertTrue(syncResult.isSuccess)
		assertEquals(1, syncResult.getOrThrow())
		assertTrue(store.listPendingTransactions().isEmpty())
		assertEquals(localUuid, api.mutationRequests.single().uuid)
	}

	@Test
	fun syncPendingTransactionsNotifiesWhenPendingRowsAreSynced() = runBlocking {
		val store = InMemoryOfflineStore()
		val repository = BuddiRepository(
			api = FakeBuddiApi(),
			offlineStore = store,
			autoSyncPendingTransactions = false
		)

		repository.createTransaction(
			TransactionEditInput(
				dateIso = "2026-04-23",
				description = "Coffee",
				number = "",
				amount = "25.75",
				fromId = 1,
				toId = 2,
				memo = ""
			)
		)
		val syncNotification = async(start = CoroutineStart.UNDISPATCHED) {
			repository.pendingTransactionsSynced.first()
		}

		val syncResult = repository.syncPendingTransactions()

		assertTrue(syncResult.isSuccess)
		assertEquals(1, syncResult.getOrThrow())
		assertTrue(store.listPendingTransactions().isEmpty())
		withTimeout(1_000L) {
			syncNotification.await()
		}
	}

	@Test
	fun offlineFetchTransactionsWithoutCachedPageDoesNotPretendHistoryIsEmpty() = runBlocking {
		val repository = BuddiRepository(
			api = FakeBuddiApi(transactionsException = IOException("offline")),
			offlineStore = InMemoryOfflineStore(),
			autoSyncPendingTransactions = false
		)

		val result = repository.fetchTransactions(sourceId = 1, start = 0, limit = 100)

		assertTrue(result.isFailure)
		assertEquals(null, repository.readCachedAccountData(1).page)
	}

	@Test
	fun loginCachesUserDatePreferencesForOfflineUse() = runBlocking {
		val store = InMemoryOfflineStore()
		val repository = BuddiRepository(
			api = FakeBuddiApi(userPreferencesResponse = userPreferencesResponse(dateFormat = "dd.MM.yyyy")),
			offlineStore = store,
			autoSyncPendingTransactions = false
		)

		val loginResult = repository.login("user", "password", stayLoggedIn = true)

		assertTrue(loginResult is LoginResult.Success)
		repository.synchronize().getOrThrow()
		val offlineRepository = BuddiRepository(
			api = FakeBuddiApi(userPreferencesException = IOException("offline")),
			offlineStore = store,
			autoSyncPendingTransactions = false
		)
		val preferences = offlineRepository.fetchUserDatePreferences().getOrThrow()
		assertEquals("dd.MM.yyyy", preferences.dateFormat)
	}
}

private fun userPreferencesResponse(dateFormat: String): UserPreferencesResponseDto {
	return UserPreferencesResponseDto(
		success = true,
		locale = "en-US",
		currency = "USD",
		dateFormat = dateFormat,
		currencyAfter = false,
		decimalSeparator = ".",
		thousandSeparator = ",",
		negativeFormat = "N",
		showCurrencySymbol = false,
		currencySpacing = true
	)
}

private fun descriptionsResponse(description: String, amount: String): TransactionDescriptionsResponseDto {
	return TransactionDescriptionsResponseDto(
		success = true,
		data = listOf(
			TransactionDescriptionItemDto(
				value = description,
				transaction = TransactionDescriptionTransactionDto(
					description = description,
					splits = listOf(
						TransactionDescriptionSplitDto(
							amountNumber = BigDecimal(amount),
							fromId = 1,
							toId = 2,
							fromType = "D",
							toType = "E"
						)
					)
				)
			)
		)
	)
}

private fun accountsResponse(vararg accounts: AccountSummary): AccountsResponseDto {
	val typeChildren = JsonArray()
	for (account in accounts) {
		typeChildren.add(
			JsonObject().apply {
				addProperty("nodeType", "account")
				addProperty("id", account.id)
				addProperty("name", account.name)
				addProperty("balance", account.balance)
				addProperty("balanceNumber", account.balanceNumber)
				addProperty("debit", account.debit)
				addProperty("deleted", account.deleted)
			}
		)
	}
	val accountType = JsonObject().apply {
		addProperty("nodeType", "type")
		addProperty("name", "Assets")
		addProperty("balance", "USD 100.00")
		addProperty("balanceNumber", BigDecimal("100.00"))
		addProperty("debit", true)
		add("children", typeChildren)
	}
	val netWorth = JsonObject().apply {
		addProperty("name", "Net Worth")
		addProperty("balance", "USD 100.00")
		addProperty("balanceNumber", BigDecimal("100.00"))
	}
	return AccountsResponseDto(success = true, children = listOf(accountType, netWorth))
}

private class FakeBuddiApi(
	private val loginResponse: AuthenticationFlowResponseDto = AuthenticationFlowResponseDto(success = true, next = null),
	private val descriptionsResponse: TransactionDescriptionsResponseDto = TransactionDescriptionsResponseDto(
		success = true,
		data = emptyList()
	),
	private val accountsResponse: AccountsResponseDto? = accountsResponse(),
	private val mutateResponse: SuccessResponseDto = SuccessResponseDto(success = true, id = 99),
	private val mutateException: IOException? = null,
	private val transactionsResponse: TransactionsResponseDto = TransactionsResponseDto(
		success = true,
		data = emptyList(),
		total = 0
	),
	private val transactionsException: IOException? = null,
	private val userPreferencesResponse: UserPreferencesResponseDto = userPreferencesResponse(dateFormat = "yyyy-MM-dd"),
	private val userPreferencesException: IOException? = null
) : BuddiApi {

	val mutationRequests = mutableListOf<TransactionMutationRequestDto>()
	var accountsRequestCount = 0
		private set
	var transactionsRequestCount = 0
		private set

	override suspend fun login(identifier: String, password: String, remember: String?): AuthenticationFlowResponseDto {
		return loginResponse
	}

	override suspend fun logout(): Response<Void> {
		error("Unused in test.")
	}

	override suspend fun getAccounts(): AccountsResponseDto {
		accountsRequestCount++
		return accountsResponse ?: error("Unused in test.")
	}

	override suspend fun getTransactions(
		sourceId: Long,
		start: Int,
		limit: Int,
		search: String?
	): TransactionsResponseDto {
		transactionsRequestCount++
		transactionsException?.let { throw it }
		return transactionsResponse
	}

	override suspend fun getTransactionDescriptions(): TransactionDescriptionsResponseDto {
		return descriptionsResponse
	}

	override suspend fun mutateTransaction(request: TransactionMutationRequestDto): SuccessResponseDto {
		mutateException?.let { throw it }
		mutationRequests.add(request)
		return mutateResponse
	}

	override suspend fun getUserPreferences(): UserPreferencesResponseDto {
		userPreferencesException?.let { throw it }
		return userPreferencesResponse
	}

	override suspend fun getSources(direction: String): SourcesResponseDto {
		return SourcesResponseDto(success = true, data = emptyList())
	}
}

private class InMemoryOfflineStore : OfflineStore {
	private val cache = mutableMapOf<String, String>()
	private val pendingTransactions = linkedMapOf<String, PendingTransaction>()

	override fun saveCache(key: String, value: String) {
		cache[key] = value
	}

	override fun loadCache(key: String): String? {
		return cache[key]
	}

	override fun deleteCachePrefix(prefix: String) {
		cache.keys.removeAll { it.startsWith(prefix) }
	}

	override fun upsertPendingTransaction(pendingTransaction: PendingTransaction) {
		pendingTransactions[pendingTransaction.localUuid] = pendingTransaction
	}

	override fun listPendingTransactions(): List<PendingTransaction> {
		return pendingTransactions.values.sortedBy { pendingTransaction -> pendingTransaction.createdAt }
	}

	override fun deletePendingTransaction(localUuid: String) {
		pendingTransactions.remove(localUuid)
	}
}

private fun testInput(): TransactionEditInput = TransactionEditInput("2026-09-13", "Coffee", "", "25.00", 1, 2, "")

private class LedgerApi : BuddiApi by FakeBuddiApi() {
	val requests = mutableListOf<TransactionMutationRequestDto>()
	val transactions = linkedMapOf<Long, TransactionDto>()
	val transactionSources = mutableListOf<Long>()
	var accountRequests = 0
	var accountsError: Exception? = null
	var afterMutation: suspend () -> Unit = { }
	private var nextId = 1L

	override suspend fun getAccounts(): AccountsResponseDto {
		accountRequests++
		accountsError?.let { throw it }
		val amount = transactions.values.fold(BigDecimal.ZERO) { total, transaction -> total + transaction.splits.single().amountNumber!! }
		return accountsResponse(
			AccountSummary(1, "Checking", "USD ${BigDecimal("100.00") - amount}", false, BigDecimal("100.00") - amount),
			AccountSummary(2, "Savings", "USD $amount", false, amount)
		)
	}

	override suspend fun getSources(direction: String): SourcesResponseDto = SourcesResponseDto(true, listOf(
		SourceItemDto(com.google.gson.JsonPrimitive(1), "Checking", null, "D"),
		SourceItemDto(com.google.gson.JsonPrimitive(2), "Savings", null, "D")
	))

	override suspend fun getTransactions(sourceId: Long, start: Int, limit: Int, search: String?): TransactionsResponseDto {
		transactionSources.add(sourceId)
		assertEquals(100, limit)
		return TransactionsResponseDto(true, transactions.values.toList().drop(start).take(limit), transactions.size)
	}

	override suspend fun mutateTransaction(request: TransactionMutationRequestDto): SuccessResponseDto {
		requests.add(request)
		val id = request.id ?: transactions.values.firstOrNull { it.uuid == request.uuid }?.id ?: nextId++
		if (request.action == "delete") {
			transactions.remove(id)
		} else if (request.action != "insert" || !transactions.containsKey(id)) {
			val split = request.splits!!.single()
			transactions[id] = TransactionDto(id, request.uuid ?: transactions[id]?.uuid, request.date!!, request.date,
				request.description!!, request.number, listOf(SplitDto(null, split.amount, split.amount.toBigDecimal(),
					split.fromId, "Checking", split.toId, "Savings", split.memo)))
		}
		afterMutation()
		return SuccessResponseDto(true, id, transactions[id]?.uuid)
	}
}
