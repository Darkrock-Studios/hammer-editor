package components.serverreauthentication

import com.darkrockstudios.apps.hammer.base.http.MfaChallenge
import com.darkrockstudios.apps.hammer.base.http.MfaMethod
import com.darkrockstudios.apps.hammer.common.components.serverreauthentication.ServerReauthenticationComponent
import com.darkrockstudios.apps.hammer.common.data.account.AccountReauthUseCase
import com.darkrockstudios.apps.hammer.common.data.account.ReauthResult
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsStore
import com.darkrockstudios.apps.hammer.common.data.globalsettings.ServerSettings
import com.darkrockstudios.apps.hammer.common.data.toMsg
import com.darkrockstudios.apps.hammer.common.util.StrRes
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.dsl.bind
import org.koin.dsl.module
import utils.ComponentTest
import utils.TestStrRes
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ServerReauthenticationComponentTest : ComponentTest() {

	private lateinit var reauthUseCase: AccountReauthUseCase
	private var dismissed = false
	private var succeeded = false

	private val challenge = MfaChallenge(
		token = "challenge-token",
		methods = listOf(MfaMethod.TOTP, MfaMethod.RECOVERY),
	)

	@BeforeEach
	override fun setup() {
		super.setup()
		dismissed = false
		succeeded = false
		reauthUseCase = mockk(relaxed = true)

		val globalSettingsStore = mockk<GlobalSettingsStore>(relaxed = true)
		every { globalSettingsStore.serverSettings } returns ServerSettings(
			url = "example.com",
			email = "writer@example.com",
			userId = 1L,
			bearerToken = "stale",
			refreshToken = "stale",
		)

		setupKoin(
			module {
				single { globalSettingsStore } bind GlobalSettingsStore::class
				single { reauthUseCase } bind AccountReauthUseCase::class
				single<StrRes> { TestStrRes() }
			}
		)
		context.resume()
	}

	private fun newComponent() = ServerReauthenticationComponent(
		componentContext = context,
		dismissAuth = { dismissed = true },
		onReauthSuccess = { succeeded = true },
	)

	private fun ServerReauthenticationComponent.logIn(password: String = "secret") {
		updateServerPassword(password)
		reauthenticate(password)
	}

	@Test
	fun `The server identity is shown from the stored settings`() = runTest(mainTestDispatcher) {
		val component = newComponent()

		assertEquals("example.com", component.state.value.serverUrl)
		assertEquals("writer@example.com", component.state.value.serverEmail)
	}

	@Test
	fun `A successful reauthentication reports success`() = runTest(mainTestDispatcher) {
		coEvery { reauthUseCase.reauthenticate("secret") } returns ReauthResult.Success

		val component = newComponent()
		component.logIn()
		advanceUntilIdle()

		assertTrue(succeeded)
		assertFalse(component.state.value.serverWorking)
	}

	@Test
	fun `A wrong password shows the server message`() = runTest(mainTestDispatcher) {
		coEvery { reauthUseCase.reauthenticate(any()) } returns
			ReauthResult.Failure(displayMessage = "Bad password".toMsg(), exception = null)

		val component = newComponent()
		component.logIn("wrong")
		advanceUntilIdle()

		assertFalse(succeeded)
		assertEquals("Bad password", component.state.value.serverError)
		assertFalse(component.state.value.serverWorking)
	}

	@Test
	fun `A second-factor challenge shows the code step`() = runTest(mainTestDispatcher) {
		coEvery { reauthUseCase.reauthenticate(any()) } returns ReauthResult.MfaRequired(challenge)

		val component = newComponent()
		component.logIn()
		advanceUntilIdle()

		assertFalse(succeeded)
		assertEquals(challenge, component.state.value.mfaChallenge)
		assertNull(component.state.value.serverError)
		assertFalse(component.state.value.serverWorking)
	}

	@Test
	fun `Submitting a valid code completes the reauthentication`() = runTest(mainTestDispatcher) {
		coEvery { reauthUseCase.reauthenticate(any()) } returns ReauthResult.MfaRequired(challenge)
		coEvery { reauthUseCase.completeMfa("123456") } returns ReauthResult.Success

		val component = newComponent()
		component.logIn()
		advanceUntilIdle()
		component.submitMfaCode(" 123456 ")
		advanceUntilIdle()

		assertTrue(succeeded)
		assertNull(component.state.value.mfaChallenge)
		coVerify { reauthUseCase.completeMfa("123456") }
	}

	@Test
	fun `A refused code shows the message and keeps the code step`() = runTest(mainTestDispatcher) {
		coEvery { reauthUseCase.reauthenticate(any()) } returns ReauthResult.MfaRequired(challenge)
		coEvery { reauthUseCase.completeMfa(any()) } returns
			ReauthResult.MfaRequired(challenge, error = "That code did not work".toMsg())

		val component = newComponent()
		component.logIn()
		advanceUntilIdle()
		component.submitMfaCode("000000")
		advanceUntilIdle()

		assertFalse(succeeded)
		assertEquals("That code did not work", component.state.value.serverError)
		assertEquals(challenge, component.state.value.mfaChallenge)
		assertFalse(component.state.value.serverWorking)
	}

	@Test
	fun `A failure while completing the code returns to the password step`() = runTest(mainTestDispatcher) {
		coEvery { reauthUseCase.reauthenticate(any()) } returns ReauthResult.MfaRequired(challenge)
		coEvery { reauthUseCase.completeMfa(any()) } returns
			ReauthResult.Failure(displayMessage = "Account pending deletion".toMsg(), exception = null)

		val component = newComponent()
		component.logIn()
		advanceUntilIdle()
		component.submitMfaCode("123456")
		advanceUntilIdle()

		assertFalse(succeeded)
		assertNull(component.state.value.mfaChallenge)
		assertEquals("Account pending deletion", component.state.value.serverError)
	}

	@Test
	fun `Backing out of the second factor returns to the password step`() = runTest(mainTestDispatcher) {
		coEvery { reauthUseCase.reauthenticate(any()) } returns ReauthResult.MfaRequired(challenge)

		val component = newComponent()
		component.logIn()
		advanceUntilIdle()
		component.cancelMfa()

		assertNull(component.state.value.mfaChallenge)
		assertFalse(dismissed)
		verify { reauthUseCase.cancelMfa() }
	}

	@Test
	fun `Submitting a blank code does nothing`() = runTest(mainTestDispatcher) {
		coEvery { reauthUseCase.reauthenticate(any()) } returns ReauthResult.MfaRequired(challenge)

		val component = newComponent()
		component.logIn()
		advanceUntilIdle()
		component.submitMfaCode("   ")
		advanceUntilIdle()

		coVerify(exactly = 0) { reauthUseCase.completeMfa(any()) }
		assertEquals(challenge, component.state.value.mfaChallenge)
		assertFalse(component.state.value.serverWorking)
	}

	@Test
	fun `Submitting a code with no challenge pending does nothing`() = runTest(mainTestDispatcher) {
		val component = newComponent()
		component.submitMfaCode("123456")
		advanceUntilIdle()

		coVerify(exactly = 0) { reauthUseCase.completeMfa(any()) }
		assertFalse(component.state.value.serverWorking)
	}

	@Test
	fun `Cancelling the dialog dismisses it`() = runTest(mainTestDispatcher) {
		val component = newComponent()
		component.cancelReauthentication()

		assertTrue(dismissed)
	}
}
