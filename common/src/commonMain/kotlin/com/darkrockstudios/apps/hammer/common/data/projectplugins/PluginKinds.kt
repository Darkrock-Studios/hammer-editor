package com.darkrockstudios.apps.hammer.common.data.projectplugins

import kotlinx.coroutines.flow.Flow

/** An active plugin that declares a [kind], of which a project runs at most one. */
data class KindedPlugin(
	val id: String,
	val kind: String,
	/** BCP 47 tags of the project languages it works with; empty when it does not depend on the language. */
	val languages: List<String> = emptyList(),
)

/** The active plugins that declare a kind, such as runtime plugins installed from Settings. */
interface PluginKindSource {
	/** In the order they were installed, emitting on each change and on collection. */
	val kindedPlugins: Flow<List<KindedPlugin>>
}

/**
 * [disabled] with every plugin of a kind turned off but one, so a project runs at most one plugin of
 * each kind: of those on, the first in [kinded] that works in [language], else the first.
 */
fun oneOfEachKind(disabled: Set<String>, kinded: List<KindedPlugin>, language: String?): Set<String> =
	disabled + kinded.groupBy { it.kind }.values.flatMap { ofKind ->
		val on = ofKind.filter { it.id !in disabled }
		val kept = on.firstOrNull { pluginWorksIn(it.languages, language) } ?: on.firstOrNull()
		on.filter { it != kept }.map { it.id }
	}

/**
 * Whether a plugin for [languages] works in a project written in [language]: always when either is
 * unset, else when a primary language subtag matches, so an `en` plugin serves an `en-GB` project.
 */
fun pluginWorksIn(languages: List<String>, language: String?): Boolean {
	if (languages.isEmpty() || language.isNullOrBlank()) return true
	val wanted = primaryLanguage(language)
	return languages.any { primaryLanguage(it) == wanted }
}

private fun primaryLanguage(tag: String) = tag.substringBefore('-').substringBefore('_').lowercase()
