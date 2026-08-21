package com.darkrockstudios.apps.hammer.plugin

import com.darkrockstudios.apps.hammer.scheduling.RecurringTask
import io.ktor.server.application.*
import io.ktor.server.routing.*
import org.koin.core.module.Module

/**
 * A server extension. Registering it in [installedPlugins] is what activates it; there is
 * no separate enablement switch.
 *
 * Plugins compile as part of `:server`, so they may use anything in it, including Koin
 * injection inside route handlers and tasks.
 */
interface ServerPlugin {
	/** Stable identifier, also used as this plugin's allow-list reason tag. */
	val id: String

	/** Koin definitions for this plugin, installed alongside the main module. */
	fun koinModule(): Module? = null

	/** Public routes, mounted at the routing root. */
	fun installRoutes(route: Route) {}

	/** Admin routes, mounted under `/admin` inside the admin-only gate. */
	fun installAdminRoutes(route: Route) {}

	/** Entries appended to the admin navigation. */
	fun adminNavEntries(): List<AdminNavEntry> = emptyList()

	/**
	 * Base name of this plugin's resource bundle (e.g. `i18n.MyPluginMessages`), merged into
	 * every page's template msg map. Must resolve for Locale.ENGLISH, so ship a base or `_en`
	 * variant. Null contributes nothing.
	 */
	fun messageBundle(): String? = null

	/** Background jobs to launch at startup. */
	fun recurringTasks(application: Application): List<RecurringTask> = emptyList()

	/** The allow-list source this plugin provides, if any. */
	fun allowedUsersSource(): AllowedUsersSource? = null
}

/**
 * @param href absolute path, e.g. `/admin/patreon`. Also used to mark the entry active.
 * @param icon Font Awesome classes, e.g. `fa-brands fa-patreon`.
 * @param label localized label for the current request.
 */
data class AdminNavEntry(
	val href: String,
	val icon: String,
	val label: suspend (ApplicationCall) -> String,
)
