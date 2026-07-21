package usecases

import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.base.http.TermsOfServiceChallenge
import com.darkrockstudios.apps.hammer.base.http.Token
import com.darkrockstudios.apps.hammer.common.data.ClientMessage
import com.darkrockstudios.apps.hammer.common.data.account.AccountUseCase
import com.darkrockstudios.apps.hammer.common.data.account.ServerSetupResult
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsStore
import com.darkrockstudios.apps.hammer.common.data.globalsettings.ServerSettings
import com.darkrockstudios.apps.hammer.common.dependencyinjection.updateCredentials
import com.darkrockstudios.apps.hammer.common.server.ServerAccountApi
import com.darkrockstudios.apps.hammer.common.server.TermsOfServiceRequiredException
import com.darkrockstudios.apps.hammer.common.util.StrRes
import com.darkrockstudios.apps.hammer.server_setup_error_unknown
import io.ktor.client.*
import io.ktor.client.plugins.auth.providers.*
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
}