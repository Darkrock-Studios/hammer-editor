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
	fun `resolves the exact name the lossless encoding round-trips`() = runTest {
		val repo = mockk<ProjectsRepository>()
		// The router percent-decodes the segment, so a literal-dash name arrives intact and the
		// exact lookup succeeds — the case the old dash-for-space slug used to 404 on.
		val name = "Draft 2026-06-07 (clash)"
		val stored = project(name)
		coEvery { repo.getProjectByName(userId, name) } returns stored

		assertEquals(stored, repo.findProjectByUrlName(userId, name))
		coVerify(exactly = 1) { repo.getProjectByName(userId, name) }
	}

	@Test
	fun `uses the exact match without trying the legacy slug when one exists`() = runTest {
		val repo = mockk<ProjectsRepository>()
		val stored = project("My Story")
		coEvery { repo.getProjectByName(userId, "My Story") } returns stored

		assertEquals(stored, repo.findProjectByUrlName(userId, "My Story"))
		// "My Story" has no dash, so there is no distinct legacy form to look up.
		coVerify(exactly = 1) { repo.getProjectByName(userId, "My Story") }
	}

	@Test
	fun `falls back to the legacy dash-for-space slug for old links`() = runTest {
		val repo = mockk<ProjectsRepository>()
		val stored = project("My Story")
		// An old bookmark hits "/story/My-Story"; the exact name has no match, the legacy form does.
		coEvery { repo.getProjectByName(userId, "My-Story") } returns null
		coEvery { repo.getProjectByName(userId, "My Story") } returns stored

		assertEquals(stored, repo.findProjectByUrlName(userId, "My-Story"))
		coVerify(exactly = 1) { repo.getProjectByName(userId, "My Story") }
	}

	@Test
	fun `returns null when neither the exact nor legacy name matches`() = runTest {
		val repo = mockk<ProjectsRepository>()
		coEvery { repo.getProjectByName(userId, any()) } returns null

		assertNull(repo.findProjectByUrlName(userId, "Nonexistent"))
	}
}
