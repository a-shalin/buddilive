package ca.digitalcave.buddilive.mobile.data

import com.google.gson.Gson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.HttpException
import java.math.BigDecimal
import java.io.IOException
import java.util.UUID
import java.security.MessageDigest

class BuddiRepository(
    private val api: BuddiApi,
    private val descriptionsApi: BuddiApi = api,
    offlineStore: OfflineStore? = null,
    private val autoSyncPendingTransactions: Boolean = true,
    private val backendIdentity: String = "",
    private val clearSession: () -> Unit = { }
) {

    private val activeOwnerCacheKey = "active-owner:$backendIdentity"
    private val rootStore = offlineStore
    private var offlineStore = offlineStore
    private val dataLock = Any()
    private val authenticationMutex = Mutex()
    private val syncRequests = Channel<Unit>(Channel.CONFLATED)
    private var foregroundJob: Job? = null
    private var foreground = false
    private val _cacheVersion = MutableStateFlow(0L)
    val cacheVersion: StateFlow<Long> = _cacheVersion.asStateFlow()
    private val _syncState = MutableStateFlow(SyncState())
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()
    private val _sessionVersion = MutableStateFlow(0L)
    val sessionVersion: StateFlow<Long> = _sessionVersion.asStateFlow()

    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val descriptionsLoadMutex = Mutex()
    private val syncMutex = Mutex()
    private val gson = Gson()
    private val _pendingTransactionsSynced = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val _transactionDescriptionTemplates = MutableStateFlow<List<TransactionDescriptionTemplate>>(emptyList())
    val pendingTransactionsSynced: SharedFlow<Unit> =
        _pendingTransactionsSynced.asSharedFlow()
    val transactionDescriptionTemplates: StateFlow<List<TransactionDescriptionTemplate>> =
        _transactionDescriptionTemplates.asStateFlow()

    @Volatile
    private var descriptionsLoaded: Boolean = false

    suspend fun login(identifier: String, password: String, stayLoggedIn: Boolean): LoginResult = authenticationMutex.withLock {
        foregroundJob?.cancelAndJoin()
        runLoginApi {
            val response = api.login(identifier, password, if (stayLoggedIn) "on" else null)
            when {
                response.success -> {
                    withContext(Dispatchers.IO) {
                        synchronized(dataLock) {
                            val owner = MessageDigest.getInstance("SHA-256")
                                .digest("$backendIdentity\n${identifier.trim()}".toByteArray())
                                .joinToString("") { "%02x".format(it) }
                            offlineStore = rootStore?.forOwner(owner)
                            rootStore?.saveCache(activeOwnerCacheKey, owner)
                            rootStore?.saveCache(SESSION_INITIALIZED_KEY, "true")
                            resetTransactionDescriptionTemplates()
                            _syncState.value = SyncState(isPrepared = offlineStore?.loadCache(READY_KEY) == "true")
                            _sessionVersion.update { it + 1 }
                            notifyCacheChanged()
                        }
                    }
                    setForeground(foreground)
                    requestSync()
                    LoginResult.Success
                }
                !response.next.isNullOrBlank() -> LoginResult.NextStep(response.next)
                else -> LoginResult.Error("Login failed.")
            }
        }
    }

    suspend fun restoreLocalSession(): Boolean = withContext(Dispatchers.IO) {
        synchronized(dataLock) {
            val savedOwner = rootStore?.loadCache(activeOwnerCacheKey)
            if (savedOwner == null && rootStore?.loadCache(SESSION_INITIALIZED_KEY) == null &&
                rootStore?.loadCache(ACCOUNTS_CACHE_KEY) != null) {
                offlineStore = rootStore
                _syncState.value = SyncState(needsLogin = true)
                notifyCacheChanged()
                return@synchronized true
            }
            val owner = savedOwner?.takeIf { it.isNotBlank() } ?: return@synchronized false
            offlineStore = rootStore.forOwner(owner)
            _syncState.value = SyncState(isPrepared = offlineStore?.loadCache(READY_KEY) == "true")
            notifyCacheChanged()
            true
        }
    }

    suspend fun hasValidSession(): Boolean = readCachedAccountsOverview() != null

    suspend fun logout() = authenticationMutex.withLock {
        setForeground(false)
        foregroundJob?.cancelAndJoin()
        withContext(Dispatchers.IO) {
            synchronized(dataLock) {
                rootStore?.saveCache(activeOwnerCacheKey, "")
                rootStore?.saveCache(SESSION_INITIALIZED_KEY, "true")
                offlineStore = null
                resetTransactionDescriptionTemplates()
                _syncState.value = SyncState()
                _sessionVersion.update { it + 1 }
                notifyCacheChanged()
            }
        }
        try {
            withTimeoutOrNull(2_000L) {
                runApi {
                    api.logout()
                    Result.success(Unit)
                }
            }
        } finally {
            clearSession()
        }
    }

    fun setForeground(value: Boolean) {
        foreground = value
        if (!value) {
            foregroundJob?.cancel()
            return
        }
        if (!autoSyncPendingTransactions || foregroundJob?.isActive == true) {
            return
        }
        foregroundJob = repositoryScope.launch {
            var retryMillis = 15_000L
            do {
                while (syncRequests.tryReceive().isSuccess) { }
                val result = synchronize()
                val delayMillis = if (result.isSuccess) 60_000L else retryMillis
                retryMillis = if (result.isSuccess) 15_000L else (retryMillis * 2).coerceAtMost(60_000L)
                withTimeoutOrNull(delayMillis) { syncRequests.receive() }
            } while (foreground)
        }
    }

    fun requestSync() {
        if (foreground && !_syncState.value.needsLogin) {
            syncRequests.trySend(Unit)
        }
    }

    fun refreshAsync() {
        if (_syncState.value.needsLogin) {
            return
        }
        repositoryScope.launch {
            synchronize()
        }
    }

    suspend fun readCachedAccountsOverview(): AccountsOverview? = withContext(Dispatchers.IO) {
        synchronized(dataLock) {
            loadCachedResponse(ACCOUNTS_CACHE_KEY, AccountsResponseDto::class.java)?.getOrNull()
                ?.let { projectAccountsOverview(it.toAccountsOverview()) }
        }
    }

    suspend fun readCachedAccountData(accountId: Long): CachedAccountData = withContext(Dispatchers.IO) {
        synchronized(dataLock) {
            loadCachedResponse(TRANSACTION_DESCRIPTIONS_CACHE_KEY, TransactionDescriptionsResponseDto::class.java)
                ?.getOrNull()?.let { response ->
                    _transactionDescriptionTemplates.update { mergeDescriptionTemplates(response.toTransactionDescriptionTemplates(), it) }
                }
            val pending = pendingTransactionsForSource(accountId)
            val hasPage = offlineStore?.loadCache(transactionsCacheKey(accountId, 0, RECENT_LIMIT)) != null
            CachedAccountData(
                page = if (hasPage || pending.isNotEmpty()) localTransactionsPage(accountId, RECENT_LIMIT, pending) else null,
                account = loadCachedAccount(accountId),
                fromSources = loadCachedResponse(sourcesCacheKey(SOURCE_DIRECTION_FROM), SourcesResponseDto::class.java)
                    ?.getOrNull()?.toSourceOptions().orEmpty(),
                toSources = loadCachedResponse(sourcesCacheKey(SOURCE_DIRECTION_TO), SourcesResponseDto::class.java)
                    ?.getOrNull()?.toSourceOptions().orEmpty(),
                preferences = loadCachedUserDatePreferences()
            )
        }
    }

    suspend fun legacyPendingCount(): Int = withContext(Dispatchers.IO) {
        synchronized(dataLock) {
            val claimedOwner = rootStore?.loadCache(LEGACY_OWNER_KEY)
            val owner = rootStore?.loadCache(activeOwnerCacheKey)
            if (owner.isNullOrBlank() || offlineStore === rootStore ||
                offlineStore?.loadCache(LEGACY_IMPORTED_KEY) == "true" ||
                claimedOwner != null && claimedOwner != owner) 0
            else rootStore.listPendingTransactions().size
        }
    }

    suspend fun restoreLegacyTransactions(): Result<Unit> = withContext(Dispatchers.IO) {
        syncMutex.withLock {
            runCatching {
                synchronized(dataLock) {
                    val root = requireNotNull(rootStore)
                    val store = requireNotNull(offlineStore)
                    val owner = requireNotNull(root.loadCache(activeOwnerCacheKey)).also { require(it.isNotBlank()) }
                    check(root.loadCache(LEGACY_OWNER_KEY).let { it == null || it == owner })
                    if (store.loadCache(LEGACY_IMPORTED_KEY) == "true") return@synchronized
                    val sources = listOf(SOURCE_DIRECTION_FROM, SOURCE_DIRECTION_TO).flatMap { direction ->
                        loadCachedResponse(sourcesCacheKey(direction), SourcesResponseDto::class.java)
                            ?.getOrNull()?.toSourceOptions().orEmpty()
                    }.mapNotNull { it.id }.toSet()
                    val pending = root.listPendingTransactions()
                    check(pending.all { it.fromId in sources && it.toId in sources }) {
                        "Download this account's categories first, and check that these transactions belong to this account."
                    }
                    root.saveCache(LEGACY_OWNER_KEY, owner)
                    store.transaction {
                        val existing = store.listPendingTransactions().mapTo(mutableSetOf()) { it.localUuid }
                        val accountIds = root.loadCache(ACCOUNTS_CACHE_KEY)?.let {
                            gson.fromJson(it, AccountsResponseDto::class.java).toAccountSummaries().map { account -> account.id }
                        }.orEmpty()
                        val keys = listOf(ACCOUNTS_CACHE_KEY, USER_PREFERENCES_CACHE_KEY, TRANSACTION_DESCRIPTIONS_CACHE_KEY,
                            sourcesCacheKey(SOURCE_DIRECTION_FROM), sourcesCacheKey(SOURCE_DIRECTION_TO)) +
                            accountIds.map { transactionsCacheKey(it, 0, RECENT_LIMIT) }
                        for (key in keys) root.loadCache(key)?.let { store.saveCache(key, it) }
                        for (transaction in pending) {
                            if (transaction.localUuid !in existing) store.upsertPendingTransaction(transaction)
                        }
                        store.saveCache(LEGACY_IMPORTED_KEY, "true")
                    }
                    notifyCacheChanged()
                    requestSync()
                }
            }
        }
    }

    private fun notifyCacheChanged() {
        _cacheVersion.update { it + 1 }
    }

    suspend fun fetchAccounts(): Result<List<AccountSummary>> {
        return fetchAccountsResponse().map { response ->
            projectAccountsOverview(response.toAccountsOverview()).accountTypes
                .flatMap { accountType -> accountType.accounts }
                .sortedBy { it.name.lowercase() }
        }
    }

    suspend fun fetchAccountsOverview(): Result<AccountsOverview> {
        return fetchAccountsResponse().map { response ->
            projectAccountsOverview(response.toAccountsOverview())
        }
    }

    fun loadCachedAccount(accountId: Long): AccountSummary? {
        val response = loadCachedResponse(ACCOUNTS_CACHE_KEY, AccountsResponseDto::class.java)
            ?.getOrNull()
            ?: return null
        return projectAccountsOverview(response.toAccountsOverview())
            .accountTypes
            .asSequence()
            .flatMap { accountType -> accountType.accounts.asSequence() }
            .firstOrNull { account -> account.id == accountId }
    }

    suspend fun fetchTransactions(sourceId: Long, start: Int, limit: Int): Result<TransactionsPage> {
        val pendingTransactions = pendingTransactionsForSource(sourceId)
        if (start == 0 && pendingTransactions.isNotEmpty()) {
            return Result.success(localTransactionsPage(sourceId, limit, pendingTransactions))
        }
        val pendingOffset = if (start > 0) pendingTransactions.size else 0
        val serverStart = (start - pendingOffset).coerceAtLeast(0)
        val responseResult = fetchTransactionsResponse(sourceId = sourceId, start = serverStart, limit = limit)
        return responseResult.fold(
            onSuccess = { response ->
                val serverPage = response.toTransactionsPage()
                if (serverStart > 0) {
                    Result.success(serverPage.copy(total = serverPage.total + pendingTransactions.size))
                }
                else {
                    val pendingItems = pendingTransactions.map { pendingTransaction ->
                        pendingTransaction.toTransactionSummary()
                    }
                    val mergedItems = (pendingItems + serverPage.items)
                        .distinctBy { transaction -> transaction.id }
                        .sortedByDescending { transaction -> transaction.dateIso }
                    Result.success(
                        TransactionsPage(
                            items = mergedItems,
                            total = serverPage.total + pendingItems.size
                        )
                    )
                }
            },
            onFailure = { error ->
                if (start == 0 && error is OfflineUnavailableException) {
                    val pendingItems = pendingTransactions.map { pendingTransaction ->
                        pendingTransaction.toTransactionSummary()
                    }.sortedByDescending { transaction -> transaction.dateIso }
                    Result.success(TransactionsPage(items = pendingItems, total = pendingItems.size))
                }
                else {
                    Result.failure(error)
                }
            }
        )
    }

    suspend fun fetchTransactionDescriptionTemplates(): Result<List<TransactionDescriptionTemplate>> {
        if (descriptionsLoaded) {
            return Result.success(_transactionDescriptionTemplates.value)
        }
        return loadTransactionDescriptionTemplatesIntoStore()
    }

    private suspend fun loadTransactionDescriptionTemplatesIntoStore(): Result<List<TransactionDescriptionTemplate>> {
        if (descriptionsLoaded) {
            return Result.success(_transactionDescriptionTemplates.value)
        }
        return descriptionsLoadMutex.withLock {
            if (descriptionsLoaded) {
                return@withLock Result.success(_transactionDescriptionTemplates.value)
            }
            val result = fetchTransactionDescriptionResponse()
                .map { response -> response.toTransactionDescriptionTemplates() }
            result.onSuccess { templates ->
                _transactionDescriptionTemplates.update { existing ->
                    mergeDescriptionTemplates(remote = templates, local = existing)
                }
                descriptionsLoaded = true
            }
            if (result.isSuccess) {
                Result.success(_transactionDescriptionTemplates.value)
            } else {
                result
            }
        }
    }

    private fun mergeDescriptionTemplates(
        remote: List<TransactionDescriptionTemplate>,
        local: List<TransactionDescriptionTemplate>
    ): List<TransactionDescriptionTemplate> {
        if (local.isEmpty()) {
            return remote
        }
        val mergedByDescription = LinkedHashMap<String, TransactionDescriptionTemplate>()
        for (template in remote) {
            mergedByDescription[template.description.trim().lowercase()] = template
        }
        for (template in local) {
            mergedByDescription[template.description.trim().lowercase()] = template
        }
        return mergedByDescription.values.toList()
    }

    private fun addOrUpdateTransactionDescriptionTemplate(input: TransactionEditInput) {
        val description = input.description.trim()
        if (description.isBlank()) {
            return
        }
        val amountNumber = input.amount.toBigDecimalOrNull() ?: return
        val updatedTemplate = TransactionDescriptionTemplate(
            description = description,
            splits = listOf(
                TransactionDescriptionTemplateSplit(
                    amountNumber = amountNumber,
                    fromId = input.fromId,
                    toId = input.toId,
                    fromType = null,
                    toType = null
                )
            )
        )
        _transactionDescriptionTemplates.update { templates ->
            val existingIndex = templates.indexOfFirst { it.description.equals(description, ignoreCase = true) }
            if (existingIndex < 0) {
                return@update templates + updatedTemplate
            }
            templates.toMutableList().apply {
                set(existingIndex, updatedTemplate)
            }
        }
    }

    private fun resetTransactionDescriptionTemplates() {
        descriptionsLoaded = false
        _transactionDescriptionTemplates.value = emptyList()
    }

    suspend fun fetchSources(direction: String): Result<List<SourceOption>> {
        return fetchSourcesResponse(direction).map { response ->
            response.toSourceOptions()
        }
    }

    suspend fun fetchUserDatePreferences(): Result<UserDatePreferences> {
        return fetchUserPreferencesResponse().fold(
            onSuccess = { response ->
                Result.success(response.toUserDatePreferences())
            },
            onFailure = { error ->
                if (error is OfflineUnavailableException) {
                    Result.success(defaultUserDatePreferences())
                }
                else {
                    Result.failure(error)
                }
            }
        )
    }

    suspend fun createTransaction(input: TransactionEditInput): Result<Unit> = withContext(Dispatchers.IO) {
        val store = offlineStore
        if (store != null) {
            val now = System.currentTimeMillis()
            return@withContext runCatching {
                synchronized(dataLock) {
                    store.upsertPendingTransaction(
                        PendingTransaction(
                            localUuid = UUID.randomUUID().toString(),
                            status = PendingTransactionStatus.PENDING,
                            dateIso = input.dateIso,
                            description = input.description,
                            number = input.number,
                            amount = input.amount,
                            fromId = input.fromId,
                            toId = input.toId,
                            memo = input.memo,
                            createdAt = now,
                            modifiedAt = now,
                            lastError = null,
                            attemptCount = 0
                        )
                    )
                    notifyCacheChanged()
                    addOrUpdateTransactionDescriptionTemplate(input)
                }
            }
        }
        return@withContext mutateTransaction(
            TransactionMutationRequestDto(
                action = "insert",
                date = input.dateIso,
                description = input.description,
                number = input.number,
                splits = listOf(
                    SplitMutationRequestDto(
                        amount = input.amount,
                        fromId = input.fromId,
                        toId = input.toId,
                        memo = input.memo
                    )
                )
            )
        ).onSuccess {
            addOrUpdateTransactionDescriptionTemplate(input)
        }
    }

    suspend fun updateTransaction(transactionId: Long, input: TransactionEditInput): Result<Unit> = withContext(Dispatchers.IO) {
        val updated = runCatching {
            synchronized(dataLock) {
                val pending = findPendingTransaction(transactionId) ?: return@synchronized false
                offlineStore?.upsertPendingTransaction(pending.copy(
                    status = PendingTransactionStatus.PENDING,
                    dateIso = input.dateIso,
                    description = input.description,
                    number = input.number,
                    amount = input.amount,
                    fromId = input.fromId,
                    toId = input.toId,
                    memo = input.memo,
                    modifiedAt = System.currentTimeMillis(),
                    lastError = null,
                    sync = pending.sync.copy(acknowledged = false)
                ))
                addOrUpdateTransactionDescriptionTemplate(input)
                notifyCacheChanged()
                true
            }
        }.getOrElse { return@withContext Result.failure(it) }
        if (updated) {
            requestSync()
            return@withContext Result.success(Unit)
        }
        val serverId = resolveTransactionId(transactionId)
            ?: return@withContext Result.failure(IllegalStateException("This transaction is no longer available. Refresh and reopen it."))
        mutateTransaction(TransactionMutationRequestDto(
            action = "update", id = serverId, date = input.dateIso,
            description = input.description, number = input.number,
            splits = listOf(SplitMutationRequestDto(input.amount, input.fromId, input.toId, input.memo))
        )).onSuccess { addOrUpdateTransactionDescriptionTemplate(input) }
    }

    suspend fun deleteTransaction(transactionId: Long): Result<Unit> = withContext(Dispatchers.IO) {
        val deleted = runCatching {
            synchronized(dataLock) {
                val pending = findPendingTransaction(transactionId) ?: return@synchronized false
                if (pending.sync.submitted == null && pending.sync.serverId == null) {
                    offlineStore?.deletePendingTransaction(pending.localUuid)
                } else {
                    offlineStore?.upsertPendingTransaction(pending.copy(
                        status = PendingTransactionStatus.PENDING,
                        sync = pending.sync.copy(deleted = true, acknowledged = false)
                    ))
                }
                notifyCacheChanged()
                true
            }
        }.getOrElse { return@withContext Result.failure(it) }
        if (deleted) {
            requestSync()
            return@withContext Result.success(Unit)
        }
        val serverId = resolveTransactionId(transactionId)
            ?: return@withContext Result.failure(IllegalStateException("This transaction is no longer available. Refresh and reopen it."))
        mutateTransaction(TransactionMutationRequestDto(action = "delete", id = serverId))
    }

    private fun resolveTransactionId(transactionId: Long): Long? = synchronized(dataLock) {
        if (transactionId >= 0) transactionId else offlineStore?.loadCache(transactionIdCacheKey(transactionId))?.toLongOrNull()
    }

    private suspend fun mutateTransaction(request: TransactionMutationRequestDto): Result<Unit> {
        return syncMutex.withLock {
            runApi {
                val response = api.mutateTransaction(request)
                if (!response.success) {
                    return@runApi Result.failure(IllegalStateException("Transaction mutation failed."))
                }
                requestSync()
                Result.success(Unit)
            }
        }
    }

    fun syncPendingTransactionsAsync() = requestSync()

    suspend fun syncPendingTransactions(): Result<Int> = synchronize()

    suspend fun synchronize(): Result<Int> = withContext(Dispatchers.IO) {
        syncMutex.withLock {
            if (_syncState.value.needsLogin || offlineStore == null) {
                return@withLock Result.failure(UnauthorizedException())
            }
            _syncState.update { it.copy(isSyncing = true, error = null) }
            var uploaded = 0
            try {
                val uploadResult = uploadPendingTransactions()
                uploaded = uploadResult.getOrDefault(0)
                // A timed-out insert may already exist remotely. Keep the old balance baseline until its UUID is acknowledged.
                uploadResult.getOrThrow()
                fetchUserPreferencesResponse(forceNetwork = true).getOrThrow()
                val accounts = fetchAccountsResponse(forceNetwork = true).getOrThrow().toAccountSummaries()
                fetchSourcesResponse(SOURCE_DIRECTION_FROM, forceNetwork = true).getOrThrow()
                fetchSourcesResponse(SOURCE_DIRECTION_TO, forceNetwork = true).getOrThrow()
                for (account in accounts) {
                    fetchTransactionsResponse(account.id, 0, RECENT_LIMIT, forceNetwork = true).getOrThrow()
                }
                val descriptions = fetchTransactionDescriptionResponse(forceNetwork = true).getOrThrow()
                _transactionDescriptionTemplates.update { mergeDescriptionTemplates(descriptions.toTransactionDescriptionTemplates(), it) }
                descriptionsLoaded = true
                synchronized(dataLock) {
                    offlineStore?.transaction {
                        for (pending in offlineStore?.listPendingTransactions().orEmpty()) {
                            if (pending.sync.acknowledged) {
                                pending.sync.serverId?.let { serverId ->
                                    offlineStore?.saveCache(transactionIdCacheKey(pending.id), serverId.toString())
                                }
                                offlineStore?.deletePendingTransaction(pending.localUuid)
                            }
                        }
                        offlineStore?.saveCache(READY_KEY, "true")
                    }
                    notifyCacheChanged()
                }
                if (uploaded > 0) _pendingTransactionsSynced.tryEmit(Unit)
                _syncState.update { it.copy(isPrepared = true, error = null) }
                Result.success(uploaded)
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                _syncState.update { it.copy(
                    needsLogin = exception is UnauthorizedException,
                    error = if (exception is UnauthorizedException) "Sign in to sync" else "Unable to sync. Changes are saved on this device."
                ) }
                if (uploaded > 0) _pendingTransactionsSynced.tryEmit(Unit)
                Result.failure(exception)
            } finally {
                _syncState.update { it.copy(isSyncing = false) }
            }
        }
    }

    private suspend fun uploadPendingTransactions(): Result<Int> {
        val store = offlineStore ?: return Result.success(0)
        var syncedCount = 0
        val uuids = synchronized(dataLock) { store.listPendingTransactions().map { it.localUuid } }
        for (uuid in uuids) {
            val sending = synchronized(dataLock) {
                val current = store.listPendingTransactions().firstOrNull { it.localUuid == uuid }
                    ?: return@synchronized null
                if (current.status == PendingTransactionStatus.FAILED || current.sync.acknowledged) {
                    return@synchronized null
                }
                current.copy(
                    status = PendingTransactionStatus.SYNCING,
                    attemptCount = current.attemptCount + 1,
                    sync = current.sync.copy(submitted = current.sync.submitted ?: current.toEditInput())
                ).also {
                    store.upsertPendingTransaction(it)
                    notifyCacheChanged()
                }
            } ?: continue
            // Replay the exact submitted insert after a lost response before sending subsequent edits.
            val deleting = sending.sync.deleted && sending.sync.serverId != null
            val request = if (deleting) {
                TransactionMutationRequestDto(action = "delete", id = sending.sync.serverId)
            } else {
                val input = requireNotNull(sending.sync.submitted)
                TransactionMutationRequestDto(
                    action = if (sending.sync.serverId == null) "insert" else "update",
                    id = sending.sync.serverId, uuid = sending.localUuid,
                    date = input.dateIso, description = input.description, number = input.number,
                    splits = listOf(SplitMutationRequestDto(input.amount, input.fromId, input.toId, input.memo))
                )
            }
            val result = runApi {
                val response = api.mutateTransaction(request)
                if (!response.success) Result.failure(IllegalArgumentException("Transaction rejected."))
                else Result.success(response)
            }
            synchronized(dataLock) {
                val current = store.listPendingTransactions().first { it.localUuid == uuid }
                result.fold(onSuccess = { response ->
                    val serverInput = if (deleting) null else sending.sync.submitted
                    val serverId = response.id ?: sending.sync.serverId
                    if (!deleting && serverId == null) {
                        throw IllegalStateException("Transaction response did not include its ID.")
                    }
                    val acknowledged = if (deleting) current.sync.deleted
                        else !current.sync.deleted && current.toEditInput() == serverInput
                    store.upsertPendingTransaction(current.copy(
                        status = PendingTransactionStatus.PENDING, lastError = null,
                        sync = current.sync.copy(
                            serverId = serverId, serverInput = serverInput, submitted = null,
                            acknowledged = acknowledged
                        )
                    ))
                    if (!acknowledged) requestSync()
                    syncedCount++
                }, onFailure = { error ->
                    val rejected = error is IllegalArgumentException || error is HttpException && error.code() in 400..499
                    store.upsertPendingTransaction(current.copy(
                        status = if (rejected) PendingTransactionStatus.FAILED else PendingTransactionStatus.PENDING,
                        lastError = error.message,
                        sync = if (rejected) current.sync.copy(submitted = null) else current.sync
                    ))
                })
                notifyCacheChanged()
            }
            val error = result.exceptionOrNull()
            if (error != null && !(error is IllegalArgumentException || error is HttpException && error.code() in 400..499)) {
                return Result.failure(error)
            }
        }
        return Result.success(syncedCount)
    }

    private fun PendingTransaction.toEditInput(): TransactionEditInput =
        TransactionEditInput(dateIso, description, number, amount, fromId, toId, memo)

    private suspend fun fetchAccountsResponse(forceNetwork: Boolean = false): Result<AccountsResponseDto> {
        return fetchCachedResponse(
            cacheKey = ACCOUNTS_CACHE_KEY,
            responseClass = AccountsResponseDto::class.java,
            failureMessage = "Accounts request failed.",
            request = { api.getAccounts() },
            isSuccess = { response -> response.success },
            forceNetwork = forceNetwork
        )
    }

    private suspend fun fetchTransactionsResponse(sourceId: Long, start: Int, limit: Int, forceNetwork: Boolean = false): Result<TransactionsResponseDto> {
        return fetchCachedResponse(
            cacheKey = transactionsCacheKey(sourceId, start, limit),
            responseClass = TransactionsResponseDto::class.java,
            failureMessage = "Transactions request failed.",
            request = { api.getTransactions(sourceId = sourceId, start = start, limit = limit) },
            isSuccess = { response -> response.success },
            forceNetwork = forceNetwork
        )
    }

    private suspend fun fetchSourcesResponse(direction: String, forceNetwork: Boolean = false): Result<SourcesResponseDto> {
        return fetchCachedResponse(
            cacheKey = sourcesCacheKey(direction),
            responseClass = SourcesResponseDto::class.java,
            failureMessage = "Sources request failed.",
            request = { api.getSources(direction) },
            isSuccess = { response -> response.success },
            forceNetwork = forceNetwork
        )
    }

    private suspend fun fetchUserPreferencesResponse(forceNetwork: Boolean = false): Result<UserPreferencesResponseDto> {
        return fetchCachedResponse(
            cacheKey = USER_PREFERENCES_CACHE_KEY,
            responseClass = UserPreferencesResponseDto::class.java,
            failureMessage = "User preferences request failed.",
            request = { api.getUserPreferences() },
            isSuccess = { response -> response.success },
            forceNetwork = forceNetwork
        )
    }

    private suspend fun fetchTransactionDescriptionResponse(forceNetwork: Boolean = false): Result<TransactionDescriptionsResponseDto> {
        return fetchCachedResponse(
            cacheKey = TRANSACTION_DESCRIPTIONS_CACHE_KEY,
            responseClass = TransactionDescriptionsResponseDto::class.java,
            failureMessage = "Transaction descriptions request failed.",
            request = { descriptionsApi.getTransactionDescriptions() },
            isSuccess = { response -> response.success },
            forceNetwork = forceNetwork
        )
    }

    private suspend fun <T> fetchCachedResponse(
        cacheKey: String,
        responseClass: Class<T>,
        failureMessage: String,
        request: suspend () -> T,
        isSuccess: (T) -> Boolean,
        forceNetwork: Boolean = false
    ): Result<T> = withContext(Dispatchers.IO) {
        val store = offlineStore
        if (!forceNetwork) {
            val cached = synchronized(dataLock) { loadCachedResponse(cacheKey, responseClass) }
            if (cached?.isSuccess == true) return@withContext cached
        }
        try {
            val response = request()
            if (!isSuccess(response)) {
                Result.failure(IllegalStateException(failureMessage))
            }
            else {
                synchronized(dataLock) {
                    if (store !== offlineStore) throw CancellationException("Account changed")
                    store?.transaction {
                        val json = gson.toJson(response)
                        if (cacheKey.startsWith("transactions:") && cacheKey.endsWith(":0:$RECENT_LIMIT") &&
                            store.loadCache(cacheKey) != json) {
                            store.deleteCachePrefix(cacheKey.substringBeforeLast(':').substringBeforeLast(':') + ":")
                        }
                        store.saveCache(cacheKey, json)
                        if (cacheKey == ACCOUNTS_CACHE_KEY && forceNetwork) {
                            for (pending in store.listPendingTransactions()) {
                                store.upsertPendingTransaction(pending.copy(sync = pending.sync.copy(balanceInput = pending.sync.serverInput)))
                            }
                        }
                    }
                    notifyCacheChanged()
                }
                Result.success(response)
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: HttpException) {
            if (exception.code() == 401 || exception.code() == 403) {
                _syncState.update { it.copy(needsLogin = true, error = "Sign in to sync") }
                Result.failure(UnauthorizedException())
            }
            else {
                Result.failure(exception)
            }
        } catch (exception: IOException) {
            Result.failure(exception)
        } catch (exception: Exception) {
            Result.failure(exception)
        }
    }

    private fun <T> loadCachedResponse(cacheKey: String, responseClass: Class<T>): Result<T>? {
        val cachedValue = offlineStore?.loadCache(cacheKey) ?: return null
        return runCatching {
            gson.fromJson(cachedValue, responseClass)
        }
    }

    private fun pendingTransactionsForSource(sourceId: Long): List<PendingTransaction> {
        return offlineStore
            ?.listPendingTransactions()
            ?.filter { pendingTransaction ->
                !pendingTransaction.sync.deleted && (pendingTransaction.fromId.toLong() == sourceId || pendingTransaction.toId.toLong() == sourceId)
            }
            .orEmpty()
    }

    fun fetchPendingTransactions(sourceId: Long): List<TransactionSummary> {
        return pendingTransactionsForSource(sourceId)
            .map { pendingTransaction -> pendingTransaction.toTransactionSummary() }
            .sortedByDescending { transaction -> transaction.dateIso }
    }

    private fun localTransactionsPage(
        sourceId: Long,
        limit: Int,
        pendingTransactions: List<PendingTransaction>
    ): TransactionsPage {
        val pendingItems = pendingTransactions
            .map { pendingTransaction -> pendingTransaction.toTransactionSummary() }
        val cachedPage = loadCachedResponse(transactionsCacheKey(sourceId, 0, limit), TransactionsResponseDto::class.java)
            ?.getOrNull()
            ?.toTransactionsPage()
        val allPending = offlineStore?.listPendingTransactions().orEmpty()
        val hiddenUuids = allPending.mapTo(mutableSetOf()) { it.localUuid }
        val cachedItems = cachedPage?.items.orEmpty().filter { it.uuid !in hiddenUuids }
        val mergedItems = (pendingItems + cachedItems)
            .distinctBy { transaction -> transaction.id }
            .sortedByDescending { transaction -> transaction.dateIso }
        return TransactionsPage(
            items = mergedItems,
            total = (cachedPage?.total ?: 0) + pendingItems.size - cachedPage?.items.orEmpty().count { it.uuid in hiddenUuids }
        )
    }

    private fun findPendingTransaction(transactionId: Long): PendingTransaction? {
        return offlineStore
            ?.listPendingTransactions()
            ?.firstOrNull { pendingTransaction -> pendingTransaction.id == transactionId }
    }

    private fun projectAccountsOverview(overview: AccountsOverview): AccountsOverview {
        val pendingTransactions = offlineStore?.listPendingTransactions().orEmpty()
        if (pendingTransactions.isEmpty()) {
            return overview
        }
        val preferences = loadCachedUserDatePreferences()
        val projectedAccountTypes = overview.accountTypes.map { accountType ->
            val projectedAccounts = accountType.accounts.map { account ->
                projectAccount(account, pendingTransactions, preferences)
            }
            if (projectedAccounts.none { account -> account.hasPending }) {
                accountType.copy(accounts = projectedAccounts)
            }
            else {
                val typeBalanceNumber = projectedAccounts.fold(BigDecimal.ZERO) { total, account ->
                    val accountBalance = account.balanceNumber ?: return@fold total
                    total + if (account.debit) accountBalance else accountBalance.negate()
                }
                accountType.copy(
                    balance = formatCurrencyAmount(typeBalanceNumber, preferences),
                    accounts = projectedAccounts,
                    balanceNumber = typeBalanceNumber,
                    hasPending = true
                )
            }
        }
        val hasPending = projectedAccountTypes.any { accountType -> accountType.hasPending }
        return overview.copy(
            accountTypes = projectedAccountTypes,
            netWorth = if (hasPending && overview.netWorth != null) {
                val netWorthBalanceNumber = projectedAccountTypes.flatMap { accountType -> accountType.accounts }
                    .fold(BigDecimal.ZERO) { total, account ->
                        val accountBalance = account.balanceNumber ?: return@fold total
                        total + if (account.debit) accountBalance else accountBalance.negate()
                    }
                overview.netWorth.copy(
                    balance = formatCurrencyAmount(netWorthBalanceNumber, preferences),
                    balanceNumber = netWorthBalanceNumber,
                    hasPending = true
                )
            }
            else {
                overview.netWorth
            }
        )
    }

    private fun projectAccount(
        account: AccountSummary,
        pendingTransactions: List<PendingTransaction>,
        preferences: UserDatePreferences
    ): AccountSummary {
        val balanceNumber = account.balanceNumber ?: return account
        fun contribution(input: TransactionEditInput?): BigDecimal {
            if (input == null) return BigDecimal.ZERO
            val amount = input.amount.toBigDecimalOrNull() ?: return BigDecimal.ZERO
            return (if (input.toId.toLong() == account.id) amount else BigDecimal.ZERO) -
                (if (input.fromId.toLong() == account.id) amount else BigDecimal.ZERO)
        }
        val delta = pendingTransactions.fold(BigDecimal.ZERO) { total, pending ->
            total + contribution(if (pending.sync.deleted) null else pending.toEditInput()) - contribution(pending.sync.balanceInput)
        }
        if (delta.compareTo(BigDecimal.ZERO) == 0) {
            return account
        }
        val projectedBalance = balanceNumber + delta
        val displayBalance = if (account.debit) projectedBalance else projectedBalance.negate()
        return account.copy(
            balance = formatCurrencyAmount(displayBalance, preferences),
            balanceNumber = projectedBalance,
            hasPending = true
        )
    }

    private fun PendingTransaction.toTransactionSummary(): TransactionSummary {
        val amountNumber = amount.toBigDecimalOrNull() ?: BigDecimal.ZERO
        return TransactionSummary(
            id = id,
            uuid = localUuid,
            dateIso = dateIso,
            description = description,
            number = number,
            split = TransactionSplitSummary(
                amountLabel = formatCurrencyAmount(amountNumber, loadCachedUserDatePreferences()),
                amountNumber = amountNumber,
                fromId = fromId,
                fromName = sourceName(fromId),
                toId = toId,
                toName = sourceName(toId),
                memo = memo
            ),
            pendingStatus = status,
            pendingError = lastError
        )
    }

    private val PendingTransaction.id: Long
        get() = pendingTransactionId(localUuid)

    private fun sourceName(sourceId: Int): String {
        for (direction in listOf(SOURCE_DIRECTION_FROM, SOURCE_DIRECTION_TO)) {
            val cachedValue = offlineStore?.loadCache(sourcesCacheKey(direction)) ?: continue
            val response = runCatching {
                gson.fromJson(cachedValue, SourcesResponseDto::class.java)
            }.getOrNull() ?: continue
            val source = response.toSourceOptions().firstOrNull { option -> option.id == sourceId }
            if (source != null) {
                return source.label.trim()
            }
        }
        return "#$sourceId"
    }

    private fun loadCachedUserDatePreferences(): UserDatePreferences {
        val cachedValue = offlineStore?.loadCache(USER_PREFERENCES_CACHE_KEY) ?: return defaultUserDatePreferences()
        return runCatching {
            gson.fromJson(cachedValue, UserPreferencesResponseDto::class.java).toUserDatePreferences()
        }.getOrDefault(defaultUserDatePreferences())
    }

    private suspend fun <T> runApi(block: suspend () -> Result<T>): Result<T> {
        return try {
            block()
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: HttpException) {
            if (exception.code() == 401 || exception.code() == 403) {
                _syncState.update { it.copy(needsLogin = true, error = "Sign in to sync") }
                Result.failure(UnauthorizedException())
            } else {
                Result.failure(exception)
            }
        } catch (exception: IOException) {
            Result.failure(exception)
        } catch (exception: Exception) {
            Result.failure(exception)
        }
    }

    private suspend fun runLoginApi(block: suspend () -> LoginResult): LoginResult {
        return try {
            block()
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: HttpException) {
            if (exception.code() == 401 || exception.code() == 403) {
                LoginResult.Error("Authentication is required.")
            } else {
                LoginResult.Error("Login request failed: HTTP ${exception.code()}.")
            }
        } catch (exception: IOException) {
            LoginResult.Error("Network error: ${exception.message ?: "unknown"}")
        } catch (exception: Exception) {
            LoginResult.Error("Login failed: ${exception.message ?: "unknown"}")
        }
    }
}

private fun transactionsCacheKey(sourceId: Long, start: Int, limit: Int): String {
    return "transactions:$sourceId:$start:$limit"
}

private fun transactionIdCacheKey(localId: Long): String = "transaction-id:$localId"

private fun sourcesCacheKey(direction: String): String {
    return "sources:$direction"
}

private fun pendingTransactionId(localUuid: String): Long {
    return -((localUuid.hashCode().toLong() and 0x00000000ffffffffL) + 1L)
}

sealed interface LoginResult {
    data object Success : LoginResult
    data class NextStep(val nextStep: String) : LoginResult
    data class Error(val message: String) : LoginResult
}

class UnauthorizedException : RuntimeException("Session is not authenticated.")
class OfflineUnavailableException : RuntimeException("Open this account once while online before using it offline.")

private const val ACCOUNTS_CACHE_KEY = "accounts"
private const val USER_PREFERENCES_CACHE_KEY = "user-preferences"
private const val TRANSACTION_DESCRIPTIONS_CACHE_KEY = "transaction-descriptions"
private const val SOURCE_DIRECTION_FROM = "from"
private const val SOURCE_DIRECTION_TO = "to"

private const val RECENT_LIMIT = 100
private const val SESSION_INITIALIZED_KEY = "session-initialized"
private const val READY_KEY = "offline-ready"

private const val LEGACY_OWNER_KEY = "legacy-owner"
private const val LEGACY_IMPORTED_KEY = "legacy-imported"
