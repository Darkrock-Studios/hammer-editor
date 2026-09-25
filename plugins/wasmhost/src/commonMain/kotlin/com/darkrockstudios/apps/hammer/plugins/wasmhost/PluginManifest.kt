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
) {
	@Serializable
	data class Permissions(
		/** Operations the plugin may dispatch. */
		val operations: List<String> = emptyList(),
	)

	@Serializable
	data class Exporter(
		/** Prefixed with the plugin id, as for compiled-in plugins. */
		val format: String,
		val extension: String,
		val mime: String,
		val label: String,
	)

	companion object {
		const val API_VERSION = 1

		fun parse(toml: String): PluginManifest {
			val manifest = Toml.decodeFromString(serializer(), toml)
			require(manifest.api == API_VERSION) { "Plugin '${manifest.id}' needs host API ${manifest.api}" }
			return manifest
		}
	}
}
