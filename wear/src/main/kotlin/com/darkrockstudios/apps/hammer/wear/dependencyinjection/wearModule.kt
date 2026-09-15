package com.darkrockstudios.apps.hammer.wear.dependencyinjection

import com.darkrockstudios.apps.hammer.wear.pairing.DataLayerPhonePairingClient
import com.darkrockstudios.apps.hammer.wear.pairing.PhonePairingClient
import com.darkrockstudios.apps.hammer.wear.pairing.PhonePairingUseCase
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

val wearModule: Module = module {
	single<PhonePairingClient> { DataLayerPhonePairingClient(context = androidContext()) }
	single {
		PhonePairingUseCase(
			client = get(),
			globalSettingsStore = get(),
			accountUseCase = get(),
		)
	}
}
