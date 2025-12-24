package com.darkrockstudios.apps.hammer.project.access

import com.darkrockstudios.apps.hammer.GetPrivateAccessForProject
import com.darkrockstudios.apps.hammer.Project_access
import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.database.ProjectAccessDao
import com.darkrockstudios.apps.hammer.database.ProjectDao
import com.darkrockstudios.apps.hammer.database.PublicProjectInfo
import com.darkrockstudios.apps.hammer.utils.TestClock
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock

class ProjectAccessRepositoryTest {

	@MockK
	private lateinit var projectAccessDao: ProjectAccessDao

	@MockK
	private lateinit var projectDao: ProjectDao

	private lateinit var testClock: TestClock
	private lateinit var repository: ProjectAccessRepository

	private val userId = 1L
	private val projectUuid = ProjectId("test-uuid")
	private val projectId = 100L
	private val penName = "TestAuthor"
	private val projectName = "TestProject"

	@BeforeEach
	fun setup() {
		MockKAnnotations.init(this, relaxUnitFun = true)
		testClock = TestClock(Clock.System)
		repository = ProjectAccessRepository(projectAccessDao, projectDao, testClock)
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

	@Test
	fun `deleteAccess - Success`() = runTest {
		coEvery { projectDao.getProjectId(userId, projectUuid) } returns projectId

		repository.deleteAccess(userId, projectUuid)

		coVerify { projectDao.getProjectId(userId, projectUuid) }
		coVerify { projectAccessDao.deleteAccess(projectId) }
	}

	@Test
	fun `deleteAccessById - Success`() = runTest {
		val accessId = 5L
		coEvery { projectDao.getProjectId(userId, projectUuid) } returns projectId

		val result = repository.deleteAccessById(userId, projectUuid, accessId)

		assertTrue(result)
		coVerify { projectDao.getProjectId(userId, projectUuid) }
		coVerify { projectAccessDao.deleteAccessById(accessId) }
	}

	@Test
	fun `isPublished - returns true when public access exists`() = runTest {
		val publicAccess = Project_access(
			id = 1,
			project_id = projectId,
			access_password = null,
			expires_at = null
		)

		coEvery { projectDao.getProjectId(userId, projectUuid) } returns projectId
		coEvery { projectAccessDao.getPublicAccessForProject(projectId) } returns publicAccess

		val result = repository.isPublished(userId, projectUuid)

		assertTrue(result)
	}

	@Test
	fun `isPublished - returns false when no public access exists`() = runTest {
		coEvery { projectDao.getProjectId(userId, projectUuid) } returns projectId
		coEvery { projectAccessDao.getPublicAccessForProject(projectId) } returns null

		val result = repository.isPublished(userId, projectUuid)

		assertFalse(result)
	}

	@Test
	fun `hasAnyAccess - returns true when access entries exist`() = runTest {
		val accessList = listOf(
			Project_access(id = 1, project_id = projectId, access_password = "pass", expires_at = null)
		)

		coEvery { projectDao.getProjectId(userId, projectUuid) } returns projectId
		coEvery { projectAccessDao.getAllAccessForProject(projectId) } returns accessList

		val result = repository.hasAnyAccess(userId, projectUuid)

		assertTrue(result)
	}

	@Test
	fun `hasAnyAccess - returns false when no access entries exist`() = runTest {
		coEvery { projectDao.getProjectId(userId, projectUuid) } returns projectId
		coEvery { projectAccessDao.getAllAccessForProject(projectId) } returns emptyList()

		val result = repository.hasAnyAccess(userId, projectUuid)

		assertFalse(result)
	}

	@Test
	fun `publish - creates public access when not already published`() = runTest {
		coEvery { projectDao.getProjectId(userId, projectUuid) } returns projectId
		coEvery { projectAccessDao.getPublicAccessForProject(projectId) } returns null

		repository.publish(userId, projectUuid)

		coVerify { projectAccessDao.insertAccess(projectId, null, null) }
	}

	@Test
	fun `publish - does not create duplicate when already published`() = runTest {
		val existingAccess = Project_access(
			id = 1,
			project_id = projectId,
			access_password = null,
			expires_at = null
		)

		coEvery { projectDao.getProjectId(userId, projectUuid) } returns projectId
		coEvery { projectAccessDao.getPublicAccessForProject(projectId) } returns existingAccess

		repository.publish(userId, projectUuid)

		coVerify(exactly = 0) { projectAccessDao.insertAccess(any(), any(), any()) }
	}

	@Test
	fun `unpublish - deletes public access`() = runTest {
		coEvery { projectDao.getProjectId(userId, projectUuid) } returns projectId

		repository.unpublish(userId, projectUuid)

		coVerify { projectAccessDao.deletePublicAccessForProject(projectId) }
	}

	@Test
	fun `createPrivateAccess - creates password-protected access`() = runTest {
		val password = "secret123"
		val expiresAt = "2025-12-31T23:59:59"

		coEvery { projectDao.getProjectId(userId, projectUuid) } returns projectId

		repository.createPrivateAccess(userId, projectUuid, password, expiresAt)

		coVerify { projectAccessDao.insertAccess(projectId, password, expiresAt) }
	}

	@Test
	fun `createPrivateAccess - creates access without expiration`() = runTest {
		val password = "secret123"

		coEvery { projectDao.getProjectId(userId, projectUuid) } returns projectId

		repository.createPrivateAccess(userId, projectUuid, password, null)

		coVerify { projectAccessDao.insertAccess(projectId, password, null) }
	}

	@Test
	fun `getPrivateAccessEntries - returns entries with formatted dates`() = runTest {
		val entries = listOf(
			GetPrivateAccessForProject(
				id = 1,
				access_password = "pass1",
				expires_at = "2099-06-15 12:00:00",
				project_id = 1,
			),
			GetPrivateAccessForProject(
				id = 2,
				access_password = "pass2",
				expires_at = null,
				project_id = 2,
			)
		)

		coEvery { projectDao.getProjectId(userId, projectUuid) } returns projectId
		coEvery { projectAccessDao.getPrivateAccessForProject(projectId) } returns entries

		val result = repository.getPrivateAccessEntries(userId, projectUuid)

		assertEquals(2, result.size)
		assertEquals(1, result[0].id)
		assertEquals("pass1", result[0].password)
		assertFalse(result[0].isExpired)
		assertEquals(2, result[1].id)
		assertEquals("pass2", result[1].password)
		assertFalse(result[1].isExpired)
	}

	@Test
	fun `getPrivateAccessEntries - marks expired entries correctly`() = runTest {
		val entries = listOf(
			GetPrivateAccessForProject(
				id = 1,
				access_password = "expired-pass",
				expires_at = "2020-01-01 00:00:00",
				project_id = 1,
			)
		)

		coEvery { projectDao.getProjectId(userId, projectUuid) } returns projectId
		coEvery { projectAccessDao.getPrivateAccessForProject(projectId) } returns entries

		val result = repository.getPrivateAccessEntries(userId, projectUuid)

		assertEquals(1, result.size)
		assertTrue(result[0].isExpired)
	}

	@Test
	fun `deleteAllAccessForUser - deletes all access entries for user`() = runTest {
		repository.deleteAllAccessForUser(userId)

		coVerify { projectAccessDao.deleteAllAccessForUser(userId) }
	}

	@Test
	fun `findPublicProject - returns success when public project exists`() = runTest {
		val publicInfo = PublicProjectInfo(
			projectUuid = projectUuid.id,
			userId = userId,
			projectName = projectName,
			penName = penName,
			expiresAt = null
		)

		coEvery { projectAccessDao.findPublicProjectByPenNameAndProjectName(penName, projectName) } returns publicInfo

		val result = repository.findPublicProject(penName, projectName)

		assertTrue(result is PublicProjectResult.Success)
		assertEquals(userId, (result as PublicProjectResult.Success).userId)
		assertEquals(projectUuid, result.projectUuid)
		assertEquals(projectName, result.projectName)
		assertEquals(penName, result.penName)
	}

	@Test
	fun `findPublicProject - returns not found when project does not exist`() = runTest {
		coEvery { projectAccessDao.findPublicProjectByPenNameAndProjectName(penName, projectName) } returns null

		val result = repository.findPublicProject(penName, projectName)

		assertTrue(result is PublicProjectResult.NotFound)
	}

	@Test
	fun `findPublicProject - returns not found when access is expired`() = runTest {
		val publicInfo = PublicProjectInfo(
			projectUuid = projectUuid.id,
			userId = userId,
			projectName = projectName,
			penName = penName,
			expiresAt = "2020-01-01 00:00:00"
		)

		coEvery { projectAccessDao.findPublicProjectByPenNameAndProjectName(penName, projectName) } returns publicInfo

		val result = repository.findPublicProject(penName, projectName)

		assertTrue(result is PublicProjectResult.NotFound)
	}

	@Test
	fun `findAccessibleProject - returns success for public project without password`() = runTest {
		val publicInfo = PublicProjectInfo(
			projectUuid = projectUuid.id,
			userId = userId,
			projectName = projectName,
			penName = penName,
			expiresAt = null
		)

		coEvery { projectAccessDao.findPublicProjectByPenNameAndProjectName(penName, projectName) } returns publicInfo

		val result = repository.findAccessibleProject(penName, projectName, null)

		assertTrue(result is PublicProjectResult.Success)
	}

	@Test
	fun `findAccessibleProject - returns password required when only private access exists`() = runTest {
		coEvery { projectAccessDao.findPublicProjectByPenNameAndProjectName(penName, projectName) } returns null
		coEvery { projectAccessDao.hasAnyAccessForProject(penName, projectName) } returns true

		val result = repository.findAccessibleProject(penName, projectName, null)

		assertTrue(result is PublicProjectResult.PasswordRequired)
	}

	@Test
	fun `findAccessibleProject - returns not found when no access exists`() = runTest {
		coEvery { projectAccessDao.findPublicProjectByPenNameAndProjectName(penName, projectName) } returns null
		coEvery { projectAccessDao.hasAnyAccessForProject(penName, projectName) } returns false

		val result = repository.findAccessibleProject(penName, projectName, null)

		assertTrue(result is PublicProjectResult.NotFound)
	}

	@Test
	fun `findAccessibleProject - returns success with correct password`() = runTest {
		val password = "secret123"
		val privateInfo = PublicProjectInfo(
			projectUuid = projectUuid.id,
			userId = userId,
			projectName = projectName,
			penName = penName,
			expiresAt = null
		)

		coEvery { projectAccessDao.findPublicProjectByPenNameAndProjectName(penName, projectName) } returns null
		coEvery { projectAccessDao.hasAnyAccessForProject(penName, projectName) } returns true
		coEvery { projectAccessDao.findProjectByPenNameProjectNameAndPassword(penName, projectName, password) } returns privateInfo

		val result = repository.findAccessibleProject(penName, projectName, password)

		assertTrue(result is PublicProjectResult.Success)
		assertEquals(projectUuid, (result as PublicProjectResult.Success).projectUuid)
	}

	@Test
	fun `findAccessibleProject - returns password required with wrong password`() = runTest {
		val wrongPassword = "wrong"

		coEvery { projectAccessDao.findPublicProjectByPenNameAndProjectName(penName, projectName) } returns null
		coEvery { projectAccessDao.hasAnyAccessForProject(penName, projectName) } returns true
		coEvery { projectAccessDao.findProjectByPenNameProjectNameAndPassword(penName, projectName, wrongPassword) } returns null

		val result = repository.findAccessibleProject(penName, projectName, wrongPassword)

		assertTrue(result is PublicProjectResult.PasswordRequired)
	}

	@Test
	fun `findAccessibleProject - returns password required when password access is expired`() = runTest {
		val password = "secret123"
		val expiredInfo = PublicProjectInfo(
			projectUuid = projectUuid.id,
			userId = userId,
			projectName = projectName,
			penName = penName,
			expiresAt = "2020-01-01 00:00:00"
		)

		coEvery { projectAccessDao.findPublicProjectByPenNameAndProjectName(penName, projectName) } returns null
		coEvery { projectAccessDao.hasAnyAccessForProject(penName, projectName) } returns true
		coEvery { projectAccessDao.findProjectByPenNameProjectNameAndPassword(penName, projectName, password) } returns expiredInfo

		val result = repository.findAccessibleProject(penName, projectName, password)

		assertTrue(result is PublicProjectResult.PasswordRequired)
	}

	@Test
	fun `findAccessibleProject - falls through to password check when public access is expired`() = runTest {
		val password = "secret123"
		val expiredPublicInfo = PublicProjectInfo(
			projectUuid = projectUuid.id,
			userId = userId,
			projectName = projectName,
			penName = penName,
			expiresAt = "2020-01-01 00:00:00"
		)
		val validPrivateInfo = PublicProjectInfo(
			projectUuid = projectUuid.id,
			userId = userId,
			projectName = projectName,
			penName = penName,
			expiresAt = null
		)

		coEvery { projectAccessDao.findPublicProjectByPenNameAndProjectName(penName, projectName) } returns expiredPublicInfo
		coEvery { projectAccessDao.hasAnyAccessForProject(penName, projectName) } returns true
		coEvery { projectAccessDao.findProjectByPenNameProjectNameAndPassword(penName, projectName, password) } returns validPrivateInfo

		val result = repository.findAccessibleProject(penName, projectName, password)

		assertTrue(result is PublicProjectResult.Success)
	}
}
