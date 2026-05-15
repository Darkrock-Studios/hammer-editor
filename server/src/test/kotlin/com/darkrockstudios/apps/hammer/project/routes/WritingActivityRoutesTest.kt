package com.darkrockstudios.apps.hammer.project.routes

import com.darkrockstudios.apps.hammer.account.AccountsRepository
import com.darkrockstudios.apps.hammer.account.PasswordResetRepository
import com.darkrockstudios.apps.hammer.account.PenNameService
import com.darkrockstudios.apps.hammer.account.BioService
import com.darkrockstudios.apps.hammer.account.AccountsComponent
import com.darkrockstudios.apps.hammer.admin.AdminComponent
import com.darkrockstudios.apps.hammer.admin.ConfigRepository
import com.darkrockstudios.apps.hammer.admin.WhiteListRepository
import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.base.http.writingactivity.DeviceLog
import com.darkrockstudios.apps.hammer.base.http.writingactivity.WritingActivityResponse
import com.darkrockstudios.apps.hammer.base.http.writingactivity.WritingSession
import com.darkrockstudios.apps.hammer.plugins.configureLocalization
import com.darkrockstudios.apps.hammer.plugins.configureRouting
import com.darkrockstudios.apps.hammer.plugins.configureSecurity
import com.darkrockstudios.apps.hammer.plugins.configureSerialization
import com.darkrockstudios.apps.hammer.project.ProjectDefinition
import com.darkrockstudios.apps.hammer.project.ProjectEntityRepository
import com.darkrockstudios.apps.hammer.project.ProjectNotFound
import com.darkrockstudios.apps.hammer.project.ServerProjectDataRepository
import com.darkrockstudios.apps.hammer.project.ServerWritingActivityRepository
import com.darkrockstudios.apps.hammer.project.access.ProjectAccessRepository
import com.darkrockstudios.apps.hammer.projects.ProjectsRepository
import com.darkrockstudios.apps.hammer.story.StoryExportService
import com.darkrockstudios.apps.hammer.utilities.MarkdownService
import com.darkrockstudios.apps.hammer.utilities.SResult
import com.darkrockstudios.apps.hammer.utils.BaseTest
import com.darkrockstudios.apps.hammer.utils.setupKtorTestKoin
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.testApplication
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.MockK
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

class WritingActivityRoutesTest : BaseTest() {

	@MockK(relaxed = true)
	private lateinit var accountsRepository: AccountsRepository

	@MockK(relaxed = true)
	private lateinit var whiteListRepository: WhiteListRepository

	@MockK(relaxed = true)
	private lateinit var serverWritingActivityRepository: ServerWritingActivityRepository

	@MockK(relaxed = true)
	private lateinit var serverProjectDataRepository: ServerProjectDataRepository

	@MockK(relaxed = true)
	private lateinit var projectEntityRepository: ProjectEntityRepository

	@MockK(relaxed = true)
	private lateinit var projectAccessRepository: ProjectAccessRepository

	@MockK(relaxed = true)
	private lateinit var projectsRepository: ProjectsRepository

	@MockK(relaxed = true)
	private lateinit var accountsComponent: AccountsComponent

	@MockK(relaxed = true)
	private lateinit var adminComponent: AdminComponent

	@MockK(relaxed = true)
	private lateinit var configRepository: ConfigRepository

	@MockK(relaxed = true)
	private lateinit var storyExportService: StoryExportService

	@MockK(relaxed = true)
	private lateinit var penNameService: PenNameService

	@MockK(relaxed = true)
	private lateinit var bioService: BioService

	@MockK(relaxed = true)
	private lateinit var passwordResetRepository: PasswordResetRepository

	@MockK(relaxed = true)
	private lateinit var markdownService: MarkdownService

	private val json: Json = Json { ignoreUnknownKeys = true }

	private lateinit var testModule: org.koin.core.module.Module

	private val bearerToken = "token-test"
	private val userId = 0L
	private val projectName = "TestProject"
	private val projectId = ProjectId("uuid-1")
	private val deviceId = "device-abc"

	@BeforeEach
	override fun setup() {
		super.setup()
		MockKAnnotations.init(this, relaxUnitFun = true)

		testModule = module {
			single { accountsRepository }
			single { whiteListRepository }
			single { serverWritingActivityRepository }
			single { serverProjectDataRepository }
			single { projectEntityRepository }
			single { projectAccessRepository }
			single { projectsRepository }
			single { accountsComponent }
			single { adminComponent }
			single { configRepository }
			single { storyExportService }
			single { penNameService }
			single { bioService }
			single { passwordResetRepository }
			single { markdownService }
			single { json }
		}
	}

	@Test
	fun `GET writing_activity returns devices map on success`() = testApplication {
		val expected = WritingActivityResponse(
			devices = mapOf(
				deviceId to DeviceLog(
					deviceLabel = "Desktop",
					sessions = listOf(
						WritingSession(
							startedAt = Instant.parse("2026-04-28T09:00:00Z"),
							endedAt = Instant.parse("2026-04-28T10:00:00Z"),
							wordsWritten = 120,
						)
					),
				)
			)
		)
		coEvery { accountsRepository.checkToken(userId, bearerToken) } returns SResult.success(0L)
		coEvery { whiteListRepository.useWhiteList() } returns false
		coEvery {
			serverWritingActivityRepository.loadAll(
				userId,
				ProjectDefinition(projectName, projectId),
			)
		} returns SResult.success(expected)

		application {
			setupKtorTestKoin(this@WritingActivityRoutesTest, testModule)
			configureSerialization()
			configureLocalization()
			configureSecurity()
			configureRouting()
		}
		val testClient = createClient { install(ContentNegotiation) { json(json) } }

		val response = testClient.get("api/project/$userId/$projectName/writing_activity") {
			header("Authorization", "Bearer $bearerToken")
			url { parameters.append("projectId", projectId.id) }
		}

		assertTrue(response.status.isSuccess())
		assertEquals(expected, response.body<WritingActivityResponse>())
	}

	@Test
	fun `GET writing_activity returns 404 when project missing`() = testApplication {
		coEvery { accountsRepository.checkToken(userId, bearerToken) } returns SResult.success(0L)
		coEvery { whiteListRepository.useWhiteList() } returns false
		coEvery {
			serverWritingActivityRepository.loadAll(any(), any())
		} returns SResult.failure(ProjectNotFound(projectId))

		application {
			setupKtorTestKoin(this@WritingActivityRoutesTest, testModule)
			configureSerialization()
			configureLocalization()
			configureSecurity()
			configureRouting()
		}

		val response = client.get("api/project/$userId/$projectName/writing_activity") {
			header("Authorization", "Bearer $bearerToken")
			url { parameters.append("projectId", projectId.id) }
		}

		assertEquals(HttpStatusCode.NotFound, response.status)
	}

	@Test
	fun `POST writing_activity stores own slot`() = testApplication {
		val log = DeviceLog(
			deviceLabel = "Desktop",
			sessions = listOf(
				WritingSession(
					startedAt = Instant.parse("2026-04-28T09:00:00Z"),
					endedAt = Instant.parse("2026-04-28T10:00:00Z"),
					wordsWritten = 120,
				)
			),
		)
		coEvery { accountsRepository.checkToken(userId, bearerToken) } returns SResult.success(0L)
		coEvery { whiteListRepository.useWhiteList() } returns false
		coEvery {
			serverWritingActivityRepository.saveDeviceLog(
				userId = userId,
				projectDef = ProjectDefinition(projectName, projectId),
				deviceId = deviceId,
				log = log,
			)
		} returns SResult.success(Unit)

		application {
			setupKtorTestKoin(this@WritingActivityRoutesTest, testModule)
			configureSerialization()
			configureLocalization()
			configureSecurity()
			configureRouting()
		}
		val testClient = createClient { install(ContentNegotiation) { json(json) } }

		val response = testClient.post("api/project/$userId/$projectName/writing_activity/$deviceId") {
			header("Authorization", "Bearer $bearerToken")
			url { parameters.append("projectId", projectId.id) }
			contentType(ContentType.Application.Json)
			setBody(log)
		}

		assertTrue(response.status.isSuccess())
		coVerify {
			serverWritingActivityRepository.saveDeviceLog(
				userId = userId,
				projectDef = ProjectDefinition(projectName, projectId),
				deviceId = deviceId,
				log = log,
			)
		}
	}

	@Test
	fun `POST writing_activity 400 when projectId missing`() = testApplication {
		coEvery { accountsRepository.checkToken(userId, bearerToken) } returns SResult.success(0L)
		coEvery { whiteListRepository.useWhiteList() } returns false

		application {
			setupKtorTestKoin(this@WritingActivityRoutesTest, testModule)
			configureSerialization()
			configureLocalization()
			configureSecurity()
			configureRouting()
		}
		val testClient = createClient { install(ContentNegotiation) { json(json) } }

		val response = testClient.post("api/project/$userId/$projectName/writing_activity/$deviceId") {
			header("Authorization", "Bearer $bearerToken")
			contentType(ContentType.Application.Json)
			setBody(DeviceLog(deviceLabel = "Desktop"))
		}

		assertEquals(HttpStatusCode.BadRequest, response.status)
	}
}
