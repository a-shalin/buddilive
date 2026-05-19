package ca.digitalcave.buddilive.mobile.data

import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.HttpException
import java.math.BigDecimal
import java.io.IOException
import java.util.UUID

class BuddiRepository(
    private val api: BuddiApi,
    private val descriptionsApi: BuddiApi = api,
    private val offlineStore: OfflineStore? = null,
    private val autoSyncPendingTransactions: Boolean = true
) {

    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val descriptionsLoadMutex = Mutex()
    private val syncMutex = Mutex()
    private val gson = Gson()
    private val _transactionDescriptionTemplates = MutableStateFlow<List<TransactionDescriptionTemplate>>(emptyList())
    val transactionDescriptionTemplates: StateFlow<List<TransactionDescriptionTemplate>> =
        _transactionDescriptionTemplates.asStateFlow()

    @Volatile
    private var descriptionsLoaded: Boolean = false

    suspend fun login(identifier: String, password: String, stayLoggedIn: Boolean): LoginResult {
        return runLoginApi {
            val response = api.login(
                identifier = identifier,
                password = password,
                remember = if (stayLoggedIn) "on" else null
            )
            when {
                response.success -> {
                    resetTransactionDescriptionTemplates()
                    preloadTransactionDescriptionTemplatesAsync()
                    syncPendingTransactionsAsync()
                    LoginResult.Success
                }

                !response.next.isNullOrBlank() -> LoginResult.NextStep(response.next)
                else -> LoginResult.Error("Login failed.")
            }
        }
    }

    suspend fun hasValidSession(): Boolean {
        return fetchAccountsOverview().isSuccess
    }

    suspend fun logout() {
        runCatching { api.logout() }
        resetTransactionDescriptionTemplates()
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

    suspend fun fetchTransactions(sourceId: Long, start: Int, limit: Int): Result<TransactionsPage> {
        syncPendingTransactionsAsync()
        val pendingTransactions = pendingTransactionsForSource(sourceId)
        val pendingOffset = if (start > 0) pendingTransactions.size else 0
        val serverStart = (start - pendingOffset).coerceAtLeast(0)
        val responseResult = fetchTransactionsResponse(sourceId = sourceId, start = serverStart, limit = limit)
        return responseResult.map { response ->
            val serverPage = response.toTransactionsPage()
            if (serverStart > 0) {
                serverPage.copy(total = serverPage.total + pendingTransactions.size)
            }
            else {
                val pendingItems = pendingTransactions.map { pendingTransaction ->
                    pendingTransaction.toTransactionSummary()
                }
                val mergedItems = (pendingItems + serverPage.items)
                    .distinctBy { transaction -> transaction.id }
                    .sortedByDescending { transaction -> transaction.dateIso }
                TransactionsPage(
                    items = mergedItems,
                    total = serverPage.total + pendingItems.size
                )
            }
        }
    }

    suspend fun fetchTransactionDescriptionTemplates(): Result<List<TransactionDescriptionTemplate>> {
        if (descriptionsLoaded) {
            return Result.success(_transactionDescriptionTemplates.value)
        }
        return loadTransactionDescriptionTemplatesIntoStore()
    }

    fun preloadTransactionDescriptionTemplatesAsync() {
        repositoryScope.launch {
            loadTransactionDescriptionTemplatesIntoStore()
        }
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
        return fetchUserPreferencesResponse().map { response ->
            response.toUserDatePreferences()
        }
    }

    suspend fun createTransaction(input: TransactionEditInput): Result<Unit> {
        val store = offlineStore
        if (store != null) {
            val now = System.currentTimeMillis()
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
            addOrUpdateTransactionDescriptionTemplate(input)
            syncPendingTransactionsAsync()
            return Result.success(Unit)
        }
        return mutateTransaction(
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

    suspend fun updateTransaction(transactionId: Long, input: TransactionEditInput): Result<Unit> {
        val pendingTransaction = findPendingTransaction(transactionId)
        if (pendingTransaction != null) {
            offlineStore?.upsertPendingTransaction(
                pendingTransaction.copy(
                    status = PendingTransactionStatus.PENDING,
                    dateIso = input.dateIso,
                    description = input.description,
                    number = input.number,
                    amount = input.amount,
                    fromId = input.fromId,
                    toId = input.toId,
                    memo = input.memo,
                    modifiedAt = System.currentTimeMillis(),
                    lastError = null
                )
            )
            addOrUpdateTransactionDescriptionTemplate(input)
            syncPendingTransactionsAsync()
            return Result.success(Unit)
        }
        return mutateTransaction(
            TransactionMutationRequestDto(
                action = "update",
                id = transactionId,
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

    suspend fun deleteTransaction(transactionId: Long): Result<Unit> {
        val pendingTransaction = findPendingTransaction(transactionId)
        if (pendingTransaction != null) {
            offlineStore?.deletePendingTransaction(pendingTransaction.localUuid)
            syncPendingTransactionsAsync()
            return Result.success(Unit)
        }
        return mutateTransaction(
            TransactionMutationRequestDto(
                action = "delete",
                id = transactionId
            )
        )
    }

    private suspend fun mutateTransaction(request: TransactionMutationRequestDto): Result<Unit> {
        return runApi {
            val response = api.mutateTransaction(request)
            if (!response.success) {
                return@runApi Result.failure(IllegalStateException("Transaction mutation failed."))
            }
            Result.success(Unit)
        }
    }

    fun syncPendingTransactionsAsync() {
        if (offlineStore == null || !autoSyncPendingTransactions) {
            return
        }
        repositoryScope.launch {
            syncPendingTransactions()
        }
    }

    suspend fun syncPendingTransactions(): Result<Int> {
        val store = offlineStore ?: return Result.success(0)
        return syncMutex.withLock {
            var syncedCount = 0
            for (pendingTransaction in store.listPendingTransactions()) {
                if (pendingTransaction.status == PendingTransactionStatus.FAILED) {
                    continue
                }
                val syncingTransaction = pendingTransaction.copy(
                    status = PendingTransactionStatus.SYNCING,
                    attemptCount = pendingTransaction.attemptCount + 1,
                    modifiedAt = System.currentTimeMillis()
                )
                store.upsertPendingTransaction(syncingTransaction)
                try {
                    val response = api.mutateTransaction(syncingTransaction.toMutationRequest())
                    if (response.success) {
                        store.deletePendingTransaction(syncingTransaction.localUuid)
                        syncedCount++
                    }
                    else {
                        store.upsertPendingTransaction(syncingTransaction.failed("Transaction mutation failed."))
                    }
                } catch (exception: HttpException) {
                    if (exception.code() == 401 || exception.code() == 403) {
                        store.upsertPendingTransaction(syncingTransaction.pending(exception.message()))
                        return@withLock Result.failure(UnauthorizedException())
                    }
                    if (exception.code() in 400..499) {
                        store.upsertPendingTransaction(syncingTransaction.failed("Transaction rejected: HTTP ${exception.code()}."))
                        continue
                    }
                    store.upsertPendingTransaction(syncingTransaction.pending(exception.message()))
                    return@withLock Result.failure(exception)
                } catch (exception: IOException) {
                    store.upsertPendingTransaction(syncingTransaction.pending(exception.message))
                    return@withLock Result.failure(exception)
                } catch (exception: Exception) {
                    store.upsertPendingTransaction(syncingTransaction.failed(exception.message ?: "Transaction sync failed."))
                }
            }
            if (syncedCount > 0) {
                refreshAccountsCacheAfterSync()
            }
            Result.success(syncedCount)
        }
    }

    private suspend fun fetchAccountsResponse(): Result<AccountsResponseDto> {
        return fetchCachedResponse(
            cacheKey = ACCOUNTS_CACHE_KEY,
            responseClass = AccountsResponseDto::class.java,
            failureMessage = "Accounts request failed.",
            request = { api.getAccounts() },
            isSuccess = { response -> response.success }
        )
    }

    private suspend fun fetchTransactionsResponse(sourceId: Long, start: Int, limit: Int): Result<TransactionsResponseDto> {
        return fetchCachedResponse(
            cacheKey = transactionsCacheKey(sourceId, start, limit),
            responseClass = TransactionsResponseDto::class.java,
            failureMessage = "Transactions request failed.",
            request = { api.getTransactions(sourceId = sourceId, start = start, limit = limit) },
            isSuccess = { response -> response.success }
        )
    }

    private suspend fun fetchSourcesResponse(direction: String): Result<SourcesResponseDto> {
        return fetchCachedResponse(
            cacheKey = sourcesCacheKey(direction),
            responseClass = SourcesResponseDto::class.java,
            failureMessage = "Sources request failed.",
            request = { api.getSources(direction) },
            isSuccess = { response -> response.success }
        )
    }

    private suspend fun fetchUserPreferencesResponse(): Result<UserPreferencesResponseDto> {
        return fetchCachedResponse(
            cacheKey = USER_PREFERENCES_CACHE_KEY,
            responseClass = UserPreferencesResponseDto::class.java,
            failureMessage = "User preferences request failed.",
            request = { api.getUserPreferences() },
            isSuccess = { response -> response.success }
        )
    }

    private suspend fun fetchTransactionDescriptionResponse(): Result<TransactionDescriptionsResponseDto> {
        return fetchCachedResponse(
            cacheKey = TRANSACTION_DESCRIPTIONS_CACHE_KEY,
            responseClass = TransactionDescriptionsResponseDto::class.java,
            failureMessage = "Transaction descriptions request failed.",
            request = { descriptionsApi.getTransactionDescriptions() },
            isSuccess = { response -> response.success }
        )
    }

    private suspend fun <T> fetchCachedResponse(
        cacheKey: String,
        responseClass: Class<T>,
        failureMessage: String,
        request: suspend () -> T,
        isSuccess: (T) -> Boolean
    ): Result<T> {
        return try {
            val response = request()
            if (!isSuccess(response)) {
                Result.failure(IllegalStateException(failureMessage))
            }
            else {
                offlineStore?.saveCache(cacheKey, gson.toJson(response))
                Result.success(response)
            }
        } catch (exception: HttpException) {
            if (exception.code() == 401 || exception.code() == 403) {
                Result.failure(UnauthorizedException())
            }
            else {
                Result.failure(exception)
            }
        } catch (exception: IOException) {
            loadCachedResponse(cacheKey, responseClass)
                ?: Result.failure(OfflineUnavailableException())
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

    private suspend fun refreshAccountsCacheAfterSync() {
        runCatching {
            val response = api.getAccounts()
            if (response.success) {
                offlineStore?.saveCache(ACCOUNTS_CACHE_KEY, gson.toJson(response))
            }
        }
    }

    private fun pendingTransactionsForSource(sourceId: Long): List<PendingTransaction> {
        return offlineStore
            ?.listPendingTransactions()
            ?.filter { pendingTransaction ->
                pendingTransaction.fromId.toLong() == sourceId || pendingTransaction.toId.toLong() == sourceId
            }
            .orEmpty()
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
        val delta = pendingTransactions.fold(BigDecimal.ZERO) { total, pendingTransaction ->
            val amount = pendingTransaction.amount.toBigDecimalOrNull() ?: return@fold total
            var updatedTotal = total
            if (pendingTransaction.fromId.toLong() == account.id) {
                updatedTotal -= amount
            }
            if (pendingTransaction.toId.toLong() == account.id) {
                updatedTotal += amount
            }
            updatedTotal
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

    private fun PendingTransaction.toMutationRequest(): TransactionMutationRequestDto {
        return TransactionMutationRequestDto(
            action = "insert",
            uuid = localUuid,
            date = dateIso,
            description = description,
            number = number,
            splits = listOf(
                SplitMutationRequestDto(
                    amount = amount,
                    fromId = fromId,
                    toId = toId,
                    memo = memo
                )
            )
        )
    }

    private fun PendingTransaction.pending(lastError: String?): PendingTransaction {
        return copy(
            status = PendingTransactionStatus.PENDING,
            modifiedAt = System.currentTimeMillis(),
            lastError = lastError
        )
    }

    private fun PendingTransaction.failed(lastError: String): PendingTransaction {
        return copy(
            status = PendingTransactionStatus.FAILED,
            modifiedAt = System.currentTimeMillis(),
            lastError = lastError
        )
    }

    private val PendingTransaction.id: Long
        get() = pendingTransactionId(localUuid)

    private fun sourceName(sourceId: Int): String {
        for (direction in listOf("from", "to")) {
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
        } catch (exception: HttpException) {
            if (exception.code() == 401 || exception.code() == 403) {
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
