package com.darkrockstudios.apps.hammer.common.data.globalsettings.datasource

import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

// TODO(F-4): iOS Keychain-backed AuthTokenStore. iOS still uses the plaintext
//  file store; replace with a Keychain implementation.
actual val authTokenStoreModule = module {
	singleOf(::FileAuthTokenStore) bind AuthTokenStore::class
}
