package com.darkrockstudios.apps.hammer.common.projecthome

import com.darkrockstudios.apps.hammer.operations.plugin.ClientPlugin

/**
 * Plugins of one kind that work in the project's language, of which the project uses one. [label]
 * is the first translated kind label among them by plugin id, or the first label when none is.
 * [others] are the kind's plugins for other languages, which still run while on, so a pick turns
 * them off too.
 */
internal data class PluginChoice(
	val kindId: String,
	val label: String,
	val plugins: List<ClientPlugin>,
	val others: List<ClientPlugin> = emptyList(),
)

/** What a [PluginChoice]'s dropdown shows as picked, from which of its plugins are on. */
internal sealed interface PluginPick {
	data object None : PluginPick
	data object Several : PluginPick
	data class One(val plugin: ClientPlugin) : PluginPick
}

/** The kinds with two or more of [plugins] working in [language], by label. */
internal fun pluginChoices(plugins: List<ClientPlugin>, language: String?): List<PluginChoice> =
	plugins.filter { it.kind != null }
		.groupBy { it.kind!!.id }
		.mapNotNull { (kindId, ofKind) ->
			val (offered, others) = ofKind.partition { worksIn(it, language) }
			if (offered.size < 2) return@mapNotNull null
			val kinds = offered.sortedBy { it.id }.mapNotNull { it.kind }
			val label = (kinds.firstOrNull { it.localized } ?: kinds.first()).label
			PluginChoice(kindId, label, offered.sortedBy { (it.name ?: it.id).lowercase() }, others)
		}
		.sortedBy { it.label.lowercase() }

internal fun PluginChoice.picked(disabled: Set<String>): PluginPick {
	val on = plugins.filter { it.id !in disabled }
	if (others.any { it.id !in disabled }) return PluginPick.Several
	return when (on.size) {
		0 -> PluginPick.None
		1 -> PluginPick.One(on.single())
		else -> PluginPick.Several
	}
}

/** The plugins to turn on and off for [pick]; nothing for [PluginPick.Several], which is not a choice. */
internal fun PluginChoice.changesFor(pick: PluginPick): Map<String, Boolean> = when (pick) {
	PluginPick.Several -> emptyMap()
	PluginPick.None -> (plugins + others).associate { it.id to false }
	is PluginPick.One -> (plugins + others).associate { it.id to (it.id == pick.plugin.id) }
}

// Matched on the primary language subtag, so an `en` plugin serves an `en-GB` project.
private fun worksIn(plugin: ClientPlugin, language: String?): Boolean {
	if (plugin.languages.isEmpty() || language.isNullOrBlank()) return true
	val wanted = primaryLanguage(language)
	return plugin.languages.any { primaryLanguage(it) == wanted }
}

private fun primaryLanguage(tag: String) = tag.substringBefore('-').substringBefore('_').lowercase()
