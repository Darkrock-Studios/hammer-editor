package com.darkrockstudios.apps.hammer.wear.data

import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsStore
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository
import com.darkrockstudios.apps.hammer.wear.FakeSyncCoordinator
import com.darkrockstudios.apps.hammer.wear.FakeWearPrefsDatasource
import com.darkrockstudios.apps.hammer.wear.sync.SyncScheduler
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import okio.IOException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SignOutUseCaseTest {

	private lateinit var subscriptions: SubscribedProjectsRepository
	private lateinit var projectsRepository: ProjectsRepository
	private lateinit var coordinator: FakeSyncCoordinator
	private lateinit var syncScheduler: SyncScheduler
	private lateinit var globalSettingsStore: GlobalSettingsStore

	@BeforeEach
	fun setUp() {
		subscriptions = SubscribedProjectsRepository(FakeWearPrefsDatasource())
		projectsRepository = mockk(relaxed = true)
		coordinator = FakeSyncCoordinator()
		syncScheduler = mockk(relaxed = true)
		globalSettingsStore = mockk(relaxed = true)
	}

	private fun useCase() = SignOutUseCase(
		globalSettingsStore = globalSettingsStore,
		projectsRepository = projectsRepository,
		subscriptions = subscriptions,
		syncScheduler = syncScheduler,
		syncCoordinator = coordinator,
	)

	@Test
	fun `signing out forgets the account, its data and its subscriptions`() = runTest {
		subscriptions.setSubscribed(ProjectId("a"), true)

		useCase().signOut()

		verify(exactly = 1) { syncScheduler.cancel() }
		verify(exactly = 1) { projectsRepository.deleteAllLocalData() }
		assertEquals(emptySet<ProjectId>(), subscriptions.currentSubscriptions())
		assertEquals(1, coordinator.autoSyncResets)
	}

	@Test
	fun `a wipe that fails still clears the subscriptions and the auto sync latch`() = runTest {
		subscriptions.setSubscribed(ProjectId("a"), true)
		every { projectsRepository.deleteAllLocalData() } throws IOException("the disk went away")

		val failure = runCatching { useCase().signOut() }.exceptionOrNull()

		assertTrue(failure is IOException)
		// Otherwise the next account signed in inherits this one's subscriptions.
		assertEquals(emptySet<ProjectId>(), subscriptions.currentSubscriptions())
		assertEquals(1, coordinator.autoSyncResets)
	}
}
