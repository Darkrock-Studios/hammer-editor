package com.darkrockstudios.apps.hammer.projects.routes

import com.darkrockstudios.apps.hammer.account.AccountsComponent
import com.darkrockstudios.apps.hammer.account.AccountsRepository
import com.darkrockstudios.apps.hammer.account.BioService
import com.darkrockstudios.apps.hammer.account.PasswordResetRepository
import com.darkrockstudios.apps.hammer.account.PenNameService
import com.darkrockstudios.apps.hammer.admin.AdminComponent
import com.darkrockstudios.apps.hammer.admin.ConfigRepository
import com.darkrockstudios.apps.hammer.admin.WhiteListRepository
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
import com.darkrockstudios.apps.hammer.utils.BaseTest
import com.darkrockstudios.apps.hammer.utils.setupKtorTestKoin
import com.darkrockstudios.apps.hammer.utils.testAccount
import io.ktor.server.application.Application
import io.ktor.server.testing.ApplicationTestBuilder
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.BeforeEach
import org.koin.dsl.module

abstract class ProjectsRoutesBaseTest : BaseTest() {

	@MockK(relaxed = true)
	protected lateinit var accountsRepository: AccountsRepository

	@MockK(relaxed = true)
	protected lateinit var whiteListRepository: WhiteListRepository

	@MockK(relaxed = true)
	protected lateinit var projectEntityRepository: ProjectEntityRepository

	@MockK(relaxed = true)
	protected lateinit var serverWritingActivityRepository: ServerWritingActivityRepository

	@MockK(relaxed = true)
	protected lateinit var serverProjectDataRepository: ServerProjectDataRepository

	@MockK(relaxed = true)
	protected lateinit var projectAccessRepository: ProjectAccessRepository

	@MockK(relaxed = true)
	protected lateinit var projectsRepository: ProjectsRepository

	@MockK(relaxed = true)
	protected lateinit var accountsComponent: AccountsComponent

	@MockK(relaxed = true)
	protected lateinit var adminComponent: AdminComponent

	@MockK(relaxed = true)
	protected lateinit var configRepository: ConfigRepository

	@MockK(relaxed = true)
	protected lateinit var storyRendererService: StoryRendererService

	@MockK(relaxed = true)
	protected lateinit var penNameService: PenNameService

	@MockK(relaxed = true)
	protected lateinit var bioService: BioService

	@MockK(relaxed = true)
	protected lateinit var passwordResetRepository: PasswordResetRepository

	@MockK(relaxed = true)
	protected lateinit var json: Json

	@MockK(relaxed = true)
	protected lateinit var markdownService: MarkdownService

	protected lateinit var testModule: org.koin.core.module.Module

	protected val BEARER_TOKEN = "token-test"

	@BeforeEach
	override fun setup() {
		super.setup()

		MockKAnnotations.init(this, relaxUnitFun = true)

		// The bearer gate loads the account after token validation; default to an
		// active, allowed one (the relaxed mock's isOnWhiteList would return false).
		coEvery { accountsRepository.getAccountOrNull(any()) } returns testAccount()
		coEvery { whiteListRepository.isOnWhiteList(any()) } returns true

		testModule = module {
			single { accountsRepository }
			single { whiteListRepository }
			single { projectEntityRepository }
			single { serverWritingActivityRepository }
			single { serverProjectDataRepository }
			single { projectAccessRepository }
			single { projectsRepository }
			single { accountsComponent }
			single { adminComponent }
			single { configRepository }
			single { storyRendererService }
			single { penNameService }
			single { bioService }
			single { json }
			single { passwordResetRepository }
			single { markdownService }
			single { mockk<com.darkrockstudios.apps.hammer.review.ReviewRepository>(relaxed = true) }
			single { mockk<com.darkrockstudios.apps.hammer.storyideas.ServerIdeasRepository>(relaxed = true) }
			single { mockk<com.darkrockstudios.apps.hammer.database.ProjectDao>(relaxed = true) }
			single { mockk<com.darkrockstudios.apps.hammer.account.AccountDeletionService>(relaxed = true) }
			single { mockk<com.darkrockstudios.apps.hammer.account.TermsOfServiceRepository>(relaxed = true) }
		}
	}

	protected fun ApplicationTestBuilder.defaultApplication(moreSetup: (Application.() -> Unit)? = null) {
		application {
			setupKtorTestKoin(this@ProjectsRoutesBaseTest, testModule)

			configureSerialization()
			configureSecurity()
			configureLocalization()
			configureRouting()

			if (moreSetup != null) moreSetup()
		}
	}
}

