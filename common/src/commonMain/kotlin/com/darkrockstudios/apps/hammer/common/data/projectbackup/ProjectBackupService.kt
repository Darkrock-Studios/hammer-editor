package com.darkrockstudios.apps.hammer.common.data.projectbackup

import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.base.validate.validateProjectName
import com.darkrockstudios.apps.hammer.common.components.storyeditor.metadata.ProjectMetadata
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsStore
import com.darkrockstudios.apps.hammer.common.data.migrator.DataMigrator
import com.darkrockstudios.apps.hammer.common.data.projectmetadata.ProjectMetadataDatasource
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.SyncJournalDatasource
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import com.darkrockstudios.apps.hammer.common.fileio.okio.toHPath
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import com.darkrockstudios.apps.hammer.common.util.format
import com.darkrockstudios.apps.hammer.common.util.zip.unzipToDirectory
import com.darkrockstudios.apps.hammer.common.util.zip.zipDirectory
import io.github.aakira.napier.Napier
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.*
import okio.FileNotFoundException
import okio.FileSystem
import okio.IOException
import okio.Path
import org.koin.core.component.KoinComponent
import kotlin.time.Clock
import kotlin.time.Instant

open class ProjectBackupService(
	protected val fileSystem: FileSystem,
	protected val projectsRepository: ProjectsRepository,
	protected val globalSettingsStore: GlobalSettingsStore,
	protected val clock: Clock,
	private val dataMigrator: DataMigrator,
	private val projectMetadataDatasource: ProjectMetadataDatasource,
) : KoinComponent {

	private val restoreLock = Mutex()

	fun getBackupsDirectory(): HPath {
		val dir = (projectsRepository.getProjectsDirectory().toOkioPath() / BACKUP_DIRECTORY)

		if (fileSystem.exists(dir).not()) {
			fileSystem.createDirectories(dir)
		}

		return dir.toHPath()
	}

	fun getBackups(projectName: String): List<ProjectBackupDef> {
		val dir = getBackupsDirectory().toOkioPath()
		val keys = backupKeysForProject(projectName)
		return fileSystem.list(dir)
			.filter {
				try {
					fileSystem.metadata(it).isRegularFile
				} catch (_: FileNotFoundException) {
					false
				}
			}
			.mapNotNull { path -> matchBackup(path, projectName, keys) }
			.sortedBy { it.date }
	}

	// The directory-safe encoding plus the legacy `spaces -> underscores` name, so backups written
	// by older clients are still matched to their project.
	private fun backupKeysForProject(projectName: String): Set<String> = setOf(
		ProjectsRepository.encodeForFilename(projectName),
		legacyBackupName(projectName),
	)

	private fun legacyBackupName(projectName: String): String = projectName.replace(" ", "_")

	protected fun createNewProjectBackupDef(projectDef: ProjectDef): ProjectBackupDef {
		val path = pathForBackup(projectDef.name, clock.now())

		return ProjectBackupDef(
			path = path,
			projectName = projectDef.name,
			date = clock.now()
		)
	}

	private fun pathForBackup(projectName: String, date: Instant): HPath {
		val filename = filenameForBackup(projectName, date)
		val dir = getBackupsDirectory().toOkioPath()
		return (dir / filename).toHPath()
	}

	private fun filenameForBackup(projectName: String, date: Instant): String {
		val backupName = ProjectsRepository.encodeForFilename(projectName)
		val dateStr = date.toBackupDate()
		return "$backupName-$dateStr.zip"
	}

	fun deleteBackup(backup: ProjectBackupDef) {
		try {
			val path = backup.path.toOkioPath()
			if (fileSystem.exists(path)) {
				fileSystem.delete(path)
				Napier.i("Deleted backup: ${backup.path.name}")
			} else {
				Napier.w("Backup file not found: ${backup.path.name}")
			}
		} catch (e: IOException) {
			Napier.e("Failed to delete backup: ${backup.path.name}", e)
			throw e
		}
	}

	fun cullBackups(project: ProjectDef) {
		val settings = globalSettingsStore.globalSettings

		val backups = getBackups(project.name).toMutableList()

		// Oldest first
		backups.sortBy { it.date }

		// Delete the oldest backups to get under budget
		if (backups.size > settings.maxBackups) {
			val overBudget = backups.size - settings.maxBackups
			Napier.i("Project '${project.name}' is over it's backup budget by $overBudget backups.")
			for (ii in 0 until overBudget) {
				val oldBackup = backups[ii]
				fileSystem.delete(oldBackup.path.toOkioPath())
				Napier.i("Deleted backup: ${oldBackup.path.name}")
			}
		}
	}

	private fun matchBackup(path: Path, projectName: String, keys: Set<String>): ProjectBackupDef? {
		val match = FILE_NAME_PATTERN.matchEntire(path.name) ?: return null
		val fileKey = match.groups[1]?.value ?: return null
		if (fileKey !in keys) return null

		return ProjectBackupDef(
			path = path.toHPath(),
			projectName = projectName,
			date = backupDate(path, match.groups[2]?.value)
		)
	}

	// Order by file modification time, not the filename's encoded date: backups written
	// before the date-format fix can encode a time months off and would otherwise cull
	// the newest backups instead of the oldest.
	private fun backupDate(path: Path, encodedDate: String?): Instant {
		val modified = try {
			fileSystem.metadata(path).lastModifiedAtMillis
		} catch (_: IOException) {
			null
		}
		if (modified != null) return Instant.fromEpochMilliseconds(modified)

		return encodedDate?.let { parseBackupDate(it) } ?: Instant.fromEpochMilliseconds(0)
	}

	private fun parseBackupDate(dateTimeStr: String): Instant? =
		try {
			localDateTime(dateTimeStr).toInstant(TimeZone.UTC)
		} catch (_: IllegalArgumentException) {
			null
		}

	open fun supportsBackup(): Boolean = true

	open suspend fun createBackup(projectDef: ProjectDef): ProjectBackupDef? {
		val projectDir = projectsRepository.getProjectDirectory(projectDef.name).toOkioPath()
		val newBackupDef = createNewProjectBackupDef(projectDef)

		return try {
			zipDirectory(
				fileSystem = fileSystem,
				sourceDirectory = projectDir,
				destinationZip = newBackupDef.path.toOkioPath(),
			)

			cullBackups(projectDef)

			newBackupDef
		} catch (e: Exception) {
			Napier.e("Failed to make backup for project: ${projectDef.name}", e)
			null
		}
	}

	/** Restores [backupDef] over its project, or recreates the project if it no longer exists. */
	suspend fun restoreBackup(backupDef: ProjectBackupDef): BackupRestoreResult {
		val targetDir = projectsRepository.findProject(backupDef.projectName)?.path
			?: projectsRepository.getProjectDirectory(backupDef.projectName)
		return restoreBackup(backupDef, targetDir)
	}

	/**
	 * Replaces the project at [targetDir] with the backup's content, migrated to the current data
	 * version. The restored project keeps the identity of the project it replaces, or gets a fresh
	 * one when there is none: the backup's own id may since have been deleted on the server, and a
	 * deleted id can never be created again.
	 *
	 * A project in use is refused before anything is touched: its open scope holds the content and
	 * id this would replace. One opened mid-restore is not caught; setting its id then fails and is
	 * logged.
	 */
	open suspend fun restoreBackup(backupDef: ProjectBackupDef, targetDir: HPath): BackupRestoreResult {
		val targetOkioPath = targetDir.toOkioPath()
		val targetDef = ProjectDef(backupDef.projectName, targetDir)
		val staging = getBackupsDirectory().toOkioPath() / RESTORE_STAGING_DIRECTORY
		val extracted = staging / "new"
		val displaced = staging / "old"

		return restoreLock.withLock {
			if (projectsRepository.isProjectInUse(targetDef)) {
				Napier.w("Did not restore '${backupDef.path.name}': '${backupDef.projectName}' is in use")
				return@withLock BackupRestoreResult.ProjectInUse
			}

			try {
				fileSystem.deleteRecursively(staging)
				fileSystem.createDirectories(extracted)

				val liveId = if (fileSystem.exists(targetOkioPath / ProjectMetadata.FILENAME)) {
					projectMetadataDatasource.loadProjectId(targetDir)
				} else {
					null
				}
				val liveSynced = liveId != null &&
					fileSystem.exists(targetOkioPath / SyncJournalDatasource.SYNC_FILE_NAME)

				unzipToDirectory(
					fileSystem = fileSystem,
					zipPath = backupDef.path.toOkioPath(),
					destinationDirectory = extracted
				)

				swapIntoPlace(archiveRoot(extracted), targetOkioPath, displaced)
				adoptIdentity(targetDef, liveId, liveSynced)
				BackupRestoreResult.Restored
			} catch (e: Exception) {
				Napier.e("Failed to restore backup: ${backupDef.path.name}", e)
				BackupRestoreResult.Failed
			} finally {
				try {
					fileSystem.deleteRecursively(staging)
				} catch (e: IOException) {
					Napier.w("Failed to clean up restore staging directory", e)
				}
			}
		}
	}

	/**
	 * Settles a restored project's identity and sync state. A restored project is never treated as
	 * in sync with the server: its sync state is the backup's, describing the server at backup time,
	 * so the next sync conflict-checks every entity and the project data against the server instead
	 * of letting either side silently overwrite the other. This holds whether or not the id changed,
	 * and when the backup predates the first sync of a live project it replaces ([liveSynced]).
	 *
	 * The content is already in place, so a failure here is logged rather than reported as a failed
	 * restore. The id is set even when the migration fails: startup retries the migration, which
	 * keeps an id already present.
	 */
	@Suppress("TooGenericExceptionCaught")
	private suspend fun adoptIdentity(targetDef: ProjectDef, liveId: ProjectId?, liveSynced: Boolean) {
		try {
			dataMigrator.migrateProject(targetDef)
		} catch (e: Exception) {
			Napier.e("Restored '${targetDef.name}' but failed to migrate it", e)
		}
		// Flagged before the id is set, so an id change keeps the backup's conflict baselines.
		try {
			projectsRepository.resetSyncStateAfterRestore(targetDef, serverKnowsProject = liveSynced)
		} catch (e: Exception) {
			Napier.e("Restored '${targetDef.name}' but failed to reset its sync state", e)
		}
		try {
			projectsRepository.setProjectId(targetDef, liveId ?: ProjectId.randomUUID())
		} catch (e: Exception) {
			Napier.e("Restored '${targetDef.name}' but failed to set its project id", e)
		}
	}

	// The current project is moved aside, not deleted, so it can be put back if the swap fails.
	private fun swapIntoPlace(restored: Path, target: Path, displaced: Path) {
		val hadExisting = fileSystem.exists(target)
		if (hadExisting) {
			fileSystem.atomicMove(target, displaced)
		} else {
			target.parent?.let { fileSystem.createDirectories(it) }
		}

		try {
			fileSystem.atomicMove(restored, target)
		} catch (e: IOException) {
			if (hadExisting) fileSystem.atomicMove(displaced, target)
			throw e
		}
	}

	// Backup archives hold the project inside a single directory named after the project.
	private fun archiveRoot(extracted: Path): Path {
		val onlyChild = fileSystem.list(extracted).singleOrNull() ?: return extracted
		return if (fileSystem.metadata(onlyChild).isDirectory) onlyChild else extracted
	}

	/**
	 * The name of every project that has at least one backup, including projects that no longer
	 * exist on disk, so a deleted project can still be restored from its backups.
	 */
	fun getBackedUpProjects(): List<String> {
		val backupKeys = fileSystem.list(getBackupsDirectory().toOkioPath())
			.mapNotNull { FILE_NAME_PATTERN.matchEntire(it.name)?.groups?.get(1)?.value }
			.toSet()

		val projectNames = projectsRepository.getProjects().map { it.name }
		val existing = projectNames.filter { name ->
			backupKeysForProject(name).any { it in backupKeys }
		}

		val claimedKeys = projectNames.flatMap { backupKeysForProject(it) }.toSet()
		val missingNames = (backupKeys - claimedKeys)
			.map { ProjectsRepository.decodeFromFilename(it) }
			.filter { validateProjectName(it) }
			.distinct()

		// A legacy-named backup belongs to the project whose current name also has backups.
		val legacyAliases = missingNames
			.filter { legacyBackupName(it) != it }
			.map { legacyBackupName(it) }
			.toSet()
		val missing = missingNames.filterNot { it in legacyAliases }

		return existing + missing
	}

	private fun localDateTime(dateTimeStr: String): LocalDateTime {
		val match = DATE_PATTERN.matchEntire(dateTimeStr) ?: throw IllegalArgumentException("Failed to parse date time")
		val year = match.groupValues[1].toInt()
		val month = match.groupValues[2].toInt()
		val day = match.groupValues[3].toInt()
		val hour = match.groupValues[4].toInt()
		val minute = match.groupValues[5].toInt()
		val second = match.groupValues[6].toInt()

		return LocalDateTime(
			date = LocalDate(
				year = year,
				monthNumber = month,
				dayOfMonth = day
			),
			time = LocalTime(hour, minute, second)
		)
	}

	companion object {
		const val BACKUP_DIRECTORY = ".backups"
		private const val RESTORE_STAGING_DIRECTORY = ".restore"
		val FILE_NAME_PATTERN = Regex("^(.+)-(\\d{4}-\\d{2}-\\d{2}T\\d+Z)\\.zip$")
		val DATE_PATTERN = Regex("^(\\d{4})-(\\d{2})-(\\d{2})T(\\d{2})(\\d{2})(\\d{2})Z$")
	}
}

private fun Instant.toBackupDate(): String {
	val dateTime = toLocalDateTime(TimeZone.UTC)
	val dateStr = dateTime.format("yyyy-MM-dd'T'HHmmss'Z'")

	return dateStr
}