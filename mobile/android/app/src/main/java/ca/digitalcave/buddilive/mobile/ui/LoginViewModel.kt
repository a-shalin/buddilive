package ca.digitalcave.buddilive.mobile.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import ca.digitalcave.buddilive.mobile.data.BuddiRepository
import ca.digitalcave.buddilive.mobile.data.LoginResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LoginUiState(
	val identifier: String = "",
	val password: String = "",
	val isLoading: Boolean = false,
	val error: String? = null
)

class LoginViewModel(private val repository: BuddiRepository) : ViewModel() {

	private val _state = MutableStateFlow(LoginUiState())
	val state: StateFlow<LoginUiState> = _state.asStateFlow()

	fun onIdentifierChanged(value: String) {
		_state.update { it.copy(identifier = value) }
	}

	fun onPasswordChanged(value: String) {
		_state.update { it.copy(password = value) }
	}

	fun login(onResult: (LoginResult) -> Unit) {
		val current = _state.value
		if (current.identifier.isBlank() || current.password.isBlank()) {
			_state.update { it.copy(error = "Email/username and password are required.") }
			return
		}

		viewModelScope.launch {
			_state.update { it.copy(isLoading = true, error = null) }
			val result = repository.login(current.identifier.trim(), current.password)
			when (result) {
				is LoginResult.Error -> _state.update { it.copy(isLoading = false, error = result.message) }
				else -> _state.update { it.copy(isLoading = false, error = null) }
			}
			onResult(result)
		}
	}
}

class LoginViewModelFactory(private val repository: BuddiRepository) : ViewModelProvider.Factory {
	@Suppress("UNCHECKED_CAST")
	override fun <T : ViewModel> create(modelClass: Class<T>): T {
		if (modelClass.isAssignableFrom(LoginViewModel::class.java)) {
			return LoginViewModel(repository) as T
		}
		throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
	}
}

