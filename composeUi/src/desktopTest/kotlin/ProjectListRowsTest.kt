import com.darkrockstudios.apps.hammer.base.GroupId
import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.common.components.projectselection.GroupData
import com.darkrockstudios.apps.hammer.common.components.projectselection.ProjectData
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.GroupDef
import com.darkrockstudios.apps.hammer.common.preview.projectselection.previewGroup
import com.darkrockstudios.apps.hammer.common.preview.projectselection.previewProject
import com.darkrockstudios.apps.hammer.common.projectselection.ProjectListRow
import com.darkrockstudios.apps.hammer.common.projectselection.ProjectsSortMode
import com.darkrockstudios.apps.hammer.common.projectselection.buildProjectListRows
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class ProjectListRowsTest {

	private val zeta = previewGroup("zeta")
	private val alpha = previewGroup("Alpha")
	private val inner = previewGroup("Inner", parent = alpha)

	private fun project(name: String, parent: GroupDef? = null, lastOpened: Long = 0): ProjectData {
		val base = previewProject(name, tags = emptySet(), parent = parent)
		return base.copy(
			metadata = base.metadata.copy(
				info = base.metadata.info.copy(lastAccessed = Instant.fromEpochSeconds(lastOpened))
			)
		)
	}

	private val looseOld = project("Loose Old", lastOpened = 10)
	private val looseNew = project("Loose New", lastOpened = 30)
	private val inAlpha = project("In Alpha", alpha, lastOpened = 20)
	private val inInner = project("In Inner", inner, lastOpened = 5)
	private val inZeta = project("In Zeta", zeta, lastOpened = 1)
	private val projects = listOf(looseOld, looseNew, inAlpha, inInner, inZeta)
	private val placement = mapOf(
		looseOld.definition.id to null,
		looseNew.definition.id to null,
		inAlpha.definition.id to alpha.id,
		inInner.definition.id to inner.id,
		inZeta.definition.id to zeta.id,
	)

	private fun rows(
		groups: List<GroupData> = listOf(GroupData(zeta), GroupData(alpha), GroupData(inner)),
		visible: List<ProjectData> = projects,
		filtered: Boolean = false,
		placement: Map<ProjectId, GroupId?> = this.placement,
	) = buildProjectListRows(groups, visible, placement, ProjectsSortMode.LastAccessedDesc, filtered)

	private fun List<ProjectListRow>.describe(): List<String> = map { row ->
		when (row) {
			is ProjectListRow.Group -> "${"  ".repeat(row.depth - 1)}[${row.data.definition.name}] ${row.projectCount}"
			is ProjectListRow.Project ->
				"${"  ".repeat(row.depth)}${row.number}. ${row.data.definition.name}" +
					row.groupPath.takeIf { it.isNotEmpty() }?.joinToString(" › ", prefix = " @ ").orEmpty()
		}
	}

	@Test
	fun `groups come first at each level, alphabetically, then projects in sort order`() {
		assertEquals(
			listOf(
				"[Alpha] 2",
				"  [Inner] 1",
				"    1. In Inner",
				"  2. In Alpha",
				"[zeta] 1",
				"  3. In Zeta",
				"4. Loose New",
				"5. Loose Old",
			),
			rows().describe(),
		)
	}

	@Test
	fun `a collapsed group hides everything beneath it but still counts it`() {
		val result = rows(groups = listOf(GroupData(zeta), GroupData(alpha, collapsed = true), GroupData(inner)))

		assertEquals(
			listOf(
				"[Alpha] 2",
				"[zeta] 1",
				"  1. In Zeta",
				"2. Loose New",
				"3. Loose Old",
			),
			result.describe(),
		)
	}

	@Test
	fun `a filter flattens matches with the path of groups they sit in`() {
		val result = rows(visible = listOf(inInner, looseOld), filtered = true)

		assertEquals(
			listOf(
				"1. Loose Old",
				"2. In Inner @ Alpha › Inner",
			),
			result.describe(),
		)
	}

	@Test
	fun `a project placed in an unknown group shows at the top level`() {
		val result = rows(groups = listOf(GroupData(zeta)))

		assertEquals(
			listOf(
				"[zeta] 1",
				"  1. In Zeta",
				"2. Loose New",
				"3. In Alpha",
				"4. Loose Old",
				"5. In Inner",
			),
			result.describe(),
		)
	}

	@Test
	fun `row keys are stable and distinct across groups and projects`() {
		val keys = rows().map { it.key }

		assertEquals(keys.size, keys.toSet().size)
		assertEquals("g:${alpha.id.id}", rows().first().key)
	}

	@Test
	fun `group counts include every project beneath them, however deep`() {
		val deep = previewGroup("Deep", parent = inner)
		val inDeep = project("In Deep", deep)
		val alsoInDeep = project("Also In Deep", deep)
		val all = projects + inDeep + alsoInDeep
		val nestedPlacement = placement + mapOf(inDeep.definition.id to deep.id, alsoInDeep.definition.id to deep.id)

		val counts = rows(
			groups = listOf(GroupData(zeta), GroupData(alpha), GroupData(inner), GroupData(deep)),
			visible = all,
			placement = nestedPlacement,
		).filterIsInstance<ProjectListRow.Group>().associate { it.data.definition.name to it.projectCount }

		assertEquals(mapOf("Alpha" to 4, "Inner" to 3, "Deep" to 2, "zeta" to 1), counts)
	}
}
