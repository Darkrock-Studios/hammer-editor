package com.darkrockstudios.apps.hammer.account

import com.darkrockstudios.apps.hammer.ServerConfig
import com.darkrockstudios.apps.hammer.admin.AdminComponent
import com.darkrockstudios.apps.hammer.admin.WhiteListRepository
import com.darkrockstudios.apps.hammer.base.BuildMetadata
import com.darkrockstudios.apps.hammer.base.http.*
import com.darkrockstudios.apps.hammer.plugins.configureLocalization
import com.darkrockstudios.apps.hammer.plugins.configureRouting
import com.darkrockstudios.apps.hammer.plugins.configureSecurity
import com.darkrockstudios.apps.hammer.plugins.configureSerialization
import com.darkrockstudios.apps.hammer.project.ProjectEntityRepository
import com.darkrockstudios.apps.hammer.projects.ProjectsRepository
import com.darkrockstudios.apps.hammer.utilities.SResult
import com.darkrockstudios.apps.hammer.utils.BaseTest
import com.darkrockstudios.apps.hammer.utils.setupKtorTestKoin
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.testing.*
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
	private lateinit var projectsRepository: ProjectsRepository

	@MockK
	private lateinit var accountsComponent: AccountsComponent

	@MockK
	private lateinit var adminComponent: AdminComponent

	@MockK
	private lateinit var whiteListRepository: WhiteListRepository

	private lateinit var testModule: org.koin.core.module.Module

	private lateinit var json: Json

	private val USER_ID = 0L
	private val INSTALL_ID = "install-id"

	@BeforeEach
	override fun setup() {
		super.setup()

		MockKAnnotations.init(this)

		json = createJsonSerializer()

		testModule = module {
			single { accountsRepository }
			single { projectEntityRepository }
			single { projectsRepository }
			single { accountsComponent }
			single { adminComponent }
			single { whiteListRepository }
			single { json }
		}
	}

	@Test
	fun `Account - Refresh Token - No User`() = testApplication {
		coEvery {
			accountsComponent.refreshToken(USER_ID, any(), any())
		} returns SResult.failure("No valid token not found", mockk())

		val mockRefreshToken = "invalid_refresh_token"
		application {
			setupKtorTestKoin(this@AccountRoutesTest, testModule)

			configureSerialization()
			configureLocalization()
			configureSecurity()
			configureRouting(ServerConfig())
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
			configureRouting(ServerConfig())
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
