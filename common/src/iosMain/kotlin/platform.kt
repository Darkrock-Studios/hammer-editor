package com.darkrockstudios.apps.hammer.common

import com.darkrockstudios.apps.hammer.common.di.NapierLogger
import com.darkrockstudios.apps.hammer.common.di.mainModule
import kotlinx.coroutines.Dispatchers
import okio.FileSystem
import org.koin.core.context.startKoin
import kotlin.coroutines.CoroutineContext

actual fun getPlatformName(): String {
    return "iOS"
}

actual fun getRootDocumentDirectory(): String {
    //let path = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0].appendingPathComponent(“todos.txt”)
    return "not implemented"
}

actual fun getConfigDirectory(): String = "not implemented"

fun initializeKoin() {
    startKoin {
        logger(NapierLogger())
        modules(mainModule)
    }
}

actual fun getPlatformFilesystem() = FileSystem.SYSTEM

actual val defaultDispatcher: CoroutineContext = Dispatchers.Default
actual val mainDispatcher: CoroutineContext = Dispatchers.Main