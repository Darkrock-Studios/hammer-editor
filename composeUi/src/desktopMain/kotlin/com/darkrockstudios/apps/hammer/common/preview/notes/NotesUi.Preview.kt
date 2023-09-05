package com.darkrockstudios.apps.hammer.common.preview.notes

/*
@Preview
@Composable
private fun NoteItemPreview() {
	val note = NoteContent(
		id = 1,
		created = Clock.System.now(),
		content = "Prow scuttle parrel provost Sail ho shrouds spirits boom mizzenmast yardarm. Pinnace holystone mizzenmast quarter crow's nest nipperkin grog yardarm hempen halter furl. Swab barque interloper chantey doubloon starboard grog black jack gangway rutters."
	)
	val component: Notes = fakeComponent()
	NoteItem(
		note = note,
		component = component,
	)
}

@Preview
@Composable
private fun NotesUiPreview() {
	val component: Notes = fakeComponent()
	NotesUi(component)
}

private fun fakeComponent(): Notes = object : Notes {
	override val state: Value<Notes.State>
		get() = MutableValue(
			Notes.State(
				projectDef = ProjectDef(
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

	override suspend fun createNote(noteText: String) = NoteError.NONE
	override suspend fun deleteNote(id: Int) {}
	override suspend fun updateNote(noteContent: NoteContent) {}
	override fun confirmDelete(note: NoteContent) {}
	override fun dismissConfirmDelete() {}
	override fun showCreate() {}
	override fun dismissCreate() {}
	override fun isAtRoot() = true
	override fun shouldConfirmClose() = emptySet<CloseConfirm>()
}
*/