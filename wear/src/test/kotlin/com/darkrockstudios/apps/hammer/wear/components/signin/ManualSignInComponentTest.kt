package com.darkrockstudios.apps.hammer.wear.components.signin

import com.darkrockstudios.apps.hammer.base.http.TermsOfServiceChallenge
import com.darkrockstudios.apps.hammer.common.data.account.AccountUseCase
import com.darkrockstudios.apps.hammer.common.data.account.ServerSetupResult
import com.darkrockstudios.apps.hammer.common.data.toMsg
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

	@BeforeEach
	override fun setUp() {
		super.setUp()
		accountUseCase = mockk()
	}

	private fun newComponent() = ManualSignInComponent(
		componentContext = componentContext,
		accountUseCase = accountUseCase,
		strRes = FakeStrRes(),
	).also { resumeLifecycle() }

	private fun ManualSignInComponent.fillIn() {
		updateServer(" hammer.ink ")
		updateEmail("writer@example.com")
		updatePassword("hunter2")
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
