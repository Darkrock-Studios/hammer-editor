package com.darkrockstudios.apps.hammer.common.data.secrets

import org.koin.dsl.bind
import org.koin.dsl.module

actual val secretDatasourceModule = module {
	single { KeychainSecretDatasource() } bind SecretDatasource::class
}
