package com.darkrockstudios.apps.hammer.account

import com.darkrockstudios.apps.hammer.admin.AdminComponent
import com.darkrockstudios.apps.hammer.plugins.configureRouting
import com.darkrockstudios.apps.hammer.plugins.configureSecurity
import com.darkrockstudios.apps.hammer.project.ProjectEntityRepository
import com.darkrockstudios.apps.hammer.projects.ProjectsRepository
import com.darkrockstudios.apps.hammer.utils.BaseTest
import com.darkrockstudios.apps.hammer.utils.setupKtorTestKoin
import io.ktor.client.request.post
import io.ktor.http.isSuccess
import io.ktor.server.testing.testApplication
import io.mockk.MockKAnnotations
import io.mockk.impl.annotations.MockK
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import kotlin.test.assertFalse

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
	private lateinit var json: Json

	private lateinit var testModule: org.koin.core.module.Module

	@BeforeEach
	override fun setup() {
		super.setup()

		MockKAnnotations.init(this)

		testModule = module {
			single { accountsRepository }
			single { projectEntityRepository }
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