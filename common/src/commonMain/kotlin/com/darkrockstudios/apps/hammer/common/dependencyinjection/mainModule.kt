package com.darkrockstudios.apps.hammer.common.dependencyinjection

import com.darkrockstudios.apps.hammer.base.http.createJsonSerializer
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.account.AccountReauthUseCase
import com.darkrockstudios.apps.hammer.common.data.account.AccountUseCase
import com.darkrockstudios.apps.hammer.common.data.drafts.SceneDraftRepository
import com.darkrockstudios.apps.hammer.common.data.drafts.SceneDraftsDatasource
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EncyclopediaDatasource
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EncyclopediaRepository
import com.darkrockstudios.apps.hammer.common.data.exampleProjectModule
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsRepository
import com.darkrockstudios.apps.hammer.common.data.globalsettings.datasource.GlobalSettingsDatasource
import com.darkrockstudios.apps.hammer.common.data.globalsettings.datasource.GlobalSettingsFilesystemDatasource
import com.darkrockstudios.apps.hammer.common.data.globalsettings.datasource.ServerSettingsDatasource
import com.darkrockstudios.apps.hammer.common.data.globalsettings.datasource.ServerSettingsFilesystemDatasource
import com.darkrockstudios.apps.hammer.common.data.id.IdRepository
import com.darkrockstudios.apps.hammer.common.data.id.datasources.*
import com.darkrockstudios.apps.hammer.common.data.notesrepository.NotesDatasource
import com.darkrockstudios.apps.hammer.common.data.notesrepository.NotesRepository
import com.darkrockstudios.apps.hammer.common.data.projectbackup.ProjectBackupRepository
import com.darkrockstudios.apps.hammer.common.data.projectbackup.createProjectBackup
import com.darkrockstudios.apps.hammer.common.data.projectmetadata.ProjectMetadataDatasource
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneDatasource
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneEditorRepository
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.scenemetadata.SceneMetadataDatasource
import com.darkrockstudios.apps.hammer.common.data.sync.accountsync.ClientAccountSynchronizer
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.ClientProjectSynchronizer
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.EntitySynchronizers
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.SyncDataDatasource
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.SyncDataRepository
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.operations.*
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.synchronizers.*
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineDatasource
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineRepository
import com.darkrockstudios.apps.hammer.common.fileio.externalFileIoModule
import com.darkrockstudios.apps.hammer.common.getPlatformFilesystem
import com.darkrockstudios.apps.hammer.common.platformDefaultDispatcher
import com.darkrockstudios.apps.hammer.common.platformIoDispatcher
import com.darkrockstudios.apps.hammer.common.platformMainDispatcher
import com.darkrockstudios.apps.hammer.common.server.ServerAccountApi
import com.darkrockstudios.apps.hammer.common.server.ServerAdminApi
import com.darkrockstudios.apps.hammer.common.server.ServerProjectApi
import com.darkrockstudios.apps.hammer.common.server.ServerProjectsApi
import com.darkrockstudios.apps.hammer.common.spellcheck.SpellCheckRepository
import com.russhwolf.settings.Settings
import io.ktor.client.*
import kotlinx.serialization.json.Json
import net.peanuuutz.tomlkt.Toml
import okio.FileSystem
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.scopedOf
import org.koin.core.module.dsl.singleOf
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module
import kotlin.time.Clock

const val DISPATCHER_MAIN = "main-dispatcher"
const val DISPATCHER_DEFAULT = "default-dispatcher"
const val DISPATCHER_IO = "io-dispatcher"

/**
 * This is the main module containing most of the DI objects
 */
val mainModule = module {
	includes(externalFileIoModule)
	includes(exampleProjectModule)

	single(named(DISPATCHER_MAIN)) { platformMainDispatcher }
	single(named(DISPATCHER_DEFAULT)) { platformDefaultDispatcher }
	single(named(DISPATCHER_IO)) { platformIoDispatcher }

	single { Clock.System } bind Clock::class

	includes(platformModule)

	single { createHttpClient(get()) } bind HttpClient::class
	singleOf(::ServerAccountApi)
	singleOf(::ServerProjectApi)
	singleOf(::ServerProjectsApi)
	singleOf(::ServerAdminApi)

	singleOf(::ServerSettingsFilesystemDatasource) bind ServerSettingsDatasource::class
	singleOf(::GlobalSettingsFilesystemDatasource) bind GlobalSettingsDatasource::class
	singleOf(::GlobalSettingsRepository) bind GlobalSettingsRepository::class

	factory { AccountUseCase(get(), get(), get()) }
	factoryOf(::AccountReauthUseCase)

	singleOf(::getPlatformFilesystem) bind FileSystem::class

	singleOf(::ProjectsRepository)

	singleOf(::createTomlSerializer) bind Toml::class

	singleOf(::createJsonSerializer) bind Json::class

	singleOf(::ClientAccountSynchronizer)

	singleOf(::createProjectBackup) bind ProjectBackupRepository::class

	singleOf(::ProjectMetadataDatasource)

	singleOf(::Settings) bind Settings::class

	includes(migratorModule)

	singleOf(::SpellCheckRepository)

	scope<ProjectDefScope> {
		scoped<ProjectDef> { get<ProjectDefScope>().projectDef }

		scopedOf(::SceneDatasource)
		scopedOf(::SceneEditorRepository)
		scopedOf(::SceneDraftsDatasource)
		scopedOf(::SceneDraftRepository)
		scopedOf(::SceneMetadataDatasource)

		factoryOf(::SceneIdDatasource)
		factoryOf(::NotesIdDatasource)
		factoryOf(::EncyclopediaIdDatasource)
		factoryOf(::TimeLineEventIdDatasource)
		factoryOf(::SceneDraftIdDatasource)
		scopedOf(::IdRepository)

		scopedOf(::NotesDatasource)
		scopedOf(::NotesRepository)

		factoryOf(::EncyclopediaDatasource)
		scopedOf(::EncyclopediaRepository)

		scopedOf(::TimeLineDatasource)
		scopedOf(::TimeLineRepository)

		scopedOf(::SyncDataDatasource)

		scopedOf(::ClientSceneSynchronizer)
		scopedOf(::ClientNoteSynchronizer)
		scopedOf(::ClientTimelineSynchronizer)
		scopedOf(::ClientEncyclopediaSynchronizer)
		scopedOf(::ClientSceneDraftSynchronizer)

		factoryOf(::PrepareForSyncOperation)
		factoryOf(::FetchLocalDataOperation)
		factoryOf(::FetchServerDataOperation)
		factoryOf(::CollateIdsOperation)
		factoryOf(::BackupOperation)
		factoryOf(::IdConflictResolutionOperation)
		factoryOf(::EntityDeleteOperation)
		factoryOf(::EntityTransferOperation)
		factoryOf(::FinalizeSyncOperation)

		scopedOf(::SyncDataRepository)
		scopedOf(::ClientProjectSynchronizer)

		scopedOf(::EntitySynchronizers)
	}
}