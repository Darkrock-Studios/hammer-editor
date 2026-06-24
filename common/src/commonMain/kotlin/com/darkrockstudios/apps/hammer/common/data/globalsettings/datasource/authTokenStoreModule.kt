package com.darkrockstudios.apps.hammer.common.data.globalsettings.datasource

import org.koin.core.module.Module

/**
 * Provides the platform's [AuthTokenStore] binding. Each platform supplies an
 * encrypted implementation where one exists; iOS currently falls back to the
 * plaintext file store pending a Keychain-backed implementation.
 */
expect val authTokenStoreModule: Module
