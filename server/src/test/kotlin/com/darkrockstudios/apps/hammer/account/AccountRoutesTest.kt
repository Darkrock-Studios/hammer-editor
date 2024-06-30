package com.darkrockstudios.apps.hammer.account

import com.darkrockstudios.apps.hammer.admin.AdminComponent
import com.darkrockstudios.apps.hammer.plugins.configureRouting
import com.darkrockstudios.apps.hammer.plugins.configureSecurity
import com.darkrockstudios.apps.hammer.project.ProjectRepository
import com.darkrockstudios.apps.hammer.projects.ProjectsRepository
import com.darkrockstudios.apps.hammer.utils.BaseTest
import com.darkrockstudios.apps.hammer.utils.setupKtorTestKoin
import io.ktor.client.request.post
import io.ktor.http.isSuccess
import io.ktor.server.testing.testApplication
import io.mockk.mockk
import kotlinx.serialization.json.Json
import org.junit.Before
import org.koin.dsl.module
import kotlin.test.Test
import kotlin.test.assertFalse

class AccountRoutesTest : BaseTest() {
	private lateinit var accountsRepository: AccountsRepository
	private lateinit var projectRepository: ProjectRepository
	private lateinit var projectsRepository: ProjectsRepository
	private lateinit var accountsComponent: AccountsComponent
	private lateinit var adminComponent: AdminComponent
	private lateinit var json: Json
	private lateinit var testModule: org.koin.core.module.Module

	@Before
	override fun setup() {
		super.setup()

		accountsRepository = mockk()
		projectRepository = mockk()
		projectsRepository = mockk()
		accountsComponent = mockk()
		adminComponent = mockk()
		json = mockk()

		testModule = module {
			single { accountsRepository }
			single { projectRepository }
			single { projectsRepository }
			single { accountsComponent }
			single { adminComponent }
			single { json }
		}
	}

	@Test
	fun `Account - Refresh Token - No User`() = testApplication {
		application {
			setupKtorTestKoin(this@AccountRoutesTest, testModule)

			configureSecurity()
			configureRouting()
		}
		client.post("/account/refresh_token/0").apply {
			assertFalse(status.isSuccess())
		}
	}
}