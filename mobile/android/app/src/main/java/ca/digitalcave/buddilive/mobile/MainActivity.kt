package ca.digitalcave.buddilive.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import ca.digitalcave.buddilive.mobile.ui.theme.AppTheme
import ca.digitalcave.buddilive.mobile.ui.BuddiMobileApp

class MainActivity : ComponentActivity() {

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		setContent {
			AppTheme {
				Surface(color = MaterialTheme.colorScheme.background) {
					BuddiMobileApp(repository = AppContainer.repository(applicationContext))
				}
			}
		}
	}
}
