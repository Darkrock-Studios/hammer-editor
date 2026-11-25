package synchronizer

import com.darkrockstudios.apps.hammer.common.data.projectstatistics.ProjectStatisticsUseCase
import com.darkrockstudios.apps.hammer.common.data.projectstatistics.StatisticsRepository
import com.darkrockstudios.apps.hammer.common.data.references.ReferenceIndexRepository
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.SyncStatisticsRefresher
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class SyncStatisticsRefresherTest {

	private val isDirty = MutableStateFlow(false)
	private val statisticsRepository = mockk<StatisticsRepository>(relaxed = true) {
		every { isDirty } returns this@SyncStatisticsRefresherTest.isDirty
	}
	private val referenceIndexRepository = mockk<ReferenceIndexRepository>(relaxed = true)
	private val projectStatisticsUseCase = mockk<ProjectStatisticsUseCase>(relaxed = true)

	private val refresher = SyncStatisticsRefresher(
		statisticsRepository,
		referenceIndexRepository,
		projectStatisticsUseCase,
	)

	@Test
	fun `changed content marks both the statistics and the reference index, written at once`() = runTest {
		refresher.contentChanged()

		coVerify(exactly = 1) { statisticsRepository.markDirtyNow() }
		coVerify(exactly = 1) { referenceIndexRepository.markDirtyNow() }
		coVerify(exactly = 0) { statisticsRepository.markDirty() }
	}

	@Test
	fun `changed activity marks only the statistics`() = runTest {
		refresher.activityChanged()

		coVerify(exactly = 1) { statisticsRepository.markDirtyNow() }
		coVerify(exactly = 0) { referenceIndexRepository.markDirtyNow() }
	}

	@Test
	fun `nothing stale, nothing recounted`() = runTest {
		refresher.refreshIfStale()

		coVerify(exactly = 0) { projectStatisticsUseCase.load() }
	}

	@Test
	fun `stale statistics are recounted once`() = runTest {
		isDirty.value = true

		refresher.refreshIfStale()

		coVerify(exactly = 1) { projectStatisticsUseCase.load() }
	}

	@Test
	fun `a failed recount doesn't fail the sync`() = runTest {
		isDirty.value = true
		coEvery { projectStatisticsUseCase.load() } throws IllegalStateException("disk full")

		refresher.refreshIfStale()
	}
}
