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
import com.darkrockstudios.apps.hammer.project.ProjectEntityDatasource
import com.darkrockstudios.apps.hammer.project.ProjectEntityRepository
import com.darkrockstudios.apps.hammer.project.ProjectNotFound
import com.darkrockstudios.apps.hammer.project.RawProjectDataConflictDto
import com.darkrockstudios.apps.hammer.project.RawProjectDataDto
import com.darkrockstudios.apps.hammer.project.ServerProjectDataRepository
import com.darkrockstudios.apps.hammer.project.ServerWritingActivityRepository
import com.darkrockstudios.apps.hammer.project.access.ProjectAccessRepository
import com.darkrockstudios.apps.hammer.projects.ProjectsRepository
import com.darkrockstudios.apps.hammer.story.StoryRendererService
import com.darkrockstudios.apps.hammer.utilities.MarkdownService
import com.darkrockstudios.apps.hammer.utilities.SResult
import com.darkrockstudios.apps.hammer.utils.BaseTest
import com.darkrockstudios.apps.hammer.utils.setupKtorTestKoin
import com.darkrockstudios.apps.hammer.utils.testAccount
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
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
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
	@MockK(relaxed = true) private lateinit var projectEntityDatasource: ProjectEntityDatasource
	@MockK(relaxed = true) private lateinit var projectAccessRepository: ProjectAccessRepository
	@MockK(relaxed = true) private lateinit var projectsRepository: ProjectsRepository
	@MockK(relaxed = true) private lateinit var accountsComponent: AccountsComponent
	@MockK(relaxed = true) private lateinit var adminComponent: AdminComponent
	@MockK(relaxed = true) private lateinit var configRepository: ConfigRepository
	@MockK(relaxed = true)
	private lateinit var storyRendererService: StoryRendererService
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

	// The typed client DTOs on the request side vs Raw* on the mocked repository side is the
	// point of these tests: they prove the raw wire format is identical to the typed one.
	private val sampleJson get() = json.encodeToJsonElement(ProjectData.serializer(), sampleData)

	@BeforeEach
	override fun setup() {
		super.setup()
		MockKAnnotations.init(this, relaxUnitFun = true)

		// The bearer gate loads the account after token validation; default to an active one.
		coEvery { accountsRepository.getAccountOrNull(any()) } returns testAccount()

		coEvery { projectEntityDatasource.getProject(userId, projectId) } returns
			ProjectDefinition(projectName, projectId)

		testModule = module {
			single { accountsRepository }
			single { whiteListRepository }
			single { serverProjectDataRepository }
			single { serverWritingActivityRepository }
			single { projectEntityRepository }
			single<ProjectEntityDatasource> { projectEntityDatasource }
			single { projectAccessRepository }
			single { projectsRepository }
			single { accountsComponent }
			single { adminComponent }
			single { configRepository }
			single { storyRendererService }
			single { penNameService }
			single { bioService }
			single { passwordResetRepository }
			single { markdownService }
			single { mockk<com.darkrockstudios.apps.hammer.review.ReviewRepository>(relaxed = true) }
			single { mockk<com.darkrockstudios.apps.hammer.storyideas.ServerIdeasRepository>(relaxed = true) }
			single { mockk<com.darkrockstudios.apps.hammer.database.ProjectDao>(relaxed = true) }
			single { mockk<com.darkrockstudios.apps.hammer.account.AccountDeletionService>(relaxed = true) }
			single { mockk<com.darkrockstudios.apps.hammer.account.TermsOfServiceRepository>(relaxed = true) }
			single { json }
		}
	}

	@Test
	fun `GET project_data returns dto when row exists`() = testApplication {
		coEvery { accountsRepository.checkToken(userId, bearerToken) } returns SResult.success(0L)
		coEvery { whiteListRepository.isOnWhiteList(any()) } returns true
		coEvery {
			serverProjectDataRepository.load(userId, ProjectDefinition(projectName, projectId))
		} returns SResult.success(RawProjectDataDto(sampleJson, "hash-abc"))

		application {
			setupKtorTestKoin(this@ProjectDataRoutesTest, testModule)
			configureSerialization()
			configureLocalization()
			configureSecurity()
			configureRouting()
		}
		val testClient = createClient { install(ContentNegotiation) { json(json) } }

		val response = testClient.get("api/project/$userId/${projectId.id}/project_data") {
			header("Authorization", "Bearer $bearerToken")
		}

		assertEquals(HttpStatusCode.OK, response.status)
		val dto = response.body<ProjectDataDto>()
		assertEquals(sampleData, dto.data)
		assertEquals("hash-abc", dto.hash)
	}

	@Test
	fun `GET project_data returns 204 when no row`() = testApplication {
		coEvery { accountsRepository.checkToken(userId, bearerToken) } returns SResult.success(0L)
		coEvery { whiteListRepository.isOnWhiteList(any()) } returns true
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

		val response = client.get("api/project/$userId/${projectId.id}/project_data") {
			header("Authorization", "Bearer $bearerToken")
		}

		assertEquals(HttpStatusCode.NoContent, response.status)
	}

	@Test
	fun `GET project_data returns 404 when project missing`() = testApplication {
		coEvery { accountsRepository.checkToken(userId, bearerToken) } returns SResult.success(0L)
		coEvery { whiteListRepository.isOnWhiteList(any()) } returns true
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

		val response = client.get("api/project/$userId/${projectId.id}/project_data") {
			header("Authorization", "Bearer $bearerToken")
		}

		assertEquals(HttpStatusCode.NotFound, response.status)
	}

	@Test
	fun `POST project_data 200 when accepted`() = testApplication {
		coEvery { accountsRepository.checkToken(userId, bearerToken) } returns SResult.success(0L)
		coEvery { whiteListRepository.isOnWhiteList(any()) } returns true
		coEvery {
			serverProjectDataRepository.save(
				userId,
				ProjectDefinition(projectName, projectId),
				sampleJson,
				null,
				"client-hash",
			)
		} returns SResult.success(
			ProjectDataSaveResult.Saved(RawProjectDataDto(sampleJson, "hash-new"))
		)

		application {
			setupKtorTestKoin(this@ProjectDataRoutesTest, testModule)
			configureSerialization()
			configureLocalization()
			configureSecurity()
			configureRouting()
		}
		val testClient = createClient { install(ContentNegotiation) { json(json) } }

		val response = testClient.post("api/project/$userId/${projectId.id}/project_data") {
			header("Authorization", "Bearer $bearerToken")
			contentType(ContentType.Application.Json)
			setBody(ProjectDataUploadRequest(sampleData, originalHash = null, hash = "client-hash"))
		}

		assertEquals(HttpStatusCode.OK, response.status)
		val dto = response.body<ProjectDataDto>()
		assertEquals(sampleData, dto.data)
		assertEquals("hash-new", dto.hash)
		coVerify {
			serverProjectDataRepository.save(
				userId,
				ProjectDefinition(projectName, projectId),
				sampleJson,
				null,
				"client-hash",
			)
		}
	}

	@Test
	fun `POST project_data 409 with conflict body when hashes mismatch`() = testApplication {
		coEvery { accountsRepository.checkToken(userId, bearerToken) } returns SResult.success(0L)
		coEvery { whiteListRepository.isOnWhiteList(any()) } returns true

		val serverState = sampleData.copy(authorName = "Server")
		coEvery {
			serverProjectDataRepository.save(any(), any(), any(), any(), any())
		} returns SResult.success(
			ProjectDataSaveResult.Conflict(
				RawProjectDataConflictDto(
					server = json.encodeToJsonElement(ProjectData.serializer(), serverState),
					serverHash = "server-hash",
				)
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

		val response = testClient.post("api/project/$userId/${projectId.id}/project_data") {
			header("Authorization", "Bearer $bearerToken")
			contentType(ContentType.Application.Json)
			setBody(ProjectDataUploadRequest(sampleData.copy(authorName = "Local"), originalHash = "stale-hash"))
		}

		assertEquals(HttpStatusCode.Conflict, response.status)
		val body = response.body<ProjectDataConflictDto>()
		assertEquals(serverState, body.server)
		assertEquals("server-hash", body.serverHash)
	}
}
