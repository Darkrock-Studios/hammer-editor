package repositories.statistics

import com.darkrockstudios.apps.hammer.common.data.projectstatistics.ProjectStatistics
import com.darkrockstudios.apps.hammer.common.data.projectstatistics.StatisticsDatasource
import com.darkrockstudios.apps.hammer.common.data.projectstatistics.StatisticsRepository
import getProject1Def
import io.mockk.*
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import utils.BaseTest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pins the dirty-flag mechanics of [StatisticsRepository.markDirty]: synchronous in-memory
 * flip, async persistence of the flag, and idempotency once already dirty.
 */
class StatisticsRepositoryTest : BaseTest() {

	@MockK
	private lateinit var datasource: StatisticsDatasource

	@BeforeEach
	override fun setup() {
		super.setup()
		MockKAnnotations.init(this, relaxUnitFun = true)
		setupKoin()

		coEvery { datasource.loadStatistics() } returns stats(isDirty = false)
		coEvery { datasource.saveStatistics(any()) } just Runs
	}

	private fun stats(isDirty: Boolean) = ProjectStatistics(
		numberOfScenes = 0,
		totalWords = 0,
		wordsByChapter = emptyMap(),
		encyclopediaEntriesByType = emptyMap(),
		isDirty = isDirty,
		lastCalculated = Instant.fromEpochSeconds(0),
	)

	private fun createRepository() = StatisticsRepository(getProject1Def(), datasource)

	@Test
	fun `markDirty flips isDirty synchronously`() = runTest(mainTestDispatcher) {
		val repo = createRepository()
		assertFalse(repo.isDirty.value)

		repo.markDirty()

		// The in-memory flag flips before the persistence coroutine runs.
		assertTrue(repo.isDirty.value)
	}

	@Test
	fun `markDirty persists the dirty flag to the cache`() = runTest(mainTestDispatcher) {
		val repo = createRepository()

		repo.markDirty()
		advanceUntilIdle()

		coVerify { datasource.saveStatistics(match { it.isDirty }) }
	}

	@Test
	fun `markDirty is idempotent once already dirty`() = runTest(mainTestDispatcher) {
		val repo = createRepository()

		repo.markDirty()
		advanceUntilIdle()
		clearMocks(datasource, answers = false)

		repo.markDirty()
		advanceUntilIdle()

		// Already dirty in-memory, so the second call short-circuits without touching the cache.
		coVerify(exactly = 0) { datasource.loadStatistics() }
		coVerify(exactly = 0) { datasource.saveStatistics(any()) }
	}

	@Test
	fun `markDirty does not rewrite an already-dirty cache`() = runTest(mainTestDispatcher) {
		coEvery { datasource.loadStatistics() } returns stats(isDirty = true)
		val repo = createRepository()

		repo.markDirty()
		advanceUntilIdle()

		// Cache is already dirty on disk; nothing to persist.
		coVerify(exactly = 0) { datasource.saveStatistics(any()) }
	}

	@Test
	fun `loadStatistics reflects the persisted dirty flag`() = runTest(mainTestDispatcher) {
		coEvery { datasource.loadStatistics() } returns stats(isDirty = true)
		val repo = createRepository()

		val loaded = repo.loadStatistics()

		assertEquals(true, loaded?.isDirty)
		assertTrue(repo.isDirty.value)
	}

	@Test
	fun `clearDirty resets the flag`() = runTest(mainTestDispatcher) {
		val repo = createRepository()
		repo.markDirty()
		advanceUntilIdle()
		assertTrue(repo.isDirty.value)

		repo.clearDirty()

		assertFalse(repo.isDirty.value)
	}
}
