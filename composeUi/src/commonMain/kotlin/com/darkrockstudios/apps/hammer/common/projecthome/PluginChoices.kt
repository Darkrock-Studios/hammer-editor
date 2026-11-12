package com.darkrockstudios.apps.hammer.common.projecthome

import com.darkrockstudios.apps.hammer.common.data.projectplugins.pluginWorksIn
import com.darkrockstudios.apps.hammer.operations.plugin.ClientPlugin

/**
 * The plugins of one kind, of which a project runs at most one: those that work in its language
 * first. [label] is the first translated kind label among them by plugin id, or the first label when
 * none is.
 */
internal data class PluginChoice(val kindId: String, val label: String, val plugins: List<ClientPlugin>)

/** What a [PluginChoice]'s dropdown shows as picked. */
internal sealed interface PluginPick {
	data object None : PluginPick
	data class One(val plugin: ClientPlugin) : PluginPick
}

/** The kinds with two or more of [plugins], by label. */
internal fun pluginChoices(plugins: List<ClientPlugin>, language: String?): List<PluginChoice> =
	plugins.filter { it.kind != null }
		.groupBy { it.kind!!.id }
		.filterValues { it.size > 1 }
		.map { (kindId, ofKind) ->
			val kinds = ofKind.sortedBy { it.id }.mapNotNull { it.kind }
			val label = (kinds.firstOrNull { it.localized } ?: kinds.first()).label
			val ordered = ofKind.sortedWith(
				compareBy<ClientPlugin> { !pluginWorksIn(it.languages, language) }.thenBy { (it.name ?: it.id).lowercase() }
			)
			PluginChoice(kindId, label, ordered)
		}
		.sortedBy { it.label.lowercase() }

/** From the project's turned-off plugins, in which at most one plugin of a kind is on. */
internal fun PluginChoice.picked(disabled: Set<String>): PluginPick =
	plugins.firstOrNull { it.id !in disabled }?.let(PluginPick::One) ?: PluginPick.None

/** The plugins to turn on and off for [pick]. */
internal fun PluginChoice.changesFor(pick: PluginPick): Map<String, Boolean> = when (pick) {
	PluginPick.None -> plugins.associate { it.id to false }
	is PluginPick.One -> plugins.associate { it.id to (it.id == pick.plugin.id) }
}
