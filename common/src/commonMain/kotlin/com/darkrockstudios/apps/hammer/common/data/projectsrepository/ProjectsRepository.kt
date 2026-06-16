package com.darkrockstudios.apps.hammer.common.data.projectsrepository

import com.darkrockstudios.apps.hammer.*
import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.common.components.storyeditor.metadata.Info
import com.darkrockstudios.apps.hammer.common.components.storyeditor.metadata.ProjectMetadata
import com.darkrockstudios.apps.hammer.common.data.*
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsStore
import com.darkrockstudios.apps.hammer.common.data.migrator.PROJECT_DATA_VERSION
import com.darkrockstudios.apps.hammer.common.data.projectmetadata.ProjectMetadataDatasource
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository.Companion.MAX_FILENAME_LENGTH
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository.Companion.encodeForFilename
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository.Companion.validateFileName
import com.darkrockstudios.apps.hammer.common.dependencyinjection.DISPATCHER_DEFAULT
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import com.darkrockstudios.apps.hammer.common.fileio.okio.toHPath
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import okio.FileSystem
import okio.IOException
import okio.Path.Companion.toPath
import org.jetbrains.compose.resources.StringResource
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.qualifier.named
import kotlin.coroutines.CoroutineContext
import kotlin.time.Clock

class ProjectsRepository(
	private val fileSystem: FileSystem,
	globalSettingsStore: GlobalSettingsStore,
	private val projectsMetadataDatasource: ProjectMetadataDatasource
) : KoinComponent {

	private val dispatcherDefault: CoroutineContext by inject(named(DISPATCHER_DEFAULT))
	private val projectsScope = CoroutineScope(dispatcherDefault)

	private var globalSettings = globalSettingsStore.globalSettings

	init {
		watchSettings(globalSettingsStore)

		val projectsDir = getProjectsDirectory().toOkioPath()
		if (!fileSystem.exists(projectsDir)) {
			fileSystem.createDirectory(projectsDir)
		}
	}

	private fun watchSettings(globalSettingsStore: GlobalSettingsStore) {
		projectsScope.launch {
			globalSettingsStore.globalSettingsUpdates.collect { newSettings ->
				globalSettings = newSettings
			}
		}
	}

	fun getProjectsDirectory(): HPath {
		val projectsDir = globalSettings.projectsDirectory.toPath()

		if (!fileSystem.exists(projectsDir)) {
			fileSystem.createDirectories(projectsDir)
		}

		return projectsDir.toHPath()
	}

	fun ensureProjectDirectory() {
		getProjectsDirectory()
	}

	fun removeProjectId(projectDef: ProjectDef) {
		projectsMetadataDatasource.updateMetadata(projectDef) {
			it.copy(info = it.info.copy(serverProjectId = null))
		}
	}

	fun setProjectId(projectDef: ProjectDef, projectId: ProjectId) {
		projectsMetadataDatasource.updateMetadata(projectDef) {
			it.copy(info = it.info.copy(serverProjectId = projectId))
		}
	}

	fun getProjectId(projectDef: ProjectDef): ProjectId? {
		val metadata = projectsMetadataDatasource.loadMetadata(projectDef)
		return metadata.info.serverProjectId
	}

	fun findProject(projectId: ProjectId): ProjectDef? {
		val allProjects = getProjects()
		val found = allProjects.find { project ->
			val id = projectsMetadataDatasource.loadProjectId(project)
			projectId == id
		}
		return found
	}

	fun findProject(projectName: String): ProjectDef? {
		val allProjects = getProjects()
		val found = allProjects.find { project -> project.name == projectName }
		return found
	}

	fun getProjects(projectsDir: HPath = getProjectsDirectory()): List<ProjectDef> {
		val projPath = projectsDir.toOkioPath()
		return fileSystem.list(projPath)
			.filter { fileSystem.metadata(it).isDirectory }
			.filter { it.name.startsWith('.').not() }
			.map { path -> ProjectDef(decodeFromFilename(path.name), path.toHPath()) }
	}

	/** Returns up to [limit] projects ordered by `metadata.info.lastAccessed` descending, optionally excluding [excludeCurrent]. Projects with unreadable metadata sort last. */
	fun getRecentProjects(limit: Int, excludeCurrent: ProjectDef? = null): List<ProjectDef> =
		getProjects()
			.filter { excludeCurrent == null || it.name != excludeCurrent.name }
			.map { def ->
				val lastAccessed = runCatching {
					projectsMetadataDatasource.loadMetadata(def).info.lastAccessed
				}.getOrNull()
				def to lastAccessed
			}
			.sortedByDescending { it.second }
			.take(limit)
			.map { it.first }

	fun getProjectDirectory(projectName: String): HPath {
		val projectsDir = getProjectsDirectory().toOkioPath()
		val projectDir = projectsDir.div(encodeForFilename(projectName))
		return projectDir.toHPath()
	}

	fun getProjectDefinition(projectName: String): ProjectDef {
		val projectDir = getProjectDirectory(projectName).toOkioPath()
		return ProjectDef(projectName, projectDir.toHPath())
	}

	fun createProject(projectName: String): CResult<ProjectDef> {
		val strippedName = projectName.trim()
		val result = validateFileName(strippedName)
		return if (isSuccess(result)) {
			val projectsDir = getProjectsDirectory().toOkioPath()
			val newProjectDir = projectsDir.div(encodeForFilename(strippedName))
			if (fileSystem.exists(newProjectDir)) {
				CResult.failure(ProjectCreationFailedException(Res.string.create_project_error_already_exists))
			} else {
				fileSystem.createDirectory(newProjectDir)

				val newDef = ProjectDef(
					name = strippedName,
					path = newProjectDir.toHPath()
				)

				val metadata = ProjectMetadata(
					info = Info(
						created = Clock.System.now(),
						lastAccessed = Clock.System.now(),
						dataVersion = PROJECT_DATA_VERSION
					)
				)
				projectsMetadataDatasource.saveMetadata(metadata, newDef)

				CResult.success(newDef)
			}
		} else {
			CResult.failure(
				error = result.error,
				displayMessage = result.displayMessage,
				exception = ProjectCreationFailedException(
					(result.displayMessage as? ClientMessage.Resource)?.getStringResource()
				)
			)
		}
	}

	@Suppress("SwallowedException") // IOException maps to a typed failure result
	fun renameProject(projectDef: ProjectDef, newName: String): CResult<ProjectDef> {
		if (validateFileName(newName).isFailure) {
			return CResult.failure(ProjectRenameFailed(ProjectRenameFailed.Reason.InvalidName))
		}

		val projectDir = getProjectDirectory(projectDef.name).toOkioPath()
		val newProjectDir = getProjectDirectory(newName).toOkioPath()
		return if (fileSystem.exists(projectDir)) {
			if (fileSystem.exists(newProjectDir).not()) {
				try {
					fileSystem.atomicMove(source = projectDir, target = newProjectDir)
					CResult.success(ProjectDef(newName, newProjectDir.toHPath()))
				} catch (e: IOException) {
					CResult.failure(ProjectRenameFailed(ProjectRenameFailed.Reason.MoveFailed))
				}
			} else {
				CResult.failure(ProjectRenameFailed(ProjectRenameFailed.Reason.AlreadyExists))
			}
		} else {
			CResult.failure(ProjectRenameFailed(ProjectRenameFailed.Reason.SourceDoesNotExist))
		}
	}

	fun deleteProject(projectDef: ProjectDef): Boolean {
		val projectDir = getProjectDirectory(projectDef.name).toOkioPath()
		return if (fileSystem.exists(projectDir)) {
			fileSystem.deleteRecursively(projectDir)
			true
		} else {
			false
		}
	}

	private data class Validator(
		val name: String,
		val errorMessage: StringResource,
		val condition: (String) -> Boolean,
	)

	companion object {
		const val MAX_FILENAME_LENGTH = 128

		/** Delimiter used in scene filenames (e.g. `order~name~id.md`). Reserved — disallowed in user input. */
		const val FILENAME_DELIMITER = '~'

		// Allowed characters in user-entered project/scene names. Includes:
		//   - letters (\p{L}), digits, space, _, ', +
		//   - newly allowed natively-OS-safe: -.,!?:()&"
		//   - encoded-on-disk via lookalike map: /\*|<>
		//   - typographic quotes: ’ “ ” (U+2019, U+201C, U+201D)
		// Disallowed: ~ (reserved delimiter), control chars, leading dot, Windows reserved names.
		private val fileNameAllowedCharsRegex =
			Regex("""[\d\p{L}+ _'\-.,!?:()&"/\\*|<>’“”]""")
		private val fileNameAllowedRegex =
			Regex("""[\d\p{L}+ _'\-.,!?:()&"/\\*|<>’“”]+""")
		private val whitespaceCollapseRegex = Regex("""\s+""")

		// Windows reserved basenames (case-insensitive, with or without extension).
		private val windowsReservedNames = setOf(
			"CON", "PRN", "AUX", "NUL",
			"COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
			"LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9",
		)

		// Bidirectional map: user-typed OS-forbidden char -> visually similar safe Unicode.
		// Filenames produced by encodeForFilename are safe on Windows/macOS/Linux/iOS.
		private val encodeMap: Map<Char, Char> = mapOf(
			':' to '꞉',  // Modifier Letter Colon
			'?' to '？',  // Fullwidth Question Mark
			'/' to '⁄',  // Fraction Slash
			'\\' to '⧹', // Big Reverse Solidus
			'*' to '∗',  // Asterisk Operator
			'"' to '＂',  // Fullwidth Quotation Mark
			'|' to '｜',  // Fullwidth Vertical Line
			'<' to '‹',  // Single Left-Pointing Angle Quotation
			'>' to '›',  // Single Right-Pointing Angle Quotation
		)
		private val decodeMap: Map<Char, Char> = encodeMap.entries.associate { (k, v) -> v to k }

		/**
		 * Encodes a display name into a filesystem-safe form. OS-forbidden ASCII chars are
		 * replaced with visually similar Unicode lookalikes; trailing `.` and ` ` are trimmed
		 * (Windows constraint). The result still looks essentially like the original to a human.
		 */
		fun encodeForFilename(displayName: String): String {
			val mapped = buildString(displayName.length) {
				for (ch in displayName) append(encodeMap[ch] ?: ch)
			}
			return mapped.trimEnd('.', ' ')
		}

		/** Inverse of [encodeForFilename]. Safe to call on names that were never encoded. */
		fun decodeFromFilename(rawName: String): String {
			return buildString(rawName.length) {
				for (ch in rawName) append(decodeMap[ch] ?: ch)
			}
		}

		/**
		 * Replaces any character not allowed by [validateFileName] with a space, collapses runs of
		 * whitespace, trims, and truncates to [MAX_FILENAME_LENGTH]. Returns an empty string if no
		 * legal characters remain — callers should fall back to a default name in that case.
		 */
		fun sanitizeFileName(name: String): String {
			val mapped = buildString(name.length) {
				for (ch in name) {
					if (fileNameAllowedCharsRegex.matches(ch.toString())) append(ch) else append(' ')
				}
			}
			return mapped
				.replace(whitespaceCollapseRegex, " ")
				.trim()
				.trimEnd('.')
				.take(MAX_FILENAME_LENGTH)
		}

		/**
		 * Maps a (possibly server-supplied) display name to one this device can actually store. A name
		 * that already passes [validateFileName] is returned unchanged; otherwise it is run through
		 * [sanitizeFileName], falling back to [RECOVERED_PROJECT_NAME] when sanitizing leaves nothing
		 * legal. The server's allowed-name set is looser than the client's, so a project synced down
		 * from another device can carry characters (e.g. `#`) this device rejects.
		 */
		fun toLocalSafeName(name: String): String {
			if (validateFileName(name).isSuccess) return name
			return sanitizeFileName(name).ifBlank { RECOVERED_PROJECT_NAME }
		}

		const val RECOVERED_PROJECT_NAME = "Recovered Project"

		private fun isWindowsReservedName(name: String): Boolean {
			val basename = name.substringBeforeLast('.', name).uppercase()
			return basename in windowsReservedNames
		}

		private val fileNameValidations = listOf(
			Validator(
				"Was Blank",
				Res.string.create_project_error_blank
			) { it.isNotBlank() },
			Validator(
				"Invalid Characters",
				Res.string.create_project_error_invalid_characters
			) {
				fileNameAllowedRegex.matches(it) &&
					!it.startsWith('.') &&
					!it.endsWith('.') &&
					!it.endsWith(' ') &&
					!isWindowsReservedName(it)
			},
			Validator(
				"Too Long",
				Res.string.create_project_error_too_long
			) { it.length <= MAX_FILENAME_LENGTH },
		)

		fun validateFileName(fileName: String?): CResult<Unit> {
			return if (fileName != null) {
				var error: StringResource? = null
				for (validator in fileNameValidations) {
					if (validator.condition(fileName).not()) {
						error = validator.errorMessage
						break
					}
				}

				if (error == null) {
					Napier.i("$fileName was valid")
					CResult.success()
				} else {
					Napier.i("$fileName was invalid: $error")
					CResult.failure(ValidationFailedException(error))
				}
			} else {
				CResult.failure(
					error = "",
					displayMessage = Res.string.create_project_error_null_filename.toMsg(),
					exception = ValidationFailedException(Res.string.create_project_error_null_filename)
				)
			}
		}
	}
}
