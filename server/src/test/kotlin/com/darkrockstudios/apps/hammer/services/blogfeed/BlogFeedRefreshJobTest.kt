package com.darkrockstudios.apps.hammer.services.blogfeed

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith

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
	fun `tick absorbs a failed refresh so the job is not reported as failing`() = runTest {
		coEvery { repository.refresh() } throws IOException("Blog feed returned 503")

		job.tick()
	}

	@Test
	fun `tick lets cancellation through`() = runTest {
		coEvery { repository.refresh() } throws CancellationException("stopping")

		assertFailsWith<CancellationException> { job.tick() }
	}
}
