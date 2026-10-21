package com.darkrockstudios.apps.hammer.operations.plugin

import kotlinx.serialization.Serializable

/** Installs and removes runtime plugins, for the `plugin.*` operations. The plugin host implements it. */
interface PluginInstaller {
	fun summaries(): List<PluginSummary>

	/**
	 * Installs, or replaces, the package at [path], granting every permission its manifest asks for.
	 * Throws [com.darkrockstudios.apps.hammer.operations.OperationException] for a missing file or a
	 * package that cannot be installed.
	 */
	fun installFrom(path: String): PluginSummary

	/** False when no plugin has [id]. */
	fun remove(id: String): Boolean
}

@Serializable
data class PluginSummary(
	val id: String,
	/** Null, as are [version] and [description], when its package can no longer be read. */
	val name: String? = null,
	val version: String? = null,
	val description: String? = null,
	val enabled: Boolean,
	/** The operations it may use. */
	val operations: List<String> = emptyList(),
	/** Hosts it may send what the user writes to. */
	val http: List<String> = emptyList(),
	/** The setting holding an address it may also send what the user writes to. */
	val userHostSetting: String? = null,
)
