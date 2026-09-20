package com.darkrockstudios.apps.hammer.wear.components.synclog

import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.SyncLogMessage

interface SyncLog {
	val state: Value<State>

	/** Newest entry first. */
	data class State(val entries: List<SyncLogMessage> = emptyList())
}
