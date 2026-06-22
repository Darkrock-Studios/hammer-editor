package com.darkrockstudios.apps.hammer.common.data.projectbackup

import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsStore
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import com.darkrockstudios.apps.hammer.common.fileio.okio.toHPath
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import com.darkrockstudios.apps.hammer.common.util.format
import com.darkrockstudios.apps.hammer.common.util.zip.unzipToDirectory
import com.darkrockstudios.apps.hammer.common.util.zip.zipDirectory
import io.github.aakira.napier.Napier
import kotlinx.datetime.*
import okio.FileNotFoundException
import okio.FileSystem
import okio.IOException
import okio.Path
import org.koin.core.component.KoinComponent
import kotlin.time.Clock
import kotlin.time.Instant

open class ProjectBackupRepository(
	protected val fileSystem: FileSystem,
	protected val projectsRepository: ProjectsRepository,
	protected val globalSettingsStore: GlobalSettingsStore,
	protected val clock: Clock
) : KoinComponent {

	fun getBackupsDirectory(): HPath {
		val dir = (projectsRepository.getProjectsDirectory().toOkioPath() / BACKUP_DIRECTORY)

		if (fileSystem.exists(dir).not()) {
			fileSystem.createDirectories(dir)
		}

		return dir.toHPath()
	}

	fun getBackups(projectDef: ProjectDef): List<ProjectBackupDef> {
		val dir = getBackupsDirectory().toOkioPath()
		val keys = backupKeysForProject(projectDef.name)
		return fileSystem.list(dir)
			.filter {
				try {
					fileSystem.metadata(it).isRegularFile
				} catch (_: FileNotFoundException) {
					false
				}
			}
			.mapNotNull { path -> matchBackup(path, projectDef, keys) }
			.sortedBy { it.date }
	}

	fun getBackupsForProject(projectDef: ProjectDef): List<ProjectBackupDef> = getBackups(projectDef)

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
			projectDef = projectDef,
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

		val backups = getBackups(project).toMutableList()

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

	private fun matchBackup(path: Path, projectDef: ProjectDef, keys: Set<String>): ProjectBackupDef? {
		val match = FILE_NAME_PATTERN.matchEntire(path.name) ?: return null
		val fileKey = match.groups[1]?.value ?: return null
		if (fileKey !in keys) return null

		return ProjectBackupDef(
			path = path.toHPath(),
			projectDef = projectDef,
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

	open suspend fun restoreBackup(backupDef: ProjectBackupDef, targetDir: HPath): Boolean {
		return try {
			val targetOkioPath = targetDir.toOkioPath()
			fileSystem.deleteRecursively(targetOkioPath)
			fileSystem.createDirectories(targetOkioPath)

			unzipToDirectory(
				fileSystem = fileSystem,
				zipPath = backupDef.path.toOkioPath(),
				destinationDirectory = targetOkioPath
			)
			true
		} catch (e: Exception) {
			Napier.e("Failed to restore backup: ${backupDef.path.name}", e)
			false
		}
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
		val FILE_NAME_PATTERN = Regex("^(.+)-(\\d{4}-\\d{2}-\\d{2}T\\d+Z)\\.zip$")
		val DATE_PATTERN = Regex("^(\\d{4})-(\\d{2})-(\\d{2})T(\\d{2})(\\d{2})(\\d{2})Z$")
	}
}

private fun Instant.toBackupDate(): String {
	val dateTime = toLocalDateTime(TimeZone.UTC)
	val dateStr = dateTime.format("yyyy-MM-dd'T'HHmmss'Z'")

	return dateStr
}