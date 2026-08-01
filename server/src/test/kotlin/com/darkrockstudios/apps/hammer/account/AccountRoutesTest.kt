package com.darkrockstudios.apps.hammer.account

import com.darkrockstudios.apps.hammer.admin.AdminComponent
import com.darkrockstudios.apps.hammer.admin.ConfigRepository
import com.darkrockstudios.apps.hammer.admin.ServerConfigKey
import com.darkrockstudios.apps.hammer.admin.WhiteListRepository
import com.darkrockstudios.apps.hammer.base.BuildMetadata
import com.darkrockstudios.apps.hammer.base.http.HAMMER_PROTOCOL_HEADER
import com.darkrockstudios.apps.hammer.base.http.HAMMER_PROTOCOL_VERSION
import com.darkrockstudios.apps.hammer.base.http.HEADER_CLIENT_VERSION
import com.darkrockstudios.apps.hammer.base.http.HTTP_STATUS_TERMS_OF_SERVICE
import com.darkrockstudios.apps.hammer.base.http.TermsOfServiceChallenge
import com.darkrockstudios.apps.hammer.base.http.Token
import com.darkrockstudios.apps.hammer.base.http.createJsonSerializer
import com.darkrockstudios.apps.hammer.plugins.configureLocalization
import com.darkrockstudios.apps.hammer.plugins.configureRouting
import com.darkrockstudios.apps.hammer.plugins.configureSecurity
import com.darkrockstudios.apps.hammer.plugins.configureSerialization
import com.darkrockstudios.apps.hammer.project.ProjectEntityRepository
import com.darkrockstudios.apps.hammer.project.ServerProjectDataRepository
import com.darkrockstudios.apps.hammer.project.ServerWritingActivityRepository
import com.darkrockstudios.apps.hammer.project.access.ProjectAccessRepository
import com.darkrockstudios.apps.hammer.projects.ProjectsRepository
import com.darkrockstudios.apps.hammer.story.StoryRendererService
import com.darkrockstudios.apps.hammer.utilities.MarkdownService
import com.darkrockstudios.apps.hammer.utilities.SResult
import com.darkrockstudios.apps.hammer.utils.BaseTest
import com.darkrockstudios.apps.hammer.utils.setupKtorTestKoin
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AccountRoutesTest : BaseTest() {
	@MockK
	private lateinit var accountsRepository: AccountsRepository

	@MockK
	private lateinit var projectEntityRepository: ProjectEntityRepository

	@MockK
	private lateinit var projectAccessRepository: ProjectAccessRepository

	@MockK(relaxed = true)
	private lateinit var serverWritingActivityRepository: ServerWritingActivityRepository

	@MockK(relaxed = true)
	private lateinit var serverProjectDataRepository: ServerProjectDataRepository

	@MockK
	private lateinit var projectsRepository: ProjectsRepository

	@MockK
	private lateinit var accountsComponent: AccountsComponent

	@MockK
	private lateinit var adminComponent: AdminComponent

	@MockK
	private lateinit var whiteListRepository: WhiteListRepository

	@MockK
	private lateinit var configRepository: ConfigRepository

	@MockK
	private lateinit var storyRendererService: StoryRendererService

	@MockK
	private lateinit var penNameService: PenNameService

	@MockK
	private lateinit var passwordResetRepository: PasswordResetRepository

	@MockK
	private lateinit var bioService: BioService

	@MockK
	private lateinit var markdownService: MarkdownService

	private lateinit var testModule: org.koin.core.module.Module

	private lateinit var json: Json

	private val USER_ID = 0L
	private val INSTALL_ID = "install-id"

	@BeforeEach
	override fun setup() {
		super.setup()

		MockKAnnotations.init(this)

		json = createJsonSerializer()
		coEvery { configRepository.get(any<ServerConfigKey<*>>()) } returns "en"

		testModule = module {
			single { accountsRepository }
			single { projectEntityRepository }
			single { projectAccessRepository }
			single { serverWritingActivityRepository }
			single { serverProjectDataRepository }
			single { projectsRepository }
			single { accountsComponent }
			single { adminComponent }
			single { whiteListRepository }
			single { configRepository }
			single { storyRendererService }
			single { penNameService }
			single { json }
			single { passwordResetRepository }
			single { bioService }
			single { markdownService }
			single { mockk<com.darkrockstudios.apps.hammer.review.ReviewRepository>(relaxed = true) }
			single { mockk<com.darkrockstudios.apps.hammer.storyideas.ServerIdeasRepository>(relaxed = true) }
			single { mockk<com.darkrockstudios.apps.hammer.database.ProjectDao>(relaxed = true) }
			single { mockk<AccountDeletionService>(relaxed = true) }
		}
	}

	@Test
	fun `Account - Refresh Token - No User`() = testApplication {
		coEvery {
			accountsComponent.refreshToken(USER_ID, any(), any())
		} returns SResult.failure("No valid token not found", null)

		val mockRefreshToken = "invalid_refresh_token"
		application {
			setupKtorTestKoin(this@AccountRoutesTest, testModule)

			configureSerialization()
			configureLocalization()
			configureSecurity()
			configureRouting()
		}

		createClient {
			install(ContentNegotiation) {
				json(json)
			}
		}

		// Test invalid refresh token scenario
		makeRefreshCall(USER_ID, mockRefreshToken).apply {
			assertEquals(HttpStatusCode.Unauthorized, status)
		}
	}

	@Test
	fun `Account - Refresh Token - pending-deletion message reaches the client`() = testApplication {
		coEvery {
			accountsComponent.refreshToken(USER_ID, any(), any())
		} returns SResult.failure(
			"Account pending deletion",
			com.darkrockstudios.apps.hammer.utilities.Msg.r("api_accounts_login_error_pending_deletion")
		)

		application {
			setupKtorTestKoin(this@AccountRoutesTest, testModule)

			configureSerialization()
			configureLocalization()
			configureSecurity()
			configureRouting()
		}

		createClient {
			install(ContentNegotiation) {
				json(json)
			}
		}

		makeRefreshCall(USER_ID, "any-refresh-token").apply {
			assertEquals(HttpStatusCode.Unauthorized, status)
			assertTrue(bodyAsText().contains("pending deletion"))
		}
	}

	@Test
	fun `Account - Refresh Token`() = testApplication {
		val mockRefreshToken = "valid_refresh_token"
		val expectedTokens = Token(
			userId = 0L,
			auth = "new_access_token",
			refresh = "new_refresh_token"
		)

		coEvery { accountsComponent.refreshToken(USER_ID, any(), mockRefreshToken) } returns SResult.success(
			expectedTokens
		)

		application {
			setupKtorTestKoin(this@AccountRoutesTest, testModule)

			configureSerialization()
			configureLocalization()
			configureSecurity()
			configureRouting()
		}

		createClient {
			install(ContentNegotiation) {
				json(json)
			}
		}

		// Test valid refresh token scenario
		makeRefreshCall(USER_ID, mockRefreshToken).apply {
			assertTrue(status.isSuccess())

			// TODO: I don't know why this won't work!
			//val newToken = body<Token>()
			// Work-around for the fact that body<Token>() isn't working
			val responseText = bodyAsText()
			val newToken = json.decodeFromString<Token>(responseText)

			assertEquals(expectedTokens.auth, newToken.auth)
			assertEquals(expectedTokens.refresh, newToken.refresh)
		}
	}

	@Test
	fun `Account - Create - Terms of service challenge`() = testApplication {
		val challenge = TermsOfServiceChallenge(text = "Be excellent to each other", version = "v1")
		coEvery {
			accountsComponent.createAccount(any(), any(), any(), acceptedTosVersion = null)
		} returns CreateAccountResult.TermsRequired(challenge)

		application {
			setupKtorTestKoin(this@AccountRoutesTest, testModule)

			configureSerialization()
			configureLocalization()
			configureSecurity()
			configureRouting()
		}

		createClient {
			install(ContentNegotiation) {
				json(json)
			}
		}

		makeCreateCall(acceptedTosVersion = null).apply {
			assertEquals(HTTP_STATUS_TERMS_OF_SERVICE, status.value)
			val body = json.decodeFromString<TermsOfServiceChallenge>(bodyAsText())
			assertEquals(challenge, body)
		}
	}

	@Test
	fun `Account - Create - Accepted terms succeeds`() = testApplication {
		val expectedToken = Token(userId = 0L, auth = "access", refresh = "refresh")
		coEvery {
			accountsComponent.createAccount(any(), any(), any(), acceptedTosVersion = "v1")
		} returns CreateAccountResult.Success(expectedToken)

		application {
			setupKtorTestKoin(this@AccountRoutesTest, testModule)

			configureSerialization()
			configureLocalization()
			configureSecurity()
			configureRouting()
		}

		createClient {
			install(ContentNegotiation) {
				json(json)
			}
		}

		makeCreateCall(acceptedTosVersion = "v1").apply {
			assertEquals(HttpStatusCode.Created, status)
			val body = json.decodeFromString<Token>(bodyAsText())
			assertEquals(expectedToken.auth, body.auth)
		}
	}

	// The soft-deleted gate lives in the token query itself (AuthToken.sq hides
	// tokens of deleted accounts), so at the route level a deleted account is a
	// failed checkToken; the data-layer behavior is covered in AccountDaoSoftDeleteTest.
	@Test
	fun `Account - Test Auth - invisible token is rejected at the bearer gate`() = testApplication {
		coEvery { accountsRepository.checkToken(USER_ID, "bearer-token") } returns
			SResult.failure("No valid token found", null)
		coEvery { whiteListRepository.useWhiteList() } returns false

		application {
			setupKtorTestKoin(this@AccountRoutesTest, testModule)

			configureSerialization()
			configureLocalization()
			configureSecurity()
			configureRouting()
		}

		makeTestAuthCall(USER_ID).apply {
			assertEquals(HttpStatusCode.Unauthorized, status)
		}
	}

	@Test
	fun `Account - Test Auth - active account passes the bearer gate`() = testApplication {
		coEvery { accountsRepository.checkToken(USER_ID, "bearer-token") } returns SResult.success(USER_ID)
		coEvery { whiteListRepository.useWhiteList() } returns false

		application {
			setupKtorTestKoin(this@AccountRoutesTest, testModule)

			configureSerialization()
			configureLocalization()
			configureSecurity()
			configureRouting()
		}

		makeTestAuthCall(USER_ID).apply {
			assertTrue(status.isSuccess())
		}
	}

	private suspend fun ApplicationTestBuilder.makeTestAuthCall(userId: Long): HttpResponse =
		client.get("/api/account/test_auth/$userId") {
			header(HttpHeaders.Authorization, "Bearer bearer-token")
			header(HAMMER_PROTOCOL_HEADER, HAMMER_PROTOCOL_VERSION)
			header(HEADER_CLIENT_VERSION, BuildMetadata.APP_VERSION)
			header(HttpHeaders.Accept, ContentType.Application.Json.toString())
		}

	private suspend fun ApplicationTestBuilder.makeCreateCall(acceptedTosVersion: String?): HttpResponse =
		client.post("/api/account/create") {
			header(HAMMER_PROTOCOL_HEADER, HAMMER_PROTOCOL_VERSION)
			header(HEADER_CLIENT_VERSION, BuildMetadata.APP_VERSION)
			header(HttpHeaders.Accept, ContentType.Application.Json.toString())
			setBody(
				FormDataContent(
					Parameters.build {
						append("email", "test@test.com")
						append("password", "qweasdZXC123")
						append("installId", INSTALL_ID)
						acceptedTosVersion?.let { append("acceptedTosVersion", it) }
					}
				)
			)
		}

	private suspend fun ApplicationTestBuilder.makeRefreshCall(userId: Long, mockRefreshToken: String): HttpResponse =
		client.post("/api/account/refresh_token/$userId") {
			header(HAMMER_PROTOCOL_HEADER, HAMMER_PROTOCOL_VERSION)
			header(HEADER_CLIENT_VERSION, BuildMetadata.APP_VERSION)
			header(HttpHeaders.Accept, ContentType.Application.Json.toString())
			setBody(
				FormDataContent(
					Parameters.build {
						append("refreshToken", mockRefreshToken)
						append("installId", INSTALL_ID)
					}
				)
			)
		}
}
