package com.darkrockstudios.apps.hammer.wear.sync

import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.SyncedProjectDefinition
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettings
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsStore
import com.darkrockstudios.apps.hammer.common.data.globalsettings.ServerSettings
import com.darkrockstudios.apps.hammer.common.data.sync.accountsync.ProjectSyncOutcome
import com.darkrockstudios.apps.hammer.common.data.sync.accountsync.SyncAccountListener
import com.darkrockstudios.apps.hammer.common.data.sync.accountsync.SyncAccountResult
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import com.darkrockstudios.apps.hammer.common.util.NetworkConnectivity
import com.darkrockstudios.apps.hammer.wear.FakeWearPrefsDatasource
import com.darkrockstudios.apps.hammer.wear.data.SubscribedProjectsRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class DefaultSyncCoordinatorTest {

	private val alpha = ProjectDef("Alpha", HPath("/projects/Alpha", "Alpha", false))
	private val beta = ProjectDef("Beta", HPath("/projects/Beta", "Beta", false))
	private val candidates = listOf(
		SyncedProjectDefinition(alpha, ProjectId("a")),
		SyncedProjectDefinition(beta, ProjectId("b")),
	)

	private val signedIn = ServerSettings(
		url = "hammer.ink",
		email = "writer@example.com",
		userId = 7,
		bearerToken = "auth",
		refreshToken = "refresh",
	)

	/** Reports which candidates the filter admits, then runs [behaviour] against the listener. */
	private inner class FakeAccountSync : AccountSync {
		var runs = 0
		var admitted: List<String> = emptyList()
		var behaviour: suspend (SyncAccountListener) -> SyncAccountResult = { successResult() }

		override suspend fun run(
			listener: SyncAccountListener,
			projectFilter: (SyncedProjectDefinition) -> Boolean,
		): SyncAccountResult {
			runs++
			admitted = candidates.filter(projectFilter).map { it.projectDef.name }
			return behaviour(listener)
		}
	}

	private fun successResult() = SyncAccountResult(
		accountSuccess = true,
		ideasSuccess = true,
		projects = mapOf("Alpha" to ProjectSyncOutcome.Success),
	)

	private val scheduler = TestCoroutineScheduler()
	private val dispatcher = StandardTestDispatcher(scheduler)
	private lateinit var appScope: CoroutineScope

	private lateinit var accountSync: FakeAccountSync
	private lateinit var subscriptions: SubscribedProjectsRepository
	private lateinit var globalSettingsStore: GlobalSettingsStore
	private lateinit var networkConnectivity: NetworkConnectivity
	private lateinit var coordinator: DefaultSyncCoordinator

	@BeforeEach
	fun setUp() {
		appScope = CoroutineScope(dispatcher)
		accountSync = FakeAccountSync()
		subscriptions = SubscribedProjectsRepository(FakeWearPrefsDatasource())
		globalSettingsStore = mockk()
		every { globalSettingsStore.serverSettings } returns signedIn
		every { globalSettingsStore.globalSettings } returns GlobalSettings(projectsDirectory = "/projects", automaticSyncing = true)
		networkConnectivity = mockk()
		coEvery { networkConnectivity.hasActiveConnection() } returns true
		coordinator = DefaultSyncCoordinator(accountSync, subscriptions, globalSettingsStore, networkConnectivity, appScope)
	}

	@AfterEach
	fun tearDown() {
		appScope.cancel()
	}

	@Test
	fun `only subscribed projects are admitted to the sync`() = runTest(dispatcher) {
		subscriptions.setSubscribed(ProjectId("b"), true)

		coordinator.sync(SyncTrigger.Manual)

		assertEquals(listOf("Beta"), accountSync.admitted)
	}

	@Test
	fun `status tracks a sync from start to finish`() = runTest(dispatcher) {
		val gate = CompletableDeferred<Unit>()
		accountSync.behaviour = { listener ->
			listener.onProjectsDiscovered(listOf(alpha))
			listener.onProjectProgress(alpha, 0.5f)
			gate.await()
			listener.onProjectOutcome(alpha, ProjectSyncOutcome.Success)
			successResult()
		}

		val run = async { coordinator.sync(SyncTrigger.Manual) }
		scheduler.advanceUntilIdle()

		val during = coordinator.status.value
		assertTrue(during.running)
		assertEquals(SyncTrigger.Manual, during.trigger)
		assertEquals(ProjectSyncState(progress = 0.5f), during.projects["Alpha"])

		gate.complete(Unit)
		assertInstanceOf(SyncRunResult.Completed::class.java, run.await())

		val after = coordinator.status.value
		assertFalse(after.running)
		assertEquals(ProjectSyncOutcome.Success, after.projects.getValue("Alpha").outcome)
		assertEquals(successResult(), after.lastResult)
	}

	@Test
	fun `a sync requested while one is running is skipped`() = runTest(dispatcher) {
		val gate = CompletableDeferred<Unit>()
		accountSync.behaviour = {
			gate.await()
			successResult()
		}
		val first = async { coordinator.sync(SyncTrigger.Manual) }
		scheduler.advanceUntilIdle()

		val second = coordinator.sync(SyncTrigger.Periodic)
		gate.complete(Unit)
		first.await()

		assertEquals(SyncRunResult.Skipped, second)
		assertEquals(1, accountSync.runs)
	}

	@Test
	fun `an expired session is flagged for re-authentication`() = runTest(dispatcher) {
		accountSync.behaviour = { listener ->
			listener.onUnauthorized()
			successResult().copy(accountSuccess = false)
		}

		coordinator.sync(SyncTrigger.Manual)

		assertTrue(coordinator.status.value.needsReauth)
	}

	@Test
	fun `a thrown sync is reported as failed and releases the lock`() = runTest(dispatcher) {
		accountSync.behaviour = { error("network gone") }

		val result = coordinator.sync(SyncTrigger.Manual)

		assertEquals(SyncRunResult.Failed, result)
		assertFalse(coordinator.status.value.running)
		assertTrue(coordinator.status.value.lastRunFailed)
		accountSync.behaviour = { successResult() }
		assertInstanceOf(SyncRunResult.Completed::class.java, coordinator.sync(SyncTrigger.Manual))
	}

	@Test
	fun `a signed out watch does not sync`() = runTest(dispatcher) {
		every { globalSettingsStore.serverSettings } returns null

		assertEquals(SyncRunResult.Skipped, coordinator.sync(SyncTrigger.Manual))
		assertEquals(0, accountSync.runs)
	}

	@Test
	fun `the app-open sync runs once per process`() = runTest(dispatcher) {
		coordinator.requestAutoSync()
		scheduler.advanceUntilIdle()
		coordinator.requestAutoSync()
		scheduler.advanceUntilIdle()

		assertEquals(1, accountSync.runs)
	}

	@Test
	fun `an app-open while offline does not use up the once-per-process sync`() = runTest(dispatcher) {
		coEvery { networkConnectivity.hasActiveConnection() } returns false
		coordinator.requestAutoSync()
		scheduler.advanceUntilIdle()
		assertEquals(0, accountSync.runs)

		coEvery { networkConnectivity.hasActiveConnection() } returns true
		coordinator.requestAutoSync()
		scheduler.advanceUntilIdle()

		assertEquals(1, accountSync.runs)
	}

	@Test
	fun `the app-open sync respects the automatic syncing setting`() = runTest(dispatcher) {
		every { globalSettingsStore.globalSettings } returns
			GlobalSettings(projectsDirectory = "/projects", automaticSyncing = false)

		coordinator.requestAutoSync()
		scheduler.advanceUntilIdle()

		assertEquals(0, accountSync.runs)
	}

	@Test
	fun `exclusive work waits for a running sync to finish`() = runTest(dispatcher) {
		val gate = CompletableDeferred<Unit>()
		val events = mutableListOf<String>()
		accountSync.behaviour = {
			gate.await()
			events += "sync finished"
			successResult()
		}
		val sync = async { coordinator.sync(SyncTrigger.Manual) }
		scheduler.advanceUntilIdle()

		val exclusive = launch { coordinator.runExclusive { events += "exclusive ran" } }
		scheduler.advanceUntilIdle()
		assertEquals(emptyList<String>(), events)

		gate.complete(Unit)
		sync.await()
		exclusive.join()

		assertEquals(listOf("sync finished", "exclusive ran"), events)
	}
}
