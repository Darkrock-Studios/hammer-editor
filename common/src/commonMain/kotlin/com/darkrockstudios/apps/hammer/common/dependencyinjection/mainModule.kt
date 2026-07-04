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
import com.darkrockstudios.apps.hammer.common.data.globalsettings.datasource.GlobalSettingsDatasource
import com.darkrockstudios.apps.hammer.common.data.globalsettings.datasource.GlobalSettingsFilesystemDatasource
import com.darkrockstudios.apps.hammer.common.data.globalsettings.datasource.ServerSettingsDatasource
import com.darkrockstudios.apps.hammer.common.data.globalsettings.datasource.ServerSettingsFilesystemDatasource
import com.darkrockstudios.apps.hammer.common.data.globalsettings.datasource.authTokenStoreModule
import com.darkrockstudios.apps.hammer.common.data.id.IdAllocator
import com.darkrockstudios.apps.hammer.common.data.id.datasources.EncyclopediaIdDatasource
import com.darkrockstudios.apps.hammer.common.data.id.datasources.NotesIdDatasource
import com.darkrockstudios.apps.hammer.common.data.id.datasources.SceneDraftIdDatasource
import com.darkrockstudios.apps.hammer.common.data.id.datasources.SceneIdDatasource
import com.darkrockstudios.apps.hammer.common.data.id.datasources.TimeLineEventIdDatasource
import com.darkrockstudios.apps.hammer.common.data.ideasrepository.IdeasDatasource
import com.darkrockstudios.apps.hammer.common.data.ideasrepository.IdeasRepository
import com.darkrockstudios.apps.hammer.common.data.ideasrepository.PromoteIdeaUseCase
import com.darkrockstudios.apps.hammer.common.data.ideasrepository.StoryIdeaCodec
import com.darkrockstudios.apps.hammer.common.data.importer.MarkdownStoryImporter
import com.darkrockstudios.apps.hammer.common.data.importer.RtfStoryImporter
import com.darkrockstudios.apps.hammer.common.data.importer.StoryImporterRegistry
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
import com.darkrockstudios.apps.hammer.common.data.references.AutoConfirmReferencesUseCase
import com.darkrockstudios.apps.hammer.common.data.references.BackfillEntryReferencesUseCase
import com.darkrockstudios.apps.hammer.common.data.references.CleanupReferencesOnEntryDeleteUseCase
import com.darkrockstudios.apps.hammer.common.data.references.NameMatcher
import com.darkrockstudios.apps.hammer.common.data.references.ReferenceIndexConfig
import com.darkrockstudios.apps.hammer.common.data.references.ReferenceIndexDatasource
import com.darkrockstudios.apps.hammer.common.data.references.ReferenceIndexRepository
import com.darkrockstudios.apps.hammer.common.data.references.ReferenceIndexService
import com.darkrockstudios.apps.hammer.common.data.references.ReferenceRemapper
import com.darkrockstudios.apps.hammer.common.data.references.SceneMetadataReferenceRemapper
import com.darkrockstudios.apps.hammer.common.data.references.ScrubInvalidReferencesUseCase
import com.darkrockstudios.apps.hammer.common.data.references.WholeWordCaseSensitiveMatcher
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneContentRepository
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneDatasource
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneEditorService
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneMetadataRepository
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneRepository
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.scenemetadata.SceneMetadataDatasource
import com.darkrockstudios.apps.hammer.common.data.sync.accountsync.ClientAccountSynchronizer
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.ClientProjectSynchronizer
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.EntitySynchronizers
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.SyncDataDatasource
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.SyncJournal
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.operations.BackupOperation
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.operations.CollateIdsOperation
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.operations.EnsureProjectIdOperation
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.operations.EntityDeleteOperation
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.operations.EntityTransferOperation
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.operations.FetchLocalDataOperation
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.operations.FetchServerDataOperation
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.operations.FinalizeSyncOperation
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.operations.IdConflictResolutionOperation
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.operations.PrepareForSyncOperation
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.operations.ProjectDataSyncOperation
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.operations.WritingActivitySyncOperation
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.synchronizers.ClientEncyclopediaSynchronizer
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.synchronizers.ClientNoteSynchronizer
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.synchronizers.ClientSceneDraftSynchronizer
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.synchronizers.ClientSceneSynchronizer
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.synchronizers.ClientTimelineSynchronizer
import com.darkrockstudios.apps.hammer.common.data.tagindex.AccountTagService
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
import com.darkrockstudios.apps.hammer.common.server.ProjectDataApi
import com.darkrockstudios.apps.hammer.common.server.ServerAccountApi
import com.darkrockstudios.apps.hammer.common.server.ServerAdminApi
import com.darkrockstudios.apps.hammer.common.server.ServerProjectApi
import com.darkrockstudios.apps.hammer.common.server.ServerProjectsApi
import com.darkrockstudios.apps.hammer.common.server.WritingActivityApi
import com.darkrockstudios.apps.hammer.common.spellcheck.SpellCheckRepository
import com.russhwolf.settings.Settings
import io.ktor.client.HttpClient
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
	includes(authTokenStoreModule)
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

	singleOf(::StoryIdeaCodec)
	singleOf(::IdeasDatasource)
	singleOf(::IdeasRepository)
	factoryOf(::PromoteIdeaUseCase)

	singleOf(::AccountTagService)

	singleOf(::createTomlSerializer) bind Toml::class

	singleOf(::createJsonSerializer) bind Json::class

	singleOf(::ClientAccountSynchronizer)

	singleOf(::ProjectBackupRepository)

	singleOf(::ProjectMetadataDatasource)

	singleOf(::ProjectStatisticsCacheReader)

	singleOf(::Settings) bind Settings::class

	includes(migratorModule)

	singleOf(::SpellCheckRepository)

	single { StoryImporterRegistry(listOf(MarkdownStoryImporter(), RtfStoryImporter())) }

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
				strRes = get(),
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
 * (user-relocatable, resolved per call) projects directory. Projects resolution
 * degrades gracefully — store, then persisted settings, then default — so a guarded
 * write can resolve the root before settings are loaded.
 *
 * This chain does NOT make construction-time writes safe: Koin re-enters a still-
 * constructing singleton instead of throwing, so a guarded write fired from
 * [GlobalSettingsStore]'s own construction recurses here forever (the fallbacks never
 * run). The real guarantee is upstream — the store performs no guarded writes during
 * construction (see GlobalSettingsFilesystemDatasource). Keep it that way.
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