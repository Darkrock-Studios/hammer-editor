package utils

import com.darkrockstudios.apps.hammer.common.data.appupdate.AppUpdateState
import com.darkrockstudios.apps.hammer.common.data.appupdate.AppUpdater
import kotlinx.coroutines.flow.MutableStateFlow

class FakeAppUpdater(initial: AppUpdateState = AppUpdateState.Idle) : AppUpdater {
	override val state = MutableStateFlow(initial)

	var checks = 0
	var updates = 0
	var dismissals = 0

	override fun checkNow() {
		checks++
	}

	override fun update() {
		updates++
	}

	override fun dismiss() {
		dismissals++
	}
}
