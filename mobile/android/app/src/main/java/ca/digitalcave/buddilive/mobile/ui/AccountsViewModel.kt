package ca.digitalcave.buddilive.mobile.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import ca.digitalcave.buddilive.mobile.data.AccountsOverview
import ca.digitalcave.buddilive.mobile.data.BuddiRepository
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AccountsUiState(
	val isLoading: Boolean = false,
	val overview: AccountsOverview = AccountsOverview(
		accountTypes = emptyList(),
		netWorth = null
	),
	val error: String? = null,
	val needsLogin: Boolean = false
)

class AccountsViewModel(private val repository: BuddiRepository) : ViewModel() {

	private val _state = MutableStateFlow(AccountsUiState())
	val state: StateFlow<AccountsUiState> = _state.asStateFlow()

	init {
		viewModelScope.launch {
			repository.cacheVersion.collect {
				val overview = repository.readCachedAccountsOverview()
				_state.update { current -> current.copy(
					overview = overview ?: current.overview,
					error = if (overview == null) "Accounts have not been downloaded yet." else null
				) }
			}
		}
	}

	fun refresh() {
		repository.refreshAsync()
	}

}

class AccountsViewModelFactory(private val repository: BuddiRepository) : ViewModelProvider.Factory {
	@Suppress("UNCHECKED_CAST")
	override fun <T : ViewModel> create(modelClass: Class<T>): T {
		if (modelClass.isAssignableFrom(AccountsViewModel::class.java)) {
			return AccountsViewModel(repository) as T
		}
		throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
	}
}
