package com.darkrockstudios.apps.hammer.common.data.globalsettings.datasource

import org.koin.android.ext.koin.androidContext
import org.koin.dsl.bind
import org.koin.dsl.module

actual val authTokenStoreModule = module {
	single {
		EncryptedSharedPrefsAuthTokenStore(
			context = androidContext(),
			json = get(),
			fileSystem = get(),
		)
	} bind AuthTokenStore::class
}
