package usecases

import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.base.http.ApiErrorCode
import com.darkrockstudios.apps.hammer.base.http.HttpResponseError
import com.darkrockstudios.apps.hammer.base.http.MfaChallenge
import com.darkrockstudios.apps.hammer.base.http.MfaMethod
import com.darkrockstudios.apps.hammer.base.http.Token
import com.darkrockstudios.apps.hammer.common.data.ClientMessage
import com.darkrockstudios.apps.hammer.common.data.account.AccountReauthUseCase
import com.darkrockstudios.apps.hammer.common.data.account.ReauthResult
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsStore
import com.darkrockstudios.apps.hammer.common.data.globalsettings.ServerSettings
import com.darkrockstudios.apps.hammer.common.dependencyinjection.updateCredentials
import com.darkrockstudios.apps.hammer.common.server.HttpFailureException
import com.darkrockstudios.apps.hammer.common.server.MfaRequiredException
import com.darkrockstudios.apps.hammer.common.server.ServerAccountApi
import com.darkrockstudios.apps.hammer.server_setup_error_unknown
import io.ktor.client.HttpClient
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.http.HttpStatusCode
import io.mockk.MockKAnnotations
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.just
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import kotlinx.coroutines.test.runTest
import okio.IOException
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import utils.BaseTest
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull

class AccountReauthUseCaseTest : BaseTest() {

	@MockK
	private lateinit var globalSettingsStore: GlobalSettingsStore

	@MockK
	private lateinit var accountApi: ServerAccountApi

	@MockK
	private lateinit var httpClient: HttpClient

	private val existingServer = ServerSettings(
		ssl = true,
		url = "hammer.ink",
		email = "test@example.com",
		userId = 1,
		bearerToken = "old-auth",
		refreshToken = "old-refresh",
	)

	private val challenge = MfaChallenge(
		token = "challenge-token",
		methods = listOf(MfaMethod.TOTP, MfaMethod.RECOVERY),
	)

	@BeforeEach
	override fun setup() {
		super.setup()
		MockKAnnotations.init(this, relaxUnitFun = true)
		mockkStatic("com.darkrockstudios.apps.hammer.common.dependencyinjection.HttpKt")
		every { globalSettingsStore.serverSettings } returns existingServer
	}

	@AfterEach
	override fun tearDown() {
		super.tearDown()
		unmockkStatic("com.darkrockstudios.apps.hammer.common.dependencyinjection.HttpKt")
	}

	private fun createSut(installId: String = "install-1"): AccountReauthUseCase {
		coEvery { globalSettingsStore.ensureInstallId() } returns installId
		return AccountReauthUseCase(globalSettingsStore, accountApi, httpClient)
	}

	private fun httpFailure(message: String, errorCode: String? = null) = Result.failure<Token>(
		HttpFailureException(
			statusCode = HttpStatusCode.Unauthorized,
			error = HttpResponseError(error = "Unauthorized", displayMessage = message, errorCode = errorCode),
		)
	)

	private fun loginNeedsSecondFactor() {
		coEvery { accountApi.login(any(), any(), any()) } returns Result.failure(MfaRequiredException(challenge))
	}

	@Test
	fun `reauthenticate stores the new tokens on success`() = runTest {
		val token = Token(userId = 1, auth = "new-auth", refresh = "new-refresh")
		coEvery { accountApi.login(existingServer.email, "secret", "install-1") } returns Result.success(token)

		val credentialsSlot = slot<BearerTokens>()
		every { httpClient.updateCredentials(credentials = capture(credentialsSlot)) } just Runs
		val settingsSlot = slot<ServerSettings>()
		every { globalSettingsStore.updateServerSettings(settings = capture(settingsSlot)) } just Runs

		val result = createSut().reauthenticate("secret")

		assertIs<ReauthResult.Success>(result)
		coVerify { globalSettingsStore.updateServerSettings(any()) }
		assertEquals("new-auth", settingsSlot.captured.bearerToken)
		assertEquals("new-refresh", settingsSlot.captured.refreshToken)
		// Unchanged server fields are preserved.
		assertEquals(existingServer.url, settingsSlot.captured.url)
		assertEquals(existingServer.ssl, settingsSlot.captured.ssl)
		assertEquals("new-auth", credentialsSlot.captured.accessToken)
		assertEquals("new-refresh", credentialsSlot.captured.refreshToken)
	}

	@Test
	fun `reauthenticate returns the server message on auth failure`() = runTest {
		coEvery { accountApi.login(any(), any(), any()) } returns httpFailure("Bad password")

		val result = createSut().reauthenticate("wrong")

		val failure = assertIs<ReauthResult.Failure>(result)
		assertEquals("Bad password", assertIs<ClientMessage.Literal>(failure.displayMessage).text())
		coVerify(exactly = 0) { globalSettingsStore.updateServerSettings(any()) }
	}

	@Test
	fun `reauthenticate falls back to the unknown error message for non-http failures`() = runTest {
		coEvery { accountApi.login(any(), any(), any()) } returns Result.failure(IOException())

		val result = createSut().reauthenticate("wrong")

		val failure = assertIs<ReauthResult.Failure>(result)
		val message = assertIs<ClientMessage.Resource>(failure.displayMessage)
		assertEquals(Res.string.server_setup_error_unknown, message.getStringResource())
	}

	@Test
	fun `reauthenticate throws when there is no server configured`() = runTest {
		every { globalSettingsStore.serverSettings } returns null

		assertFailsWith<IllegalStateException> {
			createSut().reauthenticate("secret")
		}
	}

	@Test
	fun `reauthenticate needing a second factor returns the challenge without touching the session`() = runTest {
		loginNeedsSecondFactor()

		val result = createSut().reauthenticate("secret")

		val mfaRequired = assertIs<ReauthResult.MfaRequired>(result)
		assertEquals(challenge, mfaRequired.challenge)
		assertNull(mfaRequired.error)
		coVerify(exactly = 0) { globalSettingsStore.updateServerSettings(any()) }
		coVerify(exactly = 0) { httpClient.updateCredentials(any()) }
	}

	@Test
	fun `completing the second factor stores the new tokens`() = runTest {
		loginNeedsSecondFactor()
		val token = Token(userId = 1, auth = "new-auth", refresh = "new-refresh")
		coEvery { accountApi.loginMfa("challenge-token", "123456", "install-1") } returns Result.success(token)
		val credentialsSlot = slot<BearerTokens>()
		every { httpClient.updateCredentials(credentials = capture(credentialsSlot)) } just Runs
		val settingsSlot = slot<ServerSettings>()
		every { globalSettingsStore.updateServerSettings(settings = capture(settingsSlot)) } just Runs

		val usecase = createSut()
		usecase.reauthenticate("secret")
		val result = usecase.completeMfa("123456")

		assertIs<ReauthResult.Success>(result)
		assertEquals("new-auth", credentialsSlot.captured.accessToken)
		assertEquals(existingServer.copy(bearerToken = "new-auth", refreshToken = "new-refresh"), settingsSlot.captured)
	}

	@Test
	fun `a wrong code keeps the challenge open with the server message`() = runTest {
		loginNeedsSecondFactor()
		coEvery { accountApi.loginMfa(any(), "000000", any()) } returns
			httpFailure("That code did not work", ApiErrorCode.MFA_CODE_INVALID)
		coEvery { accountApi.loginMfa(any(), "123456", any()) } returns
			Result.success(Token(userId = 1, auth = "new-auth", refresh = "new-refresh"))
		every { httpClient.updateCredentials(any()) } just Runs

		val usecase = createSut()
		usecase.reauthenticate("secret")
		val rejected = usecase.completeMfa("000000")

		val stillRequired = assertIs<ReauthResult.MfaRequired>(rejected)
		assertEquals(challenge, stillRequired.challenge)
		assertEquals("That code did not work", assertIs<ClientMessage.Literal>(stillRequired.error).text())

		assertIs<ReauthResult.Success>(usecase.completeMfa("123456"))
		coVerify(exactly = 2) { accountApi.loginMfa("challenge-token", any(), "install-1") }
	}

	@Test
	fun `a failure other than a wrong code abandons the challenge`() = runTest {
		loginNeedsSecondFactor()
		coEvery { accountApi.loginMfa(any(), any(), any()) } returns
			httpFailure("Account pending deletion", ApiErrorCode.ACCOUNT_PENDING_DELETION)

		val usecase = createSut()
		usecase.reauthenticate("secret")
		val result = usecase.completeMfa("123456")

		val failure = assertIs<ReauthResult.Failure>(result)
		assertEquals("Account pending deletion", assertIs<ClientMessage.Literal>(failure.displayMessage).text())

		assertIs<ReauthResult.Failure>(usecase.completeMfa("123456"))
		coVerify(exactly = 1) { accountApi.loginMfa(any(), any(), any()) }
	}

	@Test
	fun `completing the second factor with nothing pending fails without calling the server`() = runTest {
		val result = createSut().completeMfa("123456")

		assertIs<ReauthResult.Failure>(result)
		coVerify(exactly = 0) { accountApi.loginMfa(any(), any(), any()) }
	}

	@Test
	fun `cancelling the second factor forgets the challenge`() = runTest {
		loginNeedsSecondFactor()

		val usecase = createSut()
		usecase.reauthenticate("secret")
		usecase.cancelMfa()

		assertIs<ReauthResult.Failure>(usecase.completeMfa("123456"))
		coVerify(exactly = 0) { accountApi.loginMfa(any(), any(), any()) }
	}
}
