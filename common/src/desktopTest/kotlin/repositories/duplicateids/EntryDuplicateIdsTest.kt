package repositories.duplicateids

import com.darkrockstudios.apps.hammer.common.data.duplicateids.DuplicateIdKind
import com.darkrockstudios.apps.hammer.common.data.duplicateids.StaleDuplicateIdSet
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EncyclopediaDatasource
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryContainer
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryContent
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryType
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class EntryDuplicateIdsTest : RationalizerTestBase() {

	private val conflictSuffix = ".sync-conflict-20261024-173944-53PQXWR"

	private fun entryToml(id: Int, name: String, text: String, type: EntryType = EntryType.PERSON): String =
		toml.encodeToString(EntryContainer(EntryContent(id = id, name = name, type = type, text = text, tags = emptySet())))

	private fun entryText(relative: String): EntryContent =
		toml.decodeFromString<EntryContainer>(read(relative)).entry

	private fun datasource() = EncyclopediaDatasource(projectDef, toml, ffs, mockk())

	@Test
	fun `Identical sync copy of an entry is removed without asking`() = runTest {
		write("encyclopedia/person/person~5~Bob.toml", entryToml(5, "Bob", "The real Bob"))
		write("encyclopedia/person/person~5~Bob$conflictSuffix.toml", entryToml(5, "Bob", "The real Bob"))

		val resolution = rationalizer.resolveSafeDuplicates()

		assertTrue(resolution.remaining.isEmpty())
		assertTrue(resolution.markDirty.isEmpty())
		assertEquals(listOf("person/person~5~Bob.toml"), filesIn("encyclopedia"))
	}

	@Test
	fun `Differing sync copy of an entry is left for the user`() = runTest {
		write("encyclopedia/person/person~5~Bob.toml", entryToml(5, "Bob", "The real Bob"))
		write("encyclopedia/person/person~5~Bob$conflictSuffix.toml", entryToml(5, "Bob", "Bob from the laptop"))

		val resolution = rationalizer.resolveSafeDuplicates()

		val set = resolution.remaining.single()
		assertEquals(DuplicateIdKind.Entry, set.kind)
		assertEquals(5, set.id)
		assertEquals("encyclopedia/person/person~5~Bob.toml", set.inUse.relativePath)
		assertEquals(2, filesIn("encyclopedia").size)
	}

	@Test
	fun `Keeping the sync copy puts its content at the entry's own filename`() = runTest {
		write("encyclopedia/person/person~5~Bob.toml", entryToml(5, "Bob", "The real Bob"))
		write("encyclopedia/person/person~5~Bob$conflictSuffix.toml", entryToml(5, "Bob", "Bob from the laptop"))
		val set = onlySet(DuplicateIdKind.Entry)

		val dirty = rationalizer.resolve(
			set,
			keep = setOf(set.pathOf("encyclopedia/person/person~5~Bob$conflictSuffix.toml")),
			claimId = noIds,
		)

		assertEquals(setOf(5), dirty)
		assertEquals(listOf("person/person~5~Bob.toml"), filesIn("encyclopedia"))
		assertEquals("Bob from the laptop", entryText("encyclopedia/person/person~5~Bob.toml").text)
	}

	@Test
	fun `Three copies of an entry, keeping two, makes the extra one a new entry`() = runTest {
		write("encyclopedia/person/person~5~Bob.toml", entryToml(5, "Bob", "The real Bob"))
		write("encyclopedia/person/person~5~Bob$conflictSuffix.toml", entryToml(5, "Bob", "Bob from the laptop"))
		write("encyclopedia/person/person~5~Bobby.toml", entryToml(5, "Bobby", "Bobby from the phone"))
		val set = onlySet(DuplicateIdKind.Entry)
		assertEquals(3, set.copies.size)

		val dirty = rationalizer.resolve(
			set,
			keep = setOf(
				set.pathOf("encyclopedia/person/person~5~Bob.toml"),
				set.pathOf("encyclopedia/person/person~5~Bobby.toml"),
			),
			claimId = ids(9),
		)

		assertTrue(dirty.isEmpty())
		assertEquals(listOf("person/person~5~Bob.toml", "person/person~9~Bobby.toml"), filesIn("encyclopedia"))
		assertEquals("The real Bob", entryText("encyclopedia/person/person~5~Bob.toml").text)
		assertEquals(9, entryText("encyclopedia/person/person~9~Bobby.toml").id)

		val entries = datasource().loadEntriesImperative()
		assertEquals(listOf(5, 9), entries.map { it.id }.sorted())
	}

	@Test
	fun `After resolving a legacy pair, deleting the entry leaves nothing behind`() = runTest {
		write("encyclopedia/person/person-5-Bob.toml", entryToml(5, "Bob", "Before the migration"))
		write("encyclopedia/person/person~5~Bob.toml", entryToml(5, "Bob", "After the migration"))
		val set = onlySet(DuplicateIdKind.Entry)
		// Listing order puts the legacy file first; the current-format lookup prefers the other one.
		assertEquals("encyclopedia/person/person-5-Bob.toml", set.inUse.relativePath)

		rationalizer.resolve(set, keep = setOf(set.pathOf("encyclopedia/person/person~5~Bob.toml")), claimId = noIds)

		val datasource = datasource()
		val def = datasource.loadEntriesImperative().single()
		assertEquals(datasource.findEntryPath(5), datasource.getEntryPath(def))
		datasource.deleteEntry(def)
		assertTrue(datasource.loadEntriesImperative().isEmpty())
	}

	@Test
	fun `Entries of two types sharing an id keep their images when both are kept`() = runTest {
		write("encyclopedia/person/person~5~Bob.toml", entryToml(5, "Bob", "A person"))
		write("encyclopedia/person/person~5~image.png", "bob-image")
		write("encyclopedia/place/place~5~Castle.toml", entryToml(5, "Castle", "A place", EntryType.PLACE))
		write("encyclopedia/place/place~5~image.png", "castle-image")
		val set = onlySet(DuplicateIdKind.Entry)

		rationalizer.resolve(set, keep = set.copies.map { it.path }.toSet(), claimId = ids(9))

		assertEquals(
			listOf(
				"person/person~5~Bob.toml",
				"person/person~5~image.png",
				"place/place~9~Castle.toml",
				"place/place~9~image.png",
			),
			filesIn("encyclopedia"),
		)
		assertEquals("castle-image", read("encyclopedia/place/place~9~image.png"))
	}

	@Test
	fun `A discarded entry of another type takes its image with it`() = runTest {
		write("encyclopedia/person/person~5~Bob.toml", entryToml(5, "Bob", "A person"))
		write("encyclopedia/person/person~5~image.png", "bob-image")
		write("encyclopedia/place/place~5~Castle.toml", entryToml(5, "Castle", "A place", EntryType.PLACE))
		write("encyclopedia/place/place~5~image.png", "castle-image")
		val set = onlySet(DuplicateIdKind.Entry)

		rationalizer.resolve(set, keep = setOf(set.pathOf("encyclopedia/person/person~5~Bob.toml")), claimId = noIds)

		assertEquals(listOf("person/person~5~Bob.toml", "person/person~5~image.png"), filesIn("encyclopedia"))
	}

	@Test
	fun `Identical legacy and current images of an entry resolve without asking`() = runTest {
		write("encyclopedia/person/person~5~Bob.toml", entryToml(5, "Bob", "A person"))
		write("encyclopedia/person/person-5-image.png", "same")
		write("encyclopedia/person/person~5~image.png", "same")

		val resolution = rationalizer.resolveSafeDuplicates()

		assertTrue(resolution.remaining.isEmpty())
		assertEquals(listOf("person/person~5~Bob.toml", "person/person~5~image.png"), filesIn("encyclopedia"))
	}

	@Test
	fun `Three different images of an entry, keeping one deletes the others`() = runTest {
		write("encyclopedia/person/person~5~Bob.toml", entryToml(5, "Bob", "A person"))
		write("encyclopedia/person/person-5-image.png", "old")
		write("encyclopedia/person/person~5~image.png", "png")
		write("encyclopedia/person/person~5~image.jpg", "jpg")
		assertTrue(rationalizer.resolveSafeDuplicates().remaining.isNotEmpty())
		val set = onlySet(DuplicateIdKind.EntryImage)
		assertEquals(3, set.copies.size)

		assertFailsWith<IllegalArgumentException> {
			rationalizer.resolve(set, keep = set.copies.take(2).map { it.path }.toSet(), claimId = noIds)
		}
		rationalizer.resolve(set, keep = setOf(set.pathOf("encyclopedia/person/person~5~image.jpg")), claimId = noIds)

		assertEquals(listOf("person/person~5~Bob.toml", "person/person~5~image.jpg"), filesIn("encyclopedia"))
		val def = datasource().loadEntriesImperative().single()
		assertEquals("person~5~image.jpg", datasource().findEntryImagePath(def)?.name)
	}

	@Test
	fun `Resolving a set whose files changed since the scan deletes nothing`() = runTest {
		write("encyclopedia/person/person~5~Bob.toml", entryToml(5, "Bob", "The real Bob"))
		write("encyclopedia/person/person~5~Bob$conflictSuffix.toml", entryToml(5, "Bob", "Bob from the laptop"))
		val set = onlySet(DuplicateIdKind.Entry)
		write("encyclopedia/person/person~5~Bob.toml", entryToml(5, "Bob", "Edited since"))

		assertFailsWith<StaleDuplicateIdSet> {
			rationalizer.resolve(set, keep = setOf(set.inUse.path), claimId = noIds)
		}
		assertEquals(2, filesIn("encyclopedia").size)
	}
}
