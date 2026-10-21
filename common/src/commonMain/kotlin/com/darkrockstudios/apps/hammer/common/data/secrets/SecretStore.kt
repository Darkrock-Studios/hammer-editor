package com.darkrockstudios.apps.hammer.common.data.secrets

import org.koin.core.module.Module

/**
 * Strings kept encrypted at rest in app-private storage, by key, such as plugins' secret settings. Each
 * platform's store belongs to this machine and user, so its values never travel with a workspace.
 */
interface SecretStore {
	fun get(key: String): String?

	/** An empty [value] removes [key]. */
	fun put(key: String, value: String)

	/** Removes every key starting with [prefix]. */
	fun removeAll(prefix: String)
}

/** Provides the platform's [SecretStore]. */
expect val secretStoreModule: Module
