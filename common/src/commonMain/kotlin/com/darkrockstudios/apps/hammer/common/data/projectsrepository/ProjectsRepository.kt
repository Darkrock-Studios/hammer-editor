package com.darkrockstudios.apps.hammer.common.data.projectsrepository

import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.base.http.projectdata.ProjectData
import com.darkrockstudios.apps.hammer.base.validate.ProjectNameValidationResult
import com.darkrockstudios.apps.hammer.base.validate.ProjectNameValidator
import com.darkrockstudios.apps.hammer.base.validate.validateProjectName
import com.darkrockstudios.apps.hammer.common.components.storyeditor.metadata.Info
import com.darkrockstudios.apps.hammer.common.components.storyeditor.metadata.ProjectMetadata
import com.darkrockstudios.apps.hammer.common.data.CResult
import com.darkrockstudios.apps.hammer.common.data.ClientMessage
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EncyclopediaDatasource
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsStore
import com.darkrockstudios.apps.hammer.common.data.isSuccess
import com.darkrockstudios.apps.hammer.common.data.migrator.Migration3_4
import com.darkrockstudios.apps.hammer.common.data.migrator.PROJECT_DATA_VERSION
import com.darkrockstudios.apps.hammer.common.data.notesrepository.NotesDatasource
import com.darkrockstudios.apps.hammer.common.data.projectdata.ProjectDataDatasource
import com.darkrockstudios.apps.hammer.common.data.projectdata.ProjectDataRepository
import com.darkrockstudios.apps.hammer.common.data.projectdata.StoredProjectData
import com.darkrockstudios.apps.hammer.common.data.projectdata.clearProjectDataSyncBaseline
import com.darkrockstudios.apps.hammer.common.data.projectdata.saveStoredProjectData
import com.darkrockstudios.apps.hammer.common.data.projectmetadata.ProjectMetadataDatasource
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository.Companion.MAX_FILENAME_LENGTH
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository.Companion.RECOVERED_PROJECT_NAME
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository.Companion.encodeForFilename
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository.Companion.sanitizeFileName
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository.Companion.validateFileName
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneDatasource
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.SyncJournalDatasource
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.clearProjectSyncBaseline
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.markProjectRestored
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineDatasource
import com.darkrockstudios.apps.hammer.common.data.toMsg
import com.darkrockstudios.apps.hammer.common.dependencyinjection.DISPATCHER_DEFAULT
import com.darkrockstudios.apps.hammer.common.dependencyinjection.ProjectDefScope
import com.darkrockstudios.apps.hammer.common.util.DeviceLocaleResolver
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import com.darkrockstudios.apps.hammer.common.fileio.okio.toHPath
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import com.darkrockstudios.apps.hammer.create_project_error_already_exists
import com.darkrockstudios.apps.hammer.create_project_error_blank
import com.darkrockstudios.apps.hammer.create_project_error_invalid_characters
import com.darkrockstudios.apps.hammer.create_project_error_null_filename
import com.darkrockstudios.apps.hammer.create_project_error_too_long
import io.github.aakira.napier.Napier
import kotlinx.atomicfu.locks.reentrantLock
import kotlinx.atomicfu.locks.withLock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import net.peanuuutz.tomlkt.Toml
import okio.FileSystem
import okio.IOException
import okio.Path
import okio.Path.Companion.toPath
import org.jetbrains.compose.resources.StringResource
import org.koin.core.component.KoinComponent
import org.koin.core.component.getScopeId
import org.koin.core.component.inject
import org.koin.core.qualifier.named
import kotlin.coroutines.CoroutineContext
import kotlin.time.Clock

/** Told when a project's id changes; see [ProjectsRepository.addProjectIdChangeListener]. */
fun interface ProjectIdChangeListener {
	suspend fun onProjectIdChanged(projectDef: ProjectDef, oldId: ProjectId, newId: ProjectId)
}

class ProjectsRepository(
	private val fileSystem: FileSystem,
	globalSettingsStore: GlobalSettingsStore,
	private val projectsMetadataDatasource: ProjectMetadataDatasource,
	private val toml: Toml,
	private val json: Json,
	private val deviceLocaleResolver: DeviceLocaleResolver,
) : KoinComponent {

	private val dispatcherDefault: CoroutineContext by inject(named(DISPATCHER_DEFAULT))
	private val projectsScope = CoroutineScope(dispatcherDefault)

	private var globalSettings = globalSettingsStore.globalSettings

	private val idChangeListenersLock = reentrantLock()
	private var idChangeListeners: List<ProjectIdChangeListener> = emptyList()

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

	/**
	 * Gives the project a different identity: adopting a server project's id, or re-minting one
	 * that collides. The old sync baseline described the old id, so it goes when the id changes
	 * (a journal flagged for verification keeps its conflict baselines, see [clearProjectSyncBaseline]).
	 */
	suspend fun setProjectId(projectDef: ProjectDef, projectId: ProjectId) {
		val oldId = getProjectId(projectDef)
		if (oldId == projectId) return

		clearSyncBaseline(projectDef)
		projectsMetadataDatasource.updateMetadata(projectDef) {
			it.copy(info = it.info.copy(projectId = projectId))
		}
		notifyProjectIdChanged(projectDef, oldId, projectId)
	}

	/**
	 * [listener] runs inside [setProjectId], after the new id is persisted and before it returns,
	 * so anything keyed by project id is re-keyed before the caller acts on the new id.
	 */
	fun addProjectIdChangeListener(listener: ProjectIdChangeListener) {
		idChangeListenersLock.withLock { idChangeListeners = idChangeListeners + listener }
	}

	// The id is already persisted, so a failing listener must not fail the identity change.
	@Suppress("TooGenericExceptionCaught")
	private suspend fun notifyProjectIdChanged(projectDef: ProjectDef, oldId: ProjectId, newId: ProjectId) {
		val listeners = idChangeListenersLock.withLock { idChangeListeners }
		listeners.forEach { listener ->
			try {
				listener.onProjectIdChanged(projectDef, oldId, newId)
			} catch (e: CancellationException) {
				throw e
			} catch (e: Exception) {
				Napier.e("Project id change listener failed for '${projectDef.name}'", e)
			}
		}
	}

	/** For a move to a different server: every project keeps its id but re-uploads everything. */
	suspend fun clearAllSyncBaselines() {
		getProjects().forEach { clearSyncBaseline(it) }
	}

	/**
	 * Re-mints all but one holder of any id shared by several projects, as happens when a project
	 * folder is copied. The holder with a sync journal keeps the id (it is the one the server knows),
	 * else the earliest created.
	 */
	suspend fun resolveDuplicateProjectIds() {
		val byId = getProjects().groupBy { projectsMetadataDatasource.loadProjectId(it) }
		byId.filterValues { it.size > 1 }.forEach { (projectId, holders) ->
			val keeper = holders.minWith(
				compareBy<ProjectDef> { !hasSyncJournal(it) }
					.thenBy { projectsMetadataDatasource.loadMetadata(it).info.created }
			)
			holders.filter { it != keeper }.forEach { duplicate ->
				val newId = ProjectId.randomUUID()
				Napier.w(
					"Project '${duplicate.name}' shared id ${projectId.id} with '${keeper.name}', " +
						"assigned ${newId.id}"
				)
				setProjectId(duplicate, newId)
			}
		}
	}

	private fun hasSyncJournal(projectDef: ProjectDef): Boolean =
		fileSystem.exists(projectDef.path.toOkioPath() / SyncJournalDatasource.SYNC_FILE_NAME)

	/**
	 * The entity journal and `lastSyncedHash` record what one specific server already confirmed.
	 * Once the project points somewhere else they describe an agreement that no longer exists, and
	 * an untouched project reads as "already in sync": its content is never uploaded, and
	 * `ProjectDataSyncOperation` fast-forwards the local copy to the new server's empty one.
	 * The user's data is untouched — only the baseline goes, so the next sync uploads everything.
	 *
	 * [setProjectId] runs this before the id is written: the two are separate files, so a failure or crash between them
	 * must leave a project that still points at the old server rather than one pointing at the new
	 * server with the old server's baseline, which is the corrupt state this exists to prevent.
	 */
	suspend fun clearSyncBaseline(projectDef: ProjectDef) {
		clearProjectSyncBaseline(projectDef, fileSystem, json)
		clearProjectDataBaseline(projectDef)
	}

	/**
	 * For a project whose files were just replaced by a backup, so its journal and `lastSyncedHash`
	 * describe the server as it was at backup time. The next sync must conflict-check the restored
	 * content against the server rather than let either side silently overwrite the other. The
	 * journal is flagged for that (see [markProjectRestored]); without a `lastSyncedHash`, project
	 * data uploads against a null baseline, which the server answers with a conflict whenever its
	 * copy differs. Restored default project data is the exception: it takes the server's copy.
	 *
	 * [serverKnowsProject] when the project kept an id it has synced under: a backup without a
	 * journal then still gets one, flagged.
	 */
	suspend fun resetSyncStateAfterRestore(projectDef: ProjectDef, serverKnowsProject: Boolean) {
		markProjectRestored(projectDef, fileSystem, json, serverKnowsProject)
		clearProjectDataBaseline(projectDef)
	}

	private suspend fun clearProjectDataBaseline(projectDef: ProjectDef) {
		// An open project serves project data from ProjectDataRepository's cache, so clearing only
		// the file would leave the stale hash live (and get it written straight back out).
		val openProject = getKoin()
			.getScopeOrNull(ProjectDefScope(projectDef).getScopeId())
			?.get<ProjectDataRepository>()
		if (openProject != null) {
			openProject.clearSyncBaseline()
		} else {
			clearProjectDataSyncBaseline(projectDef, fileSystem, toml)
		}
	}

	fun getProjectId(projectDef: ProjectDef): ProjectId =
		projectsMetadataDatasource.loadProjectId(projectDef)

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

	/**
	 * A directory is considered a project only if it contains a [ProjectMetadata.FILENAME]
	 * file and its (decoded) name is a valid project name.
	 */
	fun getProjects(projectsDir: HPath = getProjectsDirectory()): List<ProjectDef> =
		listCandidateDirectories(projectsDir.toOkioPath()).filter {
			fileSystem.metadataOrNull(it.path.toOkioPath() / ProjectMetadata.FILENAME)?.isRegularFile == true
		}

	/** Non-hidden directories whose decoded name is a valid project name, marked or not. */
	private fun listCandidateDirectories(projectsDir: Path): List<ProjectDef> =
		fileSystem.list(projectsDir)
			.filter { it.name.startsWith('.').not() }
			.filter { fileSystem.metadataOrNull(it)?.isDirectory == true }
			.map { path -> ProjectDef(decodeFromFilename(path.name), path.toHPath()) }
			.filter { validateProjectName(it.name) }

	/** True only when nothing exists at [ProjectMetadata.FILENAME], not even a directory or an unreadable file. */
	private fun isUnmarked(projectDir: Path): Boolean =
		fileSystem.metadataOrNull(projectDir / ProjectMetadata.FILENAME) == null

	private fun hasProjectContent(projectDir: Path): Boolean =
		projectContentMarkers.any { fileSystem.exists(projectDir / it) }

	/**
	 * Marks the existing directory for [projectName] as a project under [projectId], if it has no
	 * [ProjectMetadata.FILENAME]. Any sync journal it holds may be stale, so it is reset like a
	 * restored backup's (see [resetSyncStateAfterRestore]). Does not migrate: the caller does, then
	 * resets again, since migration can move a pre-4 settings file's stale baseline into place.
	 *
	 * The projects directory may be shared with unrelated folders, so only an empty directory (a
	 * create interrupted before its project.toml) or one holding Hammer content is adopted.
	 */
	suspend fun adoptUnmarkedDirectory(
		projectName: String,
		projectId: ProjectId,
		serverKnowsProject: Boolean,
	): ProjectDef? {
		val projectDef = getProjectDefinition(projectName)
		val projectDir = projectDef.path.toOkioPath()
		if (fileSystem.metadataOrNull(projectDir)?.isDirectory != true) return null
		if (fileSystem.list(projectDir).isNotEmpty() && !hasProjectContent(projectDir)) return null
		return adoptUnmarkedDirectory(projectDef, projectId, serverKnowsProject)
	}

	private suspend fun adoptUnmarkedDirectory(
		projectDef: ProjectDef,
		projectId: ProjectId,
		serverKnowsProject: Boolean,
	): ProjectDef? {
		val projectDir = projectDef.path.toOkioPath()
		if (fileSystem.metadataOrNull(projectDir)?.isDirectory != true || !isUnmarked(projectDir)) return null

		// Reset before marking, so a crash between the two leaves the folder unmarked and retried.
		resetSyncStateAfterRestore(projectDef, serverKnowsProject)
		projectsMetadataDatasource.createMetadata(projectDef, projectId)
		Napier.w("Adopted unmarked project folder '${projectDef.name}' as ${projectId.id}")
		return projectDef
	}

	/**
	 * Re-marks every directory that lost its [ProjectMetadata.FILENAME] but still holds Hammer
	 * content, each under a fresh id. Directories without such content stay ignored.
	 */
	suspend fun healUnmarkedProjectDirectories(): List<ProjectDef> =
		listCandidateDirectories(getProjectsDirectory().toOkioPath())
			.filter { isUnmarked(it.path.toOkioPath()) && hasProjectContent(it.path.toOkioPath()) }
			.mapNotNull { adoptUnmarkedDirectory(it, ProjectId.randomUUID(), serverKnowsProject = false) }

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

	/**
	 * [seedDefaultLanguage] must be false when materializing a project that already exists
	 * on the server (account sync): a fresh local shell has to keep the never-synced
	 * ProjectData baseline (empty data, null lastSyncedHash), or the first project sync
	 * reads the seed as a phantom local edit and raises a spurious conflict. That case also passes
	 * the server project's [projectId].
	 */
	fun createProject(
		projectName: String,
		seedDefaultLanguage: Boolean,
		projectId: ProjectId = ProjectId.randomUUID(),
	): CResult<ProjectDef> {
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
						dataVersion = PROJECT_DATA_VERSION,
						projectId = projectId,
					)
				)
				projectsMetadataDatasource.saveMetadata(metadata, newDef)

				if (seedDefaultLanguage) {
					// New projects start declared in the device's language, without a region:
					// the auto-default must never gate spell check against a same-language
					// dictionary of another region (en vs en-GB). Users can pick a full tag
					// in project settings.
					val languageTag = deviceLocaleResolver.getCurrentLocale().language
					saveStoredProjectData(
						newDef,
						fileSystem,
						toml,
						StoredProjectData(data = ProjectData(language = languageTag)),
					)
				}

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

	/**
	 * Drops a project's content but keeps the directory and its [ProjectMetadata.FILENAME], so the
	 * project stays listed and keeps its project id. The sync and project data files go with the
	 * content, so a later re-download starts from nothing rather than from a baseline claiming the
	 * content is already present.
	 */
	fun deleteProjectContent(projectDef: ProjectDef) {
		val projectDir = projectDef.path.toOkioPath()
		if (!fileSystem.exists(projectDir)) return
		fileSystem.list(projectDir)
			.filter { it.name != ProjectMetadata.FILENAME }
			.forEach { entry ->
				try {
					fileSystem.deleteRecursively(entry)
				} catch (e: IOException) {
					Napier.e("Failed to delete $entry while removing project content", e)
				}
			}
	}

	/**
	 * Deletes everything under the projects directory: every project along with ideas, backups, and
	 * account sync state. Only for a client discarding its whole account, such as a Wear OS sign out.
	 */
	fun deleteAllLocalData() {
		val projectsDir = getProjectsDirectory().toOkioPath()
		if (!fileSystem.exists(projectsDir)) return
		fileSystem.list(projectsDir).forEach { child ->
			// One unwritable entry must not abandon the rest of the wipe.
			try {
				fileSystem.deleteRecursively(child)
			} catch (e: IOException) {
				Napier.e("Failed to delete $child while discarding local data", e)
			}
		}
	}

	companion object {
		const val MAX_FILENAME_LENGTH = ProjectNameValidator.MAX_LENGTH

		/** Delimiter used in scene filenames (e.g. `order~name~id.md`). Reserved — disallowed in user input. */
		const val FILENAME_DELIMITER = ProjectNameValidator.FILENAME_DELIMITER

		private val whitespaceCollapseRegex = Regex("""\s+""")

		/** Entries that show a directory holds Hammer project content. */
		private val projectContentMarkers = listOf(
			SceneDatasource.SCENE_DIRECTORY,
			NotesDatasource.NOTES_DIRECTORY,
			EncyclopediaDatasource.ENCYCLOPEDIA_DIRECTORY,
			TimeLineDatasource.TIMELINE_DIRECTORY,
			ProjectDataDatasource.FILENAME,
			Migration3_4.LEGACY_PROJECT_SETTINGS_FILENAME,
			SyncJournalDatasource.SYNC_FILE_NAME,
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
					if (ProjectNameValidator.isCharacterAllowed(ch)) append(ch) else append(' ')
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
		 * legal. A project synced down from the server may predate shared validation (or have been
		 * created by an older client) and carry characters (e.g. `#`) this device rejects.
		 */
		fun toLocalSafeName(name: String): String {
			if (validateFileName(name).isSuccess) return name
			return sanitizeFileName(name).ifBlank { RECOVERED_PROJECT_NAME }
		}

		const val RECOVERED_PROJECT_NAME = "Recovered Project"

		/**
		 * Validates a project/scene name using the shared [ProjectNameValidator] and maps the
		 * result to a localized [CResult] for the UI. The rules live in `:base` so the server
		 * enforces the same ones; this only translates them into display messages.
		 */
		fun validateFileName(fileName: String?, usedAsRawFilename: Boolean = true): CResult<Unit> {
			return when (ProjectNameValidator.validate(fileName, usedAsRawFilename)) {
				ProjectNameValidationResult.VALID -> {
					Napier.i("$fileName was valid")
					CResult.success()
				}

				ProjectNameValidationResult.NULL -> CResult.failure(
					error = "",
					displayMessage = Res.string.create_project_error_null_filename.toMsg(),
					exception = ValidationFailedException(Res.string.create_project_error_null_filename)
				)

				ProjectNameValidationResult.BLANK ->
					invalidFileName(fileName, Res.string.create_project_error_blank)

				ProjectNameValidationResult.INVALID_CHARACTERS ->
					invalidFileName(fileName, Res.string.create_project_error_invalid_characters)

				ProjectNameValidationResult.TOO_LONG ->
					invalidFileName(fileName, Res.string.create_project_error_too_long)
			}
		}

		private fun invalidFileName(fileName: String?, message: StringResource): CResult<Unit> {
			Napier.i("$fileName was invalid: $message")
			return CResult.failure(ValidationFailedException(message))
		}
	}
}
