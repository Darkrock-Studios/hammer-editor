package com.darkrockstudios.apps.hammer.project.access

import com.darkrockstudios.apps.hammer.GetPrivateAccessForProject
import com.darkrockstudios.apps.hammer.Project_access
import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.database.ProjectAccessDao
import com.darkrockstudios.apps.hammer.database.ProjectDao
import com.darkrockstudios.apps.hammer.database.PublicProjectInfo
import com.darkrockstudios.apps.hammer.project.ProjectDefinition
import com.darkrockstudios.apps.hammer.project.ProjectEntityDatasource
import com.darkrockstudios.apps.hammer.utilities.isFailure
import com.darkrockstudios.apps.hammer.utilities.isSuccess
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
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

class ProjectAccessRepositoryTest {

	@MockK
	private lateinit var projectAccessDao: ProjectAccessDao

	@MockK
	private lateinit var projectDao: ProjectDao

	@MockK
	private lateinit var projectEntityDatasource: ProjectEntityDatasource

	private lateinit var testClock: TestClock
	private lateinit var repository: ProjectAccessRepository

	private val userId = 1L
	private val projectUuid = ProjectId("test-uuid")
	private val projectId = 100L
	private val penName = "TestAuthor"
	private val projectName = "TestProject"
	private val projectDef = ProjectDefinition(projectName, projectUuid)

	@BeforeEach
	fun setup() {
		MockKAnnotations.init(this, relaxUnitFun = true)
		testClock = TestClock(Clock.System)
		repository = ProjectAccessRepository(projectAccessDao, projectDao, projectEntityDatasource, testClock)
	}

	@Test
	fun `getAccessForProject - Success`() = runTest {
		val expectedAccess = Project_access(
			id = 1,
			project_id = projectId,
			access_password = "password",
			expires_at = null,
			published_at = Instant.parse("2025-12-25T23:51:32Z")
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

		repository.setAccess(userId, projectUuid, "password", Instant.parse("2023-12-31T23:59:59Z"))

		coVerify { projectDao.getProjectId(userId, projectUuid) }
		coVerify { projectAccessDao.updateAccess(projectId, "password", Instant.parse("2023-12-31T23:59:59Z")) }
	}

	@Test
	fun `deleteAccess - Success`() = runTest {
		coEvery { projectDao.getProjectId(userId, projectUuid) } returns projectId

		repository.deleteAccess(userId, projectUuid)

		coVerify { projectDao.getProjectId(userId, projectUuid) }
		coVerify { projectAccessDao.deleteAccess(projectId) }
	}

	@Test
	fun `deleteAccessById - scopes the delete to the resolved project`() = runTest {
		val accessId = 5L
		coEvery { projectDao.getProjectId(userId, projectUuid) } returns projectId
		coEvery { projectAccessDao.deleteAccessById(accessId, projectId) } returns true

		val result = repository.deleteAccessById(userId, projectUuid, accessId)

		assertTrue(result)
		coVerify { projectDao.getProjectId(userId, projectUuid) }
		coVerify { projectAccessDao.deleteAccessById(accessId, projectId) }
	}

	@Test
	fun `deleteAccessById - returns false when the access row is not in the caller's project`() = runTest {
		val foreignAccessId = 5L
		coEvery { projectDao.getProjectId(userId, projectUuid) } returns projectId
		coEvery { projectAccessDao.deleteAccessById(foreignAccessId, projectId) } returns false

		val result = repository.deleteAccessById(userId, projectUuid, foreignAccessId)

		assertFalse(result)
	}

	@Test
	fun `isPublished - returns true when public access exists`() = runTest {
		val publicAccess = Project_access(
			id = 1,
			project_id = projectId,
			access_password = null,
			expires_at = null,
			published_at = Instant.parse("2025-12-25T23:51:32Z"),
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
			Project_access(
				id = 1,
				project_id = projectId,
				access_password = "pass",
				expires_at = null,
				published_at = Instant.parse("2025-12-25T23:51:32Z")
			)
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
			expires_at = null,
			published_at = Instant.parse("2025-12-25T23:51:32Z")
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
		val expiresAt = Instant.parse("2025-12-31T23:59:59Z")

		mockCreatePrivateAccessCollaborators()

		val result = repository.createPrivateAccess(userId, projectUuid, password, expiresAt)

		assertTrue(isSuccess(result))
		coVerify { projectAccessDao.insertAccessWithScenes(projectId, password, expiresAt, emptyList()) }
	}

	@Test
	fun `createPrivateAccess - creates access without expiration`() = runTest {
		val password = "secret123"

		mockCreatePrivateAccessCollaborators()

		val result = repository.createPrivateAccess(userId, projectUuid, password, null)

		assertTrue(isSuccess(result))
		coVerify { projectAccessDao.insertAccessWithScenes(projectId, password, null, emptyList()) }
	}

	@Test
	fun `createPrivateAccess - persists the selected scene ids`() = runTest {
		val password = "secret123"
		val sceneIds = listOf(3, 7)

		mockCreatePrivateAccessCollaborators()
		coEvery {
			projectEntityDatasource.findEntityType(any(), userId, projectDef)
		} returns ApiProjectEntity.Type.SCENE

		val result = repository.createPrivateAccess(userId, projectUuid, password, null, sceneIds)

		assertTrue(isSuccess(result))
		coVerify { projectAccessDao.insertAccessWithScenes(projectId, password, null, sceneIds) }
	}

	@Test
	fun `createPrivateAccess - rejects an id that is not a scene`() = runTest {
		mockCreatePrivateAccessCollaborators()
		coEvery {
			projectEntityDatasource.findEntityType(3, userId, projectDef)
		} returns ApiProjectEntity.Type.SCENE
		coEvery {
			projectEntityDatasource.findEntityType(9, userId, projectDef)
		} returns ApiProjectEntity.Type.NOTE

		val result = repository.createPrivateAccess(userId, projectUuid, "secret", null, listOf(3, 9))

		assertTrue(isFailure(result))
		coVerify(exactly = 0) { projectAccessDao.insertAccessWithScenes(any(), any(), any(), any()) }
	}

	@Test
	fun `createPrivateAccess - rejects duplicate scene ids`() = runTest {
		mockCreatePrivateAccessCollaborators()

		val result = repository.createPrivateAccess(userId, projectUuid, "secret", null, listOf(3, 3))

		assertTrue(isFailure(result))
		coVerify(exactly = 0) { projectAccessDao.insertAccessWithScenes(any(), any(), any(), any()) }
	}

	@Test
	fun `createPrivateAccess - rejects a password already used by another share`() = runTest {
		mockCreatePrivateAccessCollaborators()
		coEvery { projectAccessDao.getPrivateAccessForProject(projectId) } returns listOf(
			GetPrivateAccessForProject(
				id = 1,
				access_password = "secret123",
				expires_at = null,
				project_id = projectId,
				published_at = Instant.parse("2025-12-25T23:51:32Z")
			)
		)

		val result = repository.createPrivateAccess(userId, projectUuid, "secret123", null)

		assertTrue(isFailure(result))
		coVerify(exactly = 0) { projectAccessDao.insertAccessWithScenes(any(), any(), any(), any()) }
	}

	private fun mockCreatePrivateAccessCollaborators() {
		coEvery { projectDao.getProjectId(userId, projectUuid) } returns projectId
		coEvery { projectEntityDatasource.getProject(userId, projectUuid) } returns projectDef
		coEvery { projectAccessDao.getPrivateAccessForProject(projectId) } returns emptyList()
		coEvery { projectAccessDao.insertAccessWithScenes(any(), any(), any(), any()) } returns 42L
	}

	@Test
	fun `getPrivateAccessEntries - returns entries with formatted dates`() = runTest {
		val entries = listOf(
			GetPrivateAccessForProject(
				id = 1,
				access_password = "pass1",
				expires_at = Instant.parse("2099-06-15T12:00:00Z"),
				project_id = 1,
				published_at = Instant.parse("2025-12-25T23:51:32Z")
			),
			GetPrivateAccessForProject(
				id = 2,
				access_password = "pass2",
				expires_at = null,
				project_id = 2,
				published_at = Instant.parse("2025-12-25T23:51:32Z")
			)
		)

		coEvery { projectDao.getProjectId(userId, projectUuid) } returns projectId
		coEvery { projectAccessDao.getPrivateAccessForProject(projectId) } returns entries
		coEvery { projectAccessDao.sceneCountsForAccessIds(listOf(1L, 2L)) } returns mapOf(1L to 5)

		val result = repository.getPrivateAccessEntries(userId, projectUuid)

		assertEquals(2, result.size)
		assertEquals(1, result[0].id)
		assertEquals("pass1", result[0].password)
		assertFalse(result[0].isExpired)
		assertEquals(Instant.parse("2099-06-15T12:00:00Z"), result[0].expiresAt)
		// Locale/zone-dependent formatting: assert only the stable year.
		assertTrue(result[0].expiresAtFormatted!!.contains("2099"))
		assertEquals(5, result[0].sceneCount)
		assertEquals(2, result[1].id)
		assertEquals("pass2", result[1].password)
		assertFalse(result[1].isExpired)
		assertNull(result[1].expiresAt)
		assertNull(result[1].expiresAtFormatted)
		assertNull(result[1].sceneCount)
	}

	@Test
	fun `getPrivateAccessEntries - marks expired entries correctly`() = runTest {
		val entries = listOf(
			GetPrivateAccessForProject(
				id = 1,
				access_password = "expired-pass",
				expires_at = Instant.parse("2020-01-01T00:00:00Z"),
				project_id = 1,
				published_at = Instant.parse("2025-12-25T23:51:32Z")
			)
		)

		coEvery { projectDao.getProjectId(userId, projectUuid) } returns projectId
		coEvery { projectAccessDao.getPrivateAccessForProject(projectId) } returns entries
		coEvery { projectAccessDao.sceneCountsForAccessIds(any()) } returns emptyMap()

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
			expiresAt = Instant.parse("2020-01-01T00:00:00Z")
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
		// Gates indexing and whether the rendered page may be written to the disk cache.
		assertTrue((result as PublicProjectResult.Success).isPublic)
	}

	@Test
	fun `findAccessibleProject - public access stays public even when a password is supplied`() = runTest {
		val publicInfo = PublicProjectInfo(
			projectUuid = projectUuid.id,
			userId = userId,
			projectName = projectName,
			penName = penName,
			expiresAt = null
		)

		coEvery { projectAccessDao.findPublicProjectByPenNameAndProjectName(penName, projectName) } returns publicInfo

		val result = repository.findAccessibleProject(penName, projectName, "stray-password")

		assertTrue(result is PublicProjectResult.Success)
		assertTrue((result as PublicProjectResult.Success).isPublic)
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
		coEvery {
			projectAccessDao.findProjectByPenNameProjectNameAndPassword(
				penName,
				projectName,
				password
			)
		} returns privateInfo

		val result = repository.findAccessibleProject(penName, projectName, password)

		assertTrue(result is PublicProjectResult.Success)
		assertEquals(projectUuid, (result as PublicProjectResult.Success).projectUuid)
		// A private share unlocked by a password must never be treated as public.
		assertFalse(result.isPublic)
	}

	@Test
	fun `findAccessibleProject - private share carries its scene restriction`() = runTest {
		val password = "secret123"
		val privateInfo = PublicProjectInfo(
			projectUuid = projectUuid.id,
			userId = userId,
			projectName = projectName,
			penName = penName,
			expiresAt = null,
			accessId = 42L,
		)

		coEvery { projectAccessDao.findPublicProjectByPenNameAndProjectName(penName, projectName) } returns null
		coEvery { projectAccessDao.hasAnyAccessForProject(penName, projectName) } returns true
		coEvery {
			projectAccessDao.findProjectByPenNameProjectNameAndPassword(penName, projectName, password)
		} returns privateInfo
		coEvery { projectAccessDao.getSceneIdsForAccess(42L) } returns listOf(3, 7)

		val result = repository.findAccessibleProject(penName, projectName, password)

		assertTrue(result is PublicProjectResult.Success)
		assertEquals(setOf(3, 7), (result as PublicProjectResult.Success).sceneIds)
	}

	@Test
	fun `findAccessibleProject - unrestricted private share has null scene ids`() = runTest {
		val password = "secret123"
		val privateInfo = PublicProjectInfo(
			projectUuid = projectUuid.id,
			userId = userId,
			projectName = projectName,
			penName = penName,
			expiresAt = null,
			accessId = 42L,
		)

		coEvery { projectAccessDao.findPublicProjectByPenNameAndProjectName(penName, projectName) } returns null
		coEvery { projectAccessDao.hasAnyAccessForProject(penName, projectName) } returns true
		coEvery {
			projectAccessDao.findProjectByPenNameProjectNameAndPassword(penName, projectName, password)
		} returns privateInfo
		coEvery { projectAccessDao.getSceneIdsForAccess(42L) } returns emptyList()

		val result = repository.findAccessibleProject(penName, projectName, password)

		assertTrue(result is PublicProjectResult.Success)
		assertNull((result as PublicProjectResult.Success).sceneIds)
	}

	@Test
	fun `findAccessibleProject - public access never carries a scene restriction`() = runTest {
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
		assertNull((result as PublicProjectResult.Success).sceneIds)
	}

	@Test
	fun `findAccessibleProject - returns password required with wrong password`() = runTest {
		val wrongPassword = "wrong"

		coEvery { projectAccessDao.findPublicProjectByPenNameAndProjectName(penName, projectName) } returns null
		coEvery { projectAccessDao.hasAnyAccessForProject(penName, projectName) } returns true
		coEvery {
			projectAccessDao.findProjectByPenNameProjectNameAndPassword(
				penName,
				projectName,
				wrongPassword
			)
		} returns null

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
			expiresAt = Instant.parse("2020-01-01T00:00:00Z")
		)

		coEvery { projectAccessDao.findPublicProjectByPenNameAndProjectName(penName, projectName) } returns null
		coEvery { projectAccessDao.hasAnyAccessForProject(penName, projectName) } returns true
		coEvery {
			projectAccessDao.findProjectByPenNameProjectNameAndPassword(
				penName,
				projectName,
				password
			)
		} returns expiredInfo

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
			expiresAt = Instant.parse("2020-01-01T00:00:00Z")
		)
		val validPrivateInfo = PublicProjectInfo(
			projectUuid = projectUuid.id,
			userId = userId,
			projectName = projectName,
			penName = penName,
			expiresAt = null
		)

		coEvery {
			projectAccessDao.findPublicProjectByPenNameAndProjectName(
				penName,
				projectName
			)
		} returns expiredPublicInfo
		coEvery { projectAccessDao.hasAnyAccessForProject(penName, projectName) } returns true
		coEvery {
			projectAccessDao.findProjectByPenNameProjectNameAndPassword(
				penName,
				projectName,
				password
			)
		} returns validPrivateInfo

		val result = repository.findAccessibleProject(penName, projectName, password)

		assertTrue(result is PublicProjectResult.Success)
	}
}
