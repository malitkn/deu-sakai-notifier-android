package tr.edu.deu.sakainotifier

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import tr.edu.deu.sakainotifier.data.storage.NotificationStateManager
import tr.edu.deu.sakainotifier.data.storage.SecurePreferencesManager
import tr.edu.deu.sakainotifier.notification.NotificationHelper
import tr.edu.deu.sakainotifier.ui.MainScreen
import tr.edu.deu.sakainotifier.ui.theme.DeuSakaiTheme
import tr.edu.deu.sakainotifier.worker.WorkScheduler

class MainActivity : ComponentActivity() {

    private val requestNotificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            // Permission result handled
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Create system notification channel
        NotificationHelper.createNotificationChannel(this)

        // Request POST_NOTIFICATIONS permission on Android 13+ (API 33+)
        checkNotificationPermission()

        val securePrefs = SecurePreferencesManager(this)
        val stateManager = NotificationStateManager(this)
        if (securePrefs.hasCredentials()) {
            stateManager.checkAccountSwitch(securePrefs.username)
        }

        // Restore work scheduler if monitoring was previously active
        if (securePrefs.isMonitoringActive && securePrefs.hasCredentials()) {
            WorkScheduler.schedule(this, securePrefs.pollIntervalMinutes)
        }

        setContent {
            DeuSakaiTheme {
                MainScreen(
                    securePrefs = securePrefs,
                    stateManager = stateManager
                )
            }
        }
    }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permissionStatus = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            )
            if (permissionStatus != PackageManager.PERMISSION_GRANTED) {
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
