package com.darkrockstudios.apps.hammer.plugin

import org.slf4j.Logger

/**
 * The [installedPlugins] active in this build. Everything that consumes plugin
 * contributions (routing, DI, admin UI, notices) goes through this.
 */
class PluginRegistry(val plugins: List<ServerPlugin>) {

	// Lazy: the registry is built before Koin starts, and a source may inject dependencies
	// when constructed. First access happens at request time, when the graph is up.
	private val sources: List<AllowedUsersSource> by lazy { plugins.mapNotNull { it.allowedUsersSource() } }

	/** Reason tags owned by a source, whose whitelist entries the admin must not hand-edit. */
	val allowedUsersSourceIds: Set<String> by lazy { sources.map { it.id }.toSet() }

	/**
	 * The allow-list source currently in effect, or null if none is active. With multiple
	 * active sources the first by registration order wins for notices; syncing is
	 * unaffected since each source only touches entries tagged with its own id.
	 */
	suspend fun activeAllowedUsersSource(): AllowedUsersSource? =
		sources.firstOrNull { it.isActive() }

	companion object {
		fun create(plugins: List<ServerPlugin>, logger: Logger): PluginRegistry {
			plugins.forEach { logger.info("Server plugin '${it.id}' installed") }
			return PluginRegistry(plugins)
		}
	}
}
