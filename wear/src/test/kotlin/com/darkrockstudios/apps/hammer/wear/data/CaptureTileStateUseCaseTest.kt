package com.darkrockstudios.apps.hammer.wear.data

import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsStore
import com.darkrockstudios.apps.hammer.wear.FakeUnsyncedContentSource
import com.darkrockstudios.apps.hammer.wear.FakeWearPrefsDatasource
import com.darkrockstudios.apps.hammer.wear.SignedInSettings
import com.darkrockstudios.apps.hammer.wear.TestProjects
import com.darkrockstudios.apps.hammer.wear.WearTestBase
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class CaptureTileStateUseCaseTest : WearTestBase() {

	private lateinit var projects: TestProjects
	private lateinit var subscriptions: SubscribedProjectsRepository
	private lateinit var unsynced: FakeUnsyncedContentSource
	private lateinit var targets: CaptureTargetsUseCase
	private lateinit var globalSettingsStore: GlobalSettingsStore
	private lateinit var useCase: CaptureTileStateUseCase

	@BeforeEach
	override fun setUp() {
		super.setUp()
		projects = TestProjects()
		subscriptions = SubscribedProjectsRepository(FakeWearPrefsDatasource())
		unsynced = FakeUnsyncedContentSource()
		val listProjects = ListWatchProjectsUseCase(projects.repository, subscriptions)
		targets = CaptureTargetsUseCase(listProjects, subscriptions)
		globalSettingsStore = mockk(relaxed = true)
		every { globalSettingsStore.serverSettings } returns SignedInSettings
		useCase = CaptureTileStateUseCase(
			captureTargets = targets,
			unsyncedContent = UnsyncedContentUseCase(listProjects, unsynced),
			globalSettingsStore = globalSettingsStore,
		)
	}

	@Test
	fun `a signed out watch asks for sign in instead of offering capture`() = runTest(dispatcher) {
		every { globalSettingsStore.serverSettings } returns null
		projects.create("Alpha", serverId = "a")
		subscriptions.setSubscribed(ProjectId("a"), true)

		val state = useCase.load()

		assertFalse(state.signedIn)
		assertNull(state.projectName)
	}

	@Test
	fun `provisional settings from an unfinished sign in still count as signed out`() = runTest(dispatcher) {
		every { globalSettingsStore.serverSettings } returns SignedInSettings.copy(userId = -1, bearerToken = null)

		assertFalse(useCase.load().signedIn)
	}

	@Test
	fun `the tile shows the project a note would go to and what is waiting`() = runTest(dispatcher) {
		projects.create("Alpha", serverId = "a")
		projects.create("Beta", serverId = "b")
		subscriptions.setSubscribed(ProjectId("a"), true)
		subscriptions.setSubscribed(ProjectId("b"), true)
		subscriptions.setLastCaptureProject(ProjectId("b"))
		unsynced.pendingByProject["Beta"] = 2
		unsynced.pendingIdeas = 1

		val state = useCase.load()

		assertEquals("Beta", state.projectName)
		assertEquals(3, state.pending)
	}

	@Test
	fun `a watch with no project kept on it names none`() = runTest(dispatcher) {
		// The project exists but is not subscribed, so a note still has nowhere to go.
		projects.create("Alpha", serverId = "a")

		val state = useCase.load()

		assertNull(state.projectName)
		assertEquals(0, state.pending)
	}

	@Test
	fun `the default falls back to the first project before anything has been captured`() =
		runTest(dispatcher) {
			projects.create("Alpha", serverId = "a")
			projects.create("Beta", serverId = "b")
			subscriptions.setSubscribed(ProjectId("a"), true)
			subscriptions.setSubscribed(ProjectId("b"), true)

			assertEquals("Alpha", useCase.load().projectName)
		}

	@Test
	fun `a project that is gone is not offered as a target`() = runTest(dispatcher) {
		projects.create("Alpha", serverId = "a")
		subscriptions.setSubscribed(ProjectId("a"), true)
		// Unsubscribing elsewhere leaves the remembered id pointing at nothing on the watch.
		subscriptions.setLastCaptureProject(ProjectId("gone"))

		val loaded = targets.load()

		assertEquals(listOf("Alpha"), loaded.projects.map { it.projectDef.name })
		assertEquals("Alpha", loaded.default?.projectDef?.name)
	}

}
