package ca.digitalcave.buddilive.mobile.data

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
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
	fun acknowledgedCacheSurvivesRestartBeforeReconciliation() = runBlocking {
		val api = LedgerApi()
		val store = InMemoryOfflineStore()
		val repository = BuddiRepository(api, offlineStore = store, autoSyncPendingTransactions = false)
		repository.synchronize().getOrThrow()
		repository.createTransaction(testInput()).getOrThrow()
		api.afterMutation = { api.accountsError = IOException("Refresh unavailable") }
		assertTrue(repository.syncPendingTransactions().isFailure)
		assertTrue(store.listPendingTransactions().single().sync.acknowledged)
		val restarted = BuddiRepository(api, offlineStore = store, autoSyncPendingTransactions = false)
		assertEquals(api.transactions.keys.single(), JsonParser.parseString(store.loadCache("transactions:1:0:100")).asJsonObject.getAsJsonArray("data").single().asJsonObject.get("id").asLong)
		assertEquals("Coffee", restarted.readCachedAccountData(1).page!!.items.single().description)
		api.accountsError = null
		restarted.syncPendingTransactions().getOrThrow()
		assertEquals(1, api.requests.size)
		assertTrue(api.transactionSyncResponses.last().accounts.all { it.mode == "unchanged" })
		assertTrue(store.listPendingTransactions().isEmpty())
	}

	@Test
	fun backdatedLocalInsertKeepsTheRecentWindowAndHashes() = runBlocking {
		val api = LedgerApi()
		(1L..100L).forEach { api.transactions[it] = ledgerTransaction(it) }
		val repository = BuddiRepository(api, offlineStore = InMemoryOfflineStore(), autoSyncPendingTransactions = false)
		repository.synchronize().getOrThrow()
		repository.createTransaction(testInput().copy(dateIso = "2020-01-01")).getOrThrow()
		repository.syncPendingTransactions().getOrThrow()
		assertTrue(api.transactionSyncResponses.last().accounts.all { it.mode == "unchanged" })
		assertEquals((100L downTo 1L).toList(), repository.readCachedAccountData(1).page!!.items.map { it.id })
		assertEquals(101, repository.readCachedAccountData(1).page!!.total)
	}

	@Test
	fun acknowledgedEditsAndDeletesDoNotDownloadKnownTransactions() = runBlocking {
		val api = LedgerApi()
		val repository = BuddiRepository(api, offlineStore = InMemoryOfflineStore(), autoSyncPendingTransactions = false)
		repository.synchronize().getOrThrow()
		repository.createTransaction(testInput()).getOrThrow()
		repository.syncPendingTransactions().getOrThrow()
		val id = api.transactions.keys.single()
		repository.updateTransaction(id, testInput().copy(amount = "30.00")).getOrThrow()
		repository.syncPendingTransactions().getOrThrow()
		assertTrue(api.transactionSyncResponses.last().accounts.all { it.mode == "unchanged" })
		assertEquals(BigDecimal("30.00"), repository.readCachedAccountData(1).page!!.items.single().split!!.amountNumber)
		repository.deleteTransaction(id).getOrThrow()
		repository.syncPendingTransactions().getOrThrow()
		assertTrue(api.transactionSyncResponses.last().accounts.all { it.mode == "unchanged" })
		assertTrue(repository.readCachedAccountData(1).page!!.items.isEmpty())
		assertEquals(0, repository.readCachedAccountData(1).page!!.total)
	}

	@Test
	fun localAcknowledgementCannotHideAnExternalEditInTheSameBucket() = runBlocking {
		val api = LedgerApi()
		(1L..100L).forEach { api.transactions[it] = ledgerTransaction(it) }
		val remoteId = (2L..100L).first { TransactionBuckets.bucketId(it) == TransactionBuckets.bucketId(101) }
		val repository = BuddiRepository(api, offlineStore = InMemoryOfflineStore(), autoSyncPendingTransactions = false)
		repository.synchronize().getOrThrow()
		api.afterMutation = { api.transactions[remoteId] = ledgerTransaction(remoteId).copy(description = "External edit") }
		repository.createTransaction(testInput().copy(dateIso = "2026-11-01")).getOrThrow()
		repository.syncPendingTransactions().getOrThrow()
		assertTrue(api.transactionSyncResponses.last().accounts.all { it.buckets?.single()?.id == TransactionBuckets.bucketId(101) })
		assertEquals("External edit", repository.readCachedAccountData(1).page!!.items.single { it.id == remoteId }.description)
	}

	@Test
	fun lostInsertResponseReplaysWithoutDownloadingOrDoubleCounting() = runBlocking {
		val api = LedgerApi()
		val store = InMemoryOfflineStore()
		val repository = BuddiRepository(api, offlineStore = store, autoSyncPendingTransactions = false)
		repository.synchronize().getOrThrow()
		repository.createTransaction(testInput()).getOrThrow()
		api.afterMutation = { throw IOException("Response lost") }
		assertTrue(repository.syncPendingTransactions().isFailure)
		api.afterMutation = { }
		repository.syncPendingTransactions().getOrThrow()
		assertEquals(api.requests.first().uuid, api.requests.last().uuid)
		assertEquals(1, api.transactions.size)
		assertTrue(api.transactionSyncResponses.last().accounts.all { it.mode == "unchanged" })
		assertEquals(1, repository.readCachedAccountData(1).page!!.total)
		assertTrue(store.listPendingTransactions().isEmpty())
	}

	@Test
	fun transactionPatchAndOutboxAcknowledgementRollBackTogetherOnWriteFailure() = runBlocking {
		val backing = InMemoryOfflineStore()
		var failWrite = false
		val store = object : OfflineStore by backing {
			override fun saveCache(key: String, value: String) {
				if (failWrite && key == "transactions:2:0:100") throw IOException("Disk unavailable")
				backing.saveCache(key, value)
			}
		}
		val api = LedgerApi()
		val repository = BuddiRepository(api, offlineStore = store, autoSyncPendingTransactions = false)
		repository.synchronize().getOrThrow()
		val before = store.loadCache("transactions:1:0:100")
		repository.createTransaction(testInput()).getOrThrow()
		failWrite = true
		assertTrue(repository.syncPendingTransactions().isFailure)
		assertEquals(before, store.loadCache("transactions:1:0:100"))
		assertFalse(store.listPendingTransactions().single().sync.acknowledged)
		failWrite = false
		repository.syncPendingTransactions().getOrThrow()
		assertTrue(api.transactionSyncResponses.last().accounts.all { it.mode == "unchanged" })
		assertEquals(1, repository.readCachedAccountData(1).page!!.total)
	}

	@Test
	fun legacyMutationAcknowledgementsStillReconcileNormally() = runBlocking {
		val server = LedgerApi()
		val api = object : BuddiApi by server {
			override suspend fun mutateTransaction(request: TransactionMutationRequestDto) =
				server.mutateTransaction(request).copy(transaction = null, previousSourceIds = null)
		}
		val repository = BuddiRepository(api, offlineStore = InMemoryOfflineStore(), autoSyncPendingTransactions = false)
		repository.synchronize().getOrThrow()
		repository.createTransaction(testInput()).getOrThrow()
		repository.syncPendingTransactions().getOrThrow()
		assertTrue(server.transactionSyncResponses.last().accounts.all { it.mode != "unchanged" })
		assertEquals("Coffee", repository.readCachedAccountData(1).page!!.items.single().description)
	}

	@Test
	fun localInsertAcknowledgementMakesTransactionHashesMatch() = runBlocking {
		val api = LedgerApi()
		val store = InMemoryOfflineStore()
		val repository = BuddiRepository(api, offlineStore = store, autoSyncPendingTransactions = false)
		repository.synchronize().getOrThrow()
		repository.createTransaction(testInput()).getOrThrow()
		repository.syncPendingTransactions().getOrThrow()
		assertTrue(api.transactionSyncResponses.last().accounts.all { it.mode == "unchanged" && it.items == null && it.buckets == null })
		assertEquals(0, api.legacyTransactionRequests)
		assertTrue(store.listPendingTransactions().isEmpty())
		val restarted = BuddiRepository(api, offlineStore = store, autoSyncPendingTransactions = false)
		assertEquals(api.transactions.keys.single(), restarted.readCachedAccountData(1).page!!.items.single().id)
	}

	@Test
	fun localInsertAtFullWindowDoesNotDownloadAnyTransactions() = runBlocking {
		val api = LedgerApi()
		(1L..100L).forEach { api.transactions[it] = ledgerTransaction(it) }
		val repository = BuddiRepository(api, offlineStore = InMemoryOfflineStore(), autoSyncPendingTransactions = false)
		repository.synchronize().getOrThrow()
		repository.createTransaction(testInput().copy(dateIso = "2026-11-01")).getOrThrow()
		repository.syncPendingTransactions().getOrThrow()
		assertTrue(api.transactionSyncResponses.last().accounts.all { it.mode == "unchanged" })
		val page = repository.readCachedAccountData(1).page!!
		assertEquals(101, page.total)
		assertEquals((101L downTo 2L).toList(), page.items.map { it.id })
	}

	@Test
	fun ownerSwitchDiscardsOldTransactionSyncAuthenticationFailure() = runBlocking {
		val stores = mutableMapOf<String, OfflineStore>()
		val root = object : OfflineStore by InMemoryOfflineStore() {
			override fun forOwner(owner: String): OfflineStore = stores.getOrPut(owner) { InMemoryOfflineStore() }
		}
		val entered = CompletableDeferred<Unit>()
		val release = CompletableDeferred<Unit>()
		val api = object : BuddiApi by LedgerApi() {
			override suspend fun syncTransactions(request: TransactionsSyncRequestDto): TransactionsSyncResponseDto {
				entered.complete(Unit)
				release.await()
				throw HttpException(Response.error<Any>(401, "Old session expired".toResponseBody()))
			}
		}
		val repository = BuddiRepository(api, offlineStore = root, autoSyncPendingTransactions = false)
		repository.login("first", "password", true)
		val refresh = async { runCatching { repository.synchronize() } }
		withTimeout(2_000) { entered.await() }
		repository.login("second", "password", true)
		release.complete(Unit)
		assertTrue(refresh.await().exceptionOrNull() is kotlinx.coroutines.CancellationException)
		assertFalse(repository.syncState.value.needsLogin)
		assertFalse(repository.syncState.value.isPrepared)
		assertEquals(null, repository.readCachedAccountData(1).page)
	}

	@Test
	fun transactionBucketsBatchAccountsAndDownloadOnlyChangedBuckets() = runBlocking {
		val api = LedgerApi()
		(1L..100L).forEach { api.transactions[it] = ledgerTransaction(it) }
		val repository = BuddiRepository(api, offlineStore = InMemoryOfflineStore(), autoSyncPendingTransactions = false)
		repository.synchronize().getOrThrow()
		assertEquals(listOf(1L, 2L), api.transactionSyncRequests.single().accounts.map { it.sourceId })
		repository.synchronize().getOrThrow()
		assertTrue(api.transactionSyncResponses.last().accounts.all { it.mode == "unchanged" })
		assertTrue(api.transactionSyncRequests.last().accounts.all { it.bucketHashes?.length == 684 })
		api.transactions[1] = ledgerTransaction(1).copy(description = "Updated")
		repository.synchronize().getOrThrow()
		assertTrue(api.transactionSyncResponses.last().accounts.all { it.buckets?.single()?.id == TransactionBuckets.bucketId(1) })
		assertEquals("Updated", repository.readCachedAccountData(1).page!!.items.single { it.id == 1L }.description)
		assertEquals(0, api.legacyTransactionRequests)
	}

	@Test
	fun recentWindowHandlesInsertDeleteAndDateChanges() = runBlocking {
		val api = LedgerApi()
		(1L..101L).forEach { api.transactions[it] = ledgerTransaction(it) }
		val repository = BuddiRepository(api, offlineStore = InMemoryOfflineStore(), autoSyncPendingTransactions = false)
		repository.synchronize().getOrThrow()
		assertEquals((101L downTo 2L).toList(), repository.readCachedAccountData(1).page!!.items.map { it.id })
		api.transactions[102] = ledgerTransaction(102)
		repository.synchronize().getOrThrow()
		assertEquals((102L downTo 3L).toList(), repository.readCachedAccountData(1).page!!.items.map { it.id })
		api.transactions.remove(102)
		repository.synchronize().getOrThrow()
		assertEquals((101L downTo 2L).toList(), repository.readCachedAccountData(1).page!!.items.map { it.id })
		api.transactions[101] = ledgerTransaction(101).copy(dateIso = "2020-01-01")
		repository.synchronize().getOrThrow()
		assertEquals((100L downTo 1L).toList(), repository.readCachedAccountData(1).page!!.items.map { it.id })
	}

	@Test
	fun olderEditsInvalidateHistoryWithoutDownloadingRecentTransactions() = runBlocking {
		val api = LedgerApi()
		(1L..101L).forEach { api.transactions[it] = ledgerTransaction(it) }
		val store = InMemoryOfflineStore()
		val repository = BuddiRepository(api, offlineStore = store, autoSyncPendingTransactions = false)
		repository.synchronize().getOrThrow()
		store.saveCache("transactions:1:100:100", "history")
		val firstPage = store.loadCache("transactions:1:0:100")
		repository.synchronize().getOrThrow()
		assertEquals("history", store.loadCache("transactions:1:100:100"))
		api.transactions[1] = ledgerTransaction(1).copy(description = "Older edit")
		repository.synchronize().getOrThrow()
		assertEquals("unchanged", api.transactionSyncResponses.last().accounts.first().mode)
		assertEquals(null, store.loadCache("transactions:1:100:100"))
		assertEquals(firstPage, store.loadCache("transactions:1:0:100"))
	}

	@Test
	fun corruptAccountBucketRejectsTheWholeBatchAndRequestsRepair() = runBlocking {
		val server = LedgerApi()
		(1L..100L).forEach { server.transactions[it] = ledgerTransaction(it) }
		var corrupt = false
		val api = object : BuddiApi by server {
			override suspend fun syncTransactions(request: TransactionsSyncRequestDto): TransactionsSyncResponseDto {
				val response = server.syncTransactions(request)
				return if (!corrupt) response else response.copy(accounts = response.accounts.mapIndexed { index, account ->
					if (index == 0) account else account.copy(buckets = account.buckets!!.map { it.copy(hash = "bad") })
				})
			}
		}
		val store = InMemoryOfflineStore()
		val repository = BuddiRepository(api, offlineStore = store, autoSyncPendingTransactions = false)
		repository.synchronize().getOrThrow()
		val before = (1L..2L).map { store.loadCache("transactions:$it:0:100") }
		server.transactions[1] = ledgerTransaction(1).copy(description = "New value")
		corrupt = true
		assertTrue(repository.synchronize().isFailure)
		assertEquals(before, (1L..2L).map { store.loadCache("transactions:$it:0:100") })
		corrupt = false
		repository.synchronize().getOrThrow()
		assertTrue(server.transactionSyncRequests.last().accounts.all { it.bucketHashes == null })
		assertEquals("New value", repository.readCachedAccountData(1).page!!.items.single { it.id == 1L }.description)
	}

	@Test
	fun transactionLabelsRefreshWithoutDownloadingRecords() = runBlocking {
		val server = LedgerApi()
		server.transactions[1] = ledgerTransaction(1)
		var label = "Original name"
		val api = object : BuddiApi by server {
			override suspend fun getSources(direction: String) = SourcesResponseDto(true, emptyList())
			override suspend fun syncTransactions(request: TransactionsSyncRequestDto) =
				server.syncTransactions(request).copy(sourceNames = mapOf(1 to label, 2 to "Destination"))
		}
		val repository = BuddiRepository(api, offlineStore = InMemoryOfflineStore(), autoSyncPendingTransactions = false)
		repository.synchronize().getOrThrow()
		assertEquals(label, repository.readCachedAccountData(1).page!!.items.single().split!!.fromName)
		label = "Renamed source"
		repository.synchronize().getOrThrow()
		assertTrue(server.transactionSyncResponses.last().accounts.all { it.mode == "unchanged" })
		assertEquals(label, repository.readCachedAccountData(1).page!!.items.single().split!!.fromName)
	}

	@Test
	fun newPendingTransactionSurvivesAnInFlightTransactionRefresh() = runBlocking {
		val server = LedgerApi()
		val entered = CompletableDeferred<Unit>()
		val release = CompletableDeferred<Unit>()
		var block = false
		val api = object : BuddiApi by server {
			override suspend fun syncTransactions(request: TransactionsSyncRequestDto): TransactionsSyncResponseDto {
				val response = server.syncTransactions(request)
				if (block) { entered.complete(Unit); release.await() }
				return response
			}
		}
		val store = InMemoryOfflineStore()
		val repository = BuddiRepository(api, offlineStore = store, autoSyncPendingTransactions = false)
		repository.synchronize().getOrThrow()
		block = true
		val refresh = async { repository.synchronize() }
		withTimeout(2_000) { entered.await() }
		repository.createTransaction(testInput()).getOrThrow()
		release.complete(Unit)
		refresh.await().getOrThrow()
		assertEquals("Coffee", repository.readCachedAccountData(1).page!!.items.single().description)
		assertEquals(1, store.listPendingTransactions().size)
	}

	@Test
	fun unsupportedTransactionRouteIsProbedOnlyOncePerSession() = runBlocking {
		val server = LedgerApi()
		var probes = 0
		val api = object : BuddiApi by server {
			override suspend fun syncTransactions(request: TransactionsSyncRequestDto): TransactionsSyncResponseDto {
				probes++
				throw HttpException(Response.error<Any>(404, "Unsupported".toResponseBody()))
			}
		}
		val repository = BuddiRepository(api, offlineStore = InMemoryOfflineStore(), autoSyncPendingTransactions = false)
		repository.synchronize().getOrThrow()
		repository.synchronize().getOrThrow()
		assertEquals(1, probes)
		assertEquals(4, server.legacyTransactionRequests)
	}

	@Test
	fun expiredTransactionSyncKeepsCacheAndPausesForLogin() = runBlocking {
		val server = LedgerApi()
		server.transactions[1] = ledgerTransaction(1)
		var expired = false
		val api = object : BuddiApi by server {
			override suspend fun syncTransactions(request: TransactionsSyncRequestDto): TransactionsSyncResponseDto {
				if (expired) throw HttpException(Response.error<Any>(401, "Expired".toResponseBody()))
				return server.syncTransactions(request)
			}
		}
		val repository = BuddiRepository(api, offlineStore = InMemoryOfflineStore(), autoSyncPendingTransactions = false)
		repository.synchronize().getOrThrow()
		expired = true
		assertTrue(repository.synchronize().isFailure)
		assertTrue(repository.syncState.value.needsLogin)
		assertEquals(1L, repository.readCachedAccountData(1).page!!.items.single().id)
	}

	@Test
	fun unchangedRecentTransactionsDoNotUseLegacyDownloads() = runBlocking {
		val api = LedgerApi()
		val repository = BuddiRepository(api, offlineStore = InMemoryOfflineStore(), autoSyncPendingTransactions = false)
		repository.synchronize().getOrThrow()
		api.legacyTransactionRequests = 0
		repository.synchronize().getOrThrow()
		assertEquals(0, api.legacyTransactionRequests)
	}

	@Test
	fun failedPatchPersistenceRetainsUnacknowledgedOutboxForReplay() = runBlocking {
		val backing = InMemoryOfflineStore()
		var failWrite = false
		val store = object : OfflineStore by backing {
			override fun saveCache(key: String, value: String) {
				if (failWrite && key == "transaction-suggestions:v1") throw IOException("Disk unavailable")
				backing.saveCache(key, value)
			}
		}
		val api = SuggestionsApi()
		val repository = BuddiRepository(api, offlineStore = store, autoSyncPendingTransactions = false)
		repository.synchronize().getOrThrow()
		repository.createTransaction(testInput()).getOrThrow()
		failWrite = true
		assertTrue(repository.syncPendingTransactions().isFailure)
		assertFalse(store.listPendingTransactions().single().sync.acknowledged)
		assertEquals("Coffee", repository.fetchTransactionDescriptionTemplates().getOrThrow().single().description)
		failWrite = false
		repository.syncPendingTransactions().getOrThrow()
		assertTrue(store.listPendingTransactions().isEmpty())
		assertEquals(1, api.syncRequests.size)
	}

	@Test
	fun expiredSessionDuringSuggestionsRefreshPausesSyncAndKeepsCache() = runBlocking {
		var expired = false
		val api = object : BuddiApi by FakeBuddiApi() {
			override suspend fun syncTransactionDescriptions(request: DescriptionsSyncRequestDto): DescriptionsSyncResponseDto {
				if (expired) throw HttpException(Response.error<Any>(401, "Expired".toResponseBody()))
				return DescriptionsSyncResponseDto(true, 1, "full", listOf(suggestion("Coffee", "10.00")))
			}
		}
		val repository = BuddiRepository(api, offlineStore = InMemoryOfflineStore(), autoSyncPendingTransactions = false)
		repository.synchronize().getOrThrow()
		expired = true
		assertTrue(repository.synchronize().isFailure)
		assertTrue(repository.syncState.value.needsLogin)
		assertEquals("Coffee", repository.fetchTransactionDescriptionTemplates().getOrThrow().single().description)
	}

	@Test
	fun editCreatedDuringExternalRefreshRemainsOverlaidAfterReplacement() = runBlocking {
		val server = SuggestionsApi()
		server.items["Coffee"] = suggestion("Coffee", "10.00")
		val entered = CompletableDeferred<Unit>()
		val release = CompletableDeferred<Unit>()
		var block = false
		val api = object : BuddiApi by server {
			override suspend fun syncTransactionDescriptions(request: DescriptionsSyncRequestDto): DescriptionsSyncResponseDto {
				if (block) { entered.complete(Unit); release.await() }
				return server.syncTransactionDescriptions(request)
			}
		}
		val store = InMemoryOfflineStore()
		val repository = BuddiRepository(api, offlineStore = store, autoSyncPendingTransactions = false)
		repository.synchronize().getOrThrow()
		server.items["Coffee"] = suggestion("Coffee", "12.00")
		block = true
		val refresh = async { repository.synchronize() }
		withTimeout(2_000) { entered.await() }
		repository.createTransaction(testInput()).getOrThrow()
		release.complete(Unit)
		refresh.await().getOrThrow()
		assertEquals(BigDecimal("25.00"), repository.fetchTransactionDescriptionTemplates().getOrThrow().single().splits.single().amountNumber)
		assertEquals(1, store.listPendingTransactions().size)
	}

	@Test
	fun legacyFallbackIsOnlyDownloadedOnExternalRefreshAndRouteIsProbedOnce() = runBlocking {
		var probes = 0
		var downloads = 0
		val api = object : BuddiApi by FakeBuddiApi() {
			override suspend fun syncTransactionDescriptions(request: DescriptionsSyncRequestDto): DescriptionsSyncResponseDto {
				probes++
				throw HttpException(Response.error<Any>(404, "Unsupported".toResponseBody()))
			}
			override suspend fun getTransactionDescriptions(): TransactionDescriptionsResponseDto {
				downloads++
				return descriptionsResponse("Coffee", "10.00")
			}
		}
		val repository = BuddiRepository(api, offlineStore = InMemoryOfflineStore(), autoSyncPendingTransactions = false)
		repository.synchronize().getOrThrow()
		repository.createTransaction(testInput()).getOrThrow()
		repository.syncPendingTransactions().getOrThrow()
		assertEquals(1, downloads)
		repository.synchronize().getOrThrow()
		assertEquals(1, probes)
		assertEquals(2, downloads)
		assertEquals(BigDecimal("10.00"), repository.fetchTransactionDescriptionTemplates().getOrThrow().single().splits.single().amountNumber)
	}

	@Test
	fun ownerSwitchDiscardsInFlightBucketResponse() = runBlocking {
		val stores = mutableMapOf<String, OfflineStore>()
		val root = object : OfflineStore by InMemoryOfflineStore() {
			override fun forOwner(owner: String): OfflineStore = stores.getOrPut(owner) { InMemoryOfflineStore() }
		}
		val entered = CompletableDeferred<Unit>()
		val release = CompletableDeferred<Unit>()
		val api = object : BuddiApi by FakeBuddiApi() {
			override suspend fun syncTransactionDescriptions(request: DescriptionsSyncRequestDto): DescriptionsSyncResponseDto {
				entered.complete(Unit)
				release.await()
				return DescriptionsSyncResponseDto(true, 1, "full", listOf(suggestion("First owner", "10.00")))
			}
		}
		val repository = BuddiRepository(api, offlineStore = root, autoSyncPendingTransactions = false)
		repository.login("first", "password", true)
		val refresh = async { runCatching { repository.synchronize() } }
		withTimeout(2_000) { entered.await() }
		repository.login("second", "password", true)
		release.complete(Unit)
		assertTrue(refresh.await().exceptionOrNull() is kotlinx.coroutines.CancellationException)
		assertTrue(repository.fetchTransactionDescriptionTemplates().getOrThrow().isEmpty())
		assertFalse(repository.syncState.value.isPrepared)
	}

	@Test
	fun bucketRefreshReplacesDeletesAndPersistsWithoutNetworkReads() = runBlocking {
		val api = SuggestionsApi()
		val store = InMemoryOfflineStore()
		api.items["Coffee"] = suggestion("Coffee", "10.00")
		val repository = BuddiRepository(api, offlineStore = store, autoSyncPendingTransactions = false)
		repository.synchronize().getOrThrow()
		assertEquals(null, api.syncRequests.single().bucketHashes)
		repository.synchronize().getOrThrow()
		assertTrue(api.syncRequests.last().bucketHashes != null)
		api.items["Coffee"] = suggestion("Coffee", "20.00")
		repository.synchronize().getOrThrow()
		assertEquals(BigDecimal("20.00"), repository.fetchTransactionDescriptionTemplates().getOrThrow().single().splits.single().amountNumber)
		api.items.clear()
		repository.synchronize().getOrThrow()
		assertTrue(repository.fetchTransactionDescriptionTemplates().getOrThrow().isEmpty())
		val calls = api.syncRequests.size
		val restarted = BuddiRepository(api, offlineStore = store, autoSyncPendingTransactions = false)
		assertTrue(restarted.fetchTransactionDescriptionTemplates().getOrThrow().isEmpty())
		assertEquals(calls, api.syncRequests.size)
		assertEquals(0, api.legacyRequests)
	}

	@Test
	fun localMutationPatchesSurviveRestartWithoutReconciling() = runBlocking {
		val api = SuggestionsApi()
		val store = InMemoryOfflineStore()
		val repository = BuddiRepository(api, offlineStore = store, autoSyncPendingTransactions = false)
		repository.synchronize().getOrThrow()
		repository.createTransaction(testInput()).getOrThrow()
		repository.syncPendingTransactions().getOrThrow()
		assertEquals(1, api.syncRequests.size)
		assertEquals("Coffee", BuddiRepository(api, offlineStore = store).fetchTransactionDescriptionTemplates().getOrThrow().single().description)
		repository.updateTransaction(99, testInput().copy(description = "Tea")).getOrThrow()
		repository.syncPendingTransactions().getOrThrow()
		assertEquals("Tea", repository.fetchTransactionDescriptionTemplates().getOrThrow().single().description)
		repository.deleteTransaction(99).getOrThrow()
		repository.syncPendingTransactions().getOrThrow()
		assertTrue(repository.fetchTransactionDescriptionTemplates().getOrThrow().isEmpty())
		assertEquals(1, api.syncRequests.size)
		assertEquals(0, api.legacyRequests)
	}

	@Test
	fun partialMutationCacheStillBootstrapsAndPatchCannotHideAnExternalChangeInItsBucket() = runBlocking {
		val api = SuggestionsApi()
		val store = InMemoryOfflineStore()
		val repository = BuddiRepository(api, offlineStore = store, autoSyncPendingTransactions = false)
		repository.createTransaction(testInput()).getOrThrow()
		repository.syncPendingTransactions().getOrThrow()
		assertFalse(repository.syncState.value.isPrepared)
		repository.synchronize().getOrThrow()
		assertEquals(null, api.syncRequests.single().bucketHashes)
		val collision = (0..10000).map { "Other $it" }.first {
			TransactionSuggestionBuckets.bucketId(it) == TransactionSuggestionBuckets.bucketId("Coffee")
		}
		api.items[collision] = suggestion(collision, "12.00")
		repository.updateTransaction(99, testInput().copy(amount = "30.00")).getOrThrow()
		assertFalse(repository.fetchTransactionDescriptionTemplates().getOrThrow().any { it.description == collision })
		repository.synchronize().getOrThrow()
		assertTrue(repository.fetchTransactionDescriptionTemplates().getOrThrow().any { it.description == collision })
	}

	@Test
	fun invalidBucketAndFailedPersistenceKeepOldCacheAndRepairWithFullDownload() = runBlocking {
		val api = SuggestionsApi()
		var failWrite = false
		val store = object : OfflineStore by InMemoryOfflineStore() {
			override fun saveCache(key: String, value: String) {
				if (key == "transaction-suggestions:v1" && failWrite) throw IOException("Disk unavailable")
				backing.saveCache(key, value)
			}
			val backing = InMemoryOfflineStore()
			override fun loadCache(key: String) = backing.loadCache(key)
		}
		api.items["Coffee"] = suggestion("Coffee", "10.00")
		val repository = BuddiRepository(api, offlineStore = store, autoSyncPendingTransactions = false)
		repository.synchronize().getOrThrow()
		val id = TransactionSuggestionBuckets.bucketId("Coffee")
		api.nextResponse = DescriptionsSyncResponseDto(true, 1, "buckets", buckets = listOf(SuggestionBucketDto(id, "bad", emptyList())))
		assertTrue(repository.synchronize().isFailure)
		assertEquals(BigDecimal("10.00"), repository.fetchTransactionDescriptionTemplates().getOrThrow().single().splits.single().amountNumber)
		api.items["Coffee"] = suggestion("Coffee", "20.00")
		failWrite = true
		assertTrue(repository.synchronize().isFailure)
		assertEquals(null, api.syncRequests.last().bucketHashes)
		assertEquals(BigDecimal("10.00"), repository.fetchTransactionDescriptionTemplates().getOrThrow().single().splits.single().amountNumber)
		failWrite = false
		repository.synchronize().getOrThrow()
		assertEquals(BigDecimal("20.00"), repository.fetchTransactionDescriptionTemplates().getOrThrow().single().splits.single().amountNumber)
	}

	@Test
	fun pendingRenameKeysSurviveLostResponseReplay() = runBlocking {
		val api = LedgerApi()
		val store = InMemoryOfflineStore()
		val repository = BuddiRepository(api, offlineStore = store, autoSyncPendingTransactions = false)
		repository.createTransaction(testInput()).getOrThrow()
		api.afterMutation = { throw IOException("Response lost") }
		assertTrue(repository.syncPendingTransactions().isFailure)
		val id = repository.fetchPendingTransactions(1).single().id
		repository.updateTransaction(id, testInput().copy(description = "Tea")).getOrThrow()
		api.afterMutation = { }
		repository.syncPendingTransactions().getOrThrow()
		repository.syncPendingTransactions().getOrThrow()
		assertTrue(api.requests.last().suggestionKeys.orEmpty().containsAll(listOf("Coffee", "Tea")))
	}

	@Test
	fun localUploadDoesNotDownloadSuggestions() = runBlocking {
		var downloads = 0
		val api = object : BuddiApi by FakeBuddiApi() {
			override suspend fun syncTransactionDescriptions(request: DescriptionsSyncRequestDto): DescriptionsSyncResponseDto {
				downloads++
				return DescriptionsSyncResponseDto(true, 1, "full", descriptionsResponse("Coffee", "10.00").toTransactionDescriptionTemplates())
			}
		}
		val repository = BuddiRepository(api, offlineStore = InMemoryOfflineStore(), autoSyncPendingTransactions = false)
		repository.synchronize().getOrThrow()
		val before = downloads
		repository.createTransaction(testInput()).getOrThrow()
		repository.syncPendingTransactions().getOrThrow()
		assertEquals(before, downloads)
	}

	@Test
	fun externalRefreshReplacesOldSuggestions() = runBlocking {
		var descriptions = descriptionsResponse("Coffee", "10.00")
		val api = object : BuddiApi by FakeBuddiApi() {
			override suspend fun syncTransactionDescriptions(request: DescriptionsSyncRequestDto) =
				DescriptionsSyncResponseDto(true, 1, "full", descriptions.toTransactionDescriptionTemplates())
		}
		val repository = BuddiRepository(api, offlineStore = InMemoryOfflineStore(), autoSyncPendingTransactions = false)
		repository.synchronize().getOrThrow()
		descriptions = descriptionsResponse("Coffee", "20.00")
		repository.synchronize().getOrThrow()
		assertEquals(BigDecimal("20.00"), repository.transactionDescriptionTemplates.value.single().splits.single().amountNumber)
	}

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
		val repository = BuddiRepository(api = api, descriptionsApi = api, offlineStore = InMemoryOfflineStore(), autoSyncPendingTransactions = false)
		repository.synchronize().getOrThrow()

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
	fun readingSuggestionsKeepsPendingLocalTemplateAndDoesNotDownload() = runBlocking {
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
	fun offlineNetWorthUsesSignedCreditBalancesWithPendingTransactions() = runBlocking {
		val children = JsonParser.parseString(
			"""
			[
			  {"nodeType":"type","name":"Cash","debit":true,"balance":"USD 425,115.83","balanceNumber":425115.83,
			   "children":[{"nodeType":"account","id":1,"name":"Cash","debit":true,"balance":"USD 425,115.83","balanceNumber":425115.83}]},
			  {"nodeType":"type","name":"Investment","debit":true,"balance":"USD 16,842.84","balanceNumber":16842.84,
			   "children":[{"nodeType":"account","id":2,"name":"Investment","debit":true,"balance":"USD 16,842.84","balanceNumber":16842.84}]},
			  {"nodeType":"type","name":"Savings","debit":true,"balance":"USD 487,281.36","balanceNumber":487281.36,
			   "children":[{"nodeType":"account","id":3,"name":"Savings","debit":true,"balance":"USD 487,281.36","balanceNumber":487281.36}]},
			  {"nodeType":"type","name":"Liability","debit":false,"balance":"USD 11,000.00","balanceNumber":11000.00,
			   "children":[{"nodeType":"account","id":4,"name":"Liability","debit":false,"balance":"USD 11,000.00","balanceNumber":-11000.00}]},
			  {"nodeType":"type","name":"Loan","debit":false,"balance":"USD -140,300.00","balanceNumber":-140300.00,
			   "children":[{"nodeType":"account","id":5,"name":"Loan","debit":false,"balance":"USD -140,300.00","balanceNumber":140300.00}]},
			  {"name":"Net Worth","balance":"USD 1,058,540.03","balanceNumber":1058540.03}
			]
			""".trimIndent()
		).asJsonArray.map { it.asJsonObject }
		val api = FakeBuddiApi(accountsResponse = AccountsResponseDto(success = true, children = children))
		val repository = BuddiRepository(api, offlineStore = InMemoryOfflineStore(), autoSyncPendingTransactions = false)
		repository.fetchUserDatePreferences().getOrThrow()
		assertEquals(BigDecimal("1058540.03"), repository.fetchAccountsOverview().getOrThrow().netWorth?.balanceNumber)

		repository.createTransaction(testInput().copy(toId = 99)).getOrThrow()
		val overview = repository.readCachedAccountsOverview()!!
		assertEquals(BigDecimal("425090.83"), overview.accountTypes.first().balanceNumber)
		assertEquals(BigDecimal("1058515.03"), overview.netWorth?.balanceNumber)
		assertEquals("USD 1,058,515.03", overview.netWorth?.balance)
		assertTrue(overview.netWorth?.hasPending == true)

		repository.createTransaction(testInput().copy(toId = 4, amount = "100.00")).getOrThrow()
		val afterRepayment = repository.readCachedAccountsOverview()!!
		assertEquals(BigDecimal("1058515.03"), afterRepayment.netWorth?.balanceNumber)
		val liability = afterRepayment.accountTypes.single { it.name == "Liability" }
		assertEquals(BigDecimal("10900.00"), liability.balanceNumber)
		assertEquals("USD 10,900.00", liability.balance)
		assertEquals(BigDecimal("-10900.00"), liability.accounts.single().balanceNumber)
		assertTrue(liability.hasPending)

		repository.createTransaction(testInput().copy(fromId = 5, toId = 99, amount = "30.00")).getOrThrow()
		val afterCreditExpense = repository.readCachedAccountsOverview()!!
		assertEquals(BigDecimal("1058485.03"), afterCreditExpense.netWorth?.balanceNumber)
		val loan = afterCreditExpense.accountTypes.single { it.name == "Loan" }
		assertEquals(BigDecimal("-140270.00"), loan.balanceNumber)
		assertEquals("USD -140,270.00", loan.balance)
		assertEquals(BigDecimal("140270.00"), loan.accounts.single().balanceNumber)
		assertTrue(loan.hasPending)
		assertEquals(1, api.accountsRequestCount)
		assertTrue(api.mutationRequests.isEmpty())
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
	private val mutateResponse: TransactionResponseDto = TransactionResponseDto(success = true, id = 99),
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

	override suspend fun syncTransactionDescriptions(request: DescriptionsSyncRequestDto): DescriptionsSyncResponseDto =
		DescriptionsSyncResponseDto(true, 1, "full", descriptionsResponse.toTransactionDescriptionTemplates())

	override suspend fun syncTransactions(request: TransactionsSyncRequestDto): TransactionsSyncResponseDto {
		transactionsException?.let { throw it }
		return TransactionsSyncResponseDto(true, 1, request.accounts.map { transactionAccountResponse(it, transactionsResponse) })
	}

	override suspend fun mutateTransaction(request: TransactionMutationRequestDto): TransactionResponseDto {
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

	override fun <T> transaction(block: () -> T): T {
		val oldCache = cache.toMap()
		val oldPending = pendingTransactions.toMap()
		return try {
			block()
		} catch (error: Throwable) {
			cache.clear()
			cache.putAll(oldCache)
			pendingTransactions.clear()
			pendingTransactions.putAll(oldPending)
			throw error
		}
	}

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
	var legacyTransactionRequests = 0
	var accountRequests = 0
	var accountsError: Exception? = null
	var afterMutation: suspend () -> Unit = { }

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
		legacyTransactionRequests++
		transactionSources.add(sourceId)
		assertEquals(100, limit)
		val page = page(sourceId)
		return page.copy(data = page.data.drop(start).take(limit))
	}

	val transactionSyncRequests = mutableListOf<TransactionsSyncRequestDto>()
	val transactionSyncResponses = mutableListOf<TransactionsSyncResponseDto>()

	override suspend fun syncTransactions(request: TransactionsSyncRequestDto): TransactionsSyncResponseDto {
		transactionSyncRequests.add(request)
		return TransactionsSyncResponseDto(true, 1, request.accounts.map { scope ->
			transactionSources.add(scope.sourceId)
			transactionAccountResponse(scope, page(scope.sourceId))
		}).also { transactionSyncResponses.add(it) }
	}

	override suspend fun mutateTransaction(request: TransactionMutationRequestDto): TransactionResponseDto {
		requests.add(request)
		val id = request.id ?: transactions.values.firstOrNull { it.uuid == request.uuid }?.id ?: ((transactions.keys.maxOrNull() ?: 0L) + 1)
		val previous = transactions[id]
		val splitId = (transactions.values.flatMap { it.splits }.mapNotNull { it.id }.maxOrNull() ?: 0L) + 1
		if (request.action == "delete") {
			transactions.remove(id)
		} else if (request.action != "insert" || !transactions.containsKey(id)) {
			val split = request.splits!!.single()
			transactions[id] = TransactionDto(id, request.uuid ?: transactions[id]?.uuid, request.date!!, request.date,
				request.description!!, request.number, listOf(SplitDto(splitId, split.amount, split.amount.toBigDecimal(),
					split.fromId, "Checking", split.toId, "Savings", split.memo)))
		}
		val response = TransactionResponseDto(true, id, transactions[id]?.uuid, transaction = transactions[id]?.toRecord(),
			previousSourceIds = if (request.action == "insert") emptyList() else previous?.splits.orEmpty().flatMap { listOf(it.fromId, it.toId) }.distinct())
		afterMutation()
		return response
	}

	private fun page(sourceId: Long): TransactionsResponseDto {
		val items = transactions.values.mapNotNull { transaction ->
			val splits = transaction.splits.filter { it.fromId.toLong() == sourceId || it.toId.toLong() == sourceId }
			if (splits.isEmpty()) null else transaction.copy(splits = splits)
		}.sortedWith(compareByDescending<TransactionDto> { it.dateIso }
			.thenByDescending { item -> item.splits.mapNotNull { it.id }.maxOrNull() ?: 0L }.thenByDescending { it.id })
		return TransactionsResponseDto(true, items, items.size)
	}
}

private fun suggestion(name: String, amount: String): TransactionDescriptionTemplate =
	TransactionDescriptionTemplate(name, listOf(TransactionDescriptionTemplateSplit(amount.toBigDecimal(), 1, 2, "D", "E")))

private class SuggestionsApi : BuddiApi by FakeBuddiApi() {
	val items = linkedMapOf<String, TransactionDescriptionTemplate>()
	val syncRequests = mutableListOf<DescriptionsSyncRequestDto>()
	var legacyRequests = 0
	var nextResponse: DescriptionsSyncResponseDto? = null
	private var description: String? = null

	override suspend fun syncTransactionDescriptions(request: DescriptionsSyncRequestDto): DescriptionsSyncResponseDto {
		syncRequests.add(request)
		nextResponse?.let { nextResponse = null; return it }
		if (request.bucketHashes == null) return DescriptionsSyncResponseDto(true, 1, "full", items.values.toList())
		val previous = java.util.Base64.getDecoder().decode(request.bucketHashes)
		val buckets = items.values.groupBy { TransactionSuggestionBuckets.bucketId(it.description) }
		val changed = (0 until 256).mapNotNull { id ->
			val records = buckets[id].orEmpty()
			val hash = TransactionSuggestionBuckets.hash(id, records)
			if (hash.contentEquals(previous.copyOfRange(id * 32, (id + 1) * 32))) null
			else SuggestionBucketDto(id, java.util.Base64.getEncoder().encodeToString(hash), records)
		}
		return if (changed.isEmpty()) DescriptionsSyncResponseDto(true, 1, "unchanged")
		else DescriptionsSyncResponseDto(true, 1, "buckets", buckets = changed)
	}

	override suspend fun getTransactionDescriptions(): TransactionDescriptionsResponseDto {
		legacyRequests++
		error("Unexpected legacy download")
	}

	override suspend fun mutateTransaction(request: TransactionMutationRequestDto): TransactionResponseDto {
		val keys = listOfNotNull(description, request.description).toSet()
		description?.let { items.remove(it) }
		if (request.action != "delete") {
			description = requireNotNull(request.description)
			items[description!!] = suggestion(description!!, request.splits!!.single().amount)
		} else description = null
		return TransactionResponseDto(true, 99, request.uuid,
			SuggestionsPatchDto(keys.mapNotNull { items[it] }, keys.filter { it !in items }))
	}
}

private fun transactionAccountResponse(scope: TransactionAccountSyncRequestDto, page: TransactionsResponseDto): TransactionAccountSyncResponseDto {
	val records = page.data.map { it.toRecord() }.sortedWith(compareByDescending<TransactionRecordDto> { it.dateIso }
		.thenByDescending { item -> item.splits.mapNotNull { it.id }.maxOrNull() ?: 0L }.thenByDescending { it.id })
	val revision = java.util.Base64.getEncoder().encodeToString(TransactionBuckets.hash(scope.sourceId, -1, records))
	if (scope.bucketHashes == null) return TransactionAccountSyncResponseDto(scope.sourceId, page.total, revision, "full", records.take(100))
	val hashes = java.util.Base64.getDecoder().decode(scope.bucketHashes)
	val partition = records.take(100).groupBy { TransactionBuckets.bucketId(it.id) }
	val changed = (0 until 16).mapNotNull { id ->
		val items = partition[id].orEmpty()
		val hash = TransactionBuckets.hash(scope.sourceId, id, items)
		if (hash.contentEquals(hashes.copyOfRange(id * 32, (id + 1) * 32))) null
		else TransactionBucketDto(id, java.util.Base64.getEncoder().encodeToString(hash), items)
	}
	return if (changed.isEmpty()) TransactionAccountSyncResponseDto(scope.sourceId, page.total, revision, "unchanged")
	else TransactionAccountSyncResponseDto(scope.sourceId, page.total, revision, "buckets", buckets = changed)
}

private fun ledgerTransaction(id: Long): TransactionDto = TransactionDto(id, "uuid-$id", "2026-10-31", "2026-10-31", "Transaction $id", null,
	listOf(SplitDto(id, "1.00", "1.00".toBigDecimal(), 1, "Checking", 2, "Savings", null)))
