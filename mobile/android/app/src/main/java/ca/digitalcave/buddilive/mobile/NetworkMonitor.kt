package ca.digitalcave.buddilive.mobile

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.CacheControl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.io.IOException
import java.util.concurrent.TimeUnit

class NetworkMonitor(context: Context) {

	private val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
	private val monitorScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
	private val availabilityMutex = Mutex()
	private val client = OkHttpClient.Builder()
		.connectTimeout(API_CHECK_TIMEOUT_SECONDS, TimeUnit.SECONDS)
		.readTimeout(API_CHECK_TIMEOUT_SECONDS, TimeUnit.SECONDS)
		.writeTimeout(API_CHECK_TIMEOUT_SECONDS, TimeUnit.SECONDS)
		.callTimeout(API_CHECK_TIMEOUT_SECONDS, TimeUnit.SECONDS)
		.build()
	private val apiAvailabilityUrl = BuildConfig.BASE_URL.toHttpUrl()
		.newBuilder()
		.addPathSegments(API_AVAILABILITY_PATH)
		.build()
	private val _isOnline = MutableStateFlow(hasNetworkConnection())
	val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

	private val networkCallback = object : ConnectivityManager.NetworkCallback() {
		override fun onAvailable(network: Network) {
			checkApiAvailabilityAsync()
		}

		override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
			if (networkCapabilities.hasInternetConnection()) {
				checkApiAvailabilityAsync()
			}
			else {
				_isOnline.value = false
			}
		}

		override fun onLost(network: Network) {
			_isOnline.value = false
			checkApiAvailabilityAsync()
		}

		override fun onUnavailable() {
			_isOnline.value = false
		}
	}

	init {
		connectivityManager.registerDefaultNetworkCallback(networkCallback)
		checkApiAvailabilityAsync()
		monitorScope.launch {
			while (true) {
				delay(API_CHECK_INTERVAL_MILLIS)
				checkApiAvailability()
			}
		}
	}

	private fun checkApiAvailabilityAsync() {
		monitorScope.launch {
			checkApiAvailability()
		}
	}

	private suspend fun checkApiAvailability() {
		availabilityMutex.withLock {
			if (!hasNetworkConnection()) {
				_isOnline.value = false
				return@withLock
			}
			_isOnline.value = isApiAvailable()
		}
	}

	private fun isApiAvailable(): Boolean {
		val request = Request.Builder()
			.url(apiAvailabilityUrl)
			.cacheControl(CacheControl.FORCE_NETWORK)
			.get()
			.build()
		return try {
			client.newCall(request).execute().use { response ->
				response.code < HTTP_SERVER_ERROR_START
			}
		} catch (exception: IOException) {
			false
		}
	}

	private fun hasNetworkConnection(): Boolean {
		val network = connectivityManager.activeNetwork ?: return false
		val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
		return capabilities.hasInternetConnection()
	}

	private companion object {
		const val API_AVAILABILITY_PATH = "data/accounts"
		const val API_CHECK_TIMEOUT_SECONDS = 2L
		const val API_CHECK_INTERVAL_MILLIS = 15_000L
		const val HTTP_SERVER_ERROR_START = 500
	}
}

private fun NetworkCapabilities.hasInternetConnection(): Boolean {
	return hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}
