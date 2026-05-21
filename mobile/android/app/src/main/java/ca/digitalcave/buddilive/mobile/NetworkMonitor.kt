package ca.digitalcave.buddilive.mobile

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class NetworkMonitor(context: Context) {

	private val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
	private val _isOnline = MutableStateFlow(hasValidatedNetwork())
	val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

	private val networkCallback = object : ConnectivityManager.NetworkCallback() {
		override fun onAvailable(network: Network) {
			_isOnline.value = hasValidatedNetwork()
		}

		override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
			_isOnline.value = networkCapabilities.hasInternetAccess()
		}

		override fun onLost(network: Network) {
			_isOnline.value = hasValidatedNetwork()
		}

		override fun onUnavailable() {
			_isOnline.value = false
		}
	}

	init {
		connectivityManager.registerDefaultNetworkCallback(networkCallback)
		_isOnline.value = hasValidatedNetwork()
	}

	private fun hasValidatedNetwork(): Boolean {
		val network = connectivityManager.activeNetwork ?: return false
		val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
		return capabilities.hasInternetAccess()
	}
}

private fun NetworkCapabilities.hasInternetAccess(): Boolean {
	return hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
		&& hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}
