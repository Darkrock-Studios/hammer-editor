package com.darkrockstudios.apps.hammer.android.wear

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import com.darkrockstudios.apps.hammer.android.R
import com.darkrockstudios.apps.hammer.common.data.pairing.PairingProtocol
import com.darkrockstudios.apps.hammer.common.dependencyinjection.APP_SCOPE
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.qualifier.named

/**
 * Receives a watch's pairing request. The user must approve it on the phone, so this only brings
 * up [WearPairingActivity]: directly when Hammer is on screen, through a notification otherwise,
 * since Android blocks activity starts from the background.
 */
class WearPairingListenerService : WearableListenerService(), KoinComponent {

	private val handler: WearPairingHandler by inject()
	private val appScope: CoroutineScope by inject(named(APP_SCOPE))

	override fun onMessageReceived(event: MessageEvent) {
		if (event.path != PairingProtocol.REQUEST_PATH) return

		if (PairingProtocol.decodeRequest(event.data) == null) {
			appScope.launch { handler.rejectUnreadable(event.sourceNodeId) }
			return
		}

		val intent = WearPairingActivity.createIntent(this, event.sourceNodeId, event.data)
		val appVisible = ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
		if (appVisible) {
			startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
		} else {
			showNotification(intent)
		}
	}

	@SuppressLint("MissingPermission")
	private fun showNotification(intent: Intent) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
			checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
		) {
			Napier.w("Cannot show the watch pairing prompt: notification permission not granted")
			return
		}

		val manager = NotificationManagerCompat.from(this)
		manager.createNotificationChannel(
			NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_HIGH)
				.setName(getString(R.string.wear_pairing_channel_name))
				.build()
		)

		val pendingIntent = PendingIntent.getActivity(
			this,
			0,
			intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
			PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
		)
		val notification = NotificationCompat.Builder(this, CHANNEL_ID)
			.setSmallIcon(R.drawable.ic_launcher_monochrome)
			.setContentTitle(getString(R.string.wear_pairing_notification_title))
			.setContentText(getString(R.string.wear_pairing_notification_text))
			.setContentIntent(pendingIntent)
			.setAutoCancel(true)
			.setPriority(NotificationCompat.PRIORITY_HIGH)
			.build()
		manager.notify(WearPairingActivity.NOTIFICATION_ID, notification)
	}

	private companion object {
		const val CHANNEL_ID = "wear_pairing"
	}
}
