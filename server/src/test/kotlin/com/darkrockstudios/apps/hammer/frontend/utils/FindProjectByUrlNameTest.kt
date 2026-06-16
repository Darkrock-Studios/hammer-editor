package com.darkrockstudios.apps.hammer.frontend.utils

import com.darkrockstudios.apps.hammer.projects.ProjectWithSyncDate
import com.darkrockstudios.apps.hammer.projects.ProjectsRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

class FindProjectByUrlNameTest {

	private val userId = 1L

	private fun project(name: String) =
		ProjectWithSyncDate(name = name, uuid = "uuid-$name", lastSync = Instant.DISTANT_PAST)

	@Test
	fun `resolves a name with a literal dash the slug round-trip cannot reverse`() = runTest {
		val repo = mockk<ProjectsRepository>()
		val mangled = "Alice In Wonderland (# Name clash 2026-06-07 fk6fycC #)"
		val stored = project(mangled)
		val urlName = ProjectName.formatForUrl(mangled)

		// The exact decoded lookup misses: decodeFromUrl turns the date's dashes into spaces.
		coEvery { repo.getProjectByName(userId, any()) } returns null
		coEvery { repo.getProjectsWithSyncDate(userId) } returns listOf(project("Other"), stored)

		assertEquals(stored, repo.findProjectByUrlName(userId, urlName))
	}

	@Test
	fun `uses the exact decoded match without enumerating when one exists`() = runTest {
		val repo = mockk<ProjectsRepository>()
		val stored = project("My Story")
		coEvery { repo.getProjectByName(userId, "My Story") } returns stored

		val result = repo.findProjectByUrlName(userId, ProjectName.formatForUrl("My Story"))

		assertEquals(stored, result)
		coVerify(exactly = 0) { repo.getProjectsWithSyncDate(userId) }
	}

	@Test
	fun `returns null when no project matches the url segment`() = runTest {
		val repo = mockk<ProjectsRepository>()
		coEvery { repo.getProjectByName(userId, any()) } returns null
		coEvery { repo.getProjectsWithSyncDate(userId) } returns listOf(project("Other"))

		assertNull(repo.findProjectByUrlName(userId, "Nonexistent"))
	}
}
