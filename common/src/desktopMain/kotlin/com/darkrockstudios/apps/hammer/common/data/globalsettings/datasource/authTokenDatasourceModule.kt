package com.darkrockstudios.apps.hammer.common.data.globalsettings.datasource

import org.koin.dsl.bind
import org.koin.dsl.module

actual val authTokenDatasourceModule = module {
	single { EncryptedFileAuthTokenDatasource(fileSystem = get(), json = get()) } bind AuthTokenDatasource::class
}
