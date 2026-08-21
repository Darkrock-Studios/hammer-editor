package com.darkrockstudios.apps.hammer.plugin

import com.darkrockstudios.apps.hammer.scheduling.launchRecurringTask
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import io.ktor.util.*
import org.koin.ktor.ext.get
import org.koin.ktor.ext.inject

/** Mounts every enabled plugin's public routes at the receiver. */
fun Route.installPluginRoutes() {
	val pluginRegistry: PluginRegistry by application.inject()
	pluginRegistry.plugins.forEach { it.installRoutes(this) }
}

/** Mounts every enabled plugin's admin routes at the receiver, inside the admin gate. */
fun Route.installPluginAdminRoutes() {
	val pluginRegistry: PluginRegistry by application.inject()
	pluginRegistry.plugins.forEach { it.installAdminRoutes(this) }
}

/** Starts every enabled plugin's recurring tasks. */
fun Application.launchPluginTasks() {
	val pluginRegistry: PluginRegistry by inject()
	pluginRegistry.plugins
		.flatMap { it.recurringTasks(this) }
		.forEach { launchRecurringTask(it) }
}

/**
 * Every enabled plugin's admin nav entries as mustache model maps, marking the entry
 * whose href the current request is under as active. Admin page models put this under
 * `pluginNavEntries` for the admin-nav partial.
 */
suspend fun ApplicationCall.pluginAdminNav(): List<Map<String, Any>> {
	val pluginRegistry: PluginRegistry = application.get()
	val path = request.path()
	return pluginRegistry.plugins
		.flatMap { it.adminNavEntries() }
		.map { entry ->
			mapOf(
				"href" to entry.href,
				"icon" to entry.icon,
				"label" to entry.label(this),
				"active" to (path == entry.href || path.startsWith("${entry.href}/")),
			)
		}
}

/**
 * The allow-list source currently in effect, or null if none is active. Resolved once per
 * request: isActive() may cost a config read, and several model builders ask per render.
 */
suspend fun ApplicationCall.activeAllowedUsersSource(): AllowedUsersSource? {
	attributes.getOrNull(activeSourceKey)?.let { return it.source }
	val source = application.get<PluginRegistry>().activeAllowedUsersSource()
	attributes.put(activeSourceKey, CachedActiveSource(source))
	return source
}

private val activeSourceKey = AttributeKey<CachedActiveSource>("ActiveAllowedUsersSource")

private class CachedActiveSource(val source: AllowedUsersSource?)

/**
 * Applies [AllowedUsersSource.notice] for [slot] to a template model: [providedKey] is set
 * when an active source answered (suppressing the stock content), [htmlKey] only when there
 * is markup to render. Returns the raw notice, null when no active source provided one.
 */
suspend fun ApplicationCall.putAllowedUsersNotice(
	model: MutableMap<String, Any>,
	slot: NoticeSlot,
	htmlKey: String = "allowedUsersNoticeHtml",
	providedKey: String = "allowedUsersNoticeProvided",
): String? {
	val html = activeAllowedUsersSource()?.notice(this, slot) ?: return null
	model[providedKey] = true
	if (html.isNotBlank()) model[htmlKey] = html
	return html
}
