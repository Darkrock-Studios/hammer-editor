package com.darkrockstudios.apps.hammer.android

import android.app.Application
import com.darkrockstudios.apps.hammer.common.dependencyinjection.NapierLogger
import com.darkrockstudios.apps.hammer.common.dependencyinjection.imageLoadingModule
import com.darkrockstudios.apps.hammer.common.dependencyinjection.mainModule
import com.darkrockstudios.apps.hammer.common.setDirectories
import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.GlobalContext.startKoin

class HammerApplication: Application() {
    override fun onCreate() {
        super.onCreate()

        setDirectories(this)

        Napier.base(DebugAntilog())

        startKoin {
            logger(NapierLogger())
            androidContext(this@HammerApplication)
            modules(mainModule, imageLoadingModule)
        }

        ExampleProject.install(this)
    }
}