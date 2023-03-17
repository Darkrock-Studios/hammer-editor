package com.darkrockstudios.apps.hammer.account

import com.darkrockstudios.apps.hammer.BaseTest
import com.darkrockstudios.apps.hammer.plugins.configureRouting
import com.darkrockstudios.apps.hammer.plugins.configureSecurity
import com.darkrockstudios.apps.hammer.project.ProjectRepository
import com.darkrockstudios.apps.hammer.projects.ProjectsRepository
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.*
import io.mockk.mockk
import org.junit.Before
import org.koin.dsl.module
import kotlin.test.Test
import kotlin.test.assertFalse

class AccountRoutesTest : BaseTest() {
	private lateinit var accountsRepository: AccountsRepository
	private lateinit var projectRepository: ProjectRepository

	@Before
	override fun setup() {
		super.setup()

		accountsRepository = mockk()
		projectRepository = mockk()

		val testModule = module {
			single { accountsRepository }
			single { projectRepository }
		}
		setupKoin(testModule)
	}

	@Test
	fun `Account - Refresh Token - No User`() = testApplication {
		application {
			configureSecurity()
			configureRouting()
		}
		client.post("/account/refresh_token/0").apply {
			assertFalse(status.isSuccess())
		}
	}
}