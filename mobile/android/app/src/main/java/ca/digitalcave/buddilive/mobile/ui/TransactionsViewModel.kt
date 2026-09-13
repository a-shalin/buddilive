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
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
	val accountBalanceHasPending: Boolean = false,
	val needsLogin: Boolean = false,
	val isMutating: Boolean = false,
	val scrollToTopRequestKey: Long = 0L
)

class TransactionsViewModel(
	private val repository: BuddiRepository,
	private val accountId: Long
) : ViewModel() {

	private val _state = MutableStateFlow(TransactionsUiState())
	val state: StateFlow<TransactionsUiState> = _state.asStateFlow()

	private var extraTransactions = emptyList<TransactionSummary>()
	private var cachedFirstPage: List<TransactionSummary>? = null

	init {
		viewModelScope.launch {
			repository.transactionDescriptionTemplates.collect { templates ->
				_state.update { it.copy(descriptionTemplates = templates) }
			}
		}
		viewModelScope.launch {
			repository.cacheVersion.collect { showLocalData() }
		}
	}

	fun loadInitial() {
		viewModelScope.launch { showLocalData() }
	}

	fun refresh() {
		repository.refreshAsync()
	}

	fun loadMore() {
		val current = _state.value
		if (current.isLoading || current.transactions.size >= current.total) return
		viewModelScope.launch {
			_state.update { it.copy(isLoading = true, error = null) }
			val result = withContext(Dispatchers.IO) {
				repository.fetchTransactions(accountId, current.transactions.size, 100)
			}
			result.fold(onSuccess = { page ->
				extraTransactions = (extraTransactions + page.items).distinctBy { it.id }
				showLocalData()
			}, onFailure = { error ->
				_state.update { it.copy(error = if (error is UnauthorizedException) "Sign in to load older transactions." else "Older transactions are unavailable offline.") }
			})
			_state.update { it.copy(isLoading = false) }
		}
	}

	fun createTransaction(input: TransactionEditInput, onDone: (Boolean) -> Unit) {
		mutate(onDone, scrollToTop = true) { repository.createTransaction(input) }
	}

	fun updateTransaction(transactionId: Long, input: TransactionEditInput, onDone: (Boolean) -> Unit) {
		mutate(onDone) { repository.updateTransaction(transactionId, input) }
	}

	fun deleteTransaction(transactionId: Long, onDone: (Boolean) -> Unit) {
		mutate(onDone) { repository.deleteTransaction(transactionId) }
	}

	private fun mutate(onDone: (Boolean) -> Unit, scrollToTop: Boolean = false, operation: suspend () -> Result<Unit>) {
		viewModelScope.launch {
			_state.update { it.copy(isMutating = true, error = null) }
			val result = operation()
			if (result.isSuccess) {
				showLocalData()
				_state.update { it.copy(
					isMutating = false,
					scrollToTopRequestKey = it.scrollToTopRequestKey + if (scrollToTop) 1 else 0
				) }
				onDone(true)
				repository.requestSync()
			} else {
				val error = result.exceptionOrNull()
				_state.update { it.copy(
					isMutating = false,
					error = if (error is UnauthorizedException) "Sign in to change synced transactions." else error?.message ?: "Operation failed."
				) }
				onDone(false)
			}
		}
	}

	private suspend fun showLocalData() {
		val data = repository.readCachedAccountData(accountId)
		val page = data.page
		if (cachedFirstPage != page?.items) {
			extraTransactions = emptyList()
			cachedFirstPage = page?.items
		}
		val preferences = data.preferences
		_state.update { current -> current.copy(
			transactions = (page?.items.orEmpty() + extraTransactions).distinctBy { it.uuid ?: it.id.toString() },
			total = page?.total ?: 0,
			fromSources = data.fromSources,
			toSources = data.toSources,
			dateFormat = preferences.dateFormat,
			localeTag = preferences.localeTag,
			decimalSeparator = preferences.decimalSeparator,
			thousandSeparator = preferences.thousandSeparator,
			currencyToken = preferences.currencyToken,
			currencyAfter = preferences.currencyAfter,
			negativeFormat = preferences.negativeFormat,
			currencySpacing = preferences.currencySpacing,
			fractionDigits = preferences.fractionDigits,
			accountBalance = data.account?.balance.orEmpty(),
			accountBalanceHasPending = data.account?.hasPending ?: false,
			error = if (page == null) "Recent transactions have not been downloaded yet." else null
		) }
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
