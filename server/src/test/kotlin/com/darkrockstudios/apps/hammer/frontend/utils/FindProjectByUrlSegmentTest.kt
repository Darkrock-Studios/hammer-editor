package com.darkrockstudios.apps.hammer.frontend.utils

import com.darkrockstudios.apps.hammer.projects.ProjectWithSyncDate
import com.darkrockstudios.apps.hammer.projects.ProjectsRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

class FindProjectByUrlSegmentTest {

	private val userId = 1L

	private fun project(name: String, uuid: String) =
		ProjectWithSyncDate(name = name, uuid = uuid, lastSync = Instant.DISTANT_PAST)

	@Test
	fun `resolves a project by the id embedded in its slug-id segment`() = runTest {
		val repo = mockk<ProjectsRepository>()
		val stored = project("My Story", "uuid-1")
		coEvery { repo.getProjectsWithSyncDate(userId) } returns listOf(project("Other", "uuid-2"), stored)

		val segment = "My-Story-${ProjectName.shortId("uuid-1")}"
		assertEquals(stored, repo.findProjectByUrlSegment(userId, segment))
	}

	@Test
	fun `resolves a project from a bare id with no slug`() = runTest {
		val repo = mockk<ProjectsRepository>()
		val stored = project("My Story", "uuid-1")
		coEvery { repo.getProjectsWithSyncDate(userId) } returns listOf(stored)

		assertEquals(stored, repo.findProjectByUrlSegment(userId, ProjectName.shortId("uuid-1")))
	}

	@Test
	fun `ignores the slug and matches on the id`() = runTest {
		val repo = mockk<ProjectsRepository>()
		val stored = project("My Story", "uuid-1")
		coEvery { repo.getProjectsWithSyncDate(userId) } returns listOf(stored)

		val wrongSlug = "Totally-Different-Name-${ProjectName.shortId("uuid-1")}"
		assertEquals(stored, repo.findProjectByUrlSegment(userId, wrongSlug))
	}

	@Test
	fun `returns null when no project's id matches`() = runTest {
		val repo = mockk<ProjectsRepository>()
		coEvery { repo.getProjectsWithSyncDate(userId) } returns listOf(project("Other", "uuid-2"))

		assertNull(repo.findProjectByUrlSegment(userId, "My-Story-zzzzzz"))
	}
}
