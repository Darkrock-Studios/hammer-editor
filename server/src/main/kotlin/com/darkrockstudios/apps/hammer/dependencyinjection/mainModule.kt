package com.darkrockstudios.apps.hammer.dependencyinjection

import com.darkrockstudios.apps.hammer.account.AccountsComponent
import com.darkrockstudios.apps.hammer.account.AccountsRepository
import com.darkrockstudios.apps.hammer.admin.AdminComponent
import com.darkrockstudios.apps.hammer.admin.WhiteListRepository
import com.darkrockstudios.apps.hammer.base.http.createJsonSerializer
import com.darkrockstudios.apps.hammer.database.*
import com.darkrockstudios.apps.hammer.project.ProjectRepository
import com.darkrockstudios.apps.hammer.project.ProjectSynchronizationSession
import com.darkrockstudios.apps.hammer.project.synchronizers.*
import com.darkrockstudios.apps.hammer.projects.ProjectsRepository
import com.darkrockstudios.apps.hammer.projects.ProjectsSynchronizationSession
import com.darkrockstudios.apps.hammer.syncsessionmanager.SyncSessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import okio.FileSystem
import org.koin.core.module.dsl.singleOf
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module
import kotlin.coroutines.CoroutineContext

const val DISPATCHER_MAIN = "main-dispatcher"
const val DISPATCHER_DEFAULT = "default-dispatcher"
const val DISPATCHER_IO = "io-dispatcher"

val mainModule = module {
	single<CoroutineContext>(named(DISPATCHER_MAIN)) { Dispatchers.Unconfined }
	single<CoroutineContext>(named(DISPATCHER_DEFAULT)) { Dispatchers.Default }
	single<CoroutineContext>(named(DISPATCHER_IO)) { Dispatchers.IO }

	singleOf(::createJsonSerializer) bind Json::class
	single { Clock.System } bind Clock::class

	single { FileSystem.SYSTEM } bind FileSystem::class
	singleOf(::SqliteDatabase) bind Database::class
	singleOf(::AccountDao)
	singleOf(::AuthTokenDao)
	singleOf(::WhiteListDao)

	singleOf(::AccountsRepository)
	singleOf(::ProjectsRepository)
	singleOf(::ProjectRepository)
	singleOf(::WhiteListRepository)

	singleOf(::AdminComponent)
	singleOf(::AccountsComponent)

	singleOf(::ServerSceneSynchronizer)
	singleOf(::ServerNoteSynchronizer)
	singleOf(::ServerTimelineSynchronizer)
	singleOf(::ServerEncyclopediaSynchronizer)
	singleOf(::ServerSceneDraftSynchronizer)

	single<SyncSessionManager<ProjectsSynchronizationSession>>(named(PROJECTS_SYNC_MANAGER)) {
		SyncSessionManager(get())
	}

	single<SyncSessionManager<ProjectSynchronizationSession>>(named(PROJECT_SYNC_MANAGER)) {
		SyncSessionManager(get())
	}
}

const val PROJECTS_SYNC_MANAGER = "projects_sync_manager"
const val PROJECT_SYNC_MANAGER = "project_sync_manager"