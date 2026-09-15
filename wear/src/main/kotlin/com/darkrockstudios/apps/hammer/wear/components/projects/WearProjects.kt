package com.darkrockstudios.apps.hammer.wear.components.projects

import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.common.data.sync.accountsync.ProjectSyncOutcome

interface WearProjects {
	val state: Value<State>

	fun toggleSubscription(projectName: String)
	fun syncNow()
	fun showSyncLog()
	fun signOut()

	data class State(
		val accountEmail: String? = null,
		val projects: List<ProjectRow> = emptyList(),
		val loaded: Boolean = false,
		val syncing: Boolean = false,
		val lastSyncFailed: Boolean = false,
		val needsReauth: Boolean = false,
	)

	/** [canSubscribe] is false until account sync has registered the project with the server. */
	data class ProjectRow(
		val name: String,
		val canSubscribe: Boolean,
		val subscribed: Boolean,
		val progress: Float? = null,
		val outcome: ProjectSyncOutcome? = null,
	)
}
