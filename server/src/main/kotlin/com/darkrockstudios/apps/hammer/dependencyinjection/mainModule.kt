package com.darkrockstudios.apps.hammer.dependencyinjection

import com.darkrockstudios.apps.hammer.account.AccountsComponent
import com.darkrockstudios.apps.hammer.account.AccountsRepository
import com.darkrockstudios.apps.hammer.account.PenNameService
import com.darkrockstudios.apps.hammer.admin.AdminComponent
import com.darkrockstudios.apps.hammer.admin.ConfigRepository
import com.darkrockstudios.apps.hammer.admin.WhiteListRepository
import com.darkrockstudios.apps.hammer.base.http.createJsonSerializer
import com.darkrockstudios.apps.hammer.base.http.createTokenBase64
import com.darkrockstudios.apps.hammer.database.*
import com.darkrockstudios.apps.hammer.encryption.AesGcmContentEncryptor
import com.darkrockstudios.apps.hammer.encryption.AesGcmKeyProvider
import com.darkrockstudios.apps.hammer.encryption.ContentEncryptor
import com.darkrockstudios.apps.hammer.encryption.SimpleFileBasedAesGcmKeyProvider
import com.darkrockstudios.apps.hammer.project.*
import com.darkrockstudios.apps.hammer.project.access.ProjectAccessRepository
import com.darkrockstudios.apps.hammer.project.synchronizers.*
import com.darkrockstudios.apps.hammer.projects.ProjectsDatabaseDatasource
import com.darkrockstudios.apps.hammer.projects.ProjectsDatasource
import com.darkrockstudios.apps.hammer.projects.ProjectsRepository
import com.darkrockstudios.apps.hammer.projects.ProjectsSynchronizationSession
import com.darkrockstudios.apps.hammer.story.StoryExportService
import com.darkrockstudios.apps.hammer.syncsessionmanager.SyncSessionManager
import io.ktor.util.logging.*
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import net.peanuuutz.tomlkt.Toml
import okio.FileSystem
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module
import java.security.SecureRandom
import kotlin.coroutines.CoroutineContext
import kotlin.io.encoding.Base64
import kotlin.time.Clock

const val DISPATCHER_MAIN = "main-dispatcher"
const val DISPATCHER_DEFAULT = "default-dispatcher"
const val DISPATCHER_IO = "io-dispatcher"

fun mainModule(
	logger: Logger,
) = module {
	single<CoroutineContext>(named(DISPATCHER_MAIN)) { Dispatchers.Unconfined }
	single<CoroutineContext>(named(DISPATCHER_DEFAULT)) { Dispatchers.Default }
	single<CoroutineContext>(named(DISPATCHER_IO)) { Dispatchers.IO }

	single { logger }

	singleOf(::createJsonSerializer) bind Json::class
	single { Toml { ignoreUnknownKeys = true } } bind Toml::class
	single { Clock.System } bind Clock::class
	single { createTokenBase64() } bind Base64::class
	single { SecureRandom.getInstanceStrong() } bind SecureRandom::class
	single { FileSystem.SYSTEM } bind FileSystem::class
	single { SqliteDatabase(fileSystem = get()) } bind Database::class
	singleOf(::AccountDao)
	singleOf(::AuthTokenDao)
	singleOf(::WhiteListDao)
	singleOf(::StoryEntityDao)
	singleOf(::ProjectsDao)
	singleOf(::ProjectDao)
	singleOf(::DeletedProjectDao)
	singleOf(::DeletedEntityDao)
	singleOf(::ServerConfigDao)
	singleOf(::ProjectAccessDao)

	singleOf(::AccountsRepository)
	singleOf(::ProjectsRepository)
	singleOf(::ProjectEntityRepository)
	singleOf(::ProjectAccessRepository)
	singleOf(::WhiteListRepository)
	singleOf(::ConfigRepository)
	singleOf(::StoryExportService)
	singleOf(::PenNameService)

	singleOf(::SimpleFileBasedAesGcmKeyProvider) bind AesGcmKeyProvider::class
	singleOf(::AesGcmContentEncryptor) bind ContentEncryptor::class

	factoryOf(::ProjectsDatabaseDatasource) bind ProjectsDatasource::class
	factoryOf(::ProjectEntityDatabaseDatasource) bind ProjectEntityDatasource::class

	singleOf(::AdminComponent)
	singleOf(::AccountsComponent)

	singleOf(::ServerSceneSynchronizer)
	singleOf(::ServerNoteSynchronizer)
	singleOf(::ServerTimelineSynchronizer)
	singleOf(::ServerEncyclopediaSynchronizer)
	singleOf(::ServerSceneDraftSynchronizer)

	single<SyncSessionManager<Long, ProjectsSynchronizationSession>>(named(PROJECTS_SYNC_MANAGER)) {
		SyncSessionManager(get(), get())
	}

	single<SyncSessionManager<ProjectSyncKey, ProjectSynchronizationSession>>(
		named(
			PROJECT_SYNC_MANAGER
		)
	) {
		SyncSessionManager(get(), get())
	}
}

const val PROJECTS_SYNC_MANAGER = "projects_sync_manager"
const val PROJECT_SYNC_MANAGER = "project_sync_manager"