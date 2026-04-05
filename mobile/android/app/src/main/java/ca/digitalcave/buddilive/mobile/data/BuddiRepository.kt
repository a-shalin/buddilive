package ca.digitalcave.buddilive.mobile.data

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
import java.io.IOException

class BuddiRepository(
	private val api: BuddiApi,
	private val descriptionsApi: BuddiApi = api
) {

	private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
	private val descriptionsLoadMutex = Mutex()
	private val _transactionDescriptionTemplates = MutableStateFlow<List<TransactionDescriptionTemplate>>(emptyList())
	val transactionDescriptionTemplates: StateFlow<List<TransactionDescriptionTemplate>> = _transactionDescriptionTemplates.asStateFlow()

	@Volatile
	private var descriptionsLoaded: Boolean = false

	suspend fun login(identifier: String, password: String): LoginResult {
		return runLoginApi {
			val response = api.login(identifier, password)
			when {
				response.success -> {
					resetTransactionDescriptionTemplates()
					preloadTransactionDescriptionTemplatesAsync()
					LoginResult.Success
				}
				!response.next.isNullOrBlank() -> LoginResult.NextStep(response.next)
				else -> LoginResult.Error("Login failed.")
			}
		}
	}

	suspend fun fetchAccounts(): Result<List<AccountSummary>> {
		return runApi {
			val response = api.getAccounts()
			if (!response.success) {
				return@runApi Result.failure(IllegalStateException("Accounts request failed."))
			}
			Result.success(response.toAccountSummaries())
		}
	}

	suspend fun fetchAccountsOverview(): Result<AccountsOverview> {
		return runApi {
			val response = api.getAccounts()
			if (!response.success) {
				return@runApi Result.failure(IllegalStateException("Accounts request failed."))
			}
			Result.success(response.toAccountsOverview())
		}
	}

	suspend fun fetchTransactions(sourceId: Long, start: Int, limit: Int): Result<TransactionsPage> {
		return runApi {
			val response = api.getTransactions(sourceId = sourceId, start = start, limit = limit)
			if (!response.success) {
				return@runApi Result.failure(IllegalStateException("Transactions request failed."))
			}
			Result.success(response.toTransactionsPage())
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
			val result = runApi {
				val response = descriptionsApi.getTransactionDescriptions()
				if (!response.success) {
					return@runApi Result.failure(IllegalStateException("Transaction descriptions request failed."))
				}
				Result.success(response.toTransactionDescriptionTemplates())
			}
			result.onSuccess { templates ->
				_transactionDescriptionTemplates.update { existing ->
					mergeDescriptionTemplates(remote = templates, local = existing)
				}
				descriptionsLoaded = true
			}
			result
		}
	}

	private fun mergeDescriptionTemplates(
		remote: List<TransactionDescriptionTemplate>,
		local: List<TransactionDescriptionTemplate>
	): List<TransactionDescriptionTemplate> {
		if (local.isEmpty()) {
			return remote
		}
		val merged = remote.toMutableList()
		val knownDescriptions = remote.mapTo(mutableSetOf()) { it.description.trim().lowercase() }
		for (template in local) {
			val key = template.description.trim().lowercase()
			if (key !in knownDescriptions) {
				merged.add(template)
				knownDescriptions.add(key)
			}
		}
		return merged
	}

	private fun addTransactionDescriptionTemplateIfMissing(input: TransactionEditInput) {
		val description = input.description.trim()
		if (description.isBlank()) {
			return
		}
		val amountNumber = input.amount.toBigDecimalOrNull() ?: return
		_transactionDescriptionTemplates.update { templates ->
			if (templates.any { it.description.equals(description, ignoreCase = true) }) {
				return@update templates
			}
			templates + TransactionDescriptionTemplate(
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
		}
	}

	private fun resetTransactionDescriptionTemplates() {
		descriptionsLoaded = false
		_transactionDescriptionTemplates.value = emptyList()
	}

	suspend fun fetchSources(direction: String): Result<List<SourceOption>> {
		return runApi {
			val response = api.getSources(direction)
			if (!response.success) {
				return@runApi Result.failure(IllegalStateException("Sources request failed."))
			}
			Result.success(response.toSourceOptions())
		}
	}

	suspend fun fetchUserDatePreferences(): Result<UserDatePreferences> {
		return runApi {
			val response = api.getUserPreferences()
			if (!response.success) {
				return@runApi Result.failure(IllegalStateException("User preferences request failed."))
			}
			Result.success(response.toUserDatePreferences())
		}
	}

	suspend fun createTransaction(input: TransactionEditInput): Result<Unit> {
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
			addTransactionDescriptionTemplateIfMissing(input)
		}
	}

	suspend fun updateTransaction(transactionId: Long, input: TransactionEditInput): Result<Unit> {
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
			addTransactionDescriptionTemplateIfMissing(input)
		}
	}

	suspend fun deleteTransaction(transactionId: Long): Result<Unit> {
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

	private suspend fun <T> runApi(block: suspend () -> Result<T>): Result<T> {
		return try {
			block()
		}
		catch (exception: HttpException) {
			if (exception.code() == 401 || exception.code() == 403) {
				Result.failure(UnauthorizedException())
			}
			else {
				Result.failure(exception)
			}
		}
		catch (exception: IOException) {
			Result.failure(exception)
		}
		catch (exception: Exception) {
			Result.failure(exception)
		}
	}

	private suspend fun runLoginApi(block: suspend () -> LoginResult): LoginResult {
		return try {
			block()
		}
		catch (exception: HttpException) {
			if (exception.code() == 401 || exception.code() == 403) {
				LoginResult.Error("Authentication is required.")
			}
			else {
				LoginResult.Error("Login request failed: HTTP ${exception.code()}.")
			}
		}
		catch (exception: IOException) {
			LoginResult.Error("Network error: ${exception.message ?: "unknown"}")
		}
		catch (exception: Exception) {
			LoginResult.Error("Login failed: ${exception.message ?: "unknown"}")
		}
	}
}

sealed interface LoginResult {
	data object Success : LoginResult
	data class NextStep(val nextStep: String) : LoginResult
	data class Error(val message: String) : LoginResult
}

class UnauthorizedException : RuntimeException("Session is not authenticated.")
