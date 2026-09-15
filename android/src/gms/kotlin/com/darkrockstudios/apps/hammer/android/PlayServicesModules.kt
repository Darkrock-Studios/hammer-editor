package com.darkrockstudios.apps.hammer.android

import com.darkrockstudios.apps.hammer.android.wear.wearPairingModule
import org.koin.core.module.Module

val playServicesModules: List<Module> = listOf(wearPairingModule)
