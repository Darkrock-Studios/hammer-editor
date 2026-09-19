package com.darkrockstudios.apps.hammer.wear.components.signin

import com.darkrockstudios.apps.hammer.base.http.TermsOfServiceChallenge
import com.darkrockstudios.apps.hammer.common.data.account.AccountUseCase
import com.darkrockstudios.apps.hammer.common.data.account.ServerSetupResult
import com.darkrockstudios.apps.hammer.common.data.toMsg
import com.darkrockstudios.apps.hammer.wear.FakeLocalNetworkAccess
import com.darkrockstudios.apps.hammer.wear.FakeStrRes
import com.darkrockstudios.apps.hammer.wear.WearTestBase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ManualSignInComponentTest : WearTestBase() {

	private lateinit var accountUseCase: AccountUseCase
	private lateinit var localNetwork: FakeLocalNetworkAccess

	@BeforeEach
	override fun setUp() {
		super.setUp()
		accountUseCase = mockk()
		localNetwork = FakeLocalNetworkAccess()
	}

	private fun newComponent() = ManualSignInComponent(
		componentContext = componentContext,
		accountUseCase = accountUseCase,
		localNetworkAccess = localNetwork,
		strRes = FakeStrRes(),
	).also { resumeLifecycle() }

	private fun ManualSignInComponent.fillIn() {
		updateServer(" hammer.ink ")
		updateEmail("writer@example.com")
		updatePassword("hunter2")
	}

	@Test
	fun `the watch keyboard's capitals are folded away`() = runTest(dispatcher) {
		coEvery { accountUseCase.setupServer(any(), any(), any(), any(), any()) } returns ServerSetupResult.Success
		val component = newComponent()
		// What the Wear RemoteInput keyboard hands back when it capitalises the first letter.
		component.updateServer("Hammer.ink")
		component.updateEmail("Writer@example.com")
		component.updatePassword("hunter2")

		assertEquals("hammer.ink", component.state.value.server)
		assertEquals("writer@example.com", component.state.value.email)

		component.signIn()
		scheduler.advanceUntilIdle()

		// The password is passed through untouched; only the user can fix a capital there.
		coVerify { accountUseCase.setupServer("hammer.ink", "writer@example.com", "hunter2", false, null) }
	}

	@Test
	fun `a local server the watch may not reach yet waits for the prompt before logging in`() =
		runTest(dispatcher) {
			localNetwork.block("192.168.1.46:8081")
			val component = newComponent()
			component.updateServer("http://192.168.1.46:8081")
			component.updateEmail("writer@example.com")
			component.updatePassword("hunter2")

			component.signIn()
			scheduler.advanceUntilIdle()

			// The login is the first request, and a blocked one would only time out.
			assertTrue(component.state.value.localNetworkBlocked)
			assertFalse(component.state.value.busy)
			coVerify(exactly = 0) { accountUseCase.setupServer(any(), any(), any(), any(), any(), any()) }
		}

	@Test
	fun `granting local network access carries on with the sign in`() = runTest(dispatcher) {
		coEvery { accountUseCase.setupServer(any(), any(), any(), any(), any(), any()) } returns
			ServerSetupResult.Success
		localNetwork.block("192.168.1.46:8081")
		val component = newComponent()
		component.updateServer("http://192.168.1.46:8081")
		component.updateEmail("writer@example.com")
		component.updatePassword("hunter2")
		component.signIn()
		scheduler.advanceUntilIdle()

		localNetwork.grant()
		component.onLocalNetworkPermissionResult()
		scheduler.advanceUntilIdle()

		assertFalse(component.state.value.localNetworkBlocked)
		coVerify(exactly = 1) {
			accountUseCase.setupServer("192.168.1.46:8081", "writer@example.com", "hunter2", false, null, false)
		}
	}

	@Test
	fun `refusing local network access explains why the sign in stopped`() = runTest(dispatcher) {
		localNetwork.block("192.168.1.46:8081")
		val component = newComponent()
		component.updateServer("http://192.168.1.46:8081")
		component.updateEmail("writer@example.com")
		component.updatePassword("hunter2")
		component.signIn()
		scheduler.advanceUntilIdle()

		component.onLocalNetworkPermissionResult()
		scheduler.advanceUntilIdle()

		assertEquals(ManualSignIn.SignInError.LocalNetworkDenied, component.state.value.error)
		coVerify(exactly = 0) { accountUseCase.setupServer(any(), any(), any(), any(), any(), any()) }
	}

	@Test
	fun `signing in with a field missing asks for it without calling the server`() = runTest(dispatcher) {
		val component = newComponent()
		component.updateServer("hammer.ink")
		component.updateEmail("writer@example.com")

		component.signIn()
		scheduler.advanceUntilIdle()

		assertEquals(ManualSignIn.SignInError.MissingFields, component.state.value.error)
		coVerify(exactly = 0) { accountUseCase.setupServer(any(), any(), any(), any(), any()) }
	}

	@Test
	fun `a successful sign in logs in rather than creating an account`() = runTest(dispatcher) {
		coEvery { accountUseCase.setupServer(any(), any(), any(), any(), any()) } returns ServerSetupResult.Success
		val component = newComponent()
		component.fillIn()

		component.signIn()
		scheduler.advanceUntilIdle()

		coVerify { accountUseCase.setupServer("hammer.ink", "writer@example.com", "hunter2", false, null) }
		assertFalse(component.state.value.busy)
		assertEquals(null, component.state.value.error)
	}

	@Test
	fun `an http server is signed in to in the clear`() = runTest(dispatcher) {
		coEvery { accountUseCase.setupServer(any(), any(), any(), any(), any(), any()) } returns
			ServerSetupResult.Success
		val component = newComponent()
		component.updateServer("http://192.168.1.50:8080")
		component.updateEmail("writer@example.com")
		component.updatePassword("hunter2")

		component.signIn()
		scheduler.advanceUntilIdle()

		coVerify {
			accountUseCase.setupServer("192.168.1.50:8080", "writer@example.com", "hunter2", false, null, false)
		}
	}

	@Test
	fun `an http server is flagged as insecure and an https one is not`() = runTest(dispatcher) {
		val component = newComponent()

		component.updateServer("http://192.168.1.50:8080")
		assertTrue(component.state.value.serverInsecure)

		component.updateServer("hammer.ink")
		assertFalse(component.state.value.serverInsecure)

		component.updateServer("https://hammer.ink")
		assertFalse(component.state.value.serverInsecure)
	}

	@Test
	fun `the password is never exposed in state`() = runTest(dispatcher) {
		val component = newComponent()

		component.updatePassword("hunter2")

		assertTrue(component.state.value.hasPassword)
		assertFalse(component.state.value.toString().contains("hunter2"))
	}

	@Test
	fun `a server that needs its terms accepted is reported as such`() = runTest(dispatcher) {
		coEvery { accountUseCase.setupServer(any(), any(), any(), any(), any()) } returns
			ServerSetupResult.TermsRequired(TermsOfServiceChallenge(text = "Be kind", version = "v1"))
		val component = newComponent()
		component.fillIn()

		component.signIn()
		scheduler.advanceUntilIdle()

		assertEquals(ManualSignIn.SignInError.TermsRequired, component.state.value.error)
	}

	@Test
	fun `a rejected sign in shows the server's message`() = runTest(dispatcher) {
		coEvery { accountUseCase.setupServer(any(), any(), any(), any(), any()) } returns
			ServerSetupResult.Failure(displayMessage = "Invalid credentials".toMsg(), exception = null)
		val component = newComponent()
		component.fillIn()

		component.signIn()
		scheduler.advanceUntilIdle()

		assertEquals(ManualSignIn.SignInError.Message("Invalid credentials"), component.state.value.error)
		assertFalse(component.state.value.busy)
	}
}
