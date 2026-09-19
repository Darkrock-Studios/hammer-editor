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
	fun dismissNotice()

	/** Called once the local network prompt is answered, whichever way. */
	fun onLocalNetworkPermissionResult()

	data class State(
		val accountEmail: String? = null,
		val projects: List<ProjectRow> = emptyList(),
		val loaded: Boolean = false,
		val syncing: Boolean = false,
		val lastSyncFailed: Boolean = false,
		val needsReauth: Boolean = false,
		val signOutWarning: SignOutWarning? = null,
		/** True while the unsynced captures are being counted, before the warning can be shown. */
		val checkingSignOut: Boolean = false,
		val notice: Notice? = null,
		/**
		 * The server is on the local network and the app is not allowed to reach it. Nothing syncs
		 * until that changes.
		 */
		val localNetworkBlocked: Boolean = false,
	)

	/** The outcome of an unsubscribe that did not simply remove the project. */
	data class Notice(val projectName: String, val reason: Reason) {
		enum class Reason {
			/** Writing the server has not accepted yet, so the project was left alone. */
			UnsyncedKept,

			/** The unsubscribe itself failed, which says nothing about whether anything is unsynced. */
			Failed,
		}
	}

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
