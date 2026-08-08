package com.darkrockstudios.apps.hammer.common.compose

import androidx.compose.runtime.Stable

/**
 * Lets a platform host (Activity, view controller, window) fire the project shortcuts from its
 * own key handling. Compose's key modifiers only see events routed along the focus path, so a
 * screen with nothing focused never receives them.
 *
 * Each action returns false when no project UI is bound, so the host can pass the key on.
 */
@Stable
class ProjectShortcutHost {
	private var startSync: (() -> Unit)? = null
	private var saveAll: (() -> Unit)? = null

	fun bind(startSync: () -> Unit, saveAll: () -> Unit) {
		this.startSync = startSync
		this.saveAll = saveAll
	}

	fun unbind() {
		startSync = null
		saveAll = null
	}

	fun startProjectSync(): Boolean {
		val action = startSync ?: return false
		action()
		return true
	}

	fun saveAllBuffers(): Boolean {
		val action = saveAll ?: return false
		action()
		return true
	}
}
