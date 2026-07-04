package com.darkrockstudios.apps.hammer.projects.routes

import com.darkrockstudios.apps.hammer.account.*
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
import com.darkrockstudios.apps.hammer.story.StoryExportService
import com.darkrockstudios.apps.hammer.utilities.MarkdownService
import com.darkrockstudios.apps.hammer.utils.BaseTest
import com.darkrockstudios.apps.hammer.utils.setupKtorTestKoin
import io.ktor.server.application.*
import io.ktor.server.testing.*
import io.mockk.MockKAnnotations
import io.mockk.mockk
import io.mockk.impl.annotations.MockK
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
	protected lateinit var storyExportService: StoryExportService

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
			single { storyExportService }
			single { penNameService }
			single { bioService }
			single { json }
			single { passwordResetRepository }
			single { markdownService }
			single { mockk<com.darkrockstudios.apps.hammer.review.ReviewRepository>(relaxed = true) }
			single { mockk<com.darkrockstudios.apps.hammer.storyideas.ServerIdeasRepository>(relaxed = true) }
			single { mockk<com.darkrockstudios.apps.hammer.database.ProjectDao>(relaxed = true) }
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

