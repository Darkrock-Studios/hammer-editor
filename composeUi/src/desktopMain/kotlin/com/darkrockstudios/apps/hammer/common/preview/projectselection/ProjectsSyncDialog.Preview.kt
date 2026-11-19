package com.darkrockstudios.apps.hammer.common.preview.projectselection

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.darkrockstudios.apps.hammer.common.components.projectselection.projectslist.ProjectsList
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.SyncLogLevel
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.SyncLogMessage
import com.darkrockstudios.apps.hammer.common.projectselection.ProjectsSyncContent
import kotlin.time.Instant

private fun previewSyncState(): ProjectsList.SyncState {
	val start = Instant.parse("2026-11-19T14:32:05Z").epochSeconds
	fun log(message: String, project: String?, seconds: Int, level: SyncLogLevel = SyncLogLevel.INFO) =
		SyncLogMessage(message, level, project, Instant.fromEpochSeconds(start + seconds))
	fun status(name: String, progress: Float, status: ProjectsList.Status) =
		name to ProjectsList.ProjectSyncStatus(name, progress, status)
	return ProjectsList.SyncState(
		projectsStatus = linkedMapOf(
			status("Alice in Wonderland", 1f, ProjectsList.Status.Complete),
			status("The Northern Kingdoms", 1f, ProjectsList.Status.Complete),
			status("Short stories", 0.45f, ProjectsList.Status.Syncing),
			status("Trilogy", 0.3f, ProjectsList.Status.NeedsResolution),
			status("Notebook", 0f, ProjectsList.Status.Pending),
		),
		syncLog = listOf(
			log("Starting account sync", null, 0),
			log("Uploaded 3 scenes", "Alice in Wonderland", 2),
			log("Sync complete", "Alice in Wonderland", 3),
			log("Downloaded 1 entry", "The Northern Kingdoms", 5),
			log("Sync complete", "The Northern Kingdoms", 6),
			log("Scene \"The Ford\" changed on both sides", "Trilogy", 8, SyncLogLevel.WARN),
			log("Uploading scenes", "Short stories", 9),
		),
	)
}

@Preview(widthDp = 600, heightDp = 800)
@Composable
fun ProjectsSyncDialogPreview(showLog: Boolean = false) {
	Box(
		modifier = Modifier
			.fillMaxSize()
			.background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f)),
		contentAlignment = Alignment.Center,
	) {
		ProjectsSyncContent(
			syncState = previewSyncState(),
			showLog = showLog,
			onToggleLog = {},
			logProjectFilter = null,
			onLogProjectFilterChange = {},
			onStop = {},
			onResolveIdeaConflict = {},
			onClose = {},
		)
	}
}
