package operations

import com.darkrockstudios.apps.hammer.common.data.ClientResult
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository
import com.darkrockstudios.apps.hammer.operations.OperationException
import com.darkrockstudios.apps.hammer.operations.core.Entries
import com.darkrockstudios.apps.hammer.operations.core.Entry
import com.darkrockstudios.apps.hammer.operations.core.EntryCreateInput
import com.darkrockstudios.apps.hammer.operations.core.EntryImageImportInput
import com.darkrockstudios.apps.hammer.operations.core.EntryImageInput
import com.darkrockstudios.apps.hammer.operations.core.EntryImages
import com.darkrockstudios.apps.hammer.operations.core.EntryKind
import com.darkrockstudios.apps.hammer.operations.core.EntryListInput
import com.darkrockstudios.apps.hammer.operations.core.EntrySummary
import com.darkrockstudios.apps.hammer.operations.core.EntryUpdateInput
import com.darkrockstudios.apps.hammer.operations.core.Note
import com.darkrockstudios.apps.hammer.operations.core.NoteCreateInput
import com.darkrockstudios.apps.hammer.operations.core.NoteListInput
import com.darkrockstudios.apps.hammer.operations.core.NoteUpdateInput
import com.darkrockstudios.apps.hammer.operations.core.Notes
import com.darkrockstudios.apps.hammer.operations.core.ProjectInput
import com.darkrockstudios.apps.hammer.operations.core.ProjectItemInput
import com.darkrockstudios.apps.hammer.operations.core.Timeline
import com.darkrockstudios.apps.hammer.operations.core.TimelineCreateInput
import com.darkrockstudios.apps.hammer.operations.core.TimelineEntry
import com.darkrockstudios.apps.hammer.operations.core.TimelineMoveInput
import com.darkrockstudios.apps.hammer.operations.core.TimelineUpdateInput
import okio.Path.Companion.toPath
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.core.component.get
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ContentWriteOperationsTest : KoinOperationsTest() {

	@BeforeEach
	fun setUp() = onTestThread {
		val created = get<ProjectsRepository>().createProject(PROJECT, seedDefaultLanguage = false)
		check(created is ClientResult.Success<ProjectDef>)
	}

	private fun invalid(block: suspend () -> Unit) = onTestThread {
		assertEquals(OperationException.Kind.InvalidInput, assertFailsWith<OperationException> { block() }.kind)
	}

	@Test
	fun `notes are created, updated, and deleted`() = onTestThread {
		val note = run<NoteCreateInput, Note>("note.create", NoteCreateInput(PROJECT, "Check the tides", listOf("#research")))
		assertEquals(listOf("research"), note.tags)

		val updated = run<NoteUpdateInput, Note>("note.update", NoteUpdateInput(PROJECT, note.id, "Check the tide tables"))
		assertEquals("Check the tide tables", updated.content)
		assertEquals(listOf("research"), updated.tags)

		run<ProjectItemInput, Note>("note.delete", ProjectItemInput(PROJECT, note.id))
		assertEquals(emptyList(), run<NoteListInput, Notes>("note.list", NoteListInput(PROJECT)).notes)
	}

	@Test
	fun `empty notes are invalid`() = invalid {
		run<NoteCreateInput, Note>("note.create", NoteCreateInput(PROJECT, "  "))
	}

	@Test
	fun `entries are created, updated, and deleted`() = onTestThread {
		val entry = run<EntryCreateInput, Entry>(
			"entry.create",
			EntryCreateInput(PROJECT, "Alice", EntryKind.Person, "The keeper.", tags = listOf("cast"), aliases = listOf("Al")),
		)
		assertEquals(listOf("Al"), entry.aliases)

		val updated = run<EntryUpdateInput, Entry>("entry.update", EntryUpdateInput(PROJECT, entry.id, "The lighthouse keeper.", name = "Alice Grey"))
		assertEquals("Alice Grey", updated.name)
		assertEquals("The lighthouse keeper.", updated.text)
		assertEquals(listOf("cast"), updated.tags)

		run<ProjectItemInput, EntrySummary>("entry.delete", ProjectItemInput(PROJECT, entry.id))
		assertEquals(emptyList(), run<EntryListInput, Entries>("entry.list", EntryListInput(PROJECT)).entries)
	}

	@Test
	fun `entry images are imported, added, reordered, and removed`() = onTestThread {
		val alice = run<EntryCreateInput, Entry>("entry.create", EntryCreateInput(PROJECT, "Alice", EntryKind.Person, ""))
		val point = run<EntryCreateInput, Entry>("entry.create", EntryCreateInput(PROJECT, "Point", EntryKind.Place, ""))

		// Each import appends, so the first stays primary.
		val first = run<EntryImageImportInput, EntryImages>("entry.images.import", EntryImageImportInput(PROJECT, alice.id, "jpg", JPEG))
		val both = run<EntryImageImportInput, EntryImages>("entry.images.import", EntryImageImportInput(PROJECT, alice.id, "PNG", IMAGE))
		val portrait = first.images.single()
		val sketch = both.images.last()
		assertEquals(listOf(portrait, sketch), both.images)
		assertEquals(listOf("Alice" to "jpg", "Alice" to "png"), both.images.map { it.name to it.extension })
		assertEquals(listOf(portrait.id, sketch.id), run<ProjectItemInput, Entry>("entry.read", ProjectItemInput(PROJECT, alice.id)).images)

		val primary = run<EntryImageInput, EntryImages>("entry.images.primary", EntryImageInput(PROJECT, alice.id, sketch.id))
		assertEquals(listOf(sketch.id, portrait.id), primary.images.map { it.id })

		val shared = run<EntryImageInput, EntryImages>("entry.images.add", EntryImageInput(PROJECT, point.id, portrait.id))
		assertEquals(listOf(portrait.id), shared.images.map { it.id })

		val removed = run<EntryImageInput, EntryImages>("entry.images.remove", EntryImageInput(PROJECT, alice.id, portrait.id))
		assertEquals(listOf(sketch.id), removed.images.map { it.id })
		// Removing only detaches: the image is still in the project, on the other entry.
		assertEquals(listOf(portrait.id), run<ProjectItemInput, EntryImages>("entry.images.list", ProjectItemInput(PROJECT, point.id)).images.map { it.id })
		assertTrue(run<EntryImageInput, EntryImages>("entry.images.remove", EntryImageInput(PROJECT, alice.id, sketch.id)).images.isEmpty())
		assertFalse(run<ProjectItemInput, Entry>("entry.read", ProjectItemInput(PROJECT, alice.id)).hasImage)
	}

	@Test
	fun `entry images that are not there are not found`() {
		val entry = onTestThread { run<EntryCreateInput, Entry>("entry.create", EntryCreateInput(PROJECT, "Point", EntryKind.Place, "")) }
		val missing = listOf(
			"entry.images.add" to EntryImageInput(PROJECT, entry.id, 404),
			"entry.images.remove" to EntryImageInput(PROJECT, entry.id, 404),
			"entry.images.primary" to EntryImageInput(PROJECT, entry.id, 404),
			"entry.images.add" to EntryImageInput(PROJECT, 999, 404),
		)
		missing.forEach { (name, input) ->
			onTestThread {
				val error = assertFailsWith<OperationException> { run<EntryImageInput, EntryImages>(name, input) }
				assertEquals(OperationException.Kind.NotFound, error.kind, name)
			}
		}
	}

	@Test
	fun `an entry whose file cannot be read is listed without aliases, and can be deleted`() = onTestThread {
		val entry = run<EntryCreateInput, Entry>(
			"entry.create",
			EntryCreateInput(PROJECT, "Alice", EntryKind.Person, "The keeper.", aliases = listOf("Al")),
		)
		val file = ffs.listRecursively("/".toPath()).single { it.name.endsWith(".toml") && "Alice" in it.name }
		ffs.write(file) { writeUtf8("not = [toml") }

		val listed = run<EntryListInput, Entries>("entry.list", EntryListInput(PROJECT)).entries.single()
		assertEquals(listOf("Alice" to emptyList<String>()), listOf(listed.name to listed.aliases))
		assertEquals(emptyList(), run<ProjectItemInput, EntrySummary>("entry.delete", ProjectItemInput(PROJECT, entry.id)).aliases)
		assertEquals(emptyList(), run<EntryListInput, Entries>("entry.list", EntryListInput(PROJECT)).entries)
	}

	@Test
	fun `bad entry names and images are invalid`() {
		invalid { run<EntryCreateInput, Entry>("entry.create", EntryCreateInput(PROJECT, "bad~name", EntryKind.Place, "")) }
		val entry = onTestThread { run<EntryCreateInput, Entry>("entry.create", EntryCreateInput(PROJECT, "Point", EntryKind.Place, "")) }
		invalid { run<EntryImageImportInput, EntryImages>("entry.images.import", EntryImageImportInput(PROJECT, entry.id, "gif", IMAGE)) }
		invalid { run<EntryImageImportInput, EntryImages>("entry.images.import", EntryImageImportInput(PROJECT, entry.id, "jpg", IMAGE)) }
		invalid { run<EntryImageImportInput, EntryImages>("entry.images.import", EntryImageImportInput(PROJECT, entry.id, "png", TOO_LARGE)) }
	}

	@Test
	fun `timeline events are created in place, updated, moved, and deleted`() = onTestThread {
		val landfall = run<TimelineCreateInput, TimelineEntry>("timeline.create", TimelineCreateInput(PROJECT, "Landfall", date = "Day 2"))
		val arrival = run<TimelineCreateInput, TimelineEntry>("timeline.create", TimelineCreateInput(PROJECT, "Alice arrives", index = 0))
		run<TimelineCreateInput, TimelineEntry>("timeline.create", TimelineCreateInput(PROJECT, "Aftermath"))
		assertEquals(listOf("Alice arrives", "Landfall", "Aftermath"), events())

		val updated = run<TimelineUpdateInput, TimelineEntry>("timeline.update", TimelineUpdateInput(PROJECT, landfall.id, "The storm lands", date = ""))
		assertNull(updated.date)

		run<TimelineMoveInput, TimelineEntry>("timeline.move", TimelineMoveInput(PROJECT, arrival.id, 2))
		assertEquals(listOf("The storm lands", "Aftermath", "Alice arrives"), events())
		run<TimelineMoveInput, TimelineEntry>("timeline.move", TimelineMoveInput(PROJECT, arrival.id, 0))
		assertEquals(listOf("Alice arrives", "The storm lands", "Aftermath"), events())

		run<ProjectItemInput, TimelineEntry>("timeline.delete", ProjectItemInput(PROJECT, landfall.id))
		assertEquals(listOf("Alice arrives", "Aftermath"), events())
	}

	@Test
	fun `timeline positions out of range are invalid`() = invalid {
		run<TimelineCreateInput, TimelineEntry>("timeline.create", TimelineCreateInput(PROJECT, "Too far", index = 1))
	}

	private suspend fun events() = run<ProjectInput, Timeline>("timeline.list", ProjectInput(PROJECT)).events.map { it.content }

	private companion object {
		const val PROJECT = "Storm Novel"
		val JPEG = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0)
		val IMAGE = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2)
		val TOO_LARGE = IMAGE + ByteArray(9 * 1024 * 1024)
	}
}
