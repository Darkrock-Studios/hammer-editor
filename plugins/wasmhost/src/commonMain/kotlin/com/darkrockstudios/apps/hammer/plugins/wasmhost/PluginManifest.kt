package com.darkrockstudios.apps.hammer.plugins.wasmhost

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
) {
	@Serializable
	data class Permissions(
		/** What the plugin may dispatch, each an [OperationGrant]. */
		val operations: List<String> = emptyList(),
	)

	@Serializable
	data class Exporter(
		/** Prefixed with the plugin id, as for compiled-in plugins. */
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

	companion object {
		const val API_VERSION = 1
		const val INPUT_MARKDOWN = "markdown"
		const val INPUT_PROSE = "prose"

		fun parse(toml: String): PluginManifest {
			val manifest = Toml { ignoreUnknownKeys = true }.decodeFromString(serializer(), toml)
			require(manifest.api == API_VERSION) { "Plugin '${manifest.id}' needs host API ${manifest.api}" }
			return manifest
		}
	}
}
