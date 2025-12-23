package com.darkrockstudios.apps.hammer

import com.darkrockstudios.apps.hammer.account.AccountsComponent
import com.darkrockstudios.apps.hammer.account.AccountsRepository
import com.darkrockstudios.apps.hammer.admin.AdminComponent
import com.darkrockstudios.apps.hammer.admin.ConfigRepository
import com.darkrockstudios.apps.hammer.admin.ServerConfigKey
import com.darkrockstudios.apps.hammer.admin.WhiteListRepository
import com.darkrockstudios.apps.hammer.plugins.configureLocalization
import com.darkrockstudios.apps.hammer.plugins.configureRouting
import com.darkrockstudios.apps.hammer.plugins.configureSecurity
import com.darkrockstudios.apps.hammer.project.ProjectEntityRepository
import com.darkrockstudios.apps.hammer.project.access.ProjectAccessRepository
import com.darkrockstudios.apps.hammer.projects.ProjectsRepository
import com.darkrockstudios.apps.hammer.story.StoryExportService
import com.darkrockstudios.apps.hammer.utils.BaseTest
import com.darkrockstudios.apps.hammer.utils.setupKtorTestKoin
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
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

class ApplicationTest : BaseTest() {

	@MockK
	private lateinit var accountsRepository: AccountsRepository

	@MockK
	private lateinit var projectEntityRepository: ProjectEntityRepository

	@MockK
	private lateinit var projectAccessRepository: ProjectAccessRepository

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
	private lateinit var storyExportService: StoryExportService
	private lateinit var testModule: org.koin.core.module.Module

	@BeforeEach
	override fun setup() {
		super.setup()

		MockKAnnotations.init(this)
		coEvery { configRepository.get(any<ServerConfigKey<*>>()) } returns "en"

		testModule = module {
			single { accountsRepository }
			single { projectEntityRepository }
			single { projectAccessRepository }
			single { projectsRepository }
			single { accountsComponent }
			single { adminComponent }
			single { whiteListRepository }
			single { configRepository }
			single { storyExportService }
			single { mockk<Json>() }
		}
	}

	@Test
	fun testRoot() = testApplication {
		application {
			setupKtorTestKoin(this@ApplicationTest, testModule)
			configureSecurity()
			configureLocalization()
			configureRouting()
		}
		client.get("/api/teapot").apply {
			assertEquals(HttpStatusCode.fromValue(418), status)
			assertEquals("I'm a little Tea Pot [English]", bodyAsText())
		}
	}
}
