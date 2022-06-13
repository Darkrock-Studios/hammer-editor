package com.darkrockstudios.apps.hammer.android

import android.app.Application
import com.darkrockstudios.apps.hammer.common.di.NapierLogger
import com.darkrockstudios.apps.hammer.common.di.mainModule
import com.darkrockstudios.apps.hammer.common.setRootDocumentDirectory
import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.GlobalContext.startKoin

class HammerApplication: Application() {
    override fun onCreate() {
        super.onCreate()

        setRootDocumentDirectory(this)

        Napier.base(DebugAntilog())

        startKoin {
            logger(NapierLogger())
            androidContext(this@HammerApplication)
            modules(mainModule)
        }
    }
}