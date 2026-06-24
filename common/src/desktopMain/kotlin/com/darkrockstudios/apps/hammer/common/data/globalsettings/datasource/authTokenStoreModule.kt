package com.darkrockstudios.apps.hammer.common.data.globalsettings.datasource

import org.koin.dsl.bind
import org.koin.dsl.module

actual val authTokenStoreModule = module {
	single { EncryptedFileAuthTokenStore(fileSystem = get(), json = get()) } bind AuthTokenStore::class
}
