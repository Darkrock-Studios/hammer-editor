package com.darkrockstudios.apps.hammer.database

import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.project.ProjectDefinition
import com.darkrockstudios.apps.hammer.projects.ProjectsSyncData
import com.darkrockstudios.apps.hammer.utilities.injectIoDispatcher
import com.darkrockstudios.apps.hammer.utilities.sqliteDateTimeStringToInstant
import com.darkrockstudios.apps.hammer.utilities.toSqliteDateTimeString
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import org.koin.core.component.KoinComponent

class ProjectsDao(
	database: Database,
) : KoinComponent {
	private val ioDispatcher by injectIoDispatcher()
	private val accountQueries = database.serverDatabase.accountQueries
	private val deletedProjectQueries = database.serverDatabase.deletedProjectQueries

	suspend fun getProjectSyncData(userId: Long): ProjectsSyncData = withContext(ioDispatcher) {
		val lastSync = accountQueries.getLastSync(userId).executeAsOne()

		val deletedProjects = deletedProjectQueries.getDeletedProjects(userId).executeAsList()
			.map { ProjectId(it.uuid) }
			.toSet()

		ProjectsSyncData(
			lastSync = sqliteDateTimeStringToInstant(lastSync),
			deletedProjects = deletedProjects
		)
	}

	suspend fun updateProjectSyncData(
		userId: Long,
		lastSync: Instant,
		newDeletedProjects: Set<ProjectId>
	) = withContext(ioDispatcher) {
		accountQueries.updateLastSync(
			newSyncTime = lastSync.toSqliteDateTimeString(),
			userId = userId,
		)

		// Un-delete a project name
		val currentDeletedProjects = deletedProjectQueries
			.getDeletedProjects(userId)
			.executeAsList()

		currentDeletedProjects.filterNot { currentDeletedProject: DeletedProject ->
			// TODO https://youtrack.jetbrains.com/issue/KT-55239
			newDeletedProjects.contains<Any?> { deletedProject: ProjectDefinition ->
				deletedProject.uuid == ProjectId(currentDeletedProject.uuid)
			}
		}.forEach {
			deletedProjectQueries.removeDeletedProject(userId = userId, uuid = it.uuid)
		}

		// Delete newly deleted project names
		newDeletedProjects.filterNot { newlyDeleted: ProjectId ->
			// TODO https://youtrack.jetbrains.com/issue/KT-55239
			currentDeletedProjects.contains<Any?> { oldlyDeleted: DeletedProject ->
				ProjectId(oldlyDeleted.uuid) == newlyDeleted
			}
		}.forEach { project ->
			if (deletedProjectQueries.hasDeletedProject(userId = userId, uuid = project.id)
					.executeAsOne()
					.not()
			) {
				deletedProjectQueries.addDeletedProject(userId = userId, uuid = project.id)
			}
		}
	}
}