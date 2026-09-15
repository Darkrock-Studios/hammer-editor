package com.darkrockstudios.apps.hammer.wear.components

import com.darkrockstudios.apps.hammer.common.data.account.AccountUseCase
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsStore
import com.darkrockstudios.apps.hammer.common.data.globalsettings.ServerSettings
import com.darkrockstudios.apps.hammer.common.data.pairing.PairRequest
import com.darkrockstudios.apps.hammer.wear.FakeStrRes
import com.darkrockstudios.apps.hammer.wear.WearTestBase
import com.darkrockstudios.apps.hammer.wear.pairing.PairingState
import com.darkrockstudios.apps.hammer.wear.pairing.PhonePairingClient
import com.darkrockstudios.apps.hammer.wear.pairing.PhonePairingUseCase
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
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
		phonePairing = PhonePairingUseCase(client, globalSettingsStore, mockk<AccountUseCase>(relaxed = true))
	}

	private fun newRoot() = WearRootComponent(
		componentContext = componentContext,
		globalSettingsStore = globalSettingsStore,
		phonePairing = phonePairing,
		accountUseCase = mockk(relaxed = true),
		strRes = FakeStrRes(),
		deviceLabel = "Pixel Watch",
	)

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
	fun `leaving pairing abandons the pending request`() = runTest(dispatcher) {
		val root = newRoot()
		resumeLifecycle()
		root.showPairing()
		scheduler.advanceUntilIdle()

		root.onBack()
		scheduler.advanceUntilIdle()

		assertEquals(WearRoot.Destination.Onboarding, root.stack.value.active.instance)
		assertEquals(PairingState.Idle, phonePairing.state.value)
	}
}
