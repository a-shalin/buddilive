package ca.digitalcave.buddilive.mobile.data

import retrofit2.HttpException
import java.io.IOException

class BuddiRepository(private val api: BuddiApi) {

	suspend fun login(identifier: String, password: String): LoginResult {
		return runLoginApi {
			val response = api.login(identifier, password)
			when {
				response.success -> LoginResult.Success
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

	suspend fun fetchTransactions(sourceId: Long, start: Int, limit: Int): Result<TransactionsPage> {
		return runApi {
			val response = api.getTransactions(sourceId = sourceId, start = start, limit = limit)
			if (!response.success) {
				return@runApi Result.failure(IllegalStateException("Transactions request failed."))
			}
			Result.success(response.toTransactionsPage())
		}
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
		)
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
		)
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
