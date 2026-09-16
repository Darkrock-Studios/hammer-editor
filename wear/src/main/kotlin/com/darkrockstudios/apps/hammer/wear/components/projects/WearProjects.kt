package com.darkrockstudios.apps.hammer.wear.components.projects

import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.common.data.sync.accountsync.ProjectSyncOutcome

interface WearProjects {
	val state: Value<State>

	fun toggleSubscription(projectName: String)
	fun syncNow()
	fun showSyncLog()
	fun signOut()
	fun confirmSignOut()
	fun cancelSignOut()
	fun dismissUnsyncedNotice()

	data class State(
		val accountEmail: String? = null,
		val projects: List<ProjectRow> = emptyList(),
		val loaded: Boolean = false,
		val syncing: Boolean = false,
		val lastSyncFailed: Boolean = false,
		val needsReauth: Boolean = false,
		val signOutWarning: SignOutWarning? = null,
		/** Set to a project name when unsubscribing left its content on the watch. */
		val unsyncedKept: String? = null,
	)

	/** [canSubscribe] is false until account sync has registered the project with the server. */
	data class ProjectRow(
		val name: String,
		val canSubscribe: Boolean,
		val subscribed: Boolean,
		val unsubscribing: Boolean = false,
		val progress: Float? = null,
		val outcome: ProjectSyncOutcome? = null,
	)

	/** [items] is zero when the count could not be read, which still warrants a warning. */
	data class SignOutWarning(val items: Int)
}
