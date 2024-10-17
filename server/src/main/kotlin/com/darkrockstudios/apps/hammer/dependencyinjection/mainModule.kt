package com.darkrockstudios.apps.hammer.dependencyinjection

import com.darkrockstudios.apps.hammer.account.AccountsComponent
import com.darkrockstudios.apps.hammer.account.AccountsRepository
import com.darkrockstudios.apps.hammer.admin.AdminComponent
import com.darkrockstudios.apps.hammer.admin.WhiteListRepository
import com.darkrockstudios.apps.hammer.base.http.createJsonSerializer
import com.darkrockstudios.apps.hammer.base.http.createTokenBase64
import com.darkrockstudios.apps.hammer.database.AccountDao
import com.darkrockstudios.apps.hammer.database.AuthTokenDao
import com.darkrockstudios.apps.hammer.database.Database
import com.darkrockstudios.apps.hammer.database.DeletedEntityDao
import com.darkrockstudios.apps.hammer.database.DeletedProjectDao
import com.darkrockstudios.apps.hammer.database.ProjectDao
import com.darkrockstudios.apps.hammer.database.ProjectsDao
import com.darkrockstudios.apps.hammer.database.SqliteDatabase
import com.darkrockstudios.apps.hammer.database.StoryEntityDao
import com.darkrockstudios.apps.hammer.database.WhiteListDao
import com.darkrockstudios.apps.hammer.encryption.AesGcmContentEncryptor
import com.darkrockstudios.apps.hammer.encryption.AesGcmKeyProvider
import com.darkrockstudios.apps.hammer.encryption.ContentEncryptor
import com.darkrockstudios.apps.hammer.encryption.SimpleFileBasedAesGcmKeyProvider
import com.darkrockstudios.apps.hammer.project.ProjectEntityDatabaseDatasource
import com.darkrockstudios.apps.hammer.project.ProjectEntityDatasource
import com.darkrockstudios.apps.hammer.project.ProjectEntityRepository
import com.darkrockstudios.apps.hammer.project.ProjectSyncKey
import com.darkrockstudios.apps.hammer.project.ProjectSynchronizationSession
import com.darkrockstudios.apps.hammer.project.synchronizers.ServerEncyclopediaSynchronizer
import com.darkrockstudios.apps.hammer.project.synchronizers.ServerNoteSynchronizer
import com.darkrockstudios.apps.hammer.project.synchronizers.ServerSceneDraftSynchronizer
import com.darkrockstudios.apps.hammer.project.synchronizers.ServerSceneSynchronizer
import com.darkrockstudios.apps.hammer.project.synchronizers.ServerTimelineSynchronizer
import com.darkrockstudios.apps.hammer.projects.ProjectsDatabaseDatasource
import com.darkrockstudios.apps.hammer.projects.ProjectsDatasource
import com.darkrockstudios.apps.hammer.projects.ProjectsRepository
import com.darkrockstudios.apps.hammer.projects.ProjectsSynchronizationSession
import com.darkrockstudios.apps.hammer.syncsessionmanager.SyncSessionManager
import io.ktor.util.logging.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import okio.FileSystem
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module
import java.security.SecureRandom
import kotlin.coroutines.CoroutineContext
import kotlin.io.encoding.Base64

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
	single { Clock.System } bind Clock::class
	single { createTokenBase64() } bind Base64::class
	single { SecureRandom.getInstanceStrong() } bind SecureRandom::class
	single { FileSystem.SYSTEM } bind FileSystem::class
	singleOf(::SqliteDatabase) bind Database::class
	singleOf(::AccountDao)
	singleOf(::AuthTokenDao)
	singleOf(::WhiteListDao)
	singleOf(::StoryEntityDao)
	singleOf(::ProjectsDao)
	singleOf(::ProjectDao)
	singleOf(::DeletedProjectDao)
	singleOf(::DeletedEntityDao)

	singleOf(::AccountsRepository)
	singleOf(::ProjectsRepository)
	singleOf(::ProjectEntityRepository)
	singleOf(::WhiteListRepository)

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