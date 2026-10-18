package usecases

import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.base.http.ApiErrorCode
import com.darkrockstudios.apps.hammer.base.http.HttpResponseError
import com.darkrockstudios.apps.hammer.base.http.MfaChallenge
import com.darkrockstudios.apps.hammer.base.http.MfaMethod
import com.darkrockstudios.apps.hammer.base.http.TermsOfServiceChallenge
import com.darkrockstudios.apps.hammer.base.http.Token
import com.darkrockstudios.apps.hammer.common.data.ClientMessage
import com.darkrockstudios.apps.hammer.common.data.ClientResult
import com.darkrockstudios.apps.hammer.common.data.account.AccountUseCase
import com.darkrockstudios.apps.hammer.common.data.account.ServerSetupResult
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsStore
import com.darkrockstudios.apps.hammer.common.data.globalsettings.ServerSettings
import com.darkrockstudios.apps.hammer.common.dependencyinjection.updateCredentials
import com.darkrockstudios.apps.hammer.common.server.HttpFailureException
import com.darkrockstudios.apps.hammer.common.server.MfaRequiredException
import com.darkrockstudios.apps.hammer.common.server.ServerAccountApi
import com.darkrockstudios.apps.hammer.common.server.TermsOfServiceRequiredException
import com.darkrockstudios.apps.hammer.common.util.StrRes
import com.darkrockstudios.apps.hammer.server_setup_error_unknown
import io.ktor.client.*
import io.ktor.client.plugins.auth.providers.*
import io.ktor.http.HttpStatusCode
import io.mockk.*
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.test.runTest
import okio.IOException
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import utils.BaseTest
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class AccountUseCaseTest : BaseTest() {

	@MockK
	private lateinit var globalSettingsStore: GlobalSettingsStore

	@MockK
	private lateinit var accountApi: ServerAccountApi

	@MockK
	private lateinit var httpClient: HttpClient

	@MockK
	private lateinit var strRes: StrRes

	@BeforeEach
	override fun setup() {
		super.setup()
		MockKAnnotations.init(this, relaxUnitFun = true)
		mockkStatic("com.darkrockstudios.apps.hammer.common.dependencyinjection.HttpKt")
		coEvery { strRes.get(any()) } returns "Mocked error message"
	}

	@AfterEach
	override fun tearDown() {
		super.tearDown()
		unmockkStatic("com.darkrockstudios.apps.hammer.common.dependencyinjection.HttpKt")
	}

	private fun createSut(installId: String = "test-uuid"): AccountUseCase {
		coEvery { globalSettingsStore.ensureInstallId() } returns installId
		return AccountUseCase(globalSettingsStore, accountApi, httpClient, strRes)
	}

	@Test
	fun `Create account successfully`() = runTest {
		val token = Token(1, "test-auth", "test-refresh")
		val settings = ServerSettings(
			url = "hammer.ink",
			email = "test@example.com",
			userId = 1,
			bearerToken = token.auth,
			refreshToken = token.refresh
		)

		coEvery { accountApi.createAccount(any(), any(), any()) } returns Result.success(token)
		coEvery { accountApi.login(any(), any(), any()) } returns Result.success(token)

		val bearerTokenSlot = slot<BearerTokens>()
		every { httpClient.updateCredentials(credentials = capture(bearerTokenSlot)) } just Runs

		val settingsSlot = slot<ServerSettings>()
		every { globalSettingsStore.updateServerSettings(settings = capture(settingsSlot)) } just Runs

		val usecase = createSut()

		val result = usecase.setupServer(
			url = settings.url,
			email = settings.email,
			password = "password",
			create = true
		)
		assertIs<ServerSetupResult.Success>(result)

		coVerify { accountApi.createAccount(settings.email, "password", "test-uuid", null) }
		coVerify(exactly = 0) { accountApi.login(any(), any(), any()) }

		assertEquals(token.auth, bearerTokenSlot.captured.accessToken)
		assertEquals(token.refresh, bearerTokenSlot.captured.refreshToken)

		coVerify { globalSettingsStore.updateServerSettings(any()) }
		assertEquals(
			token.auth,
			settingsSlot.captured.bearerToken
		)
		assertEquals(
			token.refresh,
			settingsSlot.captured.refreshToken
		)

		assertEquals(
			settings,
			settingsSlot.captured
		)
	}

	@Test
	fun `Create account failure`() = runTest {
		val token = Token(1, "test-auth", "test-refresh")
		val settings = ServerSettings(
			url = "hammer.ink",
			email = "test@example.com",
			userId = 1,
			bearerToken = token.auth,
			refreshToken = token.refresh
		)

		coEvery {
			accountApi.createAccount(
				any(),
				any(),
				any(),
				any()
			)
		} returns Result.failure(IOException())
		coEvery { accountApi.login(any(), any(), any()) } returns Result.failure(IOException())
		coEvery { strRes.get(Res.string.server_setup_error_unknown) } returns "Unknown error message"

		val usecase = createSut()

		val result = usecase.setupServer(
			url = settings.url,
			email = settings.email,
			password = "password",
			create = true
		)
		val failure = assertIs<ServerSetupResult.Failure>(result)
		// Non-HTTP failures fall back to the localized unknown-error message.
		val displayMessage = assertIs<ClientMessage.Literal>(failure.displayMessage)
		assertEquals("Unknown error message", displayMessage.text())

		coVerify { accountApi.createAccount(any(), any(), any(), any()) }
		coVerify(exactly = 0) { accountApi.login(any(), any(), any()) }
		coVerify(exactly = 0) { httpClient.updateCredentials(any()) }
		coVerify { globalSettingsStore.deleteServerSettings() }
	}

	@Test
	fun `Create account requiring terms of service`() = runTest {
		val challenge = TermsOfServiceChallenge(text = "Be excellent to each other", version = "v1")
		coEvery {
			accountApi.createAccount(any(), any(), any(), acceptedTosVersion = null)
		} returns Result.failure(TermsOfServiceRequiredException(challenge))

		val usecase = createSut()

		val result = usecase.setupServer(
			url = "hammer.ink",
			email = "test@example.com",
			password = "password",
			create = true,
		)

		val termsRequired = assertIs<ServerSetupResult.TermsRequired>(result)
		assertEquals(challenge, termsRequired.challenge)
		// The provisional server settings must survive so accepting the terms can retry.
		coVerify(exactly = 0) { globalSettingsStore.deleteServerSettings() }
	}

	@Test
	fun `Accepting terms of service resubmits with the accepted version`() = runTest {
		val token = Token(1, "test-auth", "test-refresh")
		coEvery {
			accountApi.createAccount(any(), any(), any(), acceptedTosVersion = "v1")
		} returns Result.success(token)
		every { httpClient.updateCredentials(any()) } just Runs

		val usecase = createSut()

		val result = usecase.setupServer(
			url = "hammer.ink",
			email = "test@example.com",
			password = "password",
			create = true,
			acceptedTosVersion = "v1",
		)

		assertIs<ServerSetupResult.Success>(result)
		coVerify { accountApi.createAccount("test@example.com", "password", "test-uuid", "v1") }
	}

	private val phoneSettings = ServerSettings(
		url = "hammer.ink",
		email = "test@example.com",
		userId = 1,
		bearerToken = "phone-auth",
		refreshToken = "phone-refresh",
	)

	@Test
	fun `Pairing another install returns its settings and leaves this install alone`() = runTest {
		every { globalSettingsStore.serverSettings } returns phoneSettings
		coEvery { accountApi.pairInstall("watch-install") } returns
			Result.success(Token(1, "watch-auth", "watch-refresh"))

		val result = createSut().pairInstall("watch-install")

		assertIs<ClientResult.Success<ServerSettings>>(result)
		assertEquals(
			phoneSettings.copy(bearerToken = "watch-auth", refreshToken = "watch-refresh"),
			result.data,
		)
		coVerify(exactly = 0) { httpClient.updateCredentials(any()) }
		coVerify(exactly = 0) { globalSettingsStore.updateServerSettings(any()) }
	}

	@Test
	fun `Pairing without a signed-in server fails without calling the server`() = runTest {
		every { globalSettingsStore.serverSettings } returns null

		val result = createSut().pairInstall("watch-install")

		assertIs<ClientResult.Failure<ServerSettings>>(result)
		coVerify(exactly = 0) { accountApi.pairInstall(any()) }
	}

	@Test
	fun `A rejected pairing reports a failure`() = runTest {
		every { globalSettingsStore.serverSettings } returns phoneSettings
		coEvery { accountApi.pairInstall(any()) } returns Result.failure(IOException())
		coEvery { strRes.get(Res.string.server_setup_error_unknown) } returns "Unknown error message"

		val result = createSut().pairInstall("watch-install")

		assertIs<ClientResult.Failure<ServerSettings>>(result)
		coVerify(exactly = 0) { globalSettingsStore.updateServerSettings(any()) }
	}

	@Test
	fun `Applying paired settings adopts the session and stores it`() = runTest {
		val watchSettings = phoneSettings.copy(bearerToken = "watch-auth", refreshToken = "watch-refresh")
		val bearerTokenSlot = slot<BearerTokens>()
		every { httpClient.updateCredentials(credentials = capture(bearerTokenSlot)) } just Runs
		val settingsSlot = slot<ServerSettings>()
		every { globalSettingsStore.updateServerSettings(settings = capture(settingsSlot)) } just Runs

		val result = createSut().applyPairedSettings(watchSettings)

		assertIs<ClientResult.Success<Unit>>(result)
		assertEquals("watch-auth", bearerTokenSlot.captured.accessToken)
		assertEquals("watch-refresh", bearerTokenSlot.captured.refreshToken)
		assertEquals(watchSettings, settingsSlot.captured)
	}

	@Test
	fun `Paired settings without a session are refused and nothing is stored`() = runTest {
		val result = createSut().applyPairedSettings(phoneSettings.copy(bearerToken = null))

		assertIs<ClientResult.Failure<Unit>>(result)
		coVerify(exactly = 0) { httpClient.updateCredentials(any()) }
		coVerify(exactly = 0) { globalSettingsStore.updateServerSettings(any()) }
	}

	@Test
	fun `A plaintext server is stored as plaintext`() = runTest {
		val token = Token(1, "test-auth", "test-refresh")
		coEvery { accountApi.login(any(), any(), any()) } returns Result.success(token)
		every { httpClient.updateCredentials(any()) } just Runs
		val settingsSlot = slot<ServerSettings>()
		every { globalSettingsStore.updateServerSettings(settings = capture(settingsSlot)) } just Runs

		val result = createSut().setupServer(
			url = "192.168.1.50:8080",
			email = "writer@example.com",
			password = "password",
			create = false,
			ssl = false,
		)

		assertIs<ServerSetupResult.Success>(result)
		assertEquals(false, settingsSlot.captured.ssl)
		assertEquals("192.168.1.50:8080", settingsSlot.captured.url)
	}

	@Test
	fun `Login account successfully`() = runTest {
		val token = Token(1, "test-auth", "test-refresh")
		val settings = ServerSettings(
			url = "hammer.ink",
			email = "test@example.com",
			userId = 1,
			bearerToken = token.auth,
			refreshToken = token.refresh
		)

		coEvery { accountApi.createAccount(any(), any(), any()) } returns Result.success(token)
		coEvery { accountApi.login(any(), any(), any()) } returns Result.success(token)

		val bearerTokenSlot = slot<BearerTokens>()
		every { httpClient.updateCredentials(credentials = capture(bearerTokenSlot)) } just Runs

		val settingsSlot = slot<ServerSettings>()
		every { globalSettingsStore.updateServerSettings(settings = capture(settingsSlot)) } just Runs

		val usecase = createSut()

		val result = usecase.setupServer(
			url = settings.url,
			email = settings.email,
			password = "password",
			create = false
		)
		assertIs<ServerSetupResult.Success>(result)

		coVerify(exactly = 0) { accountApi.createAccount(any(), any(), any(), any()) }
		coVerify { accountApi.login(settings.email, "password", "test-uuid") }

		assertEquals(token.auth, bearerTokenSlot.captured.accessToken)
		assertEquals(token.refresh, bearerTokenSlot.captured.refreshToken)

		coVerify { globalSettingsStore.updateServerSettings(any()) }
		assertEquals(
			token.auth,
			settingsSlot.captured.bearerToken
		)
		assertEquals(
			token.refresh,
			settingsSlot.captured.refreshToken
		)

		assertEquals(
			settings,
			settingsSlot.captured
		)
	}

	private val challenge = MfaChallenge(
		token = "challenge-token",
		methods = listOf(MfaMethod.TOTP, MfaMethod.RECOVERY),
	)

	private fun loginNeedsSecondFactor() {
		coEvery { accountApi.login(any(), any(), any()) } returns Result.failure(MfaRequiredException(challenge))
	}

	private fun mfaFailure(errorCode: String, message: String) = Result.failure<Token>(
		HttpFailureException(
			statusCode = HttpStatusCode.Unauthorized,
			error = HttpResponseError(error = "Failed", displayMessage = message, errorCode = errorCode),
		)
	)

	private suspend fun AccountUseCase.logInToMfaAccount() = setupServer(
		url = "hammer.ink",
		email = "test@example.com",
		password = "password",
		create = false,
	)

	@Test
	fun `Login needing a second factor returns the challenge and keeps the provisional settings`() = runTest {
		loginNeedsSecondFactor()
		val settingsSlot = slot<ServerSettings>()
		every { globalSettingsStore.updateServerSettings(settings = capture(settingsSlot)) } just Runs

		val result = createSut().logInToMfaAccount()

		val mfaRequired = assertIs<ServerSetupResult.MfaRequired>(result)
		assertEquals(challenge, mfaRequired.challenge)
		assertNull(mfaRequired.error)
		assertNull(settingsSlot.captured.bearerToken)
		coVerify(exactly = 0) { globalSettingsStore.deleteServerSettings() }
		coVerify(exactly = 0) { httpClient.updateCredentials(any()) }
	}

	@Test
	fun `Completing the second factor stores the token`() = runTest {
		loginNeedsSecondFactor()
		val token = Token(1, "test-auth", "test-refresh")
		coEvery { accountApi.loginMfa("challenge-token", "123456", "test-uuid") } returns Result.success(token)
		val bearerTokenSlot = slot<BearerTokens>()
		every { httpClient.updateCredentials(credentials = capture(bearerTokenSlot)) } just Runs
		val settingsSlot = slot<ServerSettings>()
		every { globalSettingsStore.updateServerSettings(settings = capture(settingsSlot)) } just Runs

		val usecase = createSut()
		usecase.logInToMfaAccount()
		val result = usecase.completeMfa("123456")

		assertIs<ServerSetupResult.Success>(result)
		assertEquals(token.auth, bearerTokenSlot.captured.accessToken)
		assertEquals(
			ServerSettings(
				url = "hammer.ink",
				email = "test@example.com",
				userId = 1,
				bearerToken = token.auth,
				refreshToken = token.refresh,
			),
			settingsSlot.captured,
		)
	}

	@Test
	fun `A wrong code keeps the challenge open with the server message`() = runTest {
		loginNeedsSecondFactor()
		coEvery { accountApi.loginMfa(any(), "000000", any()) } returns
			mfaFailure(ApiErrorCode.MFA_CODE_INVALID, "That code did not work")
		coEvery { accountApi.loginMfa(any(), "123456", any()) } returns
			Result.success(Token(1, "test-auth", "test-refresh"))
		every { httpClient.updateCredentials(any()) } just Runs

		val usecase = createSut()
		usecase.logInToMfaAccount()
		val rejected = usecase.completeMfa("000000")

		val stillRequired = assertIs<ServerSetupResult.MfaRequired>(rejected)
		assertEquals(challenge, stillRequired.challenge)
		assertEquals("That code did not work", assertIs<ClientMessage.Literal>(stillRequired.error).text())
		coVerify(exactly = 0) { globalSettingsStore.deleteServerSettings() }

		assertIs<ServerSetupResult.Success>(usecase.completeMfa("123456"))
		coVerify(exactly = 2) { accountApi.loginMfa("challenge-token", any(), "test-uuid") }
	}

	@Test
	fun `A failure other than a wrong code abandons the sign-in`() = runTest {
		loginNeedsSecondFactor()
		coEvery { accountApi.loginMfa(any(), any(), any()) } returns
			mfaFailure(ApiErrorCode.ACCOUNT_PENDING_DELETION, "Account pending deletion")

		val usecase = createSut()
		usecase.logInToMfaAccount()
		val result = usecase.completeMfa("123456")

		val failure = assertIs<ServerSetupResult.Failure>(result)
		assertEquals("Account pending deletion", assertIs<ClientMessage.Literal>(failure.displayMessage).text())
		coVerify { globalSettingsStore.deleteServerSettings() }

		// The challenge is gone, so a retry never reaches the server.
		assertIs<ServerSetupResult.Failure>(usecase.completeMfa("123456"))
		coVerify(exactly = 1) { accountApi.loginMfa(any(), any(), any()) }
	}

	@Test
	fun `Completing the second factor with no sign-in pending fails without calling the server`() = runTest {
		val result = createSut().completeMfa("123456")

		assertIs<ServerSetupResult.Failure>(result)
		coVerify(exactly = 0) { accountApi.loginMfa(any(), any(), any()) }
		coVerify(exactly = 0) { globalSettingsStore.deleteServerSettings() }
	}

	@Test
	fun `Cancelling the second factor forgets the challenge`() = runTest {
		loginNeedsSecondFactor()

		val usecase = createSut()
		usecase.logInToMfaAccount()
		usecase.cancelMfa()

		assertIs<ServerSetupResult.Failure>(usecase.completeMfa("123456"))
		coVerify(exactly = 0) { accountApi.loginMfa(any(), any(), any()) }
		coVerify(exactly = 0) { globalSettingsStore.deleteServerSettings() }
	}

	@Test
	fun `A new login forgets an earlier challenge`() = runTest {
		loginNeedsSecondFactor()
		val usecase = createSut()
		usecase.logInToMfaAccount()

		coEvery { accountApi.login(any(), any(), any()) } returns Result.failure(IOException())
		assertIs<ServerSetupResult.Failure>(usecase.logInToMfaAccount())

		assertIs<ServerSetupResult.Failure>(usecase.completeMfa("123456"))
		coVerify(exactly = 0) { accountApi.loginMfa(any(), any(), any()) }
	}
}
