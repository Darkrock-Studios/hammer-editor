package com.darkrockstudios.apps.hammer.common.data.ideasrepository

import com.darkrockstudios.apps.hammer.base.IdeaId
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsStore
import com.darkrockstudios.apps.hammer.base.http.storyideas.StoryIdea
import com.darkrockstudios.apps.hammer.common.dependencyinjection.injectIoDispatcherNow
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import com.darkrockstudios.apps.hammer.common.fileio.okio.toHPath
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import io.github.aakira.napier.Napier
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.IOException
import okio.Path
import okio.Path.Companion.toPath
import org.koin.core.component.KoinComponent

/**
 * Account-level storage for Story Ideas: one `idea-<uuid>.md` file per idea in the `.ideas`
 * directory at the projects root (a sibling of the project directories). The dot prefix keeps
 * the directory out of the project list.
 */
class IdeasDatasource(
	private val fileSystem: FileSystem,
	private val codec: StoryIdeaCodec,
	private val globalSettingsStore: GlobalSettingsStore,
) : KoinComponent {
	private val ioDispatcher = injectIoDispatcherNow()

	fun getIdeasDirectory(): HPath {
		val projectsDir = globalSettingsStore.globalSettings.projectsDirectory.toPath()
		val ideasDir = projectsDir / IDEAS_DIRECTORY
		if (!fileSystem.exists(ideasDir)) {
			fileSystem.createDirectories(ideasDir)
		}
		return ideasDir.toHPath()
	}

	fun getIdeaPath(id: IdeaId): HPath {
		val dir = getIdeasDirectory().toOkioPath()
		return (dir / getIdeaFilenameFromId(id)).toHPath()
	}

	suspend fun loadIdeas(): List<StoryIdea> = withContext(ioDispatcher) {
		val dir = getIdeasDirectory().toOkioPath()
		fileSystem.list(dir)
			.filter { IDEA_FILENAME_PATTERN.matches(it.name) }
			.mapNotNull { path -> loadIdeaOrNull(path) }
	}

	private fun loadIdeaOrNull(path: Path): StoryIdea? {
		val text = try {
			fileSystem.read(path) { readUtf8() }
		} catch (e: IOException) {
			Napier.e("Failed to read idea file: ${path.toHPath().path}", e)
			return null
		}
		return codec.decodeOrNull(text) { e ->
			Napier.e("Skipping malformed idea file: ${path.toHPath().path}", e)
		}
	}

	suspend fun createIdea(idea: StoryIdea) = withContext(ioDispatcher) {
		val path = getIdeaPath(idea.id).toOkioPath()
		fileSystem.write(path, mustCreate = true) {
			writeUtf8(codec.encode(idea))
		}
	}

	suspend fun updateIdea(idea: StoryIdea) = withContext(ioDispatcher) {
		val path = getIdeaPath(idea.id).toOkioPath()
		fileSystem.write(path, mustCreate = false) {
			writeUtf8(codec.encode(idea))
		}
	}

	suspend fun deleteIdea(id: IdeaId) = withContext(ioDispatcher) {
		val path = getIdeaPath(id).toOkioPath()
		fileSystem.delete(path, mustExist = false)
	}

	companion object {
		const val IDEAS_DIRECTORY = ".ideas"
		const val IDEAS_FILENAME_EXTENSION = ".md"
		val IDEA_FILENAME_PATTERN = Regex("""idea-([0-9a-fA-F\-]{36})\.md""")

		fun getIdeaFilenameFromId(id: IdeaId): String {
			return "idea-${id.id}$IDEAS_FILENAME_EXTENSION"
		}
	}
}
