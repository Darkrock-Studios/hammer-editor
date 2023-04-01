package com.darkrockstudios.apps.hammer.common.dependencyinjection

import com.akuleshov7.ktoml.Toml
import com.darkrockstudios.apps.hammer.base.http.createJsonSerializer
import com.darkrockstudios.apps.hammer.common.data.accountrepository.AccountRepository
import com.darkrockstudios.apps.hammer.common.data.drafts.SceneDraftRepository
import com.darkrockstudios.apps.hammer.common.data.drafts.SceneDraftRepositoryOkio
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EncyclopediaRepository
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EncyclopediaRepositoryOkio
import com.darkrockstudios.apps.hammer.common.data.exampleProjectModule
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsRepository
import com.darkrockstudios.apps.hammer.common.data.id.IdRepository
import com.darkrockstudios.apps.hammer.common.data.id.IdRepositoryOkio
import com.darkrockstudios.apps.hammer.common.data.notesrepository.NotesRepository
import com.darkrockstudios.apps.hammer.common.data.notesrepository.NotesRepositoryOkio
import com.darkrockstudios.apps.hammer.common.data.projecteditorrepository.ProjectEditorRepository
import com.darkrockstudios.apps.hammer.common.data.projecteditorrepository.ProjectEditorRepositoryOkio
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepositoryOkio
import com.darkrockstudios.apps.hammer.common.data.projectsync.ClientProjectSynchronizer
import com.darkrockstudios.apps.hammer.common.data.projectsync.synchronizers.ClientNoteSynchronizer
import com.darkrockstudios.apps.hammer.common.data.projectsync.synchronizers.ClientSceneSynchronizer
import com.darkrockstudios.apps.hammer.common.data.projectsync.synchronizers.ClientTimelineSynchronizer
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineRepository
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineRepositoryOkio
import com.darkrockstudios.apps.hammer.common.fileio.externalFileIoModule
import com.darkrockstudios.apps.hammer.common.getPlatformFilesystem
import com.darkrockstudios.apps.hammer.common.platformDefaultDispatcher
import com.darkrockstudios.apps.hammer.common.platformIoDispatcher
import com.darkrockstudios.apps.hammer.common.platformMainDispatcher
import com.darkrockstudios.apps.hammer.common.server.ServerAccountApi
import com.darkrockstudios.apps.hammer.common.server.ServerProjectApi
import io.ktor.client.*
import kotlinx.serialization.json.Json
import okio.FileSystem
import org.koin.core.module.dsl.scopedOf
import org.koin.core.module.dsl.singleOf
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module

const val DISPATCHER_MAIN = "main-dispatcher"
const val DISPATCHER_DEFAULT = "default-dispatcher"
const val DISPATCHER_IO = "io-dispatcher"


val mainModule = module {
	includes(externalFileIoModule)
    includes(exampleProjectModule)

    single(named(DISPATCHER_MAIN)) { platformMainDispatcher }
    single(named(DISPATCHER_DEFAULT)) { platformDefaultDispatcher }
    single(named(DISPATCHER_IO)) { platformIoDispatcher }

    single { createHttpClient(get()) } bind HttpClient::class
    singleOf(::ServerAccountApi)
    singleOf(::ServerProjectApi)

    singleOf(::GlobalSettingsRepository) bind GlobalSettingsRepository::class

    singleOf(::AccountRepository)

    singleOf(::getPlatformFilesystem) bind FileSystem::class

    singleOf(::ProjectsRepositoryOkio) bind ProjectsRepository::class

    singleOf(::createTomlSerializer) bind Toml::class

    singleOf(::createJsonSerializer) bind Json::class

    scope<ProjectDefScope> {
		scopedOf(::ProjectEditorRepositoryOkio) bind ProjectEditorRepository::class

		scopedOf(::SceneDraftRepositoryOkio) bind SceneDraftRepository::class

		scopedOf(::IdRepositoryOkio) bind IdRepository::class

		scopedOf(::NotesRepositoryOkio) bind NotesRepository::class

		scopedOf(::EncyclopediaRepositoryOkio) bind EncyclopediaRepository::class

		scopedOf(::TimeLineRepositoryOkio) bind TimeLineRepository::class

		scopedOf(::ClientProjectSynchronizer)
		scopedOf(::ClientSceneSynchronizer)
		scopedOf(::ClientNoteSynchronizer)
		scopedOf(::ClientTimelineSynchronizer)
	}
}