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
import kotlin.test.assertNull
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
	fun `Subtree height counts the group itself`() {
		assertEquals(3, tree.subtreeHeight(series.id))
		assertEquals(1, tree.subtreeHeight(shorts.id))
	}

	@Test
	fun `Descendants are found through every level`() {
		assertTrue(tree.isSameOrDescendant(notes.id, series.id))
		assertTrue(tree.isSameOrDescendant(series.id, series.id))
		assertFalse(tree.isSameOrDescendant(series.id, notes.id))
		assertFalse(tree.isSameOrDescendant(shorts.id, series.id))
	}

	@Test
	fun `Projects under a group include nested groups`() {
		assertEquals(listOf(book1, outline), tree.projectsUnder(series.id))
		assertEquals(listOf(outline), tree.projectsIn(notes.id))
		assertEquals(listOf(loose), tree.projectsIn(null))
	}

	@Test
	fun `Children and parents follow placement`() {
		assertEquals(listOf(series, shorts), tree.childGroups(null))
		assertEquals(listOf(trilogy), tree.childGroups(series.id))
		assertEquals(trilogy, tree.parentOf(book1.id))
		assertNull(tree.parentOf(loose.id))
		assertEquals(series, tree.parentOf(trilogy))
	}

	@Test
	fun `Path names run from the top level down`() {
		assertEquals(listOf("Series", "Trilogy", "Book Notes"), tree.pathNames(notes.id))
		assertEquals(emptyList(), tree.pathNames(null))
	}
}
