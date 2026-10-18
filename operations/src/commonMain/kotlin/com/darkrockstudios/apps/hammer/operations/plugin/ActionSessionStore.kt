package com.darkrockstudios.apps.hammer.operations.plugin

import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.common.getConfigDirectory
import io.github.aakira.napier.Napier
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okio.FileSystem
import okio.IOException
import okio.Path
import okio.Path.Companion.toPath

/** Which kept run of an action a session is: the plugin's action, on a project, from a place and the item there. */
data class ActionSessionKey(
	val pluginId: String,
	val projectId: ProjectId,
	val action: String,
	val place: ActionPlace,
	val itemId: Int?,
)

/**
 * What the user has entered in a kept run of an action: the [page] they were on, with its `state`, and
 * its [values]; and the [answers] they submitted on every page, which the host keeps for itself and
 * never sends the plugin.
 */
class ActionSession(
	/** The version of the plugin the run was started with. */
	val pluginVersion: String?,
	/** Null when the saved page can no longer be read; its [answers] still can. */
	val page: ActionPage?,
	val values: JsonObject,
	/** Whether [page] is the action's first page, the host's own from the manifest. */
	val first: Boolean,
	/** By field key, each the last value submitted; rich text as markdown. */
	val answers: Map<String, ActionAnswer>,
)

/** A field's [label], and its [value] as submitted: a choice by its option's label, scenes by their names. */
@Serializable
class ActionAnswer(val label: String, val value: JsonPrimitive)

/** A session that could not be saved or removed. */
class ActionSessionException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Kept runs of plugin actions, one JSON file each under `<config>/plugins/_sessions/<pluginId>/<projectId>/`,
 * named `<action>.<place>[.<itemId>].json`. They are the user's work, so unlike the plugin cache they
 * are written atomically, a failure is thrown rather than dropped, a file that cannot be read is
 * left in place, and uninstalling the plugin leaves them.
 */
class ActionSessionStore(
	private val fileSystem: FileSystem,
	pluginsDirectory: Path = getConfigDirectory().toPath() / PluginSettingsDatasource.PLUGINS_DIRECTORY,
) {
	private val directory = pluginsDirectory / SESSIONS_DIRECTORY

	/** The session saved for [key], or null when there is none or it cannot be read; an unreadable one is logged and left. */
	fun load(key: ActionSessionKey): ActionSession? {
		val path = sessionPath(key) ?: return null
		if (!fileSystem.exists(path)) return null
		val file = try {
			json.decodeFromString(SessionFile.serializer(), fileSystem.read(path) { readUtf8() })
		} catch (e: IOException) {
			return unreadable(path, e)
		} catch (e: IllegalArgumentException) {
			// Includes SerializationException.
			return unreadable(path, e)
		}
		if (file.format != FORMAT) return unreadable(path, null)
		val page = try {
			(file.page as? JsonObject)?.let { json.decodeFromJsonElement(PageTable.serializer(), it).toActionPage() }
		} catch (e: IllegalArgumentException) {
			Napier.e(e) { "The page of the action session at $path cannot be read" }
			null
		}
		return ActionSession(file.pluginVersion, page, file.values, file.first, file.answers)
	}

	/** Throws [ActionSessionException] when it cannot be written, or is over [MAX_SESSION_BYTES] encoded. */
	fun save(key: ActionSessionKey, session: ActionSession) {
		val path = sessionPath(key) ?: throw ActionSessionException("A session cannot be kept for $key")
		val file = SessionFile(
			format = FORMAT,
			pluginVersion = session.pluginVersion,
			action = key.action,
			place = key.place.id,
			itemId = key.itemId,
			first = session.first,
			page = session.page?.let { json.encodeToJsonElement(PageTable.serializer(), it.toPageTable()) } ?: JsonNull,
			values = session.values,
			answers = session.answers,
		)
		val bytes = json.encodeToString(SessionFile.serializer(), file).encodeToByteArray()
		if (bytes.size > MAX_SESSION_BYTES) {
			throw ActionSessionException("The session for $key is ${bytes.size} bytes, over $MAX_SESSION_BYTES")
		}
		val staging = path.parent!! / "${path.name}.tmp"
		try {
			fileSystem.createDirectories(path.parent!!)
			fileSystem.write(staging) { write(bytes) }
			fileSystem.atomicMove(staging, path)
		} catch (e: IOException) {
			try {
				fileSystem.delete(staging, mustExist = false)
			} catch (_: IOException) {
				// The write failed already; that is what is reported.
			}
			throw ActionSessionException("Could not save the session for $key", e)
		}
	}

	/** Throws [ActionSessionException] when it cannot be removed. */
	fun remove(key: ActionSessionKey) {
		val path = sessionPath(key) ?: return
		try {
			fileSystem.delete(path, mustExist = false)
		} catch (e: IOException) {
			throw ActionSessionException("Could not remove the session for $key", e)
		}
	}

	/** Null when a part of [key] is not safe as a file name. */
	private fun sessionPath(key: ActionSessionKey): Path? {
		val parts = listOf(key.pluginId, key.projectId.id, key.action)
		if (parts.any { !SAFE_NAME.matches(it) }) {
			Napier.w { "No session can be kept for $key: not every part of it is a safe file name" }
			return null
		}
		val name = listOfNotNull(key.action, key.place.id, key.itemId?.toString()).joinToString(".")
		return directory / key.pluginId / key.projectId.id / "$name.json"
	}

	private fun unreadable(path: Path, e: Exception?): ActionSession? {
		Napier.e(e) { "The action session at $path cannot be read; it is left for the user to recover" }
		return null
	}

	@Serializable
	private class SessionFile(
		val format: Int,
		val pluginVersion: String?,
		val action: String,
		val place: String,
		val itemId: Int?,
		val first: Boolean,
		val page: JsonElement,
		val values: JsonObject,
		val answers: Map<String, ActionAnswer>,
	)

	companion object {
		// Plugin ids cannot start with an underscore, so no plugin's files can collide with this.
		const val SESSIONS_DIRECTORY = "_sessions"

		const val MAX_SESSION_BYTES = 4 * 1024 * 1024

		/** The file layout's version; a file in another is left unread. */
		private const val FORMAT = 1

		private val SAFE_NAME = Regex("[A-Za-z0-9][A-Za-z0-9_-]*")
		private val json = Json { ignoreUnknownKeys = true }
	}
}
