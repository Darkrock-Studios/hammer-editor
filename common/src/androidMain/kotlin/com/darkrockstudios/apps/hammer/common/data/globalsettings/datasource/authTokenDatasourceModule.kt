package com.darkrockstudios.apps.hammer.common.data.globalsettings.datasource

import org.koin.android.ext.koin.androidContext
import org.koin.dsl.bind
import org.koin.dsl.module

actual val authTokenDatasourceModule = module {
	single {
		EncryptedSharedPrefsAuthTokenDatasource(
			context = androidContext(),
			json = get(),
		)
	} bind AuthTokenDatasource::class
}
