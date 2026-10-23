package com.darkrockstudios.apps.hammer.projects.repository

import com.darkrockstudios.apps.hammer.base.GroupId
import com.darkrockstudios.apps.hammer.project.GroupNameTaken
import com.darkrockstudios.apps.hammer.project.InvalidSyncIdException
import com.darkrockstudios.apps.hammer.projects.ProjectGroup
import com.darkrockstudios.apps.hammer.utilities.ServerResult
import com.darkrockstudios.apps.hammer.utilities.isSuccess
import io.mockk.coEvery
import io.mockk.coVerify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ProjectsRepositoryDeleteGroupTest : ProjectsRepositoryBaseTest() {

	private val syncId = "sync-id"
	private val groupId = GroupId("5c0e7a2b-1d3f-4e5a-9b6c-7d8e9f0a1b2c")
	private val group = ProjectGroup(
		rowId = 7L,
		uuid = groupId,
		name = "Trilogy",
		description = null,
		parentRowId = 3L,
		parentUuid = GroupId("0f1e2d3c-4b5a-4987-8a6b-5c4d3e2f1a0b"),
	)

	@BeforeEach
	fun setupDefaults() {
		coEvery { projectsSessionManager.validateSyncId(userId, syncId, any()) } returns true
		coEvery { projectsDatasource.getGroups(userId) } returns listOf(group)
		coEvery { projectsDatasource.deleteGroup(userId, group, any()) } returns true
	}

	@Test
	fun `invalid sync id is refused`() = runTest {
		coEvery { projectsSessionManager.validateSyncId(userId, syncId, any()) } returns false

		val result = createProjectsRepository().deleteGroup(userId, syncId, groupId)

		assertIs<InvalidSyncIdException>((result as ServerResult.Failure).exception)
		coVerify(exactly = 0) { projectsDatasource.deleteGroup(any(), any(), any()) }
	}

	@Test
	fun `contents move up to the deleted group's parent`() = runTest {
		val result = createProjectsRepository().deleteGroup(userId, syncId, groupId)

		assertTrue(isSuccess(result))
		coVerify { projectsDatasource.deleteGroup(userId, group, group.parentRowId) }
	}

	@Test
	fun `an unknown group is already deleted`() = runTest {
		coEvery { projectsDatasource.getGroups(userId) } returns emptyList()

		val result = createProjectsRepository().deleteGroup(userId, syncId, groupId)

		assertTrue(isSuccess(result))
		coVerify(exactly = 0) { projectsDatasource.deleteGroup(any(), any(), any()) }
	}

	@Test
	fun `a child name collision at the parent names the child and writes nothing`() = runTest {
		val sibling = ProjectGroup(4L, GroupId("9a8b7c6d-5e4f-4a3b-9c2d-1e0f9a8b7c6d"), "Drafts", null, 3L, group.parentUuid)
		val child = ProjectGroup(8L, GroupId("2b1f3c4d-5e6f-4a1b-8c2d-3e4f5a6b7c8d"), "drafts", null, group.rowId, groupId)
		val unrelated = ProjectGroup(9L, GroupId("6d5c4b3a-2f1e-4d0c-9b8a-7f6e5d4c3b2a"), "Notes", null, group.rowId, groupId)
		coEvery { projectsDatasource.getGroups(userId) } returns listOf(sibling, group, unrelated, child)

		val result = createProjectsRepository().deleteGroup(userId, syncId, groupId)

		val failure = assertIs<GroupNameTaken>((result as ServerResult.Failure).exception)
		assertEquals("drafts", failure.name)
		coVerify(exactly = 0) { projectsDatasource.deleteGroup(any(), any(), any()) }
	}

	@Test
	fun `a collision the database reports is refused`() = runTest {
		coEvery { projectsDatasource.deleteGroup(userId, group, any()) } returns false

		val result = createProjectsRepository().deleteGroup(userId, syncId, groupId)

		assertIs<GroupNameTaken>((result as ServerResult.Failure).exception)
	}
}
