package com.darkrockstudios.apps.hammer.common.dependencyinjection

import com.darkrockstudios.apps.hammer.base.http.createJsonSerializer
import com.darkrockstudios.apps.hammer.common.components.projecthome.ExportStoryUseCase
import com.darkrockstudios.apps.hammer.common.components.projecthome.ImportStoryUseCase
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.account.AccountReauthUseCase
import com.darkrockstudios.apps.hammer.common.data.account.AccountUseCase
import com.darkrockstudios.apps.hammer.common.data.drafts.SceneDraftRepository
import com.darkrockstudios.apps.hammer.common.data.drafts.SceneDraftsDatasource
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EncyclopediaDatasource
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EncyclopediaRepository
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EncyclopediaService
import com.darkrockstudios.apps.hammer.common.data.exampleProjectModule
import com.darkrockstudios.apps.hammer.common.data.globalsearch.SearchProjectUseCase
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsStore
import com.darkrockstudios.apps.hammer.common.data.globalsettings.datasource.AuthTokenStore
import com.darkrockstudios.apps.hammer.common.data.globalsettings.datasource.FileAuthTokenStore
import com.darkrockstudios.apps.hammer.common.data.globalsettings.datasource.GlobalSettingsDatasource
import com.darkrockstudios.apps.hammer.common.data.globalsettings.datasource.GlobalSettingsFilesystemDatasource
import com.darkrockstudios.apps.hammer.common.data.globalsettings.datasource.ServerSettingsDatasource
import com.darkrockstudios.apps.hammer.common.data.globalsettings.datasource.ServerSettingsFilesystemDatasource
import com.darkrockstudios.apps.hammer.common.data.id.IdAllocator
import com.darkrockstudios.apps.hammer.common.data.id.datasources.*
import com.darkrockstudios.apps.hammer.common.data.importer.MarkdownStoryImporter
import com.darkrockstudios.apps.hammer.common.data.importer.StoryImporter
import com.darkrockstudios.apps.hammer.common.data.notesrepository.NotesDatasource
import com.darkrockstudios.apps.hammer.common.data.notesrepository.NotesRepository
import com.darkrockstudios.apps.hammer.common.data.projectbackup.ProjectBackupRepository
import com.darkrockstudios.apps.hammer.common.data.projectdata.ProjectDataConflictBroker
import com.darkrockstudios.apps.hammer.common.data.projectdata.ProjectDataDatasource
import com.darkrockstudios.apps.hammer.common.data.projectdata.ProjectDataRepository
import com.darkrockstudios.apps.hammer.common.data.projectmetadata.ProjectMetadataDatasource
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository
import com.darkrockstudios.apps.hammer.common.data.projectstatistics.ProjectStatisticsCacheReader
import com.darkrockstudios.apps.hammer.common.data.projectstatistics.StatisticsDatasource
import com.darkrockstudios.apps.hammer.common.data.projectstatistics.StatisticsRepository
import com.darkrockstudios.apps.hammer.common.data.projectstatistics.StatisticsService
import com.darkrockstudios.apps.hammer.common.data.references.*
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.*
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.scenemetadata.SceneMetadataDatasource
import com.darkrockstudios.apps.hammer.common.data.sync.accountsync.ClientAccountSynchronizer
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.ClientProjectSynchronizer
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.EntitySynchronizers
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.SyncDataDatasource
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.SyncJournal
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.operations.*
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.synchronizers.*
import com.darkrockstudios.apps.hammer.common.data.tagindex.BuildTagIndexUseCase
import com.darkrockstudios.apps.hammer.common.data.tagindex.TagIndexService
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineDatasource
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineRepository
import com.darkrockstudios.apps.hammer.common.data.versioncheck.GithubVersionCheckDataSource
import com.darkrockstudios.apps.hammer.common.data.versioncheck.ShouldNotifyOfUpdateUseCase
import com.darkrockstudios.apps.hammer.common.data.versioncheck.VersionCheckDataSource
import com.darkrockstudios.apps.hammer.common.data.versioncheck.VersionCheckRepository
import com.darkrockstudios.apps.hammer.common.data.writingactivity.WritingActivityDatasource
import com.darkrockstudios.apps.hammer.common.data.writingactivity.WritingActivityRepository
import com.darkrockstudios.apps.hammer.common.data.writingactivity.WritingSessionTracker
import com.darkrockstudios.apps.hammer.common.fileio.externalFileIoModule
import com.darkrockstudios.apps.hammer.common.fileio.okio.ContainedFileSystem
import com.darkrockstudios.apps.hammer.common.getCacheDirectory
import com.darkrockstudios.apps.hammer.common.getConfigDirectory
import com.darkrockstudios.apps.hammer.common.getPlatformFilesystem
import com.darkrockstudios.apps.hammer.common.platformDefaultDispatcher
import com.darkrockstudios.apps.hammer.common.platformIoDispatcher
import com.darkrockstudios.apps.hammer.common.platformMainDispatcher
import com.darkrockstudios.apps.hammer.common.server.*
import com.darkrockstudios.apps.hammer.common.spellcheck.SpellCheckRepository
import com.russhwolf.settings.Settings
import io.ktor.client.*
import kotlinx.serialization.json.Json
import net.peanuuutz.tomlkt.Toml
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
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

/** Unguarded platform [FileSystem] for user-chosen external write targets (exports). */
const val RAW_FILESYSTEM = "raw-filesystem"

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

	single<NameMatcher> { WholeWordCaseSensitiveMatcher() }
	single { ReferenceIndexConfig.default() }

	includes(platformModule)

	single { createHttpClient(get()) } bind HttpClient::class
	singleOf(::ServerAccountApi)
	singleOf(::ServerProjectApi)
	singleOf(::ServerProjectsApi)
	singleOf(::WritingActivityApi)
	singleOf(::ProjectDataApi)
	singleOf(::ServerAdminApi)

	singleOf(::FileAuthTokenStore) bind AuthTokenStore::class
	singleOf(::ServerSettingsFilesystemDatasource) bind ServerSettingsDatasource::class
	singleOf(::GlobalSettingsFilesystemDatasource)
	singleOf(::GlobalSettingsStore) bind GlobalSettingsStore::class

	singleOf(::GithubVersionCheckDataSource) bind VersionCheckDataSource::class
	singleOf(::VersionCheckRepository)
	factoryOf(::ShouldNotifyOfUpdateUseCase)

	factory { AccountUseCase(get(), get(), get(), get()) }
	factoryOf(::AccountReauthUseCase)

	single(named(RAW_FILESYSTEM)) { getPlatformFilesystem() } bind FileSystem::class

	// Default filesystem: guards every write against the app's managed storage roots.
	single<FileSystem> {
		val koin = getKoin()
		ContainedFileSystem(getPlatformFilesystem()) { managedStorageRoots(koin) }
	}

	singleOf(::ProjectsRepository)

	singleOf(::createTomlSerializer) bind Toml::class

	singleOf(::createJsonSerializer) bind Json::class

	singleOf(::ClientAccountSynchronizer)

	singleOf(::ProjectBackupRepository)

	singleOf(::ProjectMetadataDatasource)

	singleOf(::ProjectStatisticsCacheReader)

	singleOf(::Settings) bind Settings::class

	includes(migratorModule)

	singleOf(::SpellCheckRepository)

	single<StoryImporter> { MarkdownStoryImporter() }

	scope<ProjectDefScope> {
		scoped<ProjectDef> { get<ProjectDefScope>().projectDef }

		scopedOf(::SceneDatasource)
		scopedOf(::SceneContentRepository)
		scopedOf(::SceneRepository)
		scopedOf(::SceneEditorService)
		scopedOf(::ImportStoryUseCase)
		// Export writes to a user-chosen path, so it uses the unguarded filesystem.
		scoped {
			ExportStoryUseCase(
				sceneEditorRepository = get(),
				projectDataDatasource = get(),
				fileSystem = get(named(RAW_FILESYSTEM)),
				localeResolver = get(),
			)
		}
		scopedOf(::SceneDraftsDatasource)
		scopedOf(::SceneDraftRepository)
		scopedOf(::SceneMetadataDatasource)
		scopedOf(::SceneMetadataRepository)

		factoryOf(::SceneIdDatasource)
		factoryOf(::NotesIdDatasource)
		factoryOf(::EncyclopediaIdDatasource)
		factoryOf(::TimeLineEventIdDatasource)
		factoryOf(::SceneDraftIdDatasource)
		scopedOf(::IdAllocator)

		scopedOf(::NotesDatasource)
		scopedOf(::NotesRepository)

		factoryOf(::EncyclopediaDatasource)
		scopedOf(::EncyclopediaRepository)
		scopedOf(::EncyclopediaService)

		scopedOf(::TimeLineDatasource)
		scopedOf(::TimeLineRepository)

		scopedOf(::SearchProjectUseCase)

		scopedOf(::StatisticsDatasource)
		scopedOf(::StatisticsRepository)
		scopedOf(::StatisticsService)

		scopedOf(::WritingActivityDatasource)
		scopedOf(::WritingActivityRepository)
		scopedOf(::WritingSessionTracker)

		scopedOf(::ProjectDataDatasource)
		scopedOf(::ProjectDataRepository)
		scopedOf(::ProjectDataConflictBroker)

		scopedOf(::ReferenceIndexDatasource)
		scopedOf(::ReferenceIndexRepository)
		scopedOf(::ReferenceIndexService)

		scopedOf(::BuildTagIndexUseCase)
		scopedOf(::TagIndexService)
		scopedOf(::ScrubInvalidReferencesUseCase)
		scopedOf(::AutoConfirmReferencesUseCase)
		scopedOf(::BackfillEntryReferencesUseCase)
		scopedOf(::CleanupReferencesOnEntryDeleteUseCase)
		scopedOf(::SceneMetadataReferenceRemapper) bind ReferenceRemapper::class

		scopedOf(::SyncDataDatasource)

		scopedOf(::ClientSceneSynchronizer)
		scopedOf(::ClientNoteSynchronizer)
		scopedOf(::ClientTimelineSynchronizer)
		scopedOf(::ClientEncyclopediaSynchronizer)
		scopedOf(::ClientSceneDraftSynchronizer)

		factoryOf(::PrepareForSyncOperation)
		factoryOf(::EnsureProjectIdOperation)
		factoryOf(::FetchLocalDataOperation)
		factoryOf(::FetchServerDataOperation)
		factoryOf(::CollateIdsOperation)
		factoryOf(::BackupOperation)
		factoryOf(::IdConflictResolutionOperation)
		factoryOf(::EntityDeleteOperation)
		factoryOf(::EntityTransferOperation)
		factoryOf(::WritingActivitySyncOperation)
		factoryOf(::ProjectDataSyncOperation)
		factoryOf(::FinalizeSyncOperation)

		scopedOf(::SyncJournal)
		scopedOf(::ClientProjectSynchronizer)

		scopedOf(::EntitySynchronizers)
	}
}

/**
 * Managed storage roots checked by [ContainedFileSystem]: cache, config, and the
 * (user-relocatable, resolved per call) projects directory. Projects resolution is
 * cycle-safe — store, then persisted settings, then default — so config-root writes
 * during the store's own construction aren't blocked.
 */
internal fun managedStorageRoots(koin: org.koin.core.Koin): List<Path> {
	val cacheRoot = getCacheDirectory().toPath()
	val configRoot = getConfigDirectory().toPath()

	val projectsRoot = runCatching {
		koin.get<GlobalSettingsStore>().globalSettings.projectsDirectory.toPath()
	}.recoverCatching {
		koin.get<GlobalSettingsDatasource>().loadSettings().projectsDirectory.toPath()
	}.getOrElse {
		GlobalSettingsStore.defaultProjectDir()
	}

	return listOf(cacheRoot, configRoot, projectsRoot)
}