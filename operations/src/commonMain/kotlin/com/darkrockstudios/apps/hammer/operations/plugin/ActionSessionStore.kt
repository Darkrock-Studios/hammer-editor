package com.darkrockstudios.apps.hammer.operations.plugin

import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectIdChangeListener
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.EntityIdChange
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.EntityIdChangeListener
import com.darkrockstudios.apps.hammer.common.getConfigDirectory
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
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
	/**
	 * Whether sync has since given an item a new id that the page's `state` may hold by its old one, which
	 * now names another item: the run is shown as entered rather than carried on, as for a changed plugin.
	 */
	val renumbered: Boolean = false,
)

/** A field's [label], and its [value] as submitted: a choice by its option's label, scenes by their names. */
@Serializable
class ActionAnswer(val label: String, val value: JsonPrimitive)

/** Something keeping kept runs of its own, which must follow the entities a sync gives new ids. */
interface RenumberingFollower {
	/**
	 * Runs [pass], which follows [renumberings] in the sessions on disk, with none of this follower's
	 * writes in between. On the thread syncing.
	 */
	fun hold(renumberings: List<EntityRenumbering>, pass: () -> Unit) = pass()

	/** Told once the sessions on disk have followed [renumberings]; on the thread syncing. */
	fun renumbered(renumberings: List<EntityRenumbering>)
}

/** A session that could not be saved, removed, or read. */
open class ActionSessionException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** A saved session that cannot be read: moved to [setAside] so nothing writes over it, or left in place when that is null. */
class UnreadableSessionException(message: String, val setAside: Path?, cause: Throwable? = null) : ActionSessionException(message, cause)

/**
 * Kept runs of plugin actions, one JSON file each under `<config>/plugins/_sessions/<pluginId>/<projectId>/`,
 * named `<action>.<place>[.<itemId>].json`. They are the user's work, so unlike the plugin cache they
 * are written atomically, a failure is thrown rather than dropped, a file that cannot be read is
 * moved aside rather than written over, and uninstalling the plugin leaves them.
 */
class ActionSessionStore(
	private val fileSystem: FileSystem,
	pluginsDirectory: Path = getConfigDirectory().toPath() / PluginSettingsDatasource.PLUGINS_DIRECTORY,
) : ProjectIdChangeListener, EntityIdChangeListener {
	private val directory = pluginsDirectory / SESSIONS_DIRECTORY

	private val followers = MutableStateFlow<List<RenumberingFollower>>(emptyList())

	/** Tells [follower] of the entities each sync gives new ids, until the function returned is called. */
	fun followRenumberings(follower: RenumberingFollower): () -> Unit {
		followers.update { it + follower }
		return { followers.update { it - follower } }
	}

	/**
	 * The session saved for [key], or null when there is none. Throws [UnreadableSessionException] for
	 * one that cannot be read, a newer format's included, having moved it aside to
	 * `<name>.json.unreadable[-<n>]` when it could.
	 */
	fun load(key: ActionSessionKey): ActionSession? {
		val path = sessionPath(key) ?: return null
		if (!fileSystem.exists(path)) return null
		val file = try {
			json.decodeFromString(SessionFile.serializer(), fileSystem.read(path) { readUtf8() })
		} catch (e: IOException) {
			setAside(path, e)
		} catch (e: IllegalArgumentException) {
			// Includes SerializationException.
			setAside(path, e)
		}
		if (file.format != FORMAT) setAside(path, null)
		return sessionOf(file, path)
	}

	/** [file]'s session; with no page when its page cannot be read, which still leaves its answers. */
	private fun sessionOf(file: SessionFile, path: Path): ActionSession {
		val page = try {
			(file.page as? JsonObject)?.let { json.decodeFromJsonElement(PageTable.serializer(), it).toActionPage() }
		} catch (e: IllegalArgumentException) {
			Napier.e(e) { "The page of the action session at $path cannot be read" }
			null
		}
		return ActionSession(file.pluginVersion, page, file.values, file.first, file.answers, file.renumbered)
	}

	/** Throws [ActionSessionException] when it cannot be written, or is over [MAX_SESSION_BYTES] encoded. */
	fun save(key: ActionSessionKey, session: ActionSession) {
		val path = sessionPath(key) ?: throw ActionSessionException("A session cannot be kept for $key")
		val bytes = json.encodeToString(SessionFile.serializer(), fileOf(key, session)).encodeToByteArray()
		if (bytes.size > MAX_SESSION_BYTES) {
			throw ActionSessionException("The session for $key is ${bytes.size} bytes, over $MAX_SESSION_BYTES")
		}
		try {
			write(path, bytes)
		} catch (e: IOException) {
			throw ActionSessionException("Could not save the session for $key", e)
		}
	}

	/** [session] as it is written for [key]; [page] in place of its page, as JSON, when given. */
	private fun fileOf(key: ActionSessionKey, session: ActionSession, page: JsonElement? = null) = SessionFile(
		format = FORMAT,
		pluginVersion = session.pluginVersion,
		action = key.action,
		place = key.place.id,
		itemId = key.itemId,
		first = session.first,
		page = page ?: session.page?.let { json.encodeToJsonElement(PageTable.serializer(), it.toPageTable()) } ?: JsonNull,
		values = session.values,
		answers = session.answers,
		renumbered = session.renumbered,
	)

	/** Writes [bytes] to [path] by way of a staging file moved into place, so it is never left half written. */
	private fun write(path: Path, bytes: ByteArray) {
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
			throw e
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

	/**
	 * Gives the project's new id the sessions kept under its old one, so they are found again. They are
	 * copied, not moved: a project copied on disk is given a new id while the original keeps the old.
	 * A session the new id already has is left as it is.
	 */
	override suspend fun onProjectIdChanged(projectDef: ProjectDef, oldId: ProjectId, newId: ProjectId) {
		if (!SAFE_NAME.matches(oldId.id) || !SAFE_NAME.matches(newId.id) || !fileSystem.exists(directory)) return
		for (plugin in fileSystem.list(directory)) {
			val from = plugin / oldId.id
			if (!fileSystem.exists(from)) continue
			val to = plugin / newId.id
			fileSystem.createDirectories(to)
			for (session in fileSystem.list(from).filter { it.name.endsWith(".json") }) {
				val target = to / session.name
				if (fileSystem.exists(target)) continue
				val staging = to / "${session.name}.tmp"
				fileSystem.copy(session, staging)
				fileSystem.atomicMove(staging, target)
			}
		}
	}

	/**
	 * Follows a sync's new ids in the project's sessions: a run from a renumbered item is filed under its
	 * new id, and picks of one on a saved page are given its new id. One whose page's `state` may hold an
	 * old id is marked [renumbered][ActionSession.renumbered]. Each follower holds its own writes while
	 * that is done, then is told, even when the sessions could not all be followed.
	 */
	override suspend fun onEntityIdsChanged(projectDef: ProjectDef, changes: List<EntityIdChange>) {
		val renumberings = changes.map { EntityRenumbering(projectDef.id, it.type, it.oldId, it.newId) }
		val following = followers.value
		val pass = following.fold({ renumber(projectDef.id, renumberings) }) { inner, follower ->
			{ follower.hold(renumberings, inner) }
		}
		try {
			pass()
		} finally {
			following.forEach { it.renumbered(renumberings) }
		}
	}

	private fun renumber(projectId: ProjectId, renumberings: List<EntityRenumbering>) {
		if (renumberings.isEmpty() || !SAFE_NAME.matches(projectId.id)) return
		val plugins = try {
			fileSystem.listOrNull(directory).orEmpty()
		} catch (e: IOException) {
			Napier.e(e) { "Could not list the action sessions to follow the ids sync changed" }
			return
		}
		for (plugin in plugins) {
			val sessions = try {
				fileSystem.listOrNull(plugin / projectId.id).orEmpty()
			} catch (e: IOException) {
				Napier.e(e) { "Could not list the action sessions in $plugin to follow the ids sync changed" }
				continue
			}
			for (path in sessions.filter { it.name.endsWith(".json") }) {
				try {
					renumber(path, plugin.name, projectId, renumberings)
				} catch (e: IOException) {
					Napier.e(e) { "Could not follow the ids sync changed in the action session at $path" }
				}
			}
		}
	}

	private fun renumber(path: Path, pluginId: String, projectId: ProjectId, renumberings: List<EntityRenumbering>) {
		val file = try {
			json.decodeFromString(SessionFile.serializer(), fileSystem.read(path) { readUtf8() })
		} catch (e: IllegalArgumentException) {
			// One that cannot be read is set aside when its run is opened, and nothing is resumed from it.
			Napier.w(e) { "The action session at $path cannot be read to follow the ids sync changed" }
			return
		}
		if (file.format != FORMAT) return
		val place = ActionPlace.of(file.place) ?: return
		val key = ActionSessionKey(pluginId, projectId, file.action, place, file.itemId)
		val session = sessionOf(file, path)
		val (newKey, renumbered) = renumberings.fold(key to session) { (key, session), it -> it.key(key) to it.session(session) }
		// A page comes back as it was when nothing in it changes, so only a changed one is written afresh.
		val updated = fileOf(newKey, renumbered, page = file.page.takeIf { renumbered.page === session.page })
		if (updated == file) return
		val target = sessionPath(newKey) ?: return
		if (target != path) {
			// A session left from an item since deleted, whose id was handed out again.
			if (fileSystem.exists(target)) fileSystem.atomicMove(target, asidePath(target, SUPERSEDED))
			// Moved first, so a failure from here leaves no copy under the old id, which is now another item's.
			fileSystem.atomicMove(path, target)
		}
		write(target, json.encodeToString(SessionFile.serializer(), updated).encodeToByteArray())
	}

	/** Null when a part of [key] is not safe as a file name. */
	private fun sessionPath(key: ActionSessionKey): Path? {
		val parts = listOf(key.pluginId, key.projectId.id, key.action)
		if (parts.any { !SAFE_NAME.matches(it) }) {
			Napier.w { "No session can be kept for $key: not every part of it is a safe file name" }
			return null
		}
		return directory / key.pluginId / key.projectId.id / "${fileName(key.action, key.place, key.itemId)}.json"
	}

	private fun fileName(action: String, place: ActionPlace, itemId: Int?) =
		listOfNotNull(action, place.id, itemId?.toString()).joinToString(".")

	/** `<name>.<suffix>`, or `-2`, `-3` and so on after it, whichever is free. */
	private fun asidePath(path: Path, suffix: String): Path = generateSequence(1) { it + 1 }
		.map { n -> path.parent!! / (if (n == 1) "${path.name}.$suffix" else "${path.name}.$suffix-$n") }
		.first { !fileSystem.exists(it) }

	private fun setAside(path: Path, e: Exception?): Nothing {
		val aside = asidePath(path, UNREADABLE)
		try {
			fileSystem.atomicMove(path, aside)
		} catch (moveFailed: IOException) {
			Napier.e(moveFailed) { "The action session at $path cannot be read, nor moved aside" }
			throw UnreadableSessionException("The action session at $path cannot be read", setAside = null, cause = e ?: moveFailed)
		}
		Napier.e(e) { "The action session at $path cannot be read; it is kept at $aside" }
		throw UnreadableSessionException("The action session at $path cannot be read; it is kept at $aside", aside, e)
	}

	@Serializable
	private data class SessionFile(
		val format: Int,
		val pluginVersion: String?,
		val action: String,
		val place: String,
		val itemId: Int?,
		val first: Boolean,
		val page: JsonElement,
		val values: JsonObject,
		val answers: Map<String, ActionAnswer>,
		val renumbered: Boolean = false,
	)

	companion object {
		// Plugin ids cannot start with an underscore, so no plugin's files can collide with this.
		const val SESSIONS_DIRECTORY = "_sessions"

		const val MAX_SESSION_BYTES = 4 * 1024 * 1024

		/** The file layout's version; a file in another is left unread. */
		private const val FORMAT = 1

		private const val UNREADABLE = "unreadable"
		private const val SUPERSEDED = "superseded"

		private val SAFE_NAME = Regex("[A-Za-z0-9][A-Za-z0-9_-]*")
		private val json = Json { ignoreUnknownKeys = true }
	}
}
