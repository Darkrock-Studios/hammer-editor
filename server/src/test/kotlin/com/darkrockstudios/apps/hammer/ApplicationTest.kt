package com.darkrockstudios.apps.hammer

import com.darkrockstudios.apps.hammer.account.AccountsComponent
import com.darkrockstudios.apps.hammer.account.AccountsRepository
import com.darkrockstudios.apps.hammer.admin.AdminComponent
import com.darkrockstudios.apps.hammer.plugins.configureLocalization
import com.darkrockstudios.apps.hammer.plugins.configureRouting
import com.darkrockstudios.apps.hammer.plugins.configureSecurity
import com.darkrockstudios.apps.hammer.project.ProjectRepository
import com.darkrockstudios.apps.hammer.projects.ProjectsRepository
import com.darkrockstudios.apps.hammer.utils.BaseTest
import com.darkrockstudios.apps.hammer.utils.setupKtorTestKoin
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import io.mockk.mockk
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import kotlin.test.assertEquals

class ApplicationTest : BaseTest() {

	private lateinit var accountsRepository: AccountsRepository
	private lateinit var projectRepository: ProjectRepository
	private lateinit var projectsRepository: ProjectsRepository
	private lateinit var accountsComponent: AccountsComponent
	private lateinit var adminComponent: AdminComponent
	private lateinit var testModule: org.koin.core.module.Module

	@BeforeEach
	override fun setup() {
		super.setup()

		accountsRepository = mockk()
		projectRepository = mockk()
		projectsRepository = mockk()
		accountsComponent = mockk()
		adminComponent = mockk()

		testModule = module {
			single { accountsRepository }
			single { projectRepository }
			single { projectsRepository }
			single { accountsComponent }
			single { adminComponent }
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
