package com.darkrockstudios.apps.hammer.common.components.storyeditor.focusmode

import android.app.AutomaticZenRule
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.service.notification.Condition
import android.service.notification.ZenPolicy
import androidx.annotation.RequiresApi
import androidx.core.content.edit
import androidx.core.net.toUri
import com.darkrockstudios.apps.hammer.common.R
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsRepository

actual class FocusModeService(
	private val appContext: Context,
	private val globalSettingsRepository: GlobalSettingsRepository,
) {

	private val notificationManager =
		appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

	private val prefs =
		appContext.getSharedPreferences("hammer_focus_prefs", Context.MODE_PRIVATE)

	private val RULE_ID_KEY = "zen_rule_id"
	private val CONDITION_URI = "condition://com.darkrockstudios.apps.hammer/focus".toUri()

	/**
	 * Activates Do Not Disturb mode.
	 * Handles legacy global toggle for API < 35 and ZenRules for API 35+.
	 */
	actual fun enterFocusMode() {
		if (!globalSettingsRepository.globalSettings.enableDndInFocusMode) return
		if (!notificationManager.isNotificationPolicyAccessGranted) return

		if (Build.VERSION.SDK_INT >= 35) {
			val ruleId = getOrCreateRuleId()
			updateZenRuleState(ruleId, true)
		} else {
			notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALARMS)
		}
	}

	actual fun exitFocusMode() {
		if (!notificationManager.isNotificationPolicyAccessGranted) return

		if (Build.VERSION.SDK_INT >= 35) {
			val ruleId = prefs.getString(RULE_ID_KEY, null)
			if (ruleId != null) {
				updateZenRuleState(ruleId, false)
			}
		} else {
			notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
		}
	}

	@RequiresApi(Build.VERSION_CODES.Q)
	private fun getOrCreateRuleId(): String {
		var ruleId = prefs.getString(RULE_ID_KEY, null)

		val configurationActivity = ComponentName(
			appContext.packageName,
			"com.darkrockstudios.apps.hammer.android.ProjectSelectActivity"
		)
		val zenPolicy = ZenPolicy.Builder()
			.hideAllVisualEffects()
			.build()
		val rule = AutomaticZenRule(
			appContext.getString(R.string.android_focus_mode_rule_name),
			null,
			configurationActivity,
			CONDITION_URI,
			zenPolicy,
			NotificationManager.INTERRUPTION_FILTER_PRIORITY,
			true
		)

		if (ruleId == null) {
			ruleId = notificationManager.addAutomaticZenRule(rule)
			prefs.edit { putString(RULE_ID_KEY, ruleId) }
		} else {
			notificationManager.updateAutomaticZenRule(ruleId, rule)
		}
		return ruleId
	}

	@RequiresApi(Build.VERSION_CODES.Q)
	private fun updateZenRuleState(ruleId: String, isActive: Boolean) {
		val condition = Condition(
			CONDITION_URI,
			if (isActive) {
				appContext.getString(R.string.android_focus_mode_condition_active)
			} else {
				appContext.getString(R.string.android_focus_mode_condition_inactive)
			},
			if (isActive) Condition.STATE_TRUE else Condition.STATE_FALSE
		)
		notificationManager.setAutomaticZenRuleState(ruleId, condition)
	}
}