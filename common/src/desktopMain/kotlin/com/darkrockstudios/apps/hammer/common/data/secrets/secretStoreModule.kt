package com.darkrockstudios.apps.hammer.common.data.secrets

import org.koin.dsl.bind
import org.koin.dsl.module

actual val secretStoreModule = module {
	single { EncryptedFileSecretStore(fileSystem = get()) } bind SecretStore::class
}
