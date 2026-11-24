package com.darkrockstudios.apps.hammer.services.blogfeed

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse

class BlogFeedRefreshJobTest {

	private val repository: BlogFeedRepository = mockk()
	private val job = BlogFeedRefreshJob(repository, mockk(relaxed = true))

	@Test
	fun `tick refreshes the feed`() = runTest {
		coEvery { repository.refresh() } returns Unit

		job.tick()

		coVerify(exactly = 1) { repository.refresh() }
	}

	@Test
	fun `a failing feed does not fail the server's health`() {
		assertFalse(job.status().affectsHealth)
	}
}
