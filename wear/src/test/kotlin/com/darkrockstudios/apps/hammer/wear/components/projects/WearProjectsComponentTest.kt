package com.darkrockstudios.apps.hammer.wear.components.projects

import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsStore
import com.darkrockstudios.apps.hammer.common.data.globalsettings.ServerSettings
import com.darkrockstudios.apps.hammer.common.data.sync.accountsync.ProjectSyncOutcome
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import com.darkrockstudios.apps.hammer.wear.FakeLocalNetworkAccess
import com.darkrockstudios.apps.hammer.wear.FakeSyncCoordinator
import com.darkrockstudios.apps.hammer.wear.FakeUnsyncedContentSource
import com.darkrockstudios.apps.hammer.wear.FailingUnsubscribeDatasource
import com.darkrockstudios.apps.hammer.wear.FakeWearPrefsDatasource
import com.darkrockstudios.apps.hammer.wear.TestProjects
import com.darkrockstudios.apps.hammer.wear.WearTestBase
import com.darkrockstudios.apps.hammer.wear.data.ListWatchProjectsUseCase
import com.darkrockstudios.apps.hammer.wear.data.SignOutUseCase
import com.darkrockstudios.apps.hammer.wear.data.SubscribedProjectsRepository
import com.darkrockstudios.apps.hammer.wear.data.UnsyncedContentUseCase
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
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class WearProjectsComponentTest : WearTestBase() {

	private lateinit var projects: TestProjects
	private lateinit var subscriptions: SubscribedProjectsRepository
	private lateinit var coordinator: FakeSyncCoordinator
	private lateinit var unsynced: FakeUnsyncedContentSource
	private lateinit var signOutUseCase: SignOutUseCase
	private lateinit var localNetwork: FakeLocalNetworkAccess
	private var syncLogShown = false

	@BeforeEach
	override fun setUp() {
		super.setUp()
		projects = TestProjects()
		subscriptions = SubscribedProjectsRepository(FakeWearPrefsDatasource())
		coordinator = FakeSyncCoordinator()
		unsynced = FakeUnsyncedContentSource()
		signOutUseCase = mockk(relaxed = true)
		localNetwork = FakeLocalNetworkAccess()
		syncLogShown = false
	}

	private fun newComponent(
		advanceToIdle: Boolean = true,
		serverUrl: String = "hammer.ink",
	): WearProjectsComponent {
		val globalSettingsStore = mockk<GlobalSettingsStore>()
		every { globalSettingsStore.serverSettings } returns ServerSettings(
			url = serverUrl,
			email = "writer@example.com",
			userId = 7,
			bearerToken = "auth",
			refreshToken = "refresh",
		)
		val listProjects = ListWatchProjectsUseCase(projects.repository, subscriptions)
		return WearProjectsComponent(
			componentContext = componentContext,
			globalSettingsStore = globalSettingsStore,
			listProjects = listProjects,
			projectsRepository = projects.repository,
			subscriptions = subscriptions,
			unsyncedContent = UnsyncedContentUseCase(listProjects, unsynced),
			syncCoordinator = coordinator,
			signOutUseCase = signOutUseCase,
			localNetworkAccess = localNetwork,
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
	fun `a local server the watch may not reach yet holds the first sync back`() = runTest(dispatcher) {
		localNetwork.block("192.168.1.46:8081")

		val component = newComponent(serverUrl = "192.168.1.46:8081")

		// Pairing lands here and would sync at once; that sync would only time out.
		assertTrue(component.state.value.localNetworkBlocked)
		assertEquals(0, coordinator.autoSyncRequests)
	}

	@Test
	fun `granting local network access releases the sync`() = runTest(dispatcher) {
		localNetwork.block("192.168.1.46:8081")
		val component = newComponent(serverUrl = "192.168.1.46:8081")

		localNetwork.grant()
		component.onLocalNetworkPermissionResult()
		scheduler.advanceUntilIdle()

		assertFalse(component.state.value.localNetworkBlocked)
		assertEquals(1, coordinator.autoSyncRequests)
	}

	@Test
	fun `a refusal keeps the sync held and the explanation showing`() = runTest(dispatcher) {
		localNetwork.block("192.168.1.46:8081")
		val component = newComponent(serverUrl = "192.168.1.46:8081")

		component.onLocalNetworkPermissionResult()
		scheduler.advanceUntilIdle()

		assertTrue(component.state.value.localNetworkBlocked)
		assertEquals(0, coordinator.autoSyncRequests)
	}

	@Test
	fun `a public server is never held back`() = runTest(dispatcher) {
		val component = newComponent(serverUrl = "hammer.ink")

		assertFalse(component.state.value.localNetworkBlocked)
		assertEquals(listOf("hammer.ink"), localNetwork.checked)
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
		subscriptions.setSubscribed(ProjectId("a"), true)
		val component = newComponent()

		coordinator.status.value = SyncStatus(running = true, projects = mapOf("Alpha" to ProjectSyncState(progress = 0.25f)))
		scheduler.advanceUntilIdle()

		assertTrue(component.state.value.syncing)
		assertEquals(0.25f, component.row("Alpha").progress)
	}

	@Test
	fun `a project that is not on the watch does not report a sync it never attempted`() = runTest(dispatcher) {
		projects.create("Alpha", serverId = "a")
		projects.create("Beta", serverId = "b")
		subscriptions.setSubscribed(ProjectId("a"), true)
		val component = newComponent()

		// A failed account sync marks every project failed, including ones it filtered out.
		coordinator.status.value = SyncStatus(
			projects = mapOf(
				"Alpha" to ProjectSyncState(outcome = ProjectSyncOutcome.Failed),
				"Beta" to ProjectSyncState(outcome = ProjectSyncOutcome.Failed),
			),
		)
		scheduler.advanceUntilIdle()

		assertEquals(ProjectSyncOutcome.Failed, component.row("Alpha").outcome)
		assertNull(component.row("Beta").outcome)
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
	fun `unsubscribing uploads first when the project has writing the server has not seen`() = runTest(dispatcher) {
		val projectDef = projects.create("Alpha", serverId = "a")
		val sceneDir = projectDef.path.toOkioPath() / "scenes"
		projects.fileSystem.createDirectories(sceneDir)
		subscriptions.setSubscribed(ProjectId("a"), true)
		unsynced.pendingByProject["Alpha"] = 2
		coordinator.onSync = { unsynced.pendingByProject["Alpha"] = 0 }
		val component = newComponent()

		component.toggleSubscription("Alpha")
		scheduler.advanceUntilIdle()

		assertEquals(listOf(SyncTrigger.Manual), coordinator.requested)
		assertFalse(projects.fileSystem.exists(sceneDir))
		assertEquals(emptySet<ProjectId>(), subscriptions.currentSubscriptions())
		assertNull(component.state.value.notice)
	}

	@Test
	fun `unsubscribing keeps content and the subscription while writing is still unsynced`() = runTest(dispatcher) {
		val projectDef = projects.create("Alpha", serverId = "a")
		val sceneDir = projectDef.path.toOkioPath() / "scenes"
		projects.fileSystem.createDirectories(sceneDir)
		projects.fileSystem.write(sceneDir / "1.md") { writeUtf8("a note dictated on a run") }
		subscriptions.setSubscribed(ProjectId("a"), true)
		unsynced.pendingByProject["Alpha"] = 1
		val component = newComponent()

		component.toggleSubscription("Alpha")
		scheduler.advanceUntilIdle()

		assertTrue(projects.fileSystem.exists(sceneDir / "1.md"))
		// Unsubscribing here would filter the project out of every later sync, stranding the note.
		assertEquals(setOf(ProjectId("a")), subscriptions.currentSubscriptions())
		assertTrue(component.row("Alpha").subscribed)
		assertEquals(
			WearProjects.Notice("Alpha", WearProjects.Notice.Reason.UnsyncedKept),
			component.state.value.notice,
		)
		assertFalse(component.row("Alpha").unsubscribing)
	}

	@Test
	fun `a count that cannot be read never authorises a delete`() = runTest(dispatcher) {
		val projectDef = projects.create("Alpha", serverId = "a")
		val sceneDir = projectDef.path.toOkioPath() / "scenes"
		projects.fileSystem.createDirectories(sceneDir)
		subscriptions.setSubscribed(ProjectId("a"), true)
		unsynced.failWith = IllegalStateException("no project scope")
		val component = newComponent()

		component.toggleSubscription("Alpha")
		scheduler.advanceUntilIdle()

		assertTrue(projects.fileSystem.exists(sceneDir))
		assertEquals(setOf(ProjectId("a")), subscriptions.currentSubscriptions())
		assertEquals(
			WearProjects.Notice("Alpha", WearProjects.Notice.Reason.UnsyncedKept),
			component.state.value.notice,
		)
	}

	@Test
	fun `an unsubscribe that breaks is not reported as unsynced writing`() = runTest(dispatcher) {
		projects.create("Alpha", serverId = "a")
		// Nothing is pending, so the only thing left that can go wrong is the unsubscribe itself.
		subscriptions = SubscribedProjectsRepository(FailingUnsubscribeDatasource())
		subscriptions.setSubscribed(ProjectId("a"), true)
		val component = newComponent()

		component.toggleSubscription("Alpha")
		scheduler.advanceUntilIdle()

		// Telling the user to sync and retry would be advice that cannot possibly help.
		assertEquals(
			WearProjects.Notice("Alpha", WearProjects.Notice.Reason.Failed),
			component.state.value.notice,
		)
	}

	@Test
	fun `dismissing the notice clears it`() = runTest(dispatcher) {
		projects.create("Alpha", serverId = "a")
		subscriptions.setSubscribed(ProjectId("a"), true)
		unsynced.pendingByProject["Alpha"] = 1
		val component = newComponent()
		component.toggleSubscription("Alpha")
		scheduler.advanceUntilIdle()

		component.dismissNotice()

		assertNull(component.state.value.notice)
	}

	@Test
	fun `signing out with nothing outstanding does not stop to ask`() = runTest(dispatcher) {
		val component = newComponent()

		component.signOut()
		scheduler.advanceUntilIdle()

		assertNull(component.state.value.signOutWarning)
		coVerify(exactly = 1) { signOutUseCase.signOut() }
	}

	@Test
	fun `signing out warns before wiping captures that have not synced`() = runTest(dispatcher) {
		projects.create("Alpha", serverId = "a")
		subscriptions.setSubscribed(ProjectId("a"), true)
		unsynced.pendingByProject["Alpha"] = 2
		unsynced.pendingIdeas = 1
		val component = newComponent()

		component.signOut()
		scheduler.advanceUntilIdle()

		assertEquals(WearProjects.SignOutWarning(items = 3), component.state.value.signOutWarning)
		coVerify(exactly = 0) { signOutUseCase.signOut() }
	}

	@Test
	fun `an uncountable state still warns rather than wiping silently`() = runTest(dispatcher) {
		val component = newComponent()
		unsynced.failWith = IllegalStateException("no project scope")

		component.signOut()
		scheduler.advanceUntilIdle()

		assertEquals(WearProjects.SignOutWarning(items = 0), component.state.value.signOutWarning)
		coVerify(exactly = 0) { signOutUseCase.signOut() }
	}

	@Test
	fun `confirming the warning signs out`() = runTest(dispatcher) {
		unsynced.pendingIdeas = 1
		val component = newComponent()
		component.signOut()
		scheduler.advanceUntilIdle()

		component.confirmSignOut()
		scheduler.advanceUntilIdle()

		assertNull(component.state.value.signOutWarning)
		coVerify(exactly = 1) { signOutUseCase.signOut() }
	}

	@Test
	fun `the sign out button reports that it is counting`() = runTest(dispatcher) {
		projects.create("Alpha", serverId = "a")
		subscriptions.setSubscribed(ProjectId("a"), true)
		unsynced.pendingByProject["Alpha"] = 1
		val component = newComponent()

		component.signOut()

		// Counting opens every subscribed project, so a dead-looking button is a real hazard.
		assertTrue(component.state.value.checkingSignOut)

		scheduler.advanceUntilIdle()

		assertFalse(component.state.value.checkingSignOut)
	}

	@Test
	fun `tapping sign out twice only counts once`() = runTest(dispatcher) {
		projects.create("Alpha", serverId = "a")
		subscriptions.setSubscribed(ProjectId("a"), true)
		unsynced.pendingByProject["Alpha"] = 1
		val component = newComponent()

		component.signOut()
		component.signOut()
		scheduler.advanceUntilIdle()

		assertEquals(WearProjects.SignOutWarning(items = 1), component.state.value.signOutWarning)
		assertEquals(1, unsynced.pendingCalls)
	}

	@Test
	fun `cancelling the warning keeps the account`() = runTest(dispatcher) {
		unsynced.pendingIdeas = 1
		val component = newComponent()
		component.signOut()
		scheduler.advanceUntilIdle()

		component.cancelSignOut()
		scheduler.advanceUntilIdle()

		assertNull(component.state.value.signOutWarning)
		coVerify(exactly = 0) { signOutUseCase.signOut() }
	}
}
