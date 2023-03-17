package com.darkrockstudios.apps.hammer.dependencyinjection

import com.darkrockstudios.apps.hammer.account.AccountsRepository
import com.darkrockstudios.apps.hammer.database.AccountDao
import com.darkrockstudios.apps.hammer.database.AuthTokenDao
import com.darkrockstudios.apps.hammer.database.Database
import com.darkrockstudios.apps.hammer.database.SqliteDatabase
import com.darkrockstudios.apps.hammer.project.ProjectRepository
import com.darkrockstudios.apps.hammer.projects.ProjectsRepository
import kotlinx.coroutines.Dispatchers
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
    single<CoroutineContext>(named(DISPATCHER_MAIN)) { Dispatchers.Main }
    single<CoroutineContext>(named(DISPATCHER_DEFAULT)) { Dispatchers.Default }
    single<CoroutineContext>(named(DISPATCHER_IO)) { Dispatchers.IO }

    single { Json } bind Json::class

    single { FileSystem.SYSTEM } bind FileSystem::class
    single { SqliteDatabase() } bind Database::class
    singleOf(::AccountDao)
    singleOf(::AuthTokenDao)

    singleOf(::AccountsRepository)
    singleOf(::ProjectRepository)
}