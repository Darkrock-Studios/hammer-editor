package com.darkrockstudios.apps.hammer.wear.data

import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.wear.FakeUnsyncedContentSource
import com.darkrockstudios.apps.hammer.wear.FakeWearPrefsDatasource
import com.darkrockstudios.apps.hammer.wear.TestProjects
import com.darkrockstudios.apps.hammer.wear.WearTestBase
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class CaptureTileStateUseCaseTest : WearTestBase() {

	private lateinit var projects: TestProjects
	private lateinit var subscriptions: SubscribedProjectsRepository
	private lateinit var unsynced: FakeUnsyncedContentSource
	private lateinit var targets: CaptureTargetsUseCase
	private lateinit var useCase: CaptureTileStateUseCase

	@BeforeEach
	override fun setUp() {
		super.setUp()
		projects = TestProjects()
		subscriptions = SubscribedProjectsRepository(FakeWearPrefsDatasource())
		unsynced = FakeUnsyncedContentSource()
		val listProjects = ListWatchProjectsUseCase(projects.repository, subscriptions)
		targets = CaptureTargetsUseCase(listProjects, subscriptions)
		useCase = CaptureTileStateUseCase(targets, UnsyncedContentUseCase(listProjects, unsynced))
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
