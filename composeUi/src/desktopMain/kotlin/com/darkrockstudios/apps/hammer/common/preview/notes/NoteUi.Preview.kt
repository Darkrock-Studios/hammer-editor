package com.darkrockstudios.apps.hammer.common.preview.notes

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.common.components.notes.BrowseNotes
import com.darkrockstudios.apps.hammer.common.data.ProjectDefinition
import com.darkrockstudios.apps.hammer.common.data.notesrepository.note.NoteContent
import com.darkrockstudios.apps.hammer.common.data.tagindex.TagCount
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import com.darkrockstudios.apps.hammer.common.notes.BrowseNotesUi
import com.darkrockstudios.apps.hammer.common.preview.TABLET_HEIGHT_DP
import com.darkrockstudios.apps.hammer.common.preview.TABLET_WIDTH_DP
import com.darkrockstudios.apps.hammer.common.preview.TabletPreviewSurface
import kotlin.time.Clock

@OptIn(ExperimentalSharedTransitionApi::class)
@Preview
@Composable
fun ScreenBrowseNotesUiPreview() {
	val component: BrowseNotes = fakeComponent()
	SharedTransitionLayout {
		AnimatedVisibility(visible = true) {
			BrowseNotesUi(
				component = component,
				sharedTransitionScope = this@SharedTransitionLayout,
				animatedVisibilityScope = this@AnimatedVisibility,
			)
		}
	}
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Preview(widthDp = TABLET_WIDTH_DP, heightDp = TABLET_HEIGHT_DP)
@Composable
fun ScreenBrowseNotesUiTabletPreview() {
	val component: BrowseNotes = fakeComponent()
	TabletPreviewSurface {
		SharedTransitionLayout {
			AnimatedVisibility(visible = true) {
				BrowseNotesUi(
					component = component,
					sharedTransitionScope = this@SharedTransitionLayout,
					animatedVisibilityScope = this@AnimatedVisibility,
				)
			}
		}
	}
}

private fun fakeComponent(): BrowseNotes = object : BrowseNotes {
	override val state: Value<BrowseNotes.State>
		get() = MutableValue(
			BrowseNotes.State(
				projectDef = ProjectDefinition(
					name = "test2", path = HPath("/", "test", true)
				),
				notes = listOf(
					NoteContent(
						id = 0,
						created = Clock.System.now(),
						content = "This is a really great note, the best, everyone is saying so.",
						tags = setOf("voice", "research"),
					),
					NoteContent(
						id = 1,
						created = Clock.System.now(),
						content = "Prow scuttle parrel provost Sail ho shrouds spirits boom mizzenmast yardarm. Pinnace holystone mizzenmast quarter crow's nest nipperkin grog yardarm hempen halter furl. Swab barque interloper chantey doubloon starboard grog black jack gangway rutters.",
						tags = setOf("ch1", "research"),
					),
					NoteContent(
						id = 2,
						created = Clock.System.now(),
						content = "Deadlights jack lad schooner scallywag dance the hempen jig carouser broadside cable strike colors. Bring a spring upon her cable holystone blow the man down spanker Shiver me timbers to go on account lookout wherry doubloon chase. Belay yo-ho-ho keelhaul squiffy black spot yardarm spyglass sheet transom heave to.",
						tags = setOf("voice"),
					),
				)
			)
		)

	override val rankedTags: Value<List<TagCount>>
		get() = MutableValue(
			listOf(
				TagCount("research", 2),
				TagCount("voice", 2),
				TagCount("ch1", 1),
			)
		)

	override fun viewNote(noteId: Int) {}
	override fun showCreate() {}
}
