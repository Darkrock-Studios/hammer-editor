package com.darkrockstudios.apps.hammer.project.routes

import com.darkrockstudios.apps.hammer.account.AccountsComponent
import com.darkrockstudios.apps.hammer.account.AccountsRepository
import com.darkrockstudios.apps.hammer.account.BioService
import com.darkrockstudios.apps.hammer.account.PasswordResetRepository
import com.darkrockstudios.apps.hammer.account.PenNameService
import com.darkrockstudios.apps.hammer.admin.AdminComponent
import com.darkrockstudios.apps.hammer.admin.ConfigRepository
import com.darkrockstudios.apps.hammer.admin.WhiteListRepository
import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.base.http.projectdata.ProjectData
import com.darkrockstudios.apps.hammer.base.http.projectdata.ProjectDataConflictDto
import com.darkrockstudios.apps.hammer.base.http.projectdata.ProjectDataDto
import com.darkrockstudios.apps.hammer.base.http.projectdata.ProjectDataUploadRequest
import com.darkrockstudios.apps.hammer.base.http.projectdata.ProjectTheme
import com.darkrockstudios.apps.hammer.base.http.projectdata.WordCountGoal
import com.darkrockstudios.apps.hammer.plugins.configureLocalization
import com.darkrockstudios.apps.hammer.plugins.configureRouting
import com.darkrockstudios.apps.hammer.plugins.configureSecurity
import com.darkrockstudios.apps.hammer.plugins.configureSerialization
import com.darkrockstudios.apps.hammer.project.ProjectDataSaveResult
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
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.testApplication
import io.mockk.MockKAnnotations
import io.mockk.mockk
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.MockK
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import kotlin.test.assertEquals

class ProjectDataRoutesTest : BaseTest() {

	@MockK(relaxed = true) private lateinit var accountsRepository: AccountsRepository
	@MockK(relaxed = true) private lateinit var whiteListRepository: WhiteListRepository
	@MockK(relaxed = true) private lateinit var serverProjectDataRepository: ServerProjectDataRepository
	@MockK(relaxed = true) private lateinit var serverWritingActivityRepository: ServerWritingActivityRepository
	@MockK(relaxed = true) private lateinit var projectEntityRepository: ProjectEntityRepository
	@MockK(relaxed = true) private lateinit var projectAccessRepository: ProjectAccessRepository
	@MockK(relaxed = true) private lateinit var projectsRepository: ProjectsRepository
	@MockK(relaxed = true) private lateinit var accountsComponent: AccountsComponent
	@MockK(relaxed = true) private lateinit var adminComponent: AdminComponent
	@MockK(relaxed = true) private lateinit var configRepository: ConfigRepository
	@MockK(relaxed = true) private lateinit var storyExportService: StoryExportService
	@MockK(relaxed = true) private lateinit var penNameService: PenNameService
	@MockK(relaxed = true) private lateinit var bioService: BioService
	@MockK(relaxed = true) private lateinit var passwordResetRepository: PasswordResetRepository
	@MockK(relaxed = true) private lateinit var markdownService: MarkdownService

	private val json: Json = Json { ignoreUnknownKeys = true }
	private lateinit var testModule: org.koin.core.module.Module

	private val bearerToken = "token-test"
	private val userId = 0L
	private val projectName = "TestProject"
	private val projectId = ProjectId("uuid-1")

	private val sampleData = ProjectData(
		authorName = "Pat",
		theme = ProjectTheme("#FF112233", "#FFAABBCC"),
		wordCountGoal = WordCountGoal(WordCountGoal.Cadence.DAY, 500),
	)

	@BeforeEach
	override fun setup() {
		super.setup()
		MockKAnnotations.init(this, relaxUnitFun = true)

		testModule = module {
			single { accountsRepository }
			single { whiteListRepository }
			single { serverProjectDataRepository }
			single { serverWritingActivityRepository }
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
			single { mockk<com.darkrockstudios.apps.hammer.review.ReviewRepository>(relaxed = true) }
			single { mockk<com.darkrockstudios.apps.hammer.database.ProjectDao>(relaxed = true) }
			single { json }
		}
	}

	@Test
	fun `GET project_data returns dto when row exists`() = testApplication {
		coEvery { accountsRepository.checkToken(userId, bearerToken) } returns SResult.success(0L)
		coEvery { whiteListRepository.useWhiteList() } returns false
		coEvery {
			serverProjectDataRepository.load(userId, ProjectDefinition(projectName, projectId))
		} returns SResult.success(ProjectDataDto(sampleData, "hash-abc"))

		application {
			setupKtorTestKoin(this@ProjectDataRoutesTest, testModule)
			configureSerialization()
			configureLocalization()
			configureSecurity()
			configureRouting()
		}
		val testClient = createClient { install(ContentNegotiation) { json(json) } }

		val response = testClient.get("api/project/$userId/$projectName/project_data") {
			header("Authorization", "Bearer $bearerToken")
			url { parameters.append("projectId", projectId.id) }
		}

		assertEquals(HttpStatusCode.OK, response.status)
		val dto = response.body<ProjectDataDto>()
		assertEquals(sampleData, dto.data)
		assertEquals("hash-abc", dto.hash)
	}

	@Test
	fun `GET project_data returns 204 when no row`() = testApplication {
		coEvery { accountsRepository.checkToken(userId, bearerToken) } returns SResult.success(0L)
		coEvery { whiteListRepository.useWhiteList() } returns false
		coEvery {
			serverProjectDataRepository.load(any(), any())
		} returns SResult.success(null)

		application {
			setupKtorTestKoin(this@ProjectDataRoutesTest, testModule)
			configureSerialization()
			configureLocalization()
			configureSecurity()
			configureRouting()
		}

		val response = client.get("api/project/$userId/$projectName/project_data") {
			header("Authorization", "Bearer $bearerToken")
			url { parameters.append("projectId", projectId.id) }
		}

		assertEquals(HttpStatusCode.NoContent, response.status)
	}

	@Test
	fun `GET project_data returns 404 when project missing`() = testApplication {
		coEvery { accountsRepository.checkToken(userId, bearerToken) } returns SResult.success(0L)
		coEvery { whiteListRepository.useWhiteList() } returns false
		coEvery {
			serverProjectDataRepository.load(any(), any())
		} returns SResult.failure(ProjectNotFound(projectId))

		application {
			setupKtorTestKoin(this@ProjectDataRoutesTest, testModule)
			configureSerialization()
			configureLocalization()
			configureSecurity()
			configureRouting()
		}

		val response = client.get("api/project/$userId/$projectName/project_data") {
			header("Authorization", "Bearer $bearerToken")
			url { parameters.append("projectId", projectId.id) }
		}

		assertEquals(HttpStatusCode.NotFound, response.status)
	}

	@Test
	fun `POST project_data 200 when accepted`() = testApplication {
		coEvery { accountsRepository.checkToken(userId, bearerToken) } returns SResult.success(0L)
		coEvery { whiteListRepository.useWhiteList() } returns false
		coEvery {
			serverProjectDataRepository.save(
				userId,
				ProjectDefinition(projectName, projectId),
				sampleData,
				null,
			)
		} returns SResult.success(
			ProjectDataSaveResult.Saved(ProjectDataDto(sampleData, "hash-new"))
		)

		application {
			setupKtorTestKoin(this@ProjectDataRoutesTest, testModule)
			configureSerialization()
			configureLocalization()
			configureSecurity()
			configureRouting()
		}
		val testClient = createClient { install(ContentNegotiation) { json(json) } }

		val response = testClient.post("api/project/$userId/$projectName/project_data") {
			header("Authorization", "Bearer $bearerToken")
			url { parameters.append("projectId", projectId.id) }
			contentType(ContentType.Application.Json)
			setBody(ProjectDataUploadRequest(sampleData, originalHash = null))
		}

		assertEquals(HttpStatusCode.OK, response.status)
		val dto = response.body<ProjectDataDto>()
		assertEquals("hash-new", dto.hash)
		coVerify {
			serverProjectDataRepository.save(
				userId,
				ProjectDefinition(projectName, projectId),
				sampleData,
				null,
			)
		}
	}

	@Test
	fun `POST project_data 409 with conflict body when hashes mismatch`() = testApplication {
		coEvery { accountsRepository.checkToken(userId, bearerToken) } returns SResult.success(0L)
		coEvery { whiteListRepository.useWhiteList() } returns false

		val serverState = sampleData.copy(authorName = "Server")
		coEvery {
			serverProjectDataRepository.save(any(), any(), any(), any())
		} returns SResult.success(
			ProjectDataSaveResult.Conflict(
				ProjectDataConflictDto(server = serverState, serverHash = "server-hash")
			)
		)

		application {
			setupKtorTestKoin(this@ProjectDataRoutesTest, testModule)
			configureSerialization()
			configureLocalization()
			configureSecurity()
			configureRouting()
		}
		val testClient = createClient { install(ContentNegotiation) { json(json) } }

		val response = testClient.post("api/project/$userId/$projectName/project_data") {
			header("Authorization", "Bearer $bearerToken")
			url { parameters.append("projectId", projectId.id) }
			contentType(ContentType.Application.Json)
			setBody(ProjectDataUploadRequest(sampleData.copy(authorName = "Local"), originalHash = "stale-hash"))
		}

		assertEquals(HttpStatusCode.Conflict, response.status)
		val body = response.body<ProjectDataConflictDto>()
		assertEquals(serverState, body.server)
		assertEquals("server-hash", body.serverHash)
	}
}
