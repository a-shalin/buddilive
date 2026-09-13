package ca.digitalcave.buddilive.mobile

import android.content.Context
import ca.digitalcave.buddilive.mobile.data.AndroidOfflineStore
import ca.digitalcave.buddilive.mobile.data.BuddiApiFactory
import ca.digitalcave.buddilive.mobile.data.BuddiRepository
import ca.digitalcave.buddilive.mobile.data.PersistentCookieJar

object AppContainer {

	@Volatile
	private var repository: BuddiRepository? = null

	@Volatile
	private var networkMonitor: NetworkMonitor? = null

	fun repository(context: Context): BuddiRepository {
		return repository ?: synchronized(this) {
			repository ?: BuddiRepository(
				api = BuddiApiFactory.create(context.applicationContext),
				descriptionsApi = BuddiApiFactory.createDescriptionsApi(context.applicationContext),
				offlineStore = AndroidOfflineStore(context.applicationContext),
				backendIdentity = BuildConfig.BASE_URL,
				clearSession = { PersistentCookieJar(context.applicationContext).clear() }
			).also {
				repository = it
			}
		}
	}

	fun networkMonitor(context: Context): NetworkMonitor {
		return networkMonitor ?: synchronized(this) {
			networkMonitor ?: NetworkMonitor(context.applicationContext).also {
				networkMonitor = it
			}
		}
	}
}
