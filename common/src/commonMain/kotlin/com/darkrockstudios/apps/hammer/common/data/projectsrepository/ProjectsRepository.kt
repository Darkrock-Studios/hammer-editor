package com.darkrockstudios.apps.hammer.common.data.projectsrepository

import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.base.GroupId
import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.base.http.MAX_GROUP_NESTING_DEPTH
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
import com.darkrockstudios.apps.hammer.common.data.groupmetadata.GroupMetadata
import com.darkrockstudios.apps.hammer.common.data.groupmetadata.GroupMetadataDatasource
import com.darkrockstudios.apps.hammer.common.data.isSuccess
import com.darkrockstudios.apps.hammer.common.data.migrator.Migration3_4
import com.darkrockstudios.apps.hammer.common.data.migrator.PROJECT_DATA_VERSION
import com.darkrockstudios.apps.hammer.common.data.notesrepository.NotesDatasource
import com.darkrockstudios.apps.hammer.common.data.projectdata.ProjectDataDatasource
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
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.SyncJournal
import com.darkrockstudios.apps.hammer.common.dependencyinjection.ProjectDefScope
import com.darkrockstudios.apps.hammer.common.util.DeviceLocaleResolver
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import com.darkrockstudios.apps.hammer.common.fileio.okio.toHPath
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import com.darkrockstudios.apps.hammer.create_project_error_already_exists
import com.darkrockstudios.apps.hammer.create_project_error_blank
import com.darkrockstudios.apps.hammer.create_project_error_group_missing
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
import org.koin.core.component.inject
import org.koin.core.qualifier.named
import kotlin.coroutines.CoroutineContext
import kotlin.time.Clock

/** Told when a project's id changes; see [ProjectsRepository.addProjectIdChangeListener]. */
fun interface ProjectIdChangeListener {
	suspend fun onProjectIdChanged(projectDef: ProjectDef, oldId: ProjectId, newId: ProjectId)
}

/** Told when a project is deleted; see [ProjectsRepository.addProjectDeletedListener]. */
fun interface ProjectDeletedListener {
	fun onProjectDeleted(projectDef: ProjectDef)
}

class ProjectsRepository(
	private val fileSystem: FileSystem,
	globalSettingsStore: GlobalSettingsStore,
	private val projectsMetadataDatasource: ProjectMetadataDatasource,
	private val groupMetadataDatasource: GroupMetadataDatasource,
	private val toml: Toml,
	private val json: Json,
	private val deviceLocaleResolver: DeviceLocaleResolver,
) : KoinComponent {

	private val dispatcherDefault: CoroutineContext by inject(named(DISPATCHER_DEFAULT))
	private val projectsScope = CoroutineScope(dispatcherDefault)

	private var globalSettings = globalSettingsStore.globalSettings

	private val idChangeListenersLock = reentrantLock()
	private var idChangeListeners: List<ProjectIdChangeListener> = emptyList()
	private val deletedListenersLock = reentrantLock()
	private var deletedListeners: List<ProjectDeletedListener> = emptyList()

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
	 *
	 * Throws [ProjectInUseException] for a project in use: its open scope holds state under the old
	 * id. The old id is read from disk, so a stale [projectDef] cannot skip the write.
	 */
	suspend fun setProjectId(projectDef: ProjectDef, projectId: ProjectId): ProjectDef {
		val updated = projectDef.copy(id = projectId)
		val oldId = projectsMetadataDatasource.loadProjectId(projectDef.path)
		if (oldId == projectId) return updated
		if (isProjectInUse(projectDef)) throw ProjectInUseException(projectDef.name)

		clearSyncBaseline(projectDef)
		projectsMetadataDatasource.updateMetadata(projectDef) {
			it.copy(info = it.info.copy(projectId = projectId))
		}
		notifyProjectIdChanged(updated, oldId, projectId)
		return updated
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

	/** [listener] runs inside [deleteProject], once the project's directory is gone. */
	fun addProjectDeletedListener(listener: ProjectDeletedListener) {
		deletedListenersLock.withLock { deletedListeners = deletedListeners + listener }
	}

	// The project is already gone, so a failing listener must not fail the delete.
	@Suppress("TooGenericExceptionCaught")
	private fun notifyProjectDeleted(projectDef: ProjectDef) {
		val listeners = deletedListenersLock.withLock { deletedListeners }
		listeners.forEach { listener ->
			try {
				listener.onProjectDeleted(projectDef)
			} catch (e: Exception) {
				Napier.e("Project deleted listener failed for '${projectDef.name}'", e)
			}
		}
	}

	/**
	 * For a move to a different server: every project keeps its id but re-uploads everything.
	 * No project may be open; that is checked before any baseline changes.
	 */
	suspend fun clearAllSyncBaselines() {
		findProjectInUse()?.let { throw ProjectInUseException(it.name) }
		getProjects().forEach { clearSyncBaseline(it) }
	}

	fun findProjectInUse(): ProjectDef? = getProjects().firstOrNull { isProjectInUse(it) }

	/**
	 * Re-mints all but one holder of any id shared by several projects, as happens when a project
	 * folder is copied. The holder with a sync journal keeps the id (it is the one the server knows),
	 * else the earliest created.
	 */
	suspend fun resolveDuplicateProjectIds() {
		val byId = getProjects().groupBy { it.id }
		byId.filterValues { it.size > 1 }.forEach { (projectId, holders) ->
			val keeper = holders.minWith(
				compareBy<ProjectDef> { !hasSyncJournal(it) }
					.thenBy { projectsMetadataDatasource.loadMetadata(it).info.created }
			)
			holders.filter { it != keeper }.forEach { duplicate ->
				val newId = ProjectId.randomUUID()
				try {
					setProjectId(duplicate, newId)
				} catch (_: ProjectInUseException) {
					// Left for the next start, which runs before anything can open it.
					Napier.w("Project '${duplicate.name}' shares id ${projectId.id} with '${keeper.name}' but is in use")
					return@forEach
				}
				Napier.w(
					"Project '${duplicate.name}' shared id ${projectId.id} with '${keeper.name}', " +
						"assigned ${newId.id}"
				)
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
	 *
	 * The project must not be open: an open project caches its project data in memory, so it
	 * clears its own baseline through its [SyncJournal] and `ProjectDataRepository`.
	 */
	suspend fun clearSyncBaseline(projectDef: ProjectDef) {
		if (isProjectInUse(projectDef)) throw ProjectInUseException(projectDef.name)
		clearProjectSyncBaseline(projectDef, fileSystem, json)
		clearProjectDataSyncBaseline(projectDef, fileSystem, toml)
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
		if (isProjectInUse(projectDef)) throw ProjectInUseException(projectDef.name)
		markProjectRestored(projectDef, fileSystem, json, serverKnowsProject)
		clearProjectDataSyncBaseline(projectDef, fileSystem, toml)
	}

	fun loadProjectMetadata(projectDef: ProjectDef): ProjectMetadata =
		projectsMetadataDatasource.loadMetadata(projectDef)

	fun updateProjectMetadata(projectDef: ProjectDef, block: (ProjectMetadata) -> ProjectMetadata) =
		projectsMetadataDatasource.updateMetadata(projectDef, block)

	/** True while the project is open in an editor or a temporary task; its id and name are then fixed. */
	fun isProjectInUse(projectDir: HPath): Boolean =
		getKoin().getScopeOrNull(ProjectDefScope.scopeIdFor(projectDir)) != null

	fun isProjectInUse(projectDef: ProjectDef): Boolean = isProjectInUse(projectDef.path)

	fun findProject(projectId: ProjectId): ProjectDef? =
		scan().projects.asSequence()
			.mapNotNull { loadProjectDef(it.name, it.dir) }
			.firstOrNull { it.id == projectId }

	fun findProject(projectName: String): ProjectDef? =
		scan().projects.asSequence()
			.filter { it.name == projectName }
			.firstNotNullOfOrNull { loadProjectDef(it.name, it.dir) }

	/**
	 * Every project at any level of the projects directory. A directory is considered a project only
	 * if it contains a [ProjectMetadata.FILENAME] file and its (decoded) name is a valid project name.
	 * Each id is read from that file, minted if it has none (see
	 * [ProjectMetadataDatasource.loadProjectId]). A project deleted while the list is built is left
	 * out. Groups are not read; [getProjectTree] lists them.
	 */
	fun getProjects(projectsDir: HPath = getProjectsDirectory()): List<ProjectDef> =
		scan(projectsDir.toOkioPath()).projects.mapNotNull { loadProjectDef(it.name, it.dir) }

	/**
	 * The projects directory as groups and projects, each project placed in the group whose directory
	 * holds it. Each group's [GroupMetadata.FILENAME] is healed as it is read (see
	 * [GroupMetadataDatasource.loadMetadata]), and a group sharing an id with one listed before it, as
	 * a copied folder does, gets a fresh one. Each group's collapsed state is read along with it.
	 */
	fun getProjectTree(projectsDir: HPath = getProjectsDirectory()): ProjectTree {
		val scan = scan(projectsDir.toOkioPath())
		val groupIds = mutableMapOf<Path, GroupId>()
		val collapsed = mutableSetOf<GroupId>()
		// A group that cannot be read takes its subtree with it: listing its contents a level up
		// would read as a move.
		val skippedDirs = mutableSetOf<Path>()
		val groups = scan.groups.mapNotNull { scanned ->
			if (scanned.parentDir in skippedDirs) {
				skippedDirs += scanned.dir
				return@mapNotNull null
			}
			val parentId = scanned.parentDir?.let(groupIds::get)
			val loaded = loadGroup(scanned, parentId, taken = groupIds.values.toSet())
			if (loaded == null) {
				skippedDirs += scanned.dir
				return@mapNotNull null
			}
			groupIds[scanned.dir] = loaded.def.id
			if (loaded.collapsed) collapsed += loaded.def.id
			loaded.def
		}
		val placement = mutableMapOf<ProjectId, GroupId?>()
		val projects = scan.projects
			.filterNot { it.parentDir in skippedDirs }
			.mapNotNull { scanned ->
				loadProjectDef(scanned.name, scanned.dir)
					?.also { placement[it.id] = scanned.parentDir?.let(groupIds::get) }
			}
		return ProjectTree(groups, projects, placement, collapsed)
	}

	/** Null when its [ProjectMetadata.FILENAME] cannot be read, as when the project is deleted meanwhile. */
	private fun loadProjectDef(name: String, dir: Path): ProjectDef? =
		try {
			ProjectDef(projectsMetadataDatasource.loadProjectId(dir.toHPath()), name, dir.toHPath())
		} catch (e: IOException) {
			Napier.w("Skipped project '$name': could not read its ${ProjectMetadata.FILENAME}", e)
			null
		}

	private class LoadedGroup(val def: GroupDef, val collapsed: Boolean)

	/** Null when its [GroupMetadata.FILENAME] cannot be read or healed, as when the group is deleted meanwhile. */
	private fun loadGroup(scanned: ScannedDir, parentId: GroupId?, taken: Set<GroupId>): LoadedGroup? =
		try {
			val dir = scanned.dir.toHPath()
			val metadata = groupMetadataDatasource.loadMetadata(dir)
			val loadedId = checkNotNull(metadata.info.groupId)
			val groupId = if (loadedId in taken) {
				GroupId.randomUUID().also { newId ->
					Napier.w("Group '${scanned.name}' shared id ${loadedId.id} with another group, assigned ${newId.id}")
					groupMetadataDatasource.updateMetadata(dir) { it.copy(info = it.info.copy(groupId = newId)) }
				}
			} else {
				loadedId
			}
			LoadedGroup(
				def = GroupDef(groupId, scanned.name, dir, parentId, metadata.info.description),
				collapsed = metadata.info.collapsed,
			)
		} catch (e: IOException) {
			Napier.w("Skipped group '${scanned.name}': could not read its ${GroupMetadata.FILENAME}", e)
			null
		}

	private data class ScannedDir(val name: String, val dir: Path, val parentDir: Path?)

	/** Groups are listed parents first. [unmarked] holds Hammer content without a [ProjectMetadata.FILENAME]. */
	private class Scan(val groups: List<ScannedDir>, val projects: List<ScannedDir>, val unmarked: List<ScannedDir>)

	/**
	 * Walks [dir], whose entries sit at [depth] (1 for the projects directory's own), skipping hidden
	 * entries, files and names that are not valid project names. A directory holding a
	 * [ProjectMetadata.FILENAME] file is a project, one holding [GroupMetadata.FILENAME] a group, and
	 * one holding Hammer content but neither marker is unmarked and not descended into. Any other
	 * directory is a group if it holds a group or project, so unrelated folders are never claimed.
	 * Groups end at [MAX_GROUP_NESTING_DEPTH]. Each directory is listed once, and a marker is only
	 * looked at when that listing holds its name.
	 */
	private fun scan(dir: Path = getProjectsDirectory().toOkioPath(), depth: Int = 1): Scan {
		val groups = mutableListOf<ScannedDir>()
		val projects = mutableListOf<ScannedDir>()
		val unmarked = mutableListOf<ScannedDir>()

		fun visit(entries: List<Path>, parentGroup: Path?, entryDepth: Int): Boolean {
			var found = false
			for (entry in entries) {
				if (entry.name.startsWith('.')) continue
				if (fileSystem.metadataOrNull(entry)?.isDirectory != true) continue
				val name = decodeFromFilename(entry.name)
				if (!validateProjectName(name)) continue

				val children = fileSystem.listOrNull(entry).orEmpty()
				val listing = Listing(children)
				val scanned = ScannedDir(name, entry, parentGroup)
				val marker = if (listing.has(ProjectMetadata.FILENAME)) {
					fileSystem.metadataOrNull(entry / ProjectMetadata.FILENAME)
				} else {
					null
				}
				when {
					marker?.isRegularFile == true -> {
						projects += scanned
						found = true
					}
					// A project.toml in another form is the metadata datasource's to recover.
					marker != null -> Unit
					listing.has(GroupMetadata.FILENAME) && groupMetadataDatasource.isMarked(entry.toHPath()) -> {
						if (entryDepth <= MAX_GROUP_NESTING_DEPTH) {
							groups += scanned
							visit(children, entry, entryDepth + 1)
							found = true
						}
					}
					hasProjectContent(entry, listing) -> {
						unmarked += scanned
						found = true
					}
					entryDepth <= MAX_GROUP_NESTING_DEPTH -> {
						val index = groups.size
						if (visit(children, entry, entryDepth + 1)) {
							groups.add(index, scanned)
							found = true
						}
					}
				}
			}
			return found
		}

		visit(fileSystem.listOrNull(dir).orEmpty(), parentGroup = null, entryDepth = depth)
		return Scan(groups, projects, unmarked)
	}

	/**
	 * A directory's entry names, matched ignoring case so a case-insensitive filesystem still finds a
	 * marker spelled differently. Callers then check the marker's own path.
	 */
	private class Listing(entries: List<Path>) {
		private val names = entries.mapTo(HashSet()) { it.name.lowercase() }

		fun has(name: String): Boolean = name.lowercase() in names
	}

	/** How far [dir] sits below the projects directory: 1 for one of its own entries. */
	private fun depthBelowRoot(dir: Path): Int = dir.relativeTo(getProjectsDirectory().toOkioPath()).segments.size

	private fun Path.isSameOrUnder(ancestor: Path): Boolean = generateSequence(this) { it.parent }.any { it == ancestor }

	/** True only when nothing exists at [ProjectMetadata.FILENAME], not even a directory or an unreadable file. */
	private fun isUnmarked(projectDir: Path): Boolean =
		fileSystem.metadataOrNull(projectDir / ProjectMetadata.FILENAME) == null

	/**
	 * A child project or group that happens to be named like a content entry (a group's "Notes"
	 * subgroup, or "notes" on a case-insensitive filesystem) is not content.
	 */
	private fun hasProjectContent(projectDir: Path, listing: Listing): Boolean =
		projectContentMarkers.any { listing.has(it) && isProjectContent(projectDir / it) }

	private fun isProjectContent(entry: Path): Boolean {
		val metadata = fileSystem.metadataOrNull(entry) ?: return false
		if (!metadata.isDirectory) return true
		return fileSystem.metadataOrNull(entry / ProjectMetadata.FILENAME) == null &&
			!groupMetadataDatasource.isMarked(entry.toHPath())
	}

	/**
	 * Marks the existing top-level directory for [projectName] as a project under [projectId], if it has no
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
		// Trimmed as in createProject, so both resolve a name to the same directory.
		val trimmedName = projectName.trim()
		val projectDir = getProjectDirectory(trimmedName).toOkioPath()
		if (fileSystem.metadataOrNull(projectDir)?.isDirectory != true) return null
		val entries = fileSystem.list(projectDir)
		if (entries.isNotEmpty() && !hasProjectContent(projectDir, Listing(entries))) return null
		return adoptUnmarkedDirectory(trimmedName, projectDir, projectId, serverKnowsProject)
	}

	private suspend fun adoptUnmarkedDirectory(
		projectName: String,
		projectDir: Path,
		projectId: ProjectId,
		serverKnowsProject: Boolean,
	): ProjectDef? {
		if (fileSystem.metadataOrNull(projectDir)?.isDirectory != true || !isUnmarked(projectDir)) return null
		val projectDef = ProjectDef(projectId, projectName, projectDir.toHPath())

		// Reset before marking, so a crash between the two leaves the folder unmarked and retried.
		resetSyncStateAfterRestore(projectDef, serverKnowsProject)
		projectsMetadataDatasource.createMetadata(projectDef, projectId)
		Napier.w("Adopted unmarked project folder '${projectDef.name}' as ${projectId.id}")
		return projectDef
	}

	/**
	 * Re-marks every directory, at any level, that lost its [ProjectMetadata.FILENAME] but still holds
	 * Hammer content, each under a fresh id. Directories without such content stay ignored.
	 */
	suspend fun healUnmarkedProjectDirectories(): List<ProjectDef> =
		scan().unmarked
			.mapNotNull { adoptUnmarkedDirectory(it.name, it.dir, ProjectId.randomUUID(), serverKnowsProject = false) }

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

	/** Where a top-level project named [projectName] lives; a project in a group lives elsewhere, see [ProjectDef.path]. */
	fun getProjectDirectory(projectName: String): HPath {
		val projectsDir = getProjectsDirectory().toOkioPath()
		val projectDir = projectsDir.div(encodeForFilename(projectName))
		return projectDir.toHPath()
	}

	/**
	 * [seedDefaultLanguage] must be false when materializing a project that already exists
	 * on the server (account sync): a fresh local shell has to keep the never-synced
	 * ProjectData baseline (empty data, null lastSyncedHash), or the first project sync
	 * reads the seed as a phantom local edit and raises a spurious conflict. That case also passes
	 * the server project's [projectId].
	 *
	 * The project goes in [group], or the top level when null. Its name must be free in every group,
	 * since project names are unique per account.
	 */
	fun createProject(
		projectName: String,
		seedDefaultLanguage: Boolean,
		projectId: ProjectId = ProjectId.randomUUID(),
		group: GroupDef? = null,
	): CResult<ProjectDef> {
		val strippedName = projectName.trim()
		val result = validateFileName(strippedName)
		if (!isSuccess(result)) {
			return CResult.failure(
				error = result.error,
				displayMessage = result.displayMessage,
				exception = ProjectCreationFailedException(
					(result.displayMessage as? ClientMessage.Resource)?.getStringResource()
				)
			)
		}

		val parentDir = group.dirOrRoot()
		if (fileSystem.metadataOrNull(parentDir)?.isDirectory != true) {
			return projectCreationFailure(Res.string.create_project_error_group_missing)
		}
		val newProjectDir = parentDir / encodeForFilename(strippedName)
		if (fileSystem.exists(newProjectDir) || findProject(strippedName) != null) {
			return projectCreationFailure(Res.string.create_project_error_already_exists)
		}

		fileSystem.createDirectory(newProjectDir)

		val newDef = ProjectDef(
			id = projectId,
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

		return CResult.success(newDef)
	}

	private fun projectCreationFailure(message: StringResource): CResult<ProjectDef> =
		CResult.failure(
			error = "",
			displayMessage = message.toMsg(),
			exception = ProjectCreationFailedException(message),
		)

	/** Renames the project in place, within its group. The new name must be free in every group. */
	fun renameProject(projectDef: ProjectDef, newName: String): CResult<ProjectDef> {
		if (isProjectInUse(projectDef)) return projectRenameFailure(ProjectRenameFailed.Reason.ProjectInUse)
		if (validateFileName(newName).isFailure) return projectRenameFailure(ProjectRenameFailed.Reason.InvalidName)

		val projectDir = projectDef.path.toOkioPath()
		if (!fileSystem.exists(projectDir)) return projectRenameFailure(ProjectRenameFailed.Reason.SourceDoesNotExist)

		val newProjectDir = projectDir.parentOrRoot() / encodeForFilename(newName)
		val nameTaken = findProject(newName)?.let { it.path != projectDef.path } == true
		if (fileSystem.exists(newProjectDir) || nameTaken) {
			return projectRenameFailure(ProjectRenameFailed.Reason.AlreadyExists)
		}

		return moveDirectory(projectDir, newProjectDir, ::projectMoveFailed) {
			projectDef.copy(name = newName, path = newProjectDir.toHPath())
		}
	}

	/**
	 * Moves the project into [target], or the top level when null, keeping its name and id. Refused
	 * for a project in use: its open scope is keyed by its path.
	 */
	fun moveProject(projectDef: ProjectDef, target: GroupDef?): CResult<ProjectDef> {
		if (isProjectInUse(projectDef)) return projectRenameFailure(ProjectRenameFailed.Reason.ProjectInUse)

		val projectDir = projectDef.path.toOkioPath()
		if (!fileSystem.exists(projectDir)) return projectRenameFailure(ProjectRenameFailed.Reason.SourceDoesNotExist)

		val targetDir = target.dirOrRoot()
		if (fileSystem.metadataOrNull(targetDir)?.isDirectory != true) {
			return projectRenameFailure(ProjectRenameFailed.Reason.TargetMissing)
		}

		val newProjectDir = targetDir / projectDir.name
		if (newProjectDir == projectDir) return CResult.success(projectDef)
		if (fileSystem.exists(newProjectDir)) return projectRenameFailure(ProjectRenameFailed.Reason.AlreadyExists)

		return moveDirectory(projectDir, newProjectDir, ::projectMoveFailed) {
			projectDef.copy(path = newProjectDir.toHPath())
		}
	}

	@Suppress("SwallowedException") // IOException maps to a typed failure result
	private fun <T> moveDirectory(source: Path, target: Path, failure: () -> CResult<T>, moved: () -> T): CResult<T> =
		try {
			fileSystem.atomicMove(source = source, target = target)
			CResult.success(moved())
		} catch (e: IOException) {
			failure()
		}

	private fun projectMoveFailed(): CResult<ProjectDef> = projectRenameFailure(ProjectRenameFailed.Reason.MoveFailed)

	private fun groupMoveFailed(): CResult<GroupDef> = groupFailure(GroupOperationFailed.Reason.MoveFailed)

	fun deleteProject(projectDef: ProjectDef): Boolean {
		val projectDir = projectDef.path.toOkioPath()
		return if (fileSystem.exists(projectDir)) {
			fileSystem.deleteRecursively(projectDir)
			notifyProjectDeleted(projectDef)
			true
		} else {
			false
		}
	}

	/**
	 * Creates an empty group in [parent], or the top level when null. Account sync passes the server
	 * group's [groupId]; a new group mints one.
	 */
	fun createGroup(
		name: String,
		parent: GroupDef?,
		groupId: GroupId = GroupId.randomUUID(),
		description: String? = null,
	): CResult<GroupDef> {
		val trimmedName = name.trim()
		if (validateFileName(trimmedName).isFailure) return groupFailure(GroupOperationFailed.Reason.InvalidName)

		val parentDir = parent.dirOrRoot()
		if (fileSystem.metadataOrNull(parentDir)?.isDirectory != true) {
			return groupFailure(GroupOperationFailed.Reason.TargetMissing)
		}
		if (parent != null && depthBelowRoot(parentDir) + 1 > MAX_GROUP_NESTING_DEPTH) {
			return groupFailure(GroupOperationFailed.Reason.TooDeep)
		}

		val groupDir = parentDir / encodeForFilename(trimmedName)
		if (fileSystem.exists(groupDir)) return groupFailure(GroupOperationFailed.Reason.AlreadyExists)

		fileSystem.createDirectory(groupDir)
		val metadata = groupMetadataDatasource.createMetadata(groupDir.toHPath(), groupId, description)
		return CResult.success(GroupDef(groupId, trimmedName, groupDir.toHPath(), parent?.id, metadata.info.description))
	}

	/** Renames the group in place. Its name need only be free among its siblings. */
	fun renameGroup(groupDef: GroupDef, newName: String): CResult<GroupDef> {
		val trimmedName = newName.trim()
		if (validateFileName(trimmedName).isFailure) return groupFailure(GroupOperationFailed.Reason.InvalidName)

		val groupDir = groupDef.path.toOkioPath()
		if (fileSystem.metadataOrNull(groupDir)?.isDirectory != true) {
			return groupFailure(GroupOperationFailed.Reason.SourceDoesNotExist)
		}

		val newGroupDir = groupDir.parentOrRoot() / encodeForFilename(trimmedName)
		if (newGroupDir == groupDir) return CResult.success(groupDef.copy(name = trimmedName))
		if (isGroupInUse(groupDef)) return groupFailure(GroupOperationFailed.Reason.GroupInUse)
		if (fileSystem.exists(newGroupDir)) return groupFailure(GroupOperationFailed.Reason.AlreadyExists)

		return moveDirectory(groupDir, newGroupDir, ::groupMoveFailed) {
			groupDef.copy(name = trimmedName, path = newGroupDir.toHPath())
		}
	}

	/** Moves the group, with everything in it, into [newParent], or the top level when null. */
	fun moveGroup(groupDef: GroupDef, newParent: GroupDef?): CResult<GroupDef> {
		val groupDir = groupDef.path.toOkioPath()
		if (fileSystem.metadataOrNull(groupDir)?.isDirectory != true) {
			return groupFailure(GroupOperationFailed.Reason.SourceDoesNotExist)
		}

		val parentDir = newParent.dirOrRoot()
		if (parentDir.isSameOrUnder(groupDir)) return groupFailure(GroupOperationFailed.Reason.WouldCycle)
		if (fileSystem.metadataOrNull(parentDir)?.isDirectory != true) {
			return groupFailure(GroupOperationFailed.Reason.TargetMissing)
		}

		val newGroupDir = parentDir / groupDir.name
		if (newGroupDir == groupDir) return CResult.success(groupDef.copy(parentId = newParent?.id))
		if (isGroupInUse(groupDef)) return groupFailure(GroupOperationFailed.Reason.GroupInUse)

		val parentDepth = if (newParent == null) 0 else depthBelowRoot(parentDir)
		if (parentDepth + groupSubtreeHeight(groupDir) > MAX_GROUP_NESTING_DEPTH) {
			return groupFailure(GroupOperationFailed.Reason.TooDeep)
		}
		if (fileSystem.exists(newGroupDir)) return groupFailure(GroupOperationFailed.Reason.AlreadyExists)

		return moveDirectory(groupDir, newGroupDir, ::groupMoveFailed) {
			groupDef.copy(path = newGroupDir.toHPath(), parentId = newParent?.id)
		}
	}

	/** Levels of groups from [groupDir] down to its deepest subgroup: 1 when it has none. */
	private fun groupSubtreeHeight(groupDir: Path): Int {
		val depth = depthBelowRoot(groupDir)
		val deepest = scan(groupDir, depth + 1).groups.maxOfOrNull { depthBelowRoot(it.dir) } ?: depth
		return deepest - depth + 1
	}

	/**
	 * Deletes the group but none of its contents: everything in it moves up one level first. Refused,
	 * with nothing moved, when any entry would collide with one already in the parent.
	 */
	fun deleteGroup(groupDef: GroupDef): CResult<Unit> {
		val groupDir = groupDef.path.toOkioPath()
		val entries = fileSystem.listOrNull(groupDir)
			?: return groupFailure(GroupOperationFailed.Reason.SourceDoesNotExist)
		if (isGroupInUse(groupDef)) return groupFailure(GroupOperationFailed.Reason.GroupInUse)

		val parentDir = groupDir.parentOrRoot()
		val (osClutter, contents) = entries
			.filter { it.name != GroupMetadata.FILENAME }
			.partition { it.name in osClutterFiles && fileSystem.metadataOrNull(it)?.isRegularFile == true }
		if (contents.any { fileSystem.exists(parentDir / it.name) }) {
			return groupFailure(GroupOperationFailed.Reason.AlreadyExists)
		}

		val moved = mutableListOf<Path>()
		return try {
			contents.forEach {
				fileSystem.atomicMove(source = it, target = parentDir / it.name)
				moved += it
			}
			osClutter.forEach { fileSystem.delete(it, mustExist = false) }
			groupMetadataDatasource.deleteMetadata(groupDef.path)
			fileSystem.delete(groupDir, mustExist = false)
			CResult.success()
		} catch (e: IOException) {
			Napier.e("Failed to delete group '${groupDef.name}'", e)
			if (moved.size < contents.size) moveBack(moved, parentDir)
			groupFailure(GroupOperationFailed.Reason.MoveFailed)
		}
	}

	/** Best effort: returns [entries], already moved up into [parentDir], to the group they came from. */
	private fun moveBack(entries: List<Path>, parentDir: Path) {
		entries.asReversed().forEach { original ->
			try {
				fileSystem.atomicMove(source = parentDir / original.name, target = original)
			} catch (e: IOException) {
				Napier.e("Could not move '${original.name}' back into its group", e)
			}
		}
	}

	/** True while any project in the group, at any depth, is in use; see [isProjectInUse]. */
	fun isGroupInUse(groupDef: GroupDef): Boolean {
		val groupDir = groupDef.path.toOkioPath()
		return scan(groupDir, depthBelowRoot(groupDir) + 1).projects.any { isProjectInUse(it.dir.toHPath()) }
	}

	/**
	 * Gives the group a different identity, as when account sync adopts a server group's id.
	 * Throws [IOException] when the group's directory is gone.
	 */
	fun setGroupId(groupDef: GroupDef, groupId: GroupId): GroupDef {
		groupMetadataDatasource.updateMetadata(groupDef.path) { it.copy(info = it.info.copy(groupId = groupId)) }
		return groupDef.copy(id = groupId)
	}

	/** Fails when the group's directory is gone. */
	fun updateGroupDescription(groupDef: GroupDef, description: String?): CResult<GroupDef> =
		try {
			val updated = groupMetadataDatasource.updateMetadata(groupDef.path) {
				it.copy(info = it.info.copy(description = description))
			}
			CResult.success(groupDef.copy(description = updated.info.description))
		} catch (e: IOException) {
			Napier.e("Failed to update the description of group '${groupDef.name}'", e)
			groupFailure(GroupOperationFailed.Reason.SourceDoesNotExist)
		}

	/** Best effort: collapsed state is local display state, so a failed write is only logged. */
	fun setGroupCollapsed(groupDef: GroupDef, collapsed: Boolean) {
		try {
			groupMetadataDatasource.updateMetadata(groupDef.path) {
				it.copy(info = it.info.copy(collapsed = collapsed))
			}
		} catch (e: IOException) {
			Napier.w("Failed to save collapsed state of group '${groupDef.name}'", e)
		}
	}

	private fun Path.parentOrRoot(): Path = parent ?: getProjectsDirectory().toOkioPath()

	private fun GroupDef?.dirOrRoot(): Path = this?.path?.toOkioPath() ?: getProjectsDirectory().toOkioPath()

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

		/** Files an OS drops into any folder it shows; deleting a group discards them rather than moving them up. */
		private val osClutterFiles = setOf(".DS_Store", "Thumbs.db", "desktop.ini")

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
