package com.darkrockstudios.apps.hammer.projects.repository

import com.darkrockstudios.apps.hammer.base.http.EntityHash
import com.darkrockstudios.apps.hammer.base.http.ProjectHashItem
import com.darkrockstudios.apps.hammer.base.http.createJsonSerializer
import com.darkrockstudios.apps.hammer.base.http.projectdata.ProjectData
import com.darkrockstudios.apps.hammer.base.http.synchronizer.ProjectContentHasher
import com.darkrockstudios.apps.hammer.base.http.synchronizer.ProjectDataHasher
import com.darkrockstudios.apps.hammer.project.OwnerId
import com.darkrockstudios.apps.hammer.project.ProjectAccess
import com.darkrockstudios.apps.hammer.project.ProjectAccessResult
import com.darkrockstudios.apps.hammer.project.ProjectSyncKey
import com.darkrockstudios.apps.hammer.projects.ProbeProjectChangesUseCase
import com.darkrockstudios.apps.hammer.project.RawProjectDataDto
import io.mockk.coEvery
import io.mockk.every
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProbeProjectChangesUseCaseTest : ProjectsRepositoryBaseTest() {

	private fun probe() = ProbeProjectChangesUseCase(
		projectAccessResolver,
		projectEntityDatasource,
		serverProjectDataRepository,
		projectSessionManager,
	)

	private val projectId = projectDefinition.uuid

	private fun stubTwoEntities() {
		coEvery { projectAccessResolver.resolve(userId, projectId) } returns
			ProjectAccessResult.Granted(ProjectAccess(projectDefinition, OwnerId.unchecked(userId), isOwner = true))
		coEvery { projectEntityDatasource.getEntityHashes(OwnerId.unchecked(userId), projectDefinition) } returns listOf(
			EntityHash(1, "h1"),
			EntityHash(2, "h2"),
		)
		coEvery { serverProjectDataRepository.loadVerifiedDataHash(OwnerId.unchecked(userId), projectDefinition) } returns
			ProjectDataHasher.hash(ProjectData())
	}

	private fun expectedServerHash(): String = ProjectContentHasher.hash(
		listOf(EntityHash(1, "h1"), EntityHash(2, "h2")),
		ProjectDataHasher.hash(ProjectData()),
	)

	@Test
	fun `matching hash marks the project unchanged`() = runTest {
		stubTwoEntities()
		val result = probe()(userId, listOf(ProjectHashItem(projectId, expectedServerHash())))
		assertEquals(setOf(projectId), result)
	}

	@Test
	fun `differing hash leaves the project out`() = runTest {
		stubTwoEntities()
		val result = probe()(userId, listOf(ProjectHashItem(projectId, "stale-hash")))
		assertTrue(result.isEmpty())
	}

	@Test
	fun `an unreadable project-data blob forces a full sync`() = runTest {
		stubTwoEntities()
		coEvery { serverProjectDataRepository.loadVerifiedDataHash(OwnerId.unchecked(userId), projectDefinition) } returns null

		val result = probe()(userId, listOf(ProjectHashItem(projectId, expectedServerHash())))
		assertTrue(result.isEmpty(), "an uncomputable hash must never be reported as unchanged")
	}

	@Test
	fun `a project with an in-flight sync session is left out`() = runTest {
		stubTwoEntities()
		every {
			projectSessionManager.hasActiveSyncSession(ProjectSyncKey(projectDefinition.uuid))
		} returns true

		val result = probe()(userId, listOf(ProjectHashItem(projectId, expectedServerHash())))
		assertTrue(result.isEmpty(), "a project being synced elsewhere must not be reported unchanged")
	}

	@Test
	fun `unknown project is left out`() = runTest {
		coEvery { projectAccessResolver.resolve(userId, projectId) } returns ProjectAccessResult.Unknown

		val result = probe()(userId, listOf(ProjectHashItem(projectId, "any")))
		assertTrue(result.isEmpty())
	}

	@Test
	fun `a project the caller may not use is left out`() = runTest {
		stubTwoEntities()
		coEvery { projectAccessResolver.resolve(userId, projectId) } returns ProjectAccessResult.NotMember

		val result = probe()(userId, listOf(ProjectHashItem(projectId, expectedServerHash())))
		assertTrue(result.isEmpty())
	}

	@Test
	fun `a member project is hashed from the owner's rows`() = runTest {
		val owner = OwnerId.unchecked(userId + 1)
		coEvery { projectAccessResolver.resolve(userId, projectId) } returns
			ProjectAccessResult.Granted(ProjectAccess(projectDefinition, owner, isOwner = false))
		coEvery { projectEntityDatasource.getEntityHashes(owner, projectDefinition) } returns listOf(
			EntityHash(1, "h1"),
			EntityHash(2, "h2"),
		)
		coEvery { serverProjectDataRepository.loadVerifiedDataHash(owner, projectDefinition) } returns
			ProjectDataHasher.hash(ProjectData())

		val result = probe()(userId, listOf(ProjectHashItem(projectId, expectedServerHash())))
		assertEquals(setOf(projectId), result)
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
		coEvery { serverProjectDataRepository.loadVerifiedDataHash(OwnerId.unchecked(userId), projectDefinition) } returns withData.hash

		val expected = ProjectContentHasher.hash(
			listOf(EntityHash(1, "h1"), EntityHash(2, "h2")),
			withData.hash,
		)

		// The default-project-data hash must NOT match once real project data exists.
		val staleResult = probe()(userId, listOf(ProjectHashItem(projectId, expectedServerHash())))
		assertTrue(staleResult.isEmpty())

		val freshResult = probe()(userId, listOf(ProjectHashItem(projectId, expected)))
		assertEquals(setOf(projectId), freshResult)
	}
}
