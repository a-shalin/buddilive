package ca.digitalcave.buddilive.mobile

import android.content.Context
import ca.digitalcave.buddilive.mobile.data.BuddiApiFactory
import ca.digitalcave.buddilive.mobile.data.BuddiRepository

object AppContainer {

	@Volatile
	private var repository: BuddiRepository? = null

	fun repository(context: Context): BuddiRepository {
		return repository ?: synchronized(this) {
			repository ?: BuddiRepository(
				api = BuddiApiFactory.create(context.applicationContext),
				descriptionsApi = BuddiApiFactory.createDescriptionsApi(context.applicationContext)
			).also {
				repository = it
			}
		}
	}
}
