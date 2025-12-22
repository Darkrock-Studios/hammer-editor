package com.darkrockstudios.apps.hammer.project.access

import com.darkrockstudios.apps.hammer.Project_access
import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.database.ProjectAccessDao
import com.darkrockstudios.apps.hammer.database.ProjectDao
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class ProjectAccessRepositoryTest {

	@MockK
	private lateinit var projectAccessDao: ProjectAccessDao

	@MockK
	private lateinit var projectDao: ProjectDao

	private lateinit var repository: ProjectAccessRepository

	private val userId = 1L
	private val projectUuid = ProjectId("test-uuid")
	private val projectId = 100L

	@BeforeEach
	fun setup() {
		MockKAnnotations.init(this, relaxUnitFun = true)
		repository = ProjectAccessRepository(projectAccessDao, projectDao)
	}

	@Test
	fun `getAccessForProject - Success`() = runTest {
		val expectedAccess = Project_access(
			id = 1,
			project_id = projectId,
			access_password = "password",
			expires_at = null
		)

		coEvery { projectDao.getProjectId(userId, projectUuid) } returns projectId
		coEvery { projectAccessDao.getAccessForProject(projectId) } returns expectedAccess

		val result = repository.getAccessForProject(userId, projectUuid)

		assertEquals(expectedAccess, result)
		coVerify { projectDao.getProjectId(userId, projectUuid) }
		coVerify { projectAccessDao.getAccessForProject(projectId) }
	}

	@Test
	fun `setAccess - Success`() = runTest {
		coEvery { projectDao.getProjectId(userId, projectUuid) } returns projectId
		coEvery { projectAccessDao.updateAccess(any(), any(), any()) } returns Unit

		repository.setAccess(userId, projectUuid, "password", "2023-12-31T23:59:59Z")

		coVerify { projectDao.getProjectId(userId, projectUuid) }
		coVerify { projectAccessDao.updateAccess(projectId, "password", "2023-12-31T23:59:59Z") }
	}
}
