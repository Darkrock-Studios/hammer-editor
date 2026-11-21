package com.darkrockstudios.apps.hammer.common.preview.projectselection

import com.darkrockstudios.apps.hammer.common.preview.PreviewScrim
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.darkrockstudios.apps.hammer.base.IdeaId
import com.darkrockstudios.apps.hammer.base.http.storyideas.StoryIdea
import com.darkrockstudios.apps.hammer.common.components.projectselection.projectslist.ProjectsList
import com.darkrockstudios.apps.hammer.common.data.sync.ideassync.IdeaConflict
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

private fun previewIdeaConflict(): IdeaConflict {
	val local = StoryIdea(
		id = IdeaId("00000000-0000-0000-0000-000000000001"),
		created = Instant.parse("2026-11-02T12:00:00Z"),
		updated = Instant.parse("2026-11-18T09:00:00Z"),
		title = "The lighthouse keeper",
		content = "A lighthouse keeper who has never seen the ship she keeps the light for.",
		tags = setOf("short", "sea"),
	)
	val server = local.copy(
		title = "The last lighthouse keeper",
		content = "A lighthouse keeper who has never once seen the ship she keeps the light burning for.",
		tags = setOf("short", "sea", "grief"),
	)
	return IdeaConflict(local = local, server = server)
}

@Preview(widthDp = 600, heightDp = 800)
@Composable
fun ProjectsSyncDialogPreview(showLog: Boolean = false, ideaConflict: Boolean = false) {
	PreviewScrim {
		ProjectsSyncContent(
			syncState = previewSyncState().let { if (ideaConflict) it.copy(ideaConflict = previewIdeaConflict()) else it },
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
