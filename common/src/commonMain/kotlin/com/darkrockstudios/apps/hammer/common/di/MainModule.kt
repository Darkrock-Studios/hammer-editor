package com.darkrockstudios.apps.hammer.common.di

import com.darkrockstudios.apps.hammer.common.data.ProjectRepository
import com.darkrockstudios.apps.hammer.common.data.ProjectsRepository
import com.darkrockstudios.apps.hammer.common.fileio.okio.ProjectRepositoryOkio
import com.darkrockstudios.apps.hammer.common.fileio.okio.ProjectsRepositoryOkio
import com.darkrockstudios.apps.hammer.common.getPlatformFilesystem
import okio.FileSystem
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

val mainModule = module {
    single { getPlatformFilesystem() } bind FileSystem::class
    singleOf(::ProjectsRepositoryOkio) bind ProjectsRepository::class
    singleOf(::ProjectRepositoryOkio) bind ProjectRepository::class
}