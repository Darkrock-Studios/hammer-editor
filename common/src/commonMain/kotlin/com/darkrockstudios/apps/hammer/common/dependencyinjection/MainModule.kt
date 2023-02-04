package com.darkrockstudios.apps.hammer.common.dependencyinjection

import com.akuleshov7.ktoml.Toml
import com.darkrockstudios.apps.hammer.common.data.drafts.SceneDraftRepository
import com.darkrockstudios.apps.hammer.common.data.drafts.SceneDraftRepositoryOkio
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EncyclopediaRepository
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EncyclopediaRepositoryOkio
import com.darkrockstudios.apps.hammer.common.data.exampleProjectModule
import com.darkrockstudios.apps.hammer.common.data.id.IdRepository
import com.darkrockstudios.apps.hammer.common.data.id.IdRepositoryOkio
import com.darkrockstudios.apps.hammer.common.data.notesrepository.NotesRepository
import com.darkrockstudios.apps.hammer.common.data.notesrepository.NotesRepositoryOkio
import com.darkrockstudios.apps.hammer.common.data.projecteditorrepository.ProjectEditorRepository
import com.darkrockstudios.apps.hammer.common.data.projecteditorrepository.ProjectEditorRepositoryOkio
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepositoryOkio
import com.darkrockstudios.apps.hammer.common.defaultDispatcher
import com.darkrockstudios.apps.hammer.common.fileio.externalFileIoModule
import com.darkrockstudios.apps.hammer.common.getPlatformFilesystem
import com.darkrockstudios.apps.hammer.common.globalsettings.GlobalSettingsRepository
import com.darkrockstudios.apps.hammer.common.ioDispatcher
import com.darkrockstudios.apps.hammer.common.mainDispatcher
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

    single(named(DISPATCHER_MAIN)) { mainDispatcher }
    single(named(DISPATCHER_DEFAULT)) { defaultDispatcher }
    single(named(DISPATCHER_IO)) { ioDispatcher }

    singleOf(::GlobalSettingsRepository) bind GlobalSettingsRepository::class

    singleOf(::getPlatformFilesystem) bind FileSystem::class

    singleOf(::ProjectsRepositoryOkio) bind ProjectsRepository::class

    singleOf(::createTomlSerializer) bind Toml::class

    scope<ProjectDefScope> {
		scopedOf(::ProjectEditorRepositoryOkio) bind ProjectEditorRepository::class

		scopedOf(::SceneDraftRepositoryOkio) bind SceneDraftRepository::class

		scopedOf(::IdRepositoryOkio) bind IdRepository::class

		scopedOf(::NotesRepositoryOkio) bind NotesRepository::class

		scopedOf(::EncyclopediaRepositoryOkio) bind EncyclopediaRepository::class
	}
}