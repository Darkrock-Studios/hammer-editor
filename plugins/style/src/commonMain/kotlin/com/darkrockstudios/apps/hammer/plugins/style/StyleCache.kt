package com.darkrockstudios.apps.hammer.plugins.style

import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import com.darkrockstudios.apps.hammer.operations.plugin.ProjectPluginContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okio.ByteString.Companion.encodeUtf8
import okio.FileSystem
import okio.IOException
import okio.Path
import kotlin.random.Random

/**
 * Each scene's counts in `<project>/.plugins/style/scenes/<id>.json`, keyed by a hash of the text
 * they were counted from, so a report only re-reads scenes that changed.
 */
class StyleCache(projectDef: ProjectDef, private val fileSystem: FileSystem) {
	// Not created until a write, so reading a project the cache cannot write to still works.
	private val directory: Path =
		projectDef.path.toOkioPath() / ProjectPluginContext.PLUGINS_DIRECTORY / StylePlugin.ID / SCENES_DIRECTORY

	fun counts(sceneId: Int, markdown: String): SceneCounts {
		val hash = markdown.encodeUtf8().sha256().hex()
		read(sceneId)?.takeIf { it.version == StyleAnalyzer.VERSION && it.hash == hash }?.let { return it.counts }
		val counts = StyleAnalyzer.count(markdown)
		write(sceneId, CachedCounts(StyleAnalyzer.VERSION, hash, counts))
		return counts
	}

	/** Drops the counts of scenes no longer in [sceneIds]. */
	fun retainOnly(sceneIds: Set<Int>) {
		try {
			if (!fileSystem.exists(directory)) return
			fileSystem.list(directory)
				.filter { it.name.removeSuffix(EXTENSION).toIntOrNull()?.let { id -> id !in sceneIds } == true }
				.forEach { fileSystem.delete(it) }
		} catch (e: IOException) {
			// Stale counts only take space; they are never read for a scene that is gone.
		}
	}

	private fun read(sceneId: Int): CachedCounts? {
		val path = path(sceneId)
		return try {
			if (!fileSystem.exists(path)) return null
			json.decodeFromString(CachedCounts.serializer(), fileSystem.read(path) { readUtf8() })
		} catch (e: IOException) {
			null
		} catch (e: SerializationException) {
			null
		}
	}

	/**
	 * Written aside under a name of its own and moved into place, so reports running together never
	 * read half a file or move each other's. Best effort: a report without its cache is only slower.
	 */
	private fun write(sceneId: Int, cached: CachedCounts) {
		val temp = directory / "$sceneId$EXTENSION.${Random.nextLong().toULong()}.tmp"
		try {
			fileSystem.createDirectories(directory)
			fileSystem.write(temp) { writeUtf8(json.encodeToString(CachedCounts.serializer(), cached)) }
			fileSystem.atomicMove(temp, path(sceneId))
		} catch (e: IOException) {
			try {
				fileSystem.delete(temp, mustExist = false)
			} catch (e: IOException) {
				// Nothing was written.
			}
		}
	}

	private fun path(sceneId: Int) = directory / "$sceneId$EXTENSION"

	@Serializable
	private class CachedCounts(val version: Int, val hash: String, val counts: SceneCounts)

	private companion object {
		const val SCENES_DIRECTORY = "scenes"
		const val EXTENSION = ".json"
		val json = Json { ignoreUnknownKeys = true }
	}
}
