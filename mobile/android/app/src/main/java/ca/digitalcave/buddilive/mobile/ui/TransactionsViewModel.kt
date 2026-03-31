package ca.digitalcave.buddilive.mobile.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import ca.digitalcave.buddilive.mobile.data.BuddiRepository
import ca.digitalcave.buddilive.mobile.data.SourceOption
import ca.digitalcave.buddilive.mobile.data.TransactionDescriptionTemplate
import ca.digitalcave.buddilive.mobile.data.TransactionEditInput
import ca.digitalcave.buddilive.mobile.data.TransactionSummary
import ca.digitalcave.buddilive.mobile.data.UnauthorizedException
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.DecimalFormatSymbols
import java.util.Locale

data class TransactionsUiState(
	val isLoading: Boolean = false,
	val transactions: List<TransactionSummary> = emptyList(),
	val total: Int = 0,
	val error: String? = null,
	val descriptionTemplates: List<TransactionDescriptionTemplate> = emptyList(),
	val fromSources: List<SourceOption> = emptyList(),
	val toSources: List<SourceOption> = emptyList(),
	val dateFormat: String = "yyyy-MM-dd",
	val localeTag: String = Locale.getDefault().toLanguageTag(),
	val decimalSeparator: Char = DecimalFormatSymbols.getInstance(Locale.getDefault()).decimalSeparator,
	val thousandSeparator: Char = DecimalFormatSymbols.getInstance(Locale.getDefault()).groupingSeparator,
	val currencyToken: String = "",
	val currencyAfter: Boolean = false,
	val negativeFormat: String = "N",
	val currencySpacing: Boolean = true,
	val fractionDigits: Int = 2,
	val accountBalance: String = "",
	val needsLogin: Boolean = false,
	val isMutating: Boolean = false
)

class TransactionsViewModel(
	private val repository: BuddiRepository,
	private val accountId: Long
) : ViewModel() {

	private val _state = MutableStateFlow(TransactionsUiState())
	val state: StateFlow<TransactionsUiState> = _state.asStateFlow()

	fun loadInitial() {
		loadTransactions(start = 0, append = false)
		loadDescriptionTemplates()
		loadSources()
		loadDatePreferences()
		loadAccountBalance()
	}

	fun refresh() {
		loadTransactions(start = 0, append = false)
		loadAccountBalance()
	}

	fun loadMore() {
		val current = _state.value
		if (current.isLoading || current.transactions.size >= current.total) {
			return
		}
		loadTransactions(start = current.transactions.size, append = true)
	}

	fun createTransaction(input: TransactionEditInput, onDone: (Boolean) -> Unit) {
		viewModelScope.launch {
			_state.update { it.copy(isMutating = true, error = null) }
			val result = repository.createTransaction(input)
			handleMutationResult(result, onDone)
		}
	}

	fun updateTransaction(transactionId: Long, input: TransactionEditInput, onDone: (Boolean) -> Unit) {
		viewModelScope.launch {
			_state.update { it.copy(isMutating = true, error = null) }
			val result = repository.updateTransaction(transactionId, input)
			handleMutationResult(result, onDone)
		}
	}

	fun deleteTransaction(transactionId: Long, onDone: (Boolean) -> Unit) {
		viewModelScope.launch {
			_state.update { it.copy(isMutating = true, error = null) }
			val result = repository.deleteTransaction(transactionId)
			handleMutationResult(result, onDone)
		}
	}

	private fun handleMutationResult(result: Result<Unit>, onDone: (Boolean) -> Unit) {
		result.fold(
			onSuccess = {
				_state.update { it.copy(isMutating = false, error = null) }
				refresh()
				loadDescriptionTemplates()
				onDone(true)
			},
			onFailure = { error ->
				val needsLogin = error is UnauthorizedException
				_state.update {
					it.copy(
						isMutating = false,
						error = if (needsLogin) "Session expired. Please sign in again." else error.message ?: "Operation failed.",
						needsLogin = needsLogin
					)
				}
				onDone(false)
			}
		)
	}

	private fun loadDescriptionTemplates() {
		viewModelScope.launch {
			val result = repository.fetchTransactionDescriptionTemplates()
			result.fold(
				onSuccess = { templates ->
					_state.update { it.copy(descriptionTemplates = templates) }
				},
				onFailure = { error ->
					val needsLogin = error is UnauthorizedException
					_state.update {
						it.copy(
							error = if (needsLogin) "Session expired. Please sign in again." else error.message ?: "Unable to load descriptions.",
							needsLogin = needsLogin
						)
					}
				}
			)
		}
	}

	private fun loadSources() {
		viewModelScope.launch {
			val fromDeferred = async { repository.fetchSources("from") }
			val toDeferred = async { repository.fetchSources("to") }

			val fromResult = fromDeferred.await()
			val toResult = toDeferred.await()

			fromResult.fold(
				onSuccess = { sources ->
					_state.update { it.copy(fromSources = sources) }
				},
				onFailure = { error ->
					val needsLogin = error is UnauthorizedException
					_state.update {
						it.copy(
							error = if (needsLogin) "Session expired. Please sign in again." else error.message ?: "Unable to load sources.",
							needsLogin = needsLogin
						)
					}
				}
			)

			toResult.fold(
				onSuccess = { sources ->
					_state.update { it.copy(toSources = sources) }
				},
				onFailure = { error ->
					val needsLogin = error is UnauthorizedException
					_state.update {
						it.copy(
							error = if (needsLogin) "Session expired. Please sign in again." else error.message ?: "Unable to load sources.",
							needsLogin = needsLogin
						)
					}
				}
			)
		}
	}

	private fun loadDatePreferences() {
		viewModelScope.launch {
			val result = repository.fetchUserDatePreferences()
			result.fold(
				onSuccess = { preferences ->
					_state.update {
						it.copy(
							dateFormat = preferences.dateFormat,
							localeTag = preferences.localeTag,
							decimalSeparator = preferences.decimalSeparator,
							thousandSeparator = preferences.thousandSeparator,
							currencyToken = preferences.currencyToken,
							currencyAfter = preferences.currencyAfter,
							negativeFormat = preferences.negativeFormat,
							currencySpacing = preferences.currencySpacing,
							fractionDigits = preferences.fractionDigits
						)
					}
				},
				onFailure = { error ->
					val needsLogin = error is UnauthorizedException
					_state.update {
						it.copy(
							error = if (needsLogin) "Session expired. Please sign in again." else error.message ?: "Unable to load preferences.",
							needsLogin = needsLogin
						)
					}
				}
			)
		}
	}

	private fun loadTransactions(start: Int, append: Boolean) {
		viewModelScope.launch {
			_state.update { it.copy(isLoading = true, error = null, needsLogin = false) }
			val result = repository.fetchTransactions(sourceId = accountId, start = start, limit = 100)
			result.fold(
				onSuccess = { page ->
					_state.update { current ->
						val mergedTransactions = if (append) {
							(current.transactions + page.items).distinctBy { it.id }
						}
						else {
							page.items.distinctBy { it.id }
						}
						current.copy(
							isLoading = false,
							transactions = mergedTransactions,
							total = page.total,
							error = null
						)
					}
				},
				onFailure = { error ->
					val needsLogin = error is UnauthorizedException
					_state.update {
						it.copy(
							isLoading = false,
							error = if (needsLogin) "Session expired. Please sign in again." else error.message ?: "Unable to load transactions.",
							needsLogin = needsLogin
						)
					}
				}
			)
		}
	}

	private fun loadAccountBalance() {
		viewModelScope.launch {
			val result = repository.fetchAccounts()
			result.fold(
				onSuccess = { accounts ->
					val updatedBalance = accounts.firstOrNull { it.id == accountId }?.balance.orEmpty()
					_state.update { it.copy(accountBalance = updatedBalance) }
				},
				onFailure = { error ->
					if (error is UnauthorizedException) {
						_state.update {
							it.copy(
								error = "Session expired. Please sign in again.",
								needsLogin = true
							)
						}
					}
				}
			)
		}
	}
}

class TransactionsViewModelFactory(
	private val repository: BuddiRepository,
	private val accountId: Long
) : ViewModelProvider.Factory {
	@Suppress("UNCHECKED_CAST")
	override fun <T : ViewModel> create(modelClass: Class<T>): T {
		if (modelClass.isAssignableFrom(TransactionsViewModel::class.java)) {
			return TransactionsViewModel(repository, accountId) as T
		}
		throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
	}
}
