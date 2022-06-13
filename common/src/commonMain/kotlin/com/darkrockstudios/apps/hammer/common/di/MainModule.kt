package com.darkrockstudios.apps.hammer.common.di

import com.darkrockstudios.apps.hammer.common.data.ProjectEditorRepository
import com.darkrockstudios.apps.hammer.common.data.ProjectsRepository
import com.darkrockstudios.apps.hammer.common.fileio.okio.ProjectsRepositoryOkio
import com.darkrockstudios.apps.hammer.common.getPlatformFilesystem
import org.koin.dsl.module

val mainModule = module {
    single { getPlatformFilesystem() }
    single<ProjectsRepository> { ProjectsRepositoryOkio(get()) }
    single { ProjectEditorRepository() }
}