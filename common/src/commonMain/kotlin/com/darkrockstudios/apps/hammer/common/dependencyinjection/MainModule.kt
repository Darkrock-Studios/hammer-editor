package com.darkrockstudios.apps.hammer.common.dependencyinjection

import com.akuleshov7.ktoml.Toml
import com.darkrockstudios.apps.hammer.common.data.drafts.SceneDraftRepository
import com.darkrockstudios.apps.hammer.common.data.drafts.SceneDraftRepositoryOkio
import com.darkrockstudios.apps.hammer.common.data.id.IdRepository
import com.darkrockstudios.apps.hammer.common.data.id.IdRepositoryOkio
import com.darkrockstudios.apps.hammer.common.data.notes.NotesRepository
import com.darkrockstudios.apps.hammer.common.data.notes.NotesRepositoryOkio
import com.darkrockstudios.apps.hammer.common.data.projecteditorrepository.ProjectEditorRepository
import com.darkrockstudios.apps.hammer.common.data.projecteditorrepository.ProjectEditorRepositoryOkio
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepositoryOkio
import com.darkrockstudios.apps.hammer.common.getPlatformFilesystem
import com.darkrockstudios.apps.hammer.common.globalsettings.GlobalSettingsRepository
import okio.FileSystem
import org.koin.core.module.dsl.scopedOf
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

val mainModule = module {
	singleOf(::GlobalSettingsRepository) bind GlobalSettingsRepository::class

	singleOf(::getPlatformFilesystem) bind FileSystem::class

	singleOf(::ProjectsRepositoryOkio) bind ProjectsRepository::class

	singleOf(::createTomlSerializer) bind Toml::class

	scope<ProjectDefScope> {
		scopedOf(::ProjectEditorRepositoryOkio) bind ProjectEditorRepository::class

		scopedOf(::SceneDraftRepositoryOkio) bind SceneDraftRepository::class

		scopedOf(::IdRepositoryOkio) bind IdRepository::class

		scopedOf(::NotesRepositoryOkio) bind NotesRepository::class
	}
}