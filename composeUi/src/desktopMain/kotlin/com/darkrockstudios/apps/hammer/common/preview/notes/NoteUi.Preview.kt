package com.darkrockstudios.apps.hammer.common.preview.notes

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.runtime.Composable
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.common.components.notes.BrowseNotes
import com.darkrockstudios.apps.hammer.common.data.ProjectDefinition
import com.darkrockstudios.apps.hammer.common.data.notesrepository.note.NoteContent
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import com.darkrockstudios.apps.hammer.common.notes.BrowseNotesUi
import com.darkrockstudios.apps.hammer.common.notes.NoteItem
import kotlin.time.Clock

@Preview
@Composable
private fun NoteItemPreview() {
	val note = NoteContent(
		id = 1,
		created = Clock.System.now(),
		content = "Prow scuttle parrel provost Sail ho shrouds spirits boom mizzenmast yardarm. Pinnace holystone mizzenmast quarter crow's nest nipperkin grog yardarm hempen halter furl. Swab barque interloper chantey doubloon starboard grog black jack gangway rutters."
	)
	NoteItem(
		note = note,
		viewNote = {}
	)
}

@Preview
@Composable
private fun BrowseNotesUiPreview() {
	val component: BrowseNotes = fakeComponent()
	BrowseNotesUi(component)
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
						content = "This is a really great note, the best, everyone is saying so."
					),
					NoteContent(
						id = 1,
						created = Clock.System.now(),
						content = "Prow scuttle parrel provost Sail ho shrouds spirits boom mizzenmast yardarm. Pinnace holystone mizzenmast quarter crow's nest nipperkin grog yardarm hempen halter furl. Swab barque interloper chantey doubloon starboard grog black jack gangway rutters."
					),
					NoteContent(
						id = 2,
						created = Clock.System.now(),
						content = "Deadlights jack lad schooner scallywag dance the hempen jig carouser broadside cable strike colors. Bring a spring upon her cable holystone blow the man down spanker Shiver me timbers to go on account lookout wherry doubloon chase. Belay yo-ho-ho keelhaul squiffy black spot yardarm spyglass sheet transom heave to."
					),
				)
			)
		)

	override fun viewNote(noteId: Int) {}
	override fun showCreate() {}
}
