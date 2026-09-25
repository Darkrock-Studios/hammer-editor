package com.darkrockstudios.apps.hammer.plugins.wasmhost

import com.darkrockstudios.apps.hammer.operations.plugin.ActionOutput
import com.darkrockstudios.apps.hammer.operations.plugin.ActionPlace
import com.darkrockstudios.apps.hammer.operations.plugin.FieldTable
import kotlinx.serialization.Serializable
import net.peanuuutz.tomlkt.Toml

/** A runtime plugin's `manifest.toml`. */
@Serializable
data class PluginManifest(
	val id: String,
	val name: String,
	val version: String,
	/** The host API version the plugin was built against. */
	val api: Int,
	val permissions: Permissions = Permissions(),
	val exporters: List<Exporter> = emptyList(),
	val commands: List<Command> = emptyList(),
	val actions: List<Action> = emptyList(),
	val diagnostics: List<Diagnostics> = emptyList(),
	val limits: Limits = Limits(),
) {
	@Serializable
	data class Permissions(
		/** What the plugin may dispatch, each an [OperationGrant]. */
		val operations: List<String> = emptyList(),
	)

	@Serializable
	data class Exporter(
		/** Prefixed with the plugin id. */
		val format: String,
		val extension: String,
		val mime: String,
		val label: String,
		/** What each scene arrives as: `markdown`, its text, or `prose`, the host's parse of it. */
		val input: String = INPUT_MARKDOWN,
	)

	/**
	 * A top-level CLI command, such as `hammer mcp`: the host reads its standard input a line at a time,
	 * calls the module's `command` export on each, and writes what it returns as a line of output.
	 */
	@Serializable
	data class Command(
		val name: String,
		/** One line, for `hammer help`. */
		val help: String,
	)

	/**
	 * An item in the menus of the screens in [places], which asks for any [field]s and then calls the
	 * module's `action` export on the project and the item the screen shows.
	 */
	@Serializable
	data class Action(
		/** Tells the module which of its actions to run. */
		val name: String,
		val label: String,
		/** An [ActionOutput] id: `message`, `document`, or `interactive`. */
		val output: String = ActionOutput.Message.id,
		/** [ActionPlace] ids: `project`, `scene`, `note`, `entry`, or `event`. */
		val places: List<String> = listOf(ActionPlace.Project.id),
		val field: List<FieldTable> = emptyList(),
	)

	/**
	 * A check the editor runs over the text being written, which calls the module's `diagnose` export
	 * with the paragraphs that changed and underlines the issues it returns.
	 */
	@Serializable
	data class Diagnostics(
		/** Tells the module which of its checks to run. */
		val name: String,
		val label: String,
	)

	@Serializable
	data class Limits(
		/** MiB of linear memory the module may grow to, from 1 to [MAX_MEMORY_MIB]. */
		val memory: Int = DEFAULT_MEMORY_MIB,
	)

	companion object {
		const val API_VERSION = 1
		const val DEFAULT_MEMORY_MIB = 64
		const val MAX_MEMORY_MIB = 1024
		const val INPUT_MARKDOWN = "markdown"
		const val INPUT_PROSE = "prose"

		fun parse(toml: String): PluginManifest {
			val manifest = Toml { ignoreUnknownKeys = true }.decodeFromString(serializer(), toml)
			require(manifest.api == API_VERSION) { "Plugin '${manifest.id}' needs host API ${manifest.api}" }
			return manifest
		}
	}
}
