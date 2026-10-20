package com.darkrockstudios.apps.hammer.projects.repository

import com.darkrockstudios.apps.hammer.base.GroupId
import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.project.InvalidSyncIdException
import com.darkrockstudios.apps.hammer.project.ProjectNotFound
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

class ProjectsRepositoryMoveProjectTest : ProjectsRepositoryBaseTest() {

	private val syncId = "sync-id"
	private val projectId = ProjectId("2b1f3c4d-5e6f-4a1b-8c2d-3e4f5a6b7c8d")
	private val groupId = GroupId("5c0e7a2b-1d3f-4e5a-9b6c-7d8e9f0a1b2c")
	private val group = ProjectGroup(7L, groupId, "Trilogy", null, null, null)

	@BeforeEach
	fun setupDefaults() {
		coEvery { projectsSessionManager.validateSyncId(userId, syncId, any()) } returns true
		coEvery { projectsDatasource.getGroup(userId, any()) } returns null
		coEvery { projectsDatasource.getGroup(userId, groupId) } returns group
		coEvery { projectsDatasource.setProjectGroup(userId, projectId, any()) } returns true
	}

	@Test
	fun `invalid sync id is refused`() = runTest {
		coEvery { projectsSessionManager.validateSyncId(userId, syncId, any()) } returns false

		val result = createProjectsRepository().moveProject(userId, syncId, projectId, groupId)

		assertIs<InvalidSyncIdException>((result as ServerResult.Failure).exception)
		coVerify(exactly = 0) { projectsDatasource.setProjectGroup(any(), any(), any()) }
	}

	@Test
	fun `moves the project into the group`() = runTest {
		val result = createProjectsRepository().moveProject(userId, syncId, projectId, groupId)

		assertTrue(isSuccess(result))
		coVerify { projectsDatasource.setProjectGroup(userId, projectId, group.rowId) }
	}

	@Test
	fun `no group moves the project to the top level`() = runTest {
		val result = createProjectsRepository().moveProject(userId, syncId, projectId, null)

		assertTrue(isSuccess(result))
		coVerify { projectsDatasource.setProjectGroup(userId, projectId, null) }
	}

	@Test
	fun `an unknown group moves the project to the top level`() = runTest {
		val result = createProjectsRepository().moveProject(userId, syncId, projectId, GroupId.randomUUID())

		assertTrue(isSuccess(result))
		coVerify { projectsDatasource.setProjectGroup(userId, projectId, null) }
	}

	@Test
	fun `an unknown project is not found`() = runTest {
		coEvery { projectsDatasource.setProjectGroup(userId, projectId, any()) } returns false

		val result = createProjectsRepository().moveProject(userId, syncId, projectId, groupId)

		assertIs<ProjectNotFound>((result as ServerResult.Failure).exception)
	}
}
