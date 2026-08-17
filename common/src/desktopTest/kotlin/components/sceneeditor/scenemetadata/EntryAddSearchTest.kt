package components.sceneeditor.scenemetadata

import com.darkrockstudios.apps.hammer.common.components.storyeditor.sceneeditor.scenemetadata.SearchableEntry
import com.darkrockstudios.apps.hammer.common.components.storyeditor.sceneeditor.scenemetadata.filterEntriesForAdd
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryDef
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryType
import getProject1Def
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EntryAddSearchTest {

	private val proj = getProject1Def()

	private fun entry(
		id: Int,
		name: String,
		type: EntryType = EntryType.PERSON,
		aliases: List<String> = emptyList(),
	) = SearchableEntry(
		entryDef = EntryDef(proj, id, type, name),
		lowerCaseSearchTerms = (listOf(name) + aliases).map { it.lowercase() },
	)

	private val entries = listOf(
		entry(1, "Bob"),
		entry(2, "Bobby"),
		entry(3, "Robert", aliases = listOf("Rob", "Bobby")),
		entry(4, "Alice"),
		entry(5, "Atlantis", type = EntryType.PLACE),
		entry(6, "Charlie", type = EntryType.THING),
	)

	@Test
	fun `Empty query returns empty list`() {
		val result = filterEntriesForAdd("", entries, emptySet(), emptySet(), maxResults = 20)
		assertTrue(result.isEmpty())
	}

	@Test
	fun `Blank query returns empty list`() {
		val result = filterEntriesForAdd("   ", entries, emptySet(), emptySet(), maxResults = 20)
		assertTrue(result.isEmpty())
	}

	@Test
	fun `Substring match is case-insensitive`() {
		// 'bo' should match Bob, Bobby, Robert (alias Rob), and Robert (alias Bobby).
		val result = filterEntriesForAdd("BO", entries, emptySet(), emptySet(), maxResults = 20)
		assertEquals(setOf(1, 2, 3), result.map { it.entryDef.id }.toSet())
	}

	@Test
	fun `Match scans aliases as well as name`() {
		// 'rob' is not in 'Bob' or 'Bobby' or 'Alice' or 'Atlantis' or 'Charlie' as
		// a substring of the name, but is the alias 'Rob' on Robert.
		val result = filterEntriesForAdd("rob", entries, emptySet(), emptySet(), maxResults = 20)
		assertEquals(listOf(3), result.map { it.entryDef.id })
	}

	@Test
	fun `Already-confirmed entries are excluded`() {
		// Bob is in confirmed; he should not appear even though he matches.
		val result = filterEntriesForAdd(
			query = "bo",
			entries = entries,
			confirmedIds = setOf(1),
			dismissedIds = emptySet(),
			maxResults = 20,
		)
		assertEquals(setOf(2, 3), result.map { it.entryDef.id }.toSet())
	}

	@Test
	fun `Dismissed entries are included with the isDismissed flag`() {
		// Picking a dismissed entry from search un-dismisses it; the flag tells
		// the UI to show a hint.
		val result = filterEntriesForAdd(
			query = "bob",
			entries = entries,
			confirmedIds = emptySet(),
			dismissedIds = setOf(2),
			maxResults = 20,
		)
		val byId = result.associateBy { it.entryDef.id }
		assertEquals(false, byId[1]?.isDismissed)
		assertEquals(true, byId[2]?.isDismissed)
	}

	@Test
	fun `Results sorted alphabetically by name`() {
		val result = filterEntriesForAdd(
			query = "a",
			entries = entries,
			confirmedIds = emptySet(),
			dismissedIds = emptySet(),
			maxResults = 20,
		)
		assertEquals(listOf("Alice", "Atlantis", "Charlie"), result.map { it.entryDef.name })
	}

	@Test
	fun `All entry types are searchable`() {
		// Manual add should not be gated by ReferenceIndexConfig - the user wants
		// to add an Item or Concept reference that auto-detect won't surface.
		val result = filterEntriesForAdd(
			query = "charlie",
			entries = entries,
			confirmedIds = emptySet(),
			dismissedIds = emptySet(),
			maxResults = 20,
		)
		assertEquals(EntryType.THING, result.single().entryDef.type)
	}

	@Test
	fun `types restricts the result to the requested entry types`() {
		val result = filterEntriesForAdd(
			query = "a",
			entries = entries,
			confirmedIds = emptySet(),
			dismissedIds = emptySet(),
			maxResults = 20,
			types = setOf(EntryType.PLACE),
		)
		assertEquals(listOf("Atlantis"), result.map { it.entryDef.name })
	}

	@Test
	fun `types narrows before maxResults truncates`() {
		// The alphabetically-early people would fill the cap on their own; the
		// requested type must still come back rather than being starved out.
		val crowded = listOf(
			entry(1, "Aaron"),
			entry(2, "Abel"),
			entry(3, "Abigail"),
			entry(4, "Aventine", type = EntryType.PLACE),
		)
		val result = filterEntriesForAdd(
			query = "a",
			entries = crowded,
			confirmedIds = emptySet(),
			dismissedIds = emptySet(),
			maxResults = 2,
			types = setOf(EntryType.PLACE),
		)
		assertEquals(listOf("Aventine"), result.map { it.entryDef.name })
	}

	@Test
	fun `maxResults caps the returned list`() {
		// 'b' matches Bob, Bobby, and Robert; the cap must keep the first two in sorted order.
		val result = filterEntriesForAdd(
			query = "b",
			entries = entries,
			confirmedIds = emptySet(),
			dismissedIds = emptySet(),
			maxResults = 2,
		)
		assertEquals(listOf(1, 2), result.map { it.entryDef.id })
	}

	@Test
	fun `No matches returns empty list`() {
		val result = filterEntriesForAdd(
			query = "zzz",
			entries = entries,
			confirmedIds = emptySet(),
			dismissedIds = emptySet(),
			maxResults = 20,
		)
		assertTrue(result.isEmpty())
	}

	@Test
	fun `Each matching entry appears at most once even when multiple terms match`() {
		// Robert has alias 'Bobby' AND name 'Robert' - both contain 'b'. The result
		// must dedupe to a single entry, not duplicate it once per matching term.
		val robertOnly = listOf(entry(3, "Robert", aliases = listOf("Rob", "Bobby")))
		val result = filterEntriesForAdd(
			query = "b",
			entries = robertOnly,
			confirmedIds = emptySet(),
			dismissedIds = emptySet(),
			maxResults = 20,
		)
		assertEquals(1, result.size)
		assertEquals(3, result.single().entryDef.id)
	}
}
