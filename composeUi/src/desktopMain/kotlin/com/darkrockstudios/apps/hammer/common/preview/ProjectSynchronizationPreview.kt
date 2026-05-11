package com.darkrockstudios.apps.hammer.common.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Density
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.update
import com.darkrockstudios.apps.hammer.*
import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.base.http.ApiSceneType
import com.darkrockstudios.apps.hammer.base.http.projectdata.ProjectData
import com.darkrockstudios.apps.hammer.base.http.projectdata.ProjectTheme
import com.darkrockstudios.apps.hammer.base.http.projectdata.WordCountGoal
import com.darkrockstudios.apps.hammer.common.components.projectsync.ProjectSynchronization
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.SyncLogLevel
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.SyncLogMessage
import com.darkrockstudios.apps.hammer.common.projectsync.ProjectSynchronizationContent
import org.jetbrains.compose.resources.StringResource
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
private fun expandedSize(): WindowSizeClass = WindowSizeClass.calculateFromSize(
	size = Size.Zero.copy(1920f, 1280f),
	density = Density(1f)
)

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
private fun compactSize(): WindowSizeClass = WindowSizeClass.calculateFromSize(
	size = Size.Zero.copy(420f, 900f),
	density = Density(1f)
)

@Preview
@Composable
private fun SceneConflictPreview() {
	val serverScene = ApiProjectEntity.SceneEntity(
		id = 1,
		sceneType = ApiSceneType.Scene,
		order = 1,
		name = "Down the Rabbit-Hole",
		path = listOf(3, 5, 8),
		content = sceneContent,
		outline = "Alice grows bored, follows the White Rabbit, falls into the hole.",
		notes = "Set tone for the rest of the chapter; introduce the watch as a recurring motif.",
		confirmedReferences = setOf(1, 2),
		dismissedReferences = setOf(3),
	)
	val clientScene = serverScene.copy(
		name = "Down the Rabbit Hole (draft)",
		content = sceneContent.replace("White\nRabbit", "March Hare"),
		outline = "Alice is bored on the riverbank, sees the March Hare, follows it underground.",
		confirmedReferences = setOf(1, 4),
		dismissedReferences = setOf(2),
	)

	val conflict = ProjectSynchronization.EntityConflict.SceneConflict(
		serverScene = serverScene,
		clientScene = clientScene,
	)

	ProjectSynchronizationPreview(conflict, Res.string.sync_conflict_scene_title, expandedSize())
}

@Preview
@Composable
private fun SceneConflictCompactPreview() {
	val serverScene = ApiProjectEntity.SceneEntity(
		id = 1,
		sceneType = ApiSceneType.Scene,
		order = 1,
		name = "Down the Rabbit-Hole",
		path = listOf(3, 5, 8),
		content = sceneContent,
		outline = "Alice grows bored, follows the White Rabbit, falls into the hole.",
		notes = "Set tone for the rest of the chapter.",
		confirmedReferences = setOf(1, 2),
		dismissedReferences = setOf(3),
	)
	val clientScene = serverScene.copy(
		content = sceneContent.replace("White\nRabbit", "March Hare"),
		outline = "Alice is bored, follows the March Hare.",
	)

	val conflict = ProjectSynchronization.EntityConflict.SceneConflict(
		serverScene = serverScene,
		clientScene = clientScene,
	)

	ProjectSynchronizationPreview(conflict, Res.string.sync_conflict_scene_title, compactSize())
}

@Preview
@Composable
private fun NoteConflictPreview() {
	val serverEntity = ApiProjectEntity.NoteEntity(
		id = 1,
		content = "The watch in the Rabbit's waistcoat is the first hint that this world runs on its own clock — flag for callback in chapter 7.",
		created = Clock.System.now().minus(3.days),
		tags = setOf("worldbuilding", "ch1", "motif"),
	)
	val clientEntity = serverEntity.copy(
		content = "The pocket-watch is our first temporal anomaly. Decide whether to foreshadow it earlier in the riverbank scene.",
		tags = setOf("worldbuilding", "revision", "motif"),
	)

	val conflict = ProjectSynchronization.EntityConflict.NoteConflict(
		serverNote = serverEntity,
		clientNote = clientEntity,
	)

	ProjectSynchronizationPreview(conflict, Res.string.sync_conflict_note_title, expandedSize())
}

@Preview
@Composable
private fun TimelineConflictPreview() {
	val serverEntity = ApiProjectEntity.TimelineEventEntity(
		id = 1,
		content = "Alice follows the White Rabbit down the hole and lands in the long hallway of doors.",
		date = "May 4th, afternoon",
		order = 1,
		tags = setOf("alice", "rabbit-hole"),
	)
	val clientEntity = serverEntity.copy(
		content = "Alice chases the March Hare into the warren and emerges in the corridor of doors.",
		date = "May 4th, late afternoon",
		tags = setOf("alice", "hare", "corridor"),
	)

	val conflict = ProjectSynchronization.EntityConflict.TimelineEventConflict(
		serverEvent = serverEntity,
		clientEvent = clientEntity,
	)

	ProjectSynchronizationPreview(conflict, Res.string.sync_conflict_timeline_title, expandedSize())
}

@Preview
@Composable
private fun EncyclopediaEntryConflictPreview() {
	val serverEntity = ApiProjectEntity.EncyclopediaEntryEntity(
		id = 1,
		name = "White Rabbit",
		entryType = "person",
		text = "A nervous, waistcoated rabbit who carries a pocket-watch and is always late. Functions as Alice's reluctant guide into Wonderland.",
		tags = setOf("wonderland", "guide", "anthropomorphic"),
		image = ApiProjectEntity.EncyclopediaEntryEntity.Image(
			base64 = "",
			fileExtension = "png",
		),
		aliases = listOf("The Rabbit", "Mr. Rabbit"),
	)
	val clientEntity = serverEntity.copy(
		name = "White Rabbit (the Herald)",
		text = "A pocket-watch-carrying rabbit acting as a herald between worlds. Revised in draft 3 to give him a formal title at court.",
		tags = setOf("wonderland", "herald", "court"),
		image = null,
		aliases = listOf("The Rabbit", "The Herald", "His Excellency"),
	)

	val conflict = ProjectSynchronization.EntityConflict.EncyclopediaEntryConflict(
		serverEntry = serverEntity,
		clientEntry = clientEntity,
	)

	ProjectSynchronizationPreview(conflict, Res.string.sync_conflict_encyclopedia_title, expandedSize())
}

@Preview
@Composable
private fun SceneDraftConflictPreview() {
	val serverEntity = ApiProjectEntity.SceneDraftEntity(
		id = 1,
		sceneId = 42,
		created = Clock.System.now().minus(2.days),
		name = "Rabbit-Hole — opening pass",
		content = "Alice sat by the riverbank, idle and itching for something to happen. Then a rabbit passed by, talking to itself.",
	)
	val clientEntity = serverEntity.copy(
		name = "Rabbit-Hole — second pass",
		content = "Alice was bored on the bank when something white and hurried tore through the grass — a rabbit, and one with a pocket-watch at that.",
	)

	val conflict = ProjectSynchronization.EntityConflict.SceneDraftConflict(
		serverEntry = serverEntity,
		clientEntry = clientEntity,
	)

	ProjectSynchronizationPreview(conflict, Res.string.sync_conflict_scene_draft_title, expandedSize())
}

@Preview
@Composable
private fun ProjectDataConflictPreview() {
	val local = ProjectData(
		authorName = "Lewis Carroll",
		theme = ProjectTheme(primary = "#3F51B5", secondary = "#FFC107"),
		wordCountGoal = WordCountGoal(cadence = WordCountGoal.Cadence.DAY, count = 500),
	)
	val server = ProjectData(
		authorName = "Charles Dodgson",
		theme = ProjectTheme(primary = "#009688", secondary = "#E91E63"),
		wordCountGoal = WordCountGoal(cadence = WordCountGoal.Cadence.WEEK, count = 3500),
	)

	val conflictState = ProjectSynchronization.ProjectDataConflictState(
		local = local,
		server = server,
		serverHash = "preview-hash",
	)

	ProjectDataSynchronizationPreview(conflictState, expandedSize())
}

@Preview
@Composable
private fun SyncInProgressPreview() {
	StatePreview(
		state = ProjectSynchronization.State(
			isSyncing = true,
			syncProgress = 0.45f,
		),
		screenCharacteristics = expandedSize(),
	)
}

@Preview
@Composable
private fun SyncSuccessPreview() {
	StatePreview(
		state = ProjectSynchronization.State(
			isSyncing = false,
			failed = false,
			syncProgress = 1.0f,
		),
		screenCharacteristics = expandedSize(),
	)
}

@Preview
@Composable
private fun SyncFailedPreview() {
	StatePreview(
		state = ProjectSynchronization.State(
			isSyncing = false,
			failed = true,
			syncProgress = 0.6f,
		),
		screenCharacteristics = expandedSize(),
	)
}

@Preview
@Composable
private fun SyncLogPreview() {
	val now = Clock.System.now()
	StatePreview(
		state = ProjectSynchronization.State(
			isSyncing = true,
			syncProgress = 0.8f,
			showLog = true,
			syncLog = listOf(
				SyncLogMessage(
					message = "Beginning sync for Wonderland",
					level = SyncLogLevel.INFO,
					projectName = "Wonderland",
					timestamp = now.minus(8.seconds),
				),
				SyncLogMessage(
					message = "Uploaded 12 scenes, 4 notes",
					level = SyncLogLevel.DEBUG,
					projectName = "Wonderland",
					timestamp = now.minus(6.seconds),
				),
				SyncLogMessage(
					message = "Server returned 3 updated encyclopedia entries",
					level = SyncLogLevel.INFO,
					projectName = "Wonderland",
					timestamp = now.minus(4.seconds),
				),
				SyncLogMessage(
					message = "Entry #42 hash mismatch — flagging for conflict resolution",
					level = SyncLogLevel.WARN,
					projectName = "Wonderland",
					timestamp = now.minus(2.seconds),
				),
				SyncLogMessage(
					message = "Failed to upload draft #7: network timeout",
					level = SyncLogLevel.ERROR,
					projectName = "Wonderland",
					timestamp = now.minus(1.seconds),
				),
			),
		),
		screenCharacteristics = expandedSize(),
	)
}

@Composable
private fun ProjectSynchronizationPreview(
	conflict: ProjectSynchronization.EntityConflict<*>,
	conflictTitle: StringResource,
	screenCharacteristics: WindowSizeClass,
) {
	StatePreview(
		state = ProjectSynchronization.State(
			isSyncing = true,
			entityConflict = conflict,
			conflictTitle = conflictTitle,
		),
		screenCharacteristics = screenCharacteristics,
	)
}

@Composable
private fun ProjectDataSynchronizationPreview(
	conflictState: ProjectSynchronization.ProjectDataConflictState,
	screenCharacteristics: WindowSizeClass,
) {
	StatePreview(
		state = ProjectSynchronization.State(
			isSyncing = true,
			projectDataConflict = conflictState,
			conflictTitle = Res.string.sync_conflict_project_data_title,
		),
		screenCharacteristics = screenCharacteristics,
	)
}

@Composable
private fun StatePreview(
	state: ProjectSynchronization.State,
	screenCharacteristics: WindowSizeClass,
) {
	KoinApplicationPreview {
		Box(modifier = Modifier.background(Color.White)) {
			ProjectSynchronizationContent(
				component = previewProjectSyncComponent(state),
				showSnackbar = {},
				screenCharacteristics = screenCharacteristics,
			)
		}
	}
}

private fun previewProjectSyncComponent(
	state: ProjectSynchronization.State,
): ProjectSynchronization {
	val component = object : ProjectSynchronization {
		override val state = MutableValue(ProjectSynchronization.State())
		override fun syncProject(onComplete: (Boolean) -> Unit) {}
		override fun resolveConflict(resolvedEntity: ApiProjectEntity): ProjectSynchronization.EntityMergeError? = null
		override fun resolveProjectDataConflict(resolved: ProjectData) {}
		override fun endSync() {}
		override fun cancelSync() {}
		override fun showLog(show: Boolean) {}
		override fun onUnauthorized() {}
		override fun resolveEntryRef(id: Int) = null
	}

	component.state.update { state }

	return component
}

private val sceneContent = "Alice was beginning to get very tired of sitting by her sister\n" +
		"on the bank, and of having nothing to do:  once or twice she had\n" +
		"peeped into the book her sister was reading, but it had no\n" +
		"pictures or conversations in it, `and what is the use of a book,'\n" +
		"thought Alice `without pictures or conversation?'\n" +
		"\n" +
		"So she was considering in her own mind (as well as she could,\n" +
		"for the hot day made her feel very sleepy and stupid), whether\n" +
		"the pleasure of making a daisy-chain would be worth the trouble\n" +
		"of getting up and picking the daisies, when suddenly a White\n" +
		"Rabbit with pink eyes ran close by her.\n" +
		"\n" +
		"There was nothing so VERY remarkable in that; nor did Alice\n" +
		"think it so VERY much out of the way to hear the Rabbit say to\n" +
		"itself, `Oh dear!  Oh dear!  I shall be late!'  (when she thought\n" +
	"it over afterwards, it occurred to her that she ought to have\n" +
	"wondered at this, but at the time it all seemed quite natural);\n" +
	"but when the Rabbit actually TOOK A WATCH OUT OF ITS WAISTCOAT-\n" +
	"POCKET, and looked at it, and then hurried on, Alice started to\n" +
	"her feet, for it flashed across her mind that she had never\n" +
	"before seen a rabbit with either a waistcoat-pocket, or a watch to\n" +
	"take out of it, and burning with curiosity, she ran across the\n" +
	"field after it, and fortunately was just in time to see it pop\n" +
	"down a large rabbit-hole under the hedge.\n"
