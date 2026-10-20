package com.darkrockstudios.apps.hammer.projects.repository

import com.darkrockstudios.apps.hammer.base.GroupId
import com.darkrockstudios.apps.hammer.base.http.ApiGroupDefinition
import com.darkrockstudios.apps.hammer.base.http.MAX_GROUP_DESCRIPTION_LENGTH
import com.darkrockstudios.apps.hammer.base.http.MAX_GROUP_NESTING_DEPTH
import com.darkrockstudios.apps.hammer.project.GroupCycleException
import com.darkrockstudios.apps.hammer.project.GroupIdTakenException
import com.darkrockstudios.apps.hammer.project.GroupNameTaken
import com.darkrockstudios.apps.hammer.project.GroupTooDeepException
import com.darkrockstudios.apps.hammer.project.InvalidGroupDescription
import com.darkrockstudios.apps.hammer.project.InvalidGroupName
import com.darkrockstudios.apps.hammer.project.InvalidSyncIdException
import com.darkrockstudios.apps.hammer.projects.ProjectGroup
import com.darkrockstudios.apps.hammer.projects.ProjectsRepository.GroupUpsertResult
import com.darkrockstudios.apps.hammer.utilities.SResult
import com.darkrockstudios.apps.hammer.utilities.ServerResult
import com.darkrockstudios.apps.hammer.utilities.isSuccess
import io.mockk.coEvery
import io.mockk.coVerify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ProjectsRepositoryUpsertGroupTest : ProjectsRepositoryBaseTest() {

	private val syncId = "sync-id"
	private val groups = mutableListOf<ProjectGroup>()

	@BeforeEach
	fun setupDefaults() {
		groups.clear()
		coEvery { projectsSessionManager.validateSyncId(userId, syncId, any()) } returns true
		coEvery { projectsDatasource.getGroups(userId) } answers { groups.toList() }
		coEvery { projectsDatasource.getGroup(userId, any()) } answers { groups.find { it.uuid == secondArg() } }
		coEvery { projectsDatasource.findSiblingGroupByName(userId, any(), any()) } answers {
			val parentRowId = secondArg<Long?>()
			val name = thirdArg<String>()
			groups.find { it.parentRowId == parentRowId && it.name.equals(name, ignoreCase = true) }
		}
		coEvery { projectsDatasource.createGroup(userId, any(), any(), any(), any()) } returns true
		coEvery { projectsDatasource.updateGroup(userId, any(), any(), any(), any()) } returns true
	}

	private fun stored(name: String, parent: ProjectGroup? = null): ProjectGroup {
		val group = ProjectGroup(
			rowId = groups.size + 1L,
			uuid = GroupId.randomUUID(),
			name = name,
			description = null,
			parentRowId = parent?.rowId,
			parentUuid = parent?.uuid,
		)
		groups += group
		return group
	}

	/** A chain of [depth] nested groups; returns the deepest. */
	private fun chain(depth: Int): ProjectGroup {
		var parent: ProjectGroup? = null
		repeat(depth) { parent = stored("Level ${it + 1}", parent) }
		return parent!!
	}

	private suspend fun upsert(group: ApiGroupDefinition): SResult<GroupUpsertResult> =
		createProjectsRepository().upsertGroup(userId, syncId, group)

	private fun failure(result: SResult<GroupUpsertResult>) = (result as ServerResult.Failure).exception

	private fun assertNoWrites() {
		coVerify(exactly = 0) { projectsDatasource.createGroup(any(), any(), any(), any(), any()) }
		coVerify(exactly = 0) { projectsDatasource.updateGroup(any(), any(), any(), any(), any()) }
	}

	@Test
	fun `invalid sync id is refused`() = runTest {
		coEvery { projectsSessionManager.validateSyncId(userId, syncId, any()) } returns false

		val result = upsert(ApiGroupDefinition(GroupId.randomUUID(), "Trilogy"))

		assertIs<InvalidSyncIdException>(failure(result))
		assertNoWrites()
	}

	@Test
	fun `invalid name is refused`() = runTest {
		val result = upsert(ApiGroupDefinition(GroupId.randomUUID(), "bad~name"))

		assertIs<InvalidGroupName>(failure(result))
		assertNoWrites()
	}

	@Test
	fun `overlong description is refused`() = runTest {
		val description = "x".repeat(MAX_GROUP_DESCRIPTION_LENGTH + 1)

		val result = upsert(ApiGroupDefinition(GroupId.randomUUID(), "Trilogy", description = description))

		assertIs<InvalidGroupDescription>(failure(result))
		assertNoWrites()
	}

	@Test
	fun `new group is created under its parent`() = runTest {
		val parent = stored("Series")
		val id = GroupId.randomUUID()

		val result = upsert(ApiGroupDefinition(id, "Trilogy", parent.uuid, "three books"))

		assertEquals(SResult.success(GroupUpsertResult(id, alreadyExisted = false)), result)
		coVerify { projectsDatasource.createGroup(userId, id, "Trilogy", "three books", parent.rowId) }
	}

	@Test
	fun `blank description is stored as none`() = runTest {
		val id = GroupId.randomUUID()

		upsert(ApiGroupDefinition(id, "Trilogy", description = "  "))

		coVerify { projectsDatasource.createGroup(userId, id, "Trilogy", null, null) }
	}

	@Test
	fun `unknown parent places the group at the top level`() = runTest {
		val id = GroupId.randomUUID()

		val result = upsert(ApiGroupDefinition(id, "Trilogy", parentId = GroupId.randomUUID()))

		assertEquals(SResult.success(GroupUpsertResult(id, alreadyExisted = false)), result)
		coVerify { projectsDatasource.createGroup(userId, id, "Trilogy", null, null) }
	}

	@Test
	fun `new group adopts a same-name sibling without writing`() = runTest {
		val parent = stored("Series")
		val sibling = stored("Trilogy", parent)

		val result = upsert(ApiGroupDefinition(GroupId.randomUUID(), "TRILOGY", parent.uuid))

		assertEquals(SResult.success(GroupUpsertResult(sibling.uuid, alreadyExisted = true)), result)
		assertNoWrites()
	}

	@Test
	fun `existing group is updated`() = runTest {
		val parent = stored("Series")
		val group = stored("Trilogy")

		val result = upsert(ApiGroupDefinition(group.uuid, "Duology", parent.uuid, "two books"))

		assertEquals(SResult.success(GroupUpsertResult(group.uuid, alreadyExisted = true)), result)
		coVerify { projectsDatasource.updateGroup(userId, group.rowId, "Duology", "two books", parent.rowId) }
	}

	@Test
	fun `existing group may change only the case of its name`() = runTest {
		val group = stored("Trilogy")

		val result = upsert(ApiGroupDefinition(group.uuid, "TRILOGY"))

		assertEquals(SResult.success(GroupUpsertResult(group.uuid, alreadyExisted = true)), result)
		coVerify { projectsDatasource.updateGroup(userId, group.rowId, "TRILOGY", null, null) }
	}

	@Test
	fun `renaming onto a sibling is refused`() = runTest {
		stored("Duology")
		val group = stored("Trilogy")

		val result = upsert(ApiGroupDefinition(group.uuid, "duology"))

		assertIs<GroupNameTaken>(failure(result))
		assertNoWrites()
	}

	@Test
	fun `a collision the database reports is a name conflict`() = runTest {
		val group = stored("Trilogy")
		coEvery { projectsDatasource.updateGroup(userId, any(), any(), any(), any()) } returns false

		val result = upsert(ApiGroupDefinition(group.uuid, "Duology"))

		assertIs<GroupNameTaken>(failure(result))
	}

	@Test
	fun `moving a group inside itself is refused`() = runTest {
		val group = stored("Series")

		val result = upsert(ApiGroupDefinition(group.uuid, "Series", parentId = group.uuid))

		assertIs<GroupCycleException>(failure(result))
		assertNoWrites()
	}

	@Test
	fun `moving a group inside its descendant is refused`() = runTest {
		val top = stored("Series")
		val middle = stored("Trilogy", top)
		val bottom = stored("Book", middle)

		val result = upsert(ApiGroupDefinition(top.uuid, "Series", parentId = bottom.uuid))

		assertIs<GroupCycleException>(failure(result))
		assertNoWrites()
	}

	@Test
	fun `a group may sit at the maximum depth`() = runTest {
		val deepest = chain(MAX_GROUP_NESTING_DEPTH - 1)
		val id = GroupId.randomUUID()

		val result = upsert(ApiGroupDefinition(id, "Bottom", deepest.uuid))

		assertEquals(SResult.success(GroupUpsertResult(id, alreadyExisted = false)), result)
	}

	@Test
	fun `a group below the maximum depth is refused`() = runTest {
		val deepest = chain(MAX_GROUP_NESTING_DEPTH)

		val result = upsert(ApiGroupDefinition(GroupId.randomUUID(), "Too Deep", deepest.uuid))

		assertIs<GroupTooDeepException>(failure(result))
		assertNoWrites()
	}

	@Test
	fun `moving a subtree that would end up too deep is refused`() = runTest {
		val target = chain(MAX_GROUP_NESTING_DEPTH - 2)
		val moved = stored("Moved")
		val child = stored("Child", moved)
		stored("Grandchild", child)

		val result = upsert(ApiGroupDefinition(moved.uuid, "Moved", target.uuid))

		assertIs<GroupTooDeepException>(failure(result))
		assertNoWrites()
	}

	@Test
	fun `moving a subtree that fits is allowed`() = runTest {
		val target = chain(MAX_GROUP_NESTING_DEPTH - 3)
		val moved = stored("Moved")
		val child = stored("Child", moved)
		stored("Grandchild", child)

		val result = upsert(ApiGroupDefinition(moved.uuid, "Moved", target.uuid))

		assertEquals(SResult.success(GroupUpsertResult(moved.uuid, alreadyExisted = true)), result)
	}

	@Test
	fun `losing the insert to a concurrent retry of the same create is idempotent`() = runTest {
		val id = GroupId.randomUUID()
		coEvery { projectsDatasource.createGroup(userId, id, any(), any(), any()) } answers {
			groups += ProjectGroup(99L, id, "Trilogy", null, null, null)
			false
		}

		val result = upsert(ApiGroupDefinition(id, "Trilogy"))

		assertEquals(SResult.success(GroupUpsertResult(id, alreadyExisted = true)), result)
	}

	@Test
	fun `losing the insert to a same-name sibling adopts it`() = runTest {
		val id = GroupId.randomUUID()
		val winner = GroupId.randomUUID()
		coEvery { projectsDatasource.createGroup(userId, id, any(), any(), any()) } answers {
			groups += ProjectGroup(99L, winner, "Trilogy", null, null, null)
			false
		}

		val result = upsert(ApiGroupDefinition(id, "Trilogy"))

		assertEquals(SResult.success(GroupUpsertResult(winner, alreadyExisted = true)), result)
	}

	@Test
	fun `an id another account holds is refused`() = runTest {
		coEvery { projectsDatasource.createGroup(userId, any(), any(), any(), any()) } returns false

		val result = upsert(ApiGroupDefinition(GroupId.randomUUID(), "Trilogy"))

		assertIs<GroupIdTakenException>(failure(result))
	}
}
