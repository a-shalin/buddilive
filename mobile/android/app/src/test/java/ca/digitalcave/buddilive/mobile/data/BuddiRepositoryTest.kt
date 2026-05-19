package ca.digitalcave.buddilive.mobile.data

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response
import java.io.IOException
import java.math.BigDecimal

class BuddiRepositoryTest {

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

private class FakeBuddiApi(
	private val descriptionsResponse: TransactionDescriptionsResponseDto = TransactionDescriptionsResponseDto(
		success = true,
		data = emptyList()
	),
	private val mutateResponse: SuccessResponseDto = SuccessResponseDto(success = true),
	private val mutateException: IOException? = null,
	private val transactionsResponse: TransactionsResponseDto = TransactionsResponseDto(
		success = true,
		data = emptyList(),
		total = 0
	)
) : BuddiApi {

	val mutationRequests = mutableListOf<TransactionMutationRequestDto>()

	override suspend fun login(identifier: String, password: String, remember: String?): AuthenticationFlowResponseDto {
		error("Unused in test.")
	}

	override suspend fun logout(): Response<Void> {
		error("Unused in test.")
	}

	override suspend fun getAccounts(): AccountsResponseDto {
		error("Unused in test.")
	}

	override suspend fun getTransactions(
		sourceId: Long,
		start: Int,
		limit: Int,
		search: String?
	): TransactionsResponseDto {
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
		error("Unused in test.")
	}

	override suspend fun getSources(direction: String): SourcesResponseDto {
		error("Unused in test.")
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
