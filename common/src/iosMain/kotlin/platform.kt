package com.darkrockstudios.apps.hammer.common

import com.darkrockstudios.apps.hammer.common.di.NapierLogger
import com.darkrockstudios.apps.hammer.common.di.mainModule
import okio.FileSystem
import org.koin.core.context.startKoin

actual fun getPlatformName(): String {
    return "iOS"
}

actual fun getRootDocumentDirectory(): String {
    //let path = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0].appendingPathComponent(“todos.txt”)
    return "not implemented"
}

fun initializeKoin() {
    startKoin {
        logger(NapierLogger())
        modules(mainModule)
    }
}

actual fun getPlatformFilesystem() = FileSystem.SYSTEM