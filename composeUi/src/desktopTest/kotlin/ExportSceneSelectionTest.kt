import com.darkrockstudios.apps.hammer.common.data.ExportableScene
import com.darkrockstudios.apps.hammer.common.projecthome.allSceneIds
import com.darkrockstudios.apps.hammer.common.projecthome.descendantSceneIds
import com.darkrockstudios.apps.hammer.common.projecthome.isGroupFullySelected
import com.darkrockstudios.apps.hammer.common.projecthome.toggleGroup
import com.darkrockstudios.apps.hammer.common.projecthome.toggleScene
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExportSceneSelectionTest {

	// Scene 1, Group 2 [Scene 3, Scene 4, Group 5 [Scene 6]], Scene 7, Group 8 (empty)
	private val entries = listOf(
		ExportableScene(id = 1, name = "Scene 1", isGroup = false, depth = 0),
		ExportableScene(id = 2, name = "Group 2", isGroup = true, depth = 0),
		ExportableScene(id = 3, name = "Scene 3", isGroup = false, depth = 1),
		ExportableScene(id = 4, name = "Scene 4", isGroup = false, depth = 1),
		ExportableScene(id = 5, name = "Group 5", isGroup = true, depth = 1),
		ExportableScene(id = 6, name = "Scene 6", isGroup = false, depth = 2),
		ExportableScene(id = 7, name = "Scene 7", isGroup = false, depth = 0),
		ExportableScene(id = 8, name = "Group 8", isGroup = true, depth = 0),
	)

	private val topGroup = entries[1]
	private val nestedGroup = entries[4]
	private val emptyGroup = entries[7]

	@Test
	fun `allSceneIds contains only leaf scenes`() {
		assertEquals(setOf(1, 3, 4, 6, 7), allSceneIds(entries))
	}

	@Test
	fun `descendantSceneIds spans the whole subtree including nested groups`() {
		assertEquals(listOf(3, 4, 6), descendantSceneIds(entries, topGroup))
		assertEquals(listOf(6), descendantSceneIds(entries, nestedGroup))
		assertEquals(emptyList(), descendantSceneIds(entries, emptyGroup))
	}

	@Test
	fun `descendantSceneIds stops at the next sibling of the same depth`() {
		assertFalse(7 in descendantSceneIds(entries, topGroup))
	}

	@Test
	fun `toggleScene adds and removes a single id`() {
		val selected = toggleScene(emptySet(), 3)
		assertEquals(setOf(3), selected)
		assertEquals(emptySet(), toggleScene(selected, 3))
	}

	@Test
	fun `toggleGroup selects every descendant scene`() {
		assertEquals(setOf(3, 4, 6), toggleGroup(entries, emptySet(), topGroup))
	}

	@Test
	fun `toggleGroup on a fully selected group clears its descendants only`() {
		val selected = setOf(1, 3, 4, 6, 7)
		assertEquals(setOf(1, 7), toggleGroup(entries, selected, topGroup))
	}

	@Test
	fun `toggleGroup on a partially selected group completes the selection`() {
		assertEquals(setOf(3, 4, 6), toggleGroup(entries, setOf(3), topGroup))
	}

	@Test
	fun `toggleGroup on an empty group changes nothing`() {
		val selected = setOf(1)
		assertEquals(selected, toggleGroup(entries, selected, emptyGroup))
	}

	@Test
	fun `isGroupFullySelected requires every descendant and is never true for empty groups`() {
		assertTrue(isGroupFullySelected(entries, setOf(3, 4, 6), topGroup))
		assertFalse(isGroupFullySelected(entries, setOf(3, 4), topGroup))
		assertFalse(isGroupFullySelected(entries, setOf(1, 3, 4, 6, 7), emptyGroup))
	}
}
