package com.darkrockstudios.apps.hammer.android.wear

import com.darkrockstudios.apps.hammer.common.data.account.AccountUseCase
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsStore
import org.koin.core.module.Module
import org.koin.dsl.module

val wearPairingModule: Module = module {
	single<PairingResponder> { WearPairingResponder(context = get()) }
	single {
		val globalSettingsStore: GlobalSettingsStore = get()
		val accountUseCase: AccountUseCase = get()
		WearPairingHandler(
			serverSettings = { globalSettingsStore.serverSettings },
			pairInstall = accountUseCase::pairInstall,
			responder = get(),
		)
	}
}
