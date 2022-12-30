package com.darkrockstudios.apps.hammer.common.di

import com.akuleshov7.ktoml.Toml
import com.darkrockstudios.apps.hammer.common.data.drafts.SceneDraftRepository
import com.darkrockstudios.apps.hammer.common.data.drafts.SceneDraftRepositoryOkio
import com.darkrockstudios.apps.hammer.common.data.projectrepository.ProjectRepository
import com.darkrockstudios.apps.hammer.common.data.projectrepository.ProjectRepositoryOkio
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepositoryOkio
import com.darkrockstudios.apps.hammer.common.getPlatformFilesystem
import com.darkrockstudios.apps.hammer.common.globalsettings.GlobalSettingsRepository
import okio.FileSystem
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

val mainModule = module {
	singleOf(::GlobalSettingsRepository) bind GlobalSettingsRepository::class

	singleOf(::getPlatformFilesystem) bind FileSystem::class

	singleOf(::ProjectsRepositoryOkio) bind ProjectsRepository::class

	single {
		ProjectRepositoryOkio(
			fileSystem = get(),
			projectsRepository = get()
		)
	} bind ProjectRepository::class

	singleOf(::SceneDraftRepositoryOkio) bind SceneDraftRepository::class

	singleOf(::createTomlSerializer) bind Toml::class
}