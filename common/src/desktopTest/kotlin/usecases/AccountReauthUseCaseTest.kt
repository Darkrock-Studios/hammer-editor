package usecases

import com.darkrockstudios.apps.hammer.base.http.HttpResponseError
import com.darkrockstudios.apps.hammer.base.http.Token
import com.darkrockstudios.apps.hammer.common.data.ClientResult
import com.darkrockstudios.apps.hammer.common.data.account.AccountReauthUseCase
import com.darkrockstudios.apps.hammer.common.data.account.ServerSetupFailed
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsStore
import com.darkrockstudios.apps.hammer.common.data.globalsettings.ServerSettings
import com.darkrockstudios.apps.hammer.common.data.isFailure
import com.darkrockstudios.apps.hammer.common.data.isSuccess
import com.darkrockstudios.apps.hammer.common.dependencyinjection.updateCredentials
import com.darkrockstudios.apps.hammer.common.server.HttpFailureException
import com.darkrockstudios.apps.hammer.common.server.ServerAccountApi
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
import kotlin.test.assertTrue

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

	@BeforeEach
	override fun setup() {
		super.setup()
		MockKAnnotations.init(this, relaxUnitFun = true)
		mockkStatic("com.darkrockstudios.apps.hammer.common.dependencyinjection.HttpKt")
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

	@Test
	fun `reauthenticate stores the new tokens on success`() = runTest {
		val token = Token(userId = 1, auth = "new-auth", refresh = "new-refresh")
		every { globalSettingsStore.serverSettings } returns existingServer
		coEvery { accountApi.login(existingServer.email, "secret", "install-1") } returns Result.success(token)

		val credentialsSlot = slot<BearerTokens>()
		every { httpClient.updateCredentials(credentials = capture(credentialsSlot)) } just Runs
		val settingsSlot = slot<ServerSettings>()
		every { globalSettingsStore.updateServerSettings(settings = capture(settingsSlot)) } just Runs

		val result = createSut().reauthenticate("secret")

		assertTrue(isSuccess(result))
		coVerify { globalSettingsStore.updateServerSettings(any()) }
		assertEquals("new-auth", settingsSlot.captured.bearerToken)
		assertEquals("new-refresh", settingsSlot.captured.refreshToken)
		// Unchanged server fields are preserved.
		assertEquals(existingServer.url, settingsSlot.captured.url)
		assertEquals(existingServer.ssl, settingsSlot.captured.ssl)
		assertEquals("new-auth", credentialsSlot.captured.accessToken)
	}

	@Test
	fun `reauthenticate returns the server message on auth failure`() = runTest {
		every { globalSettingsStore.serverSettings } returns existingServer
		val failure = HttpFailureException(
			statusCode = HttpStatusCode.Unauthorized,
			error = HttpResponseError(error = "Unauthorized", displayMessage = "Bad password"),
		)
		coEvery { accountApi.login(any(), any(), any()) } returns Result.failure(failure)

		val result = createSut().reauthenticate("wrong")

		assertTrue(isFailure(result))
		val asFailure = assertIs<ClientResult.Failure<Unit>>(result)
		val exception = assertIs<ServerSetupFailed>(asFailure.exception)
		assertEquals("Bad password", exception.message)
		coVerify(exactly = 0) { globalSettingsStore.updateServerSettings(any()) }
	}

	@Test
	fun `reauthenticate falls back to Unknown error for non-http failures`() = runTest {
		every { globalSettingsStore.serverSettings } returns existingServer
		coEvery { accountApi.login(any(), any(), any()) } returns Result.failure(IOException())

		val result = createSut().reauthenticate("wrong")

		assertTrue(isFailure(result))
		val asFailure = assertIs<ClientResult.Failure<Unit>>(result)
		assertEquals("Unknown error", assertIs<ServerSetupFailed>(asFailure.exception).message)
	}

	@Test
	fun `reauthenticate throws when there is no server configured`() = runTest {
		every { globalSettingsStore.serverSettings } returns null

		assertFailsWith<IllegalStateException> {
			createSut().reauthenticate("secret")
		}
	}
}
