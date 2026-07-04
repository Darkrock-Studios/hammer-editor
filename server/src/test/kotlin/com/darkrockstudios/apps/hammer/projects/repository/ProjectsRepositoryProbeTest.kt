package com.darkrockstudios.apps.hammer.projects.repository

import com.darkrockstudios.apps.hammer.base.http.EntityHash
import com.darkrockstudios.apps.hammer.base.http.ProjectHashItem
import com.darkrockstudios.apps.hammer.base.http.createJsonSerializer
import com.darkrockstudios.apps.hammer.base.http.projectdata.ProjectData
import com.darkrockstudios.apps.hammer.base.http.synchronizer.ProjectContentHasher
import com.darkrockstudios.apps.hammer.base.http.synchronizer.ProjectDataHasher
import com.darkrockstudios.apps.hammer.project.ProjectSyncKey
import com.darkrockstudios.apps.hammer.project.RawProjectDataDto
import com.darkrockstudios.apps.hammer.utilities.SResult
import io.mockk.coEvery
import io.mockk.every
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProjectsRepositoryProbeTest : ProjectsRepositoryBaseTest() {

	private val projectId = projectDefinition.uuid

	private fun stubTwoEntities() {
		coEvery { projectsDatasource.getProject(userId, projectId) } returns projectDefinition
		coEvery { projectEntityDatasource.getEntityHashes(userId, projectDefinition) } returns listOf(
			EntityHash(1, "h1"),
			EntityHash(2, "h2"),
		)
		coEvery { serverProjectDataRepository.load(userId, projectDefinition) } returns
			SResult.success<RawProjectDataDto?>(null)
	}

	private fun expectedServerHash(): String = ProjectContentHasher.hash(
		listOf(EntityHash(1, "h1"), EntityHash(2, "h2")),
		ProjectDataHasher.hash(ProjectData()),
	)

	@Test
	fun `matching hash marks the project unchanged`() = runTest {
		stubTwoEntities()
		createProjectsRepository().apply {
			val result = probeProjectChanges(userId, listOf(ProjectHashItem(projectId, expectedServerHash())))
			assertEquals(setOf(projectId), result)
		}
	}

	@Test
	fun `differing hash leaves the project out`() = runTest {
		stubTwoEntities()
		createProjectsRepository().apply {
			val result = probeProjectChanges(userId, listOf(ProjectHashItem(projectId, "stale-hash")))
			assertTrue(result.isEmpty())
		}
	}

	@Test
	fun `an unreadable project-data blob forces a full sync`() = runTest {
		stubTwoEntities()
		coEvery { serverProjectDataRepository.load(userId, projectDefinition) } returns
			SResult.failure(Exception("boom"))

		createProjectsRepository().apply {
			val result = probeProjectChanges(userId, listOf(ProjectHashItem(projectId, expectedServerHash())))
			assertTrue(result.isEmpty(), "an uncomputable hash must never be reported as unchanged")
		}
	}

	@Test
	fun `a project with an in-flight sync session is left out`() = runTest {
		stubTwoEntities()
		every {
			projectSessionManager.hasActiveSyncSession(ProjectSyncKey(userId, projectDefinition))
		} returns true

		createProjectsRepository().apply {
			val result = probeProjectChanges(userId, listOf(ProjectHashItem(projectId, expectedServerHash())))
			assertTrue(result.isEmpty(), "a project being synced elsewhere must not be reported unchanged")
		}
	}

	@Test
	fun `unknown project is left out`() = runTest {
		coEvery { projectsDatasource.getProject(userId, projectId) } returns null

		createProjectsRepository().apply {
			val result = probeProjectChanges(userId, listOf(ProjectHashItem(projectId, "any")))
			assertTrue(result.isEmpty())
		}
	}

	@Test
	fun `project-data changes are reflected in the hash`() = runTest {
		stubTwoEntities()
		val withData = RawProjectDataDto(
			data = createJsonSerializer().encodeToJsonElement(
				ProjectData.serializer(),
				ProjectData(authorName = "Pat"),
			),
			hash = ProjectDataHasher.hash(ProjectData(authorName = "Pat")),
		)
		coEvery { serverProjectDataRepository.load(userId, projectDefinition) } returns SResult.success(withData)

		val expected = ProjectContentHasher.hash(
			listOf(EntityHash(1, "h1"), EntityHash(2, "h2")),
			withData.hash,
		)

		createProjectsRepository().apply {
			// The default-project-data hash must NOT match once real project data exists.
			val staleResult = probeProjectChanges(userId, listOf(ProjectHashItem(projectId, expectedServerHash())))
			assertTrue(staleResult.isEmpty())

			val freshResult = probeProjectChanges(userId, listOf(ProjectHashItem(projectId, expected)))
			assertEquals(setOf(projectId), freshResult)
		}
	}
}
