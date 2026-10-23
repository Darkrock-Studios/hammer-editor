package repositories.projectsrepository

import com.darkrockstudios.apps.hammer.base.GroupId
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.GroupDef
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectTree
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import org.junit.jupiter.api.Test
import testGroupId
import testProjectId
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProjectTreeTest {
	private fun group(name: String, parent: GroupDef? = null) =
		GroupDef(testGroupId(name), name, HPath("/p/$name", name, true), parent?.id, description = null)

	private fun project(name: String) = ProjectDef(testProjectId(name), name, HPath("/p/$name", name, true))

	// Series > Trilogy > Book Notes, plus a separate Shorts group.
	private val series = group("Series")
	private val trilogy = group("Trilogy", series)
	private val notes = group("Book Notes", trilogy)
	private val shorts = group("Shorts")
	private val book1 = project("Book 1")
	private val outline = project("Outline")
	private val loose = project("Loose")
	private val tree = ProjectTree(
		groups = listOf(series, trilogy, notes, shorts),
		projects = listOf(book1, outline, loose),
		placement = mapOf(book1.id to trilogy.id, outline.id to notes.id, loose.id to null),
	)

	@Test
	fun `Depth counts the top level as one`() {
		assertEquals(1, tree.depthOf(series.id))
		assertEquals(3, tree.depthOf(notes.id))
		assertEquals(0, tree.depthOf(GroupId.randomUUID()))
	}

	@Test
	fun `Descendants are found through every level`() {
		assertTrue(tree.isSameOrDescendant(notes.id, series.id))
		assertTrue(tree.isSameOrDescendant(series.id, series.id))
		assertFalse(tree.isSameOrDescendant(series.id, notes.id))
		assertFalse(tree.isSameOrDescendant(shorts.id, series.id))
	}

	@Test
	fun `Tree order lists each group before its subgroups, siblings by name`() {
		val orphan = GroupDef(testGroupId("Orphan"), "orphan", HPath("/p/orphan", "orphan", true), testGroupId("Gone"), null)
		val withOrphan = tree.copy(groups = tree.groups + orphan)

		val ordered = withOrphan.groupsInTreeOrder().map { (group, depth) -> group.name to depth }

		assertEquals(
			listOf("orphan" to 1, "Series" to 1, "Trilogy" to 2, "Book Notes" to 3, "Shorts" to 1),
			ordered,
		)
	}

	@Test
	fun `Tree order skips a cycle`() {
		val a = GroupDef(testGroupId("A"), "A", HPath("/p/A", "A", true), testGroupId("B"), null)
		val b = GroupDef(testGroupId("B"), "B", HPath("/p/B", "B", true), testGroupId("A"), null)

		val ordered = ProjectTree(listOf(a, b, shorts), emptyList(), emptyMap()).groupsInTreeOrder()

		assertEquals(listOf(shorts to 1), ordered)
	}

	@Test
	fun `Path names run from the top level down`() {
		assertEquals(listOf("Series", "Trilogy", "Book Notes"), tree.pathNames(notes.id))
		assertEquals(emptyList(), tree.pathNames(null))
	}
}
