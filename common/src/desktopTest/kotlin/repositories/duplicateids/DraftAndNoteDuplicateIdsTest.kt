package repositories.duplicateids

import com.darkrockstudios.apps.hammer.common.data.duplicateids.DuplicateIdKind
import com.darkrockstudios.apps.hammer.common.data.notesrepository.NotesDatasource
import com.darkrockstudios.apps.hammer.common.data.notesrepository.note.NoteContainer
import com.darkrockstudios.apps.hammer.common.data.notesrepository.note.NoteContent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.decodeFromString
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DraftAndNoteDuplicateIdsTest : RationalizerTestBase() {

	private fun noteAt(relative: String): NoteContent = toml.decodeFromString<NoteContainer>(read(relative)).note

	@Test
	fun `Identical legacy and current copies of a draft resolve without asking`() = runTest {
		write("scenes/.drafts/12/12-30-Take one-1700000000.md", "First take")
		write("scenes/.drafts/12/12~30~Take one~1700000000.md", "First take")

		val resolution = rationalizer.resolveSafeDuplicates()

		assertTrue(resolution.remaining.isEmpty())
		assertEquals(listOf("12/12~30~Take one~1700000000.md"), filesIn("scenes/.drafts"))
	}

	@Test
	fun `Three drafts with one id, keeping two, gives the extra one a new id`() = runTest {
		write("scenes/.drafts/12/12~30~Take one~1700000000.md", "First take")
		write("scenes/.drafts/12/12~30~Take two~1700000100.md", "Second take")
		write("scenes/.drafts/14/14~30~Other scene~1700000200.md", "Elsewhere")
		val set = onlySet(DuplicateIdKind.Draft)
		assertEquals(3, set.copies.size)

		val dirty = rationalizer.resolve(
			set,
			keep = setOf(
				set.pathOf("scenes/.drafts/12/12~30~Take one~1700000000.md"),
				set.pathOf("scenes/.drafts/14/14~30~Other scene~1700000200.md"),
			),
			claimId = ids(31),
		)

		assertTrue(dirty.isEmpty())
		assertEquals(
			listOf("12/12~30~Take one~1700000000.md", "14/14~31~Other scene~1700000200.md"),
			filesIn("scenes/.drafts"),
		)
		assertTrue(rationalizer.findDuplicates().isEmpty())
	}

	@Test
	fun `An identical note copied into a subfolder resolves without asking`() = runTest {
		write("notes/note-5.toml", noteToml(5, "Remember the sword"))
		write("notes/old/note-5.toml", noteToml(5, "Remember the sword"))

		val resolution = rationalizer.resolveSafeDuplicates()

		assertTrue(resolution.remaining.isEmpty())
		assertEquals(listOf("note-5.toml"), filesIn("notes"))
	}

	@Test
	fun `Three notes with one id, keeping one, leaves it at the note's own filename`() = runTest {
		write("notes/note-5.toml", noteToml(5, "Remember the sword"))
		write("notes/note-8.toml", noteToml(5, "Remember the shield"))
		write("notes/old/note-5.toml", noteToml(5, "Remember the horse"))
		val set = onlySet(DuplicateIdKind.Note)
		assertEquals(3, set.copies.size)
		assertEquals("notes/note-5.toml", set.inUse.relativePath)

		val dirty = rationalizer.resolve(set, keep = setOf(set.pathOf("notes/note-8.toml")), claimId = noIds)

		assertEquals(setOf(5), dirty)
		assertEquals(listOf("note-5.toml"), filesIn("notes"))
		val notes = NotesDatasource(projectDef, ffs, toml).loadNotes()
		assertEquals(listOf("Remember the shield"), notes.map { it.note.content })
	}

	@Test
	fun `Keeping two notes with one id writes the new id into the extra one`() = runTest {
		write("notes/note-5.toml", noteToml(5, "Remember the sword"))
		write("notes/old/note-5.toml", noteToml(5, "Remember the horse"))
		val set = onlySet(DuplicateIdKind.Note)

		rationalizer.resolve(set, keep = set.copies.map { it.path }.toSet(), claimId = ids(9))

		assertEquals(listOf("note-5.toml", "note-9.toml"), filesIn("notes"))
		assertEquals(9, noteAt("notes/note-9.toml").id)
		assertEquals("Remember the horse", noteAt("notes/note-9.toml").content)
	}
}
