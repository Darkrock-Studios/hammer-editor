package com.darkrockstudios.apps.hammer.wear.components.projects

import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsStore
import com.darkrockstudios.apps.hammer.common.data.globalsettings.ServerSettings
import com.darkrockstudios.apps.hammer.common.data.sync.accountsync.ProjectSyncOutcome
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import com.darkrockstudios.apps.hammer.wear.FakeSyncCoordinator
import com.darkrockstudios.apps.hammer.wear.FakeWearPrefsDatasource
import com.darkrockstudios.apps.hammer.wear.TestProjects
import com.darkrockstudios.apps.hammer.wear.WearTestBase
import com.darkrockstudios.apps.hammer.wear.data.ListWatchProjectsUseCase
import com.darkrockstudios.apps.hammer.wear.data.SignOutUseCase
import com.darkrockstudios.apps.hammer.wear.data.SubscribedProjectsRepository
import com.darkrockstudios.apps.hammer.wear.sync.ProjectSyncState
import com.darkrockstudios.apps.hammer.wear.sync.SyncStatus
import com.darkrockstudios.apps.hammer.wear.sync.SyncTrigger
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class WearProjectsComponentTest : WearTestBase() {

	private lateinit var projects: TestProjects
	private lateinit var subscriptions: SubscribedProjectsRepository
	private lateinit var coordinator: FakeSyncCoordinator
	private lateinit var signOutUseCase: SignOutUseCase
	private var syncLogShown = false

	@BeforeEach
	override fun setUp() {
		super.setUp()
		projects = TestProjects()
		subscriptions = SubscribedProjectsRepository(FakeWearPrefsDatasource())
		coordinator = FakeSyncCoordinator()
		signOutUseCase = mockk(relaxed = true)
		syncLogShown = false
	}

	private fun newComponent(advanceToIdle: Boolean = true): WearProjectsComponent {
		val globalSettingsStore = mockk<GlobalSettingsStore>()
		every { globalSettingsStore.serverSettings } returns ServerSettings(
			url = "hammer.ink",
			email = "writer@example.com",
			userId = 7,
			bearerToken = "auth",
			refreshToken = "refresh",
		)
		return WearProjectsComponent(
			componentContext = componentContext,
			globalSettingsStore = globalSettingsStore,
			listProjects = ListWatchProjectsUseCase(projects.repository, subscriptions),
			projectsRepository = projects.repository,
			subscriptions = subscriptions,
			syncCoordinator = coordinator,
			signOutUseCase = signOutUseCase,
			appScope = CoroutineScope(dispatcher),
			onShowSyncLog = { syncLogShown = true },
		).also {
			resumeLifecycle()
			if (advanceToIdle) scheduler.advanceUntilIdle()
		}
	}

	private fun WearProjectsComponent.row(name: String) = state.value.projects.single { it.name == name }

	@Test
	fun `opening the screen lists projects and asks for the app-open sync`() = runTest(dispatcher) {
		projects.create("Alpha", serverId = "a")
		projects.create("Draft")

		val component = newComponent()

		assertEquals(listOf("Alpha", "Draft"), component.state.value.projects.map { it.name })
		assertTrue(component.row("Alpha").canSubscribe)
		assertFalse(component.row("Draft").canSubscribe)
		assertEquals("writer@example.com", component.state.value.accountEmail)
		assertEquals(1, coordinator.autoSyncRequests)
	}

	@Test
	fun `subscribing to a project keeps it on the watch and syncs it`() = runTest(dispatcher) {
		projects.create("Alpha", serverId = "a")
		val component = newComponent()

		component.toggleSubscription("Alpha")
		scheduler.advanceUntilIdle()

		assertTrue(component.row("Alpha").subscribed)
		assertEquals(setOf(ProjectId("a")), subscriptions.currentSubscriptions())
		assertEquals(listOf(SyncTrigger.Manual), coordinator.requested)
	}

	@Test
	fun `unsubscribing drops the content but leaves the project listed`() = runTest(dispatcher) {
		val projectDef = projects.create("Alpha", serverId = "a")
		val sceneDir = projectDef.path.toOkioPath() / "scenes"
		projects.fileSystem.createDirectories(sceneDir)
		projects.fileSystem.write(sceneDir / "1.md") { writeUtf8("a scene synced to the watch") }
		subscriptions.setSubscribed(ProjectId("a"), true)
		val component = newComponent()

		component.toggleSubscription("Alpha")
		scheduler.advanceUntilIdle()

		assertFalse(projects.fileSystem.exists(sceneDir))
		// The row has to survive, or the project cannot be subscribed to again without a sync first.
		assertFalse(component.row("Alpha").subscribed)
		assertTrue(component.row("Alpha").canSubscribe)
		assertEquals(emptySet<ProjectId>(), subscriptions.currentSubscriptions())
		assertEquals(emptyList<SyncTrigger>(), coordinator.requested)
	}

	@Test
	fun `the list is not reported as empty until it has loaded`() = runTest(dispatcher) {
		projects.create("Alpha", serverId = "a")

		val component = newComponent(advanceToIdle = false)

		assertFalse(component.state.value.loaded)

		scheduler.advanceUntilIdle()

		assertTrue(component.state.value.loaded)
		assertEquals(listOf("Alpha"), component.state.value.projects.map { it.name })
	}

	@Test
	fun `a project the server does not know yet cannot be subscribed`() = runTest(dispatcher) {
		projects.create("Draft")
		val component = newComponent()

		component.toggleSubscription("Draft")
		scheduler.advanceUntilIdle()

		assertFalse(component.row("Draft").subscribed)
		assertEquals(emptySet<ProjectId>(), subscriptions.currentSubscriptions())
	}

	@Test
	fun `sync progress shows on the project row`() = runTest(dispatcher) {
		projects.create("Alpha", serverId = "a")
		val component = newComponent()

		coordinator.status.value = SyncStatus(running = true, projects = mapOf("Alpha" to ProjectSyncState(progress = 0.25f)))
		scheduler.advanceUntilIdle()

		assertTrue(component.state.value.syncing)
		assertEquals(0.25f, component.row("Alpha").progress)
	}

	@Test
	fun `projects that arrive during a sync appear once it finishes`() = runTest(dispatcher) {
		projects.create("Alpha", serverId = "a")
		val component = newComponent()
		coordinator.status.value = SyncStatus(running = true)
		scheduler.advanceUntilIdle()

		projects.create("Beta", serverId = "b")
		coordinator.status.value = SyncStatus(
			running = false,
			projects = mapOf("Beta" to ProjectSyncState(outcome = ProjectSyncOutcome.Skipped)),
		)
		scheduler.advanceUntilIdle()

		assertFalse(component.state.value.syncing)
		assertEquals(listOf("Alpha", "Beta"), component.state.value.projects.map { it.name })
	}

	@Test
	fun `sync now and the sync log reach their destinations`() = runTest(dispatcher) {
		val component = newComponent()

		component.syncNow()
		component.showSyncLog()

		assertEquals(listOf(SyncTrigger.Manual), coordinator.requested)
		assertTrue(syncLogShown)
	}

	@Test
	fun `signing out runs the sign out`() = runTest(dispatcher) {
		val component = newComponent()

		component.signOut()
		scheduler.advanceUntilIdle()

		coVerify(exactly = 1) { signOutUseCase.signOut() }
	}
}
