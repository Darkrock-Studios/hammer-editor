package com.darkrockstudios.apps.hammer.common.data.globalsettings.datasource

import org.koin.core.module.Module

/**
 * Provides the platform's [AuthTokenStore] binding. Each platform supplies an
 * encrypted-at-rest implementation: Android uses EncryptedSharedPreferences,
 * desktop uses an AES-GCM encrypted file, and iOS uses the Keychain.
 */
expect val authTokenStoreModule: Module
