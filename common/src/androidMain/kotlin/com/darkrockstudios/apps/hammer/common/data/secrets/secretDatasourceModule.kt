package com.darkrockstudios.apps.hammer.common.data.secrets

import org.koin.android.ext.koin.androidContext
import org.koin.dsl.bind
import org.koin.dsl.module

actual val secretDatasourceModule = module {
	single { EncryptedSharedPrefsSecretDatasource(androidContext()) } bind SecretDatasource::class
}
