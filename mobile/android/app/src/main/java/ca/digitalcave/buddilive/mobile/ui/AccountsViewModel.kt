package ca.digitalcave.buddilive.mobile.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import ca.digitalcave.buddilive.mobile.data.AccountsOverview
import ca.digitalcave.buddilive.mobile.data.BuddiRepository
import ca.digitalcave.buddilive.mobile.data.UnauthorizedException
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
			repository.pendingTransactionsSynced.collect {
				refresh()
			}
		}
	}

	fun refresh() {
		viewModelScope.launch {
			_state.update { it.copy(isLoading = true, error = null, needsLogin = false) }
			val result = repository.fetchAccountsOverview()
			result.fold(
				onSuccess = { overview ->
					_state.update { it.copy(isLoading = false, overview = overview, error = null) }
				},
				onFailure = { error ->
					val needsLogin = error is UnauthorizedException
					_state.update {
						it.copy(
							isLoading = false,
							error = if (needsLogin) "Session expired. Please sign in again." else error.message ?: "Unable to load accounts.",
							needsLogin = needsLogin
						)
					}
				}
			)
		}
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
