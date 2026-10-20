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
		coEvery { projectsDatasource.getGroup(userId, groupId) } returns group
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
		coEvery { projectsDatasource.getGroup(userId, groupId) } returns null

		val result = createProjectsRepository().deleteGroup(userId, syncId, groupId)

		assertTrue(isSuccess(result))
		coVerify(exactly = 0) { projectsDatasource.deleteGroup(any(), any(), any()) }
	}

	@Test
	fun `a child name collision at the parent is refused`() = runTest {
		coEvery { projectsDatasource.deleteGroup(userId, group, any()) } returns false

		val result = createProjectsRepository().deleteGroup(userId, syncId, groupId)

		assertIs<GroupNameTaken>((result as ServerResult.Failure).exception)
	}
}
