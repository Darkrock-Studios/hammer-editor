import com.darkrockstudios.apps.hammer.common.components.projectselection.GroupData
import com.darkrockstudios.apps.hammer.common.components.projectselection.MoveTarget
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import com.darkrockstudios.apps.hammer.common.preview.projectselection.previewGroup
import com.darkrockstudios.apps.hammer.common.preview.projectselection.previewProject
import com.darkrockstudios.apps.hammer.common.projectselection.current
import org.junit.Test
import kotlin.test.assertEquals

class MoveTargetCurrentTest {

	private val trilogy = previewGroup("Trilogy")
	private val stored = previewProject("Book One", tags = emptySet())
	private val storedGroup = previewGroup("Drafts")

	@Test
	fun `a project moved or renamed since the dialog opened resolves to its current definition`() {
		val moved = stored.copy(
			definition = stored.definition.copy(
				name = "Book 1",
				path = HPath(name = "Book 1", path = "/Trilogy/Book 1", isAbsolute = true),
			)
		)

		val result = MoveTarget.Project(stored.definition).current(listOf(moved), emptyList())

		assertEquals(MoveTarget.Project(moved.definition), result)
	}

	@Test
	fun `a group moved since the dialog opened resolves to its current definition and parent`() {
		val moved = storedGroup.copy(parentId = trilogy.id, path = HPath(name = "Drafts", path = "/Trilogy/Drafts", isAbsolute = true))

		val result = MoveTarget.Group(storedGroup).current(emptyList(), listOf(GroupData(trilogy), GroupData(moved)))

		assertEquals(MoveTarget.Group(moved), result)
	}

	@Test
	fun `a target no longer listed keeps the stored definition`() {
		assertEquals(
			MoveTarget.Project(stored.definition),
			MoveTarget.Project(stored.definition).current(emptyList(), emptyList()),
		)
	}
}
