package com.darkrockstudios.apps.hammer.android.sync

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.darkrockstudios.apps.hammer.android.R
import com.darkrockstudios.apps.hammer.common.data.sync.accountsync.AccountSyncProgress

/** The ongoing notification shown while a nightly sync runs. */
class NightlySyncNotifier(private val context: Context) {
	private val manager = NotificationManagerCompat.from(context)

	private val openApp: PendingIntent? by lazy {
		context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
			PendingIntent.getActivity(context, 0, it, PendingIntent.FLAG_IMMUTABLE)
		}
	}

	init {
		manager.createNotificationChannel(
			NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_LOW)
				.setName(context.getString(R.string.nightly_sync_channel_name))
				.setDescription(context.getString(R.string.nightly_sync_channel_description))
				.build()
		)
	}

	fun build(progress: AccountSyncProgress): Notification {
		val builder = NotificationCompat.Builder(context, CHANNEL_ID)
			.setSmallIcon(R.drawable.ic_launcher_monochrome)
			.setContentTitle(context.getString(R.string.nightly_sync_notification_title))
			.setContentIntent(openApp)
			.setOngoing(true)
			.setOnlyAlertOnce(true)
			.setSilent(true)
			.setCategory(NotificationCompat.CATEGORY_PROGRESS)
			.setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
		if (progress is AccountSyncProgress.Projects && progress.total > 0) {
			builder
				.setContentText(
					context.resources.getQuantityString(
						R.plurals.nightly_sync_notification_projects,
						progress.total,
						progress.done,
						progress.total,
					)
				)
				.setProgress(progress.total, progress.done, false)
		} else {
			builder
				.setContentText(context.getString(R.string.nightly_sync_notification_account))
				.setProgress(0, 0, true)
		}
		return builder.build()
	}

	/** Posts [notification] itself, for a sync that could not run as a foreground service. */
	fun post(notification: Notification) {
		if (!canPost()) return
		manager.notify(NOTIFICATION_ID, notification)
	}

	fun cancel() = manager.cancel(NOTIFICATION_ID)

	private fun canPost(): Boolean =
		Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
			ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
			PackageManager.PERMISSION_GRANTED

	companion object {
		const val NOTIFICATION_ID = 7301
		private const val CHANNEL_ID = "nightly_sync"
	}
}
