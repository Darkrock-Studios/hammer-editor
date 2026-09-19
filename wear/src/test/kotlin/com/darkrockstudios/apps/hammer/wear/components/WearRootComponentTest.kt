package com.darkrockstudios.apps.hammer.wear.components

import com.darkrockstudios.apps.hammer.common.data.CResult
import com.darkrockstudios.apps.hammer.common.data.account.AccountUseCase
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsStore
import com.darkrockstudios.apps.hammer.common.data.globalsettings.ServerSettings
import com.darkrockstudios.apps.hammer.common.data.pairing.PairRequest
import com.darkrockstudios.apps.hammer.common.data.pairing.PairResponse
import com.darkrockstudios.apps.hammer.common.data.pairing.PairingProtocol
import com.darkrockstudios.apps.hammer.wear.FakeLocalNetworkAccess
import com.darkrockstudios.apps.hammer.wear.FakeStrRes
import com.darkrockstudios.apps.hammer.wear.FakeSyncCoordinator
import com.darkrockstudios.apps.hammer.wear.FakeUnsyncedContentSource
import com.darkrockstudios.apps.hammer.wear.FakeWearPrefsDatasource
import com.darkrockstudios.apps.hammer.wear.TestProjects
import com.darkrockstudios.apps.hammer.wear.WearTestBase
import com.darkrockstudios.apps.hammer.wear.data.ListWatchProjectsUseCase
import com.darkrockstudios.apps.hammer.wear.data.SubscribedProjectsRepository
import com.darkrockstudios.apps.hammer.wear.data.UnsyncedContentUseCase
import com.darkrockstudios.apps.hammer.wear.pairing.PairingState
import com.darkrockstudios.apps.hammer.wear.pairing.PhonePairingClient
import com.darkrockstudios.apps.hammer.wear.pairing.PhonePairingUseCase
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class WearRootComponentTest : WearTestBase() {

	private val signedIn = ServerSettings(
		url = "hammer.ink",
		email = "writer@example.com",
		userId = 7,
		bearerToken = "auth",
		refreshToken = "refresh",
	)

	private val sentRequests = mutableListOf<PairRequest>()
	private lateinit var settingsUpdates: MutableSharedFlow<ServerSettings?>
	private lateinit var globalSettingsStore: GlobalSettingsStore
	private lateinit var phonePairing: PhonePairingUseCase

	@BeforeEach
	override fun setUp() {
		super.setUp()
		settingsUpdates = MutableSharedFlow(extraBufferCapacity = 1)
		globalSettingsStore = mockk(relaxed = true)
		every { globalSettingsStore.serverSettingsUpdates } returns settingsUpdates
		every { globalSettingsStore.serverSettings } returns null
		coEvery { globalSettingsStore.ensureInstallId() } returns "watch-install"

		val client = object : PhonePairingClient {
			override suspend fun findPhone(): String = "phone-node"
			override suspend fun sendRequest(nodeId: String, request: PairRequest) {
				sentRequests += request
			}
		}
		val accountUseCase = mockk<AccountUseCase>(relaxed = true)
		every { accountUseCase.applyPairedSettings(any()) } returns CResult.success()
		phonePairing = PhonePairingUseCase(client, globalSettingsStore, accountUseCase)
	}

	private fun newRoot(): WearRootComponent {
		val subscriptions = SubscribedProjectsRepository(FakeWearPrefsDatasource())
		val projects = TestProjects()
		return WearRootComponent(
			componentContext = componentContext,
			globalSettingsStore = globalSettingsStore,
			phonePairing = phonePairing,
			accountUseCase = mockk(relaxed = true),
			listProjects = ListWatchProjectsUseCase(projects.repository, subscriptions),
			projectsRepository = projects.repository,
			subscriptions = subscriptions,
			unsyncedContent = UnsyncedContentUseCase(
				ListWatchProjectsUseCase(projects.repository, subscriptions),
				FakeUnsyncedContentSource(),
			),
			syncCoordinator = FakeSyncCoordinator(),
			signOutUseCase = mockk(relaxed = true),
			localNetworkAccess = FakeLocalNetworkAccess(),
			appScope = CoroutineScope(dispatcher),
			strRes = FakeStrRes(),
			deviceLabel = "Pixel Watch",
		)
	}

	@Test
	fun `a signed out watch starts on onboarding`() = runTest(dispatcher) {
		val root = newRoot()

		assertEquals(WearRoot.Destination.Onboarding, root.stack.value.active.instance)
	}

	@Test
	fun `a signed in watch starts on its projects`() = runTest(dispatcher) {
		every { globalSettingsStore.serverSettings } returns signedIn

		val root = newRoot()

		assertInstanceOf(WearRoot.Destination.ProjectsDestination::class.java, root.stack.value.active.instance)
	}

	@Test
	fun `opening pairing asks the phone straight away`() = runTest(dispatcher) {
		val root = newRoot()
		resumeLifecycle()

		root.showPairing()
		scheduler.advanceUntilIdle()

		assertInstanceOf(WearRoot.Destination.PairingDestination::class.java, root.stack.value.active.instance)
		assertEquals(1, sentRequests.size)
		assertEquals(PairingState.AwaitingConfirmation, phonePairing.state.value)
	}

	@Test
	fun `signing in from pairing lands on projects with nothing to go back to`() = runTest(dispatcher) {
		val root = newRoot()
		resumeLifecycle()
		root.showPairing()
		scheduler.advanceUntilIdle()

		settingsUpdates.emit(signedIn)
		scheduler.advanceUntilIdle()

		assertInstanceOf(WearRoot.Destination.ProjectsDestination::class.java, root.stack.value.active.instance)
		assertEquals(0, root.stack.value.backStack.size)
	}

	@Test
	fun `signing out returns to onboarding`() = runTest(dispatcher) {
		every { globalSettingsStore.serverSettings } returns signedIn
		val root = newRoot()
		resumeLifecycle()
		scheduler.advanceUntilIdle()

		settingsUpdates.emit(null)
		scheduler.advanceUntilIdle()

		assertEquals(WearRoot.Destination.Onboarding, root.stack.value.active.instance)
	}

	@Test
	fun `signing out from the sync log also returns to onboarding`() = runTest(dispatcher) {
		every { globalSettingsStore.serverSettings } returns signedIn
		val root = newRoot()
		resumeLifecycle()
		scheduler.advanceUntilIdle()
		val projects = root.stack.value.active.instance as WearRoot.Destination.ProjectsDestination
		projects.component.showSyncLog()
		scheduler.advanceUntilIdle()
		assertInstanceOf(WearRoot.Destination.SyncLogDestination::class.java, root.stack.value.active.instance)

		settingsUpdates.emit(null)
		scheduler.advanceUntilIdle()

		assertEquals(WearRoot.Destination.Onboarding, root.stack.value.active.instance)
		assertEquals(0, root.stack.value.backStack.size)
	}

	@Test
	fun `leaving pairing keeps the request pending so a late reply still lands`() = runTest(dispatcher) {
		val root = newRoot()
		resumeLifecycle()
		root.showPairing()
		scheduler.advanceUntilIdle()

		root.onBack()
		scheduler.advanceUntilIdle()

		assertEquals(WearRoot.Destination.Onboarding, root.stack.value.active.instance)
		// The phone may still be showing its prompt. Abandoning here strands the session the server
		// mints when the user approves it, leaving the watch signed out with no way to recover it.
		assertEquals(PairingState.AwaitingConfirmation, phonePairing.state.value)
	}

	@Test
	fun `cancelling pairing abandons the pending request`() = runTest(dispatcher) {
		val root = newRoot()
		resumeLifecycle()
		root.showPairing()
		scheduler.advanceUntilIdle()

		val pairing = root.stack.value.active.instance as WearRoot.Destination.PairingDestination
		pairing.component.cancel()
		scheduler.advanceUntilIdle()

		assertEquals(WearRoot.Destination.Onboarding, root.stack.value.active.instance)
		assertEquals(PairingState.Idle, phonePairing.state.value)
	}

	@Test
	fun `pairing again after signing out asks the phone afresh`() = runTest(dispatcher) {
		val root = newRoot()
		resumeLifecycle()
		root.showPairing()
		scheduler.advanceUntilIdle()
		val firstRequest = sentRequests.single()
		phonePairing.onResponse(PairingProtocol.encodeResponse(PairResponse.Success(firstRequest.requestId, signedIn)))
		settingsUpdates.emit(signedIn)
		scheduler.advanceUntilIdle()
		settingsUpdates.emit(null)
		scheduler.advanceUntilIdle()

		root.showPairing()
		scheduler.advanceUntilIdle()

		assertEquals(2, sentRequests.size)
		assertEquals(PairingState.AwaitingConfirmation, phonePairing.state.value)
	}
}
