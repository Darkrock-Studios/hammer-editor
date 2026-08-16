package com.darkrockstudios.apps.hammer.database

import com.darkrockstudios.apps.hammer.GetPrivateAccessForProject
import com.darkrockstudios.apps.hammer.Project_access
import com.darkrockstudios.apps.hammer.utilities.injectIoDispatcher
import kotlinx.coroutines.withContext
import kotlin.time.Instant
import org.koin.core.component.KoinComponent

data class PublicProjectInfo(
	val projectUuid: String,
	val userId: Long,
	val projectName: String,
	val penName: String,
	val expiresAt: Instant?,
	val accessId: Long? = null,
)

data class PublishedStoryInfo(
	val projectUuid: String,
	val projectName: String,
	val publishedAt: Instant,
)

data class CommunityFeedStory(
	val projectUuid: String,
	val projectName: String,
	val penName: String,
	val publishedAt: Instant,
)

class ProjectAccessDao(
	database: Database,
) : KoinComponent {
	private val ioDispatcher by injectIoDispatcher()
	private val queries = database.serverDatabase.projectAccessQueries
	private val sceneQueries = database.serverDatabase.projectAccessSceneQueries

	suspend fun getAccessForProject(projectId: Long): Project_access? = withContext(ioDispatcher) {
		queries.getAccessForProject(projectId).executeAsOneOrNull()
	}

	suspend fun getAllAccessForProject(projectId: Long): List<Project_access> = withContext(ioDispatcher) {
		queries.getAllAccessForProject(projectId).executeAsList()
	}

	suspend fun getPublicAccessForProject(projectId: Long): Project_access? = withContext(ioDispatcher) {
		queries.getPublicAccessForProject(projectId).executeAsOneOrNull()
	}

	suspend fun getPrivateAccessForProject(projectId: Long): List<GetPrivateAccessForProject> = withContext(ioDispatcher) {
		queries.getPrivateAccessForProject(projectId).executeAsList()
	}

	suspend fun insertAccess(
		projectId: Long,
		password: String?,
		expiresAt: Instant?,
	) {
		withContext(ioDispatcher) {
			queries.insertAccess(projectId, password, expiresAt)
		}
	}

	suspend fun updateAccess(
		projectId: Long,
		password: String?,
		expiresAt: Instant?,
	) {
		withContext(ioDispatcher) {
			queries.updateAccess(projectId, password, expiresAt)
		}
	}

	/**
	 * Inserts a private share and its scene restriction atomically, or returns null when
	 * another live share of the project already uses [password]. The project row is
	 * locked for the transaction so two concurrent creates cannot both pass the check.
	 */
	suspend fun insertAccessWithScenes(
		projectId: Long,
		password: String?,
		expiresAt: Instant?,
		sceneIds: Collection<Int>,
		now: Instant,
	): Long? = withContext(ioDispatcher) {
		queries.transactionWithResult {
			queries.lockProjectRow(projectId).executeAsOneOrNull()
			val duplicate = password != null &&
				queries.findLiveAccessByPassword(projectId, password, now).executeAsOneOrNull() != null
			if (duplicate) {
				null
			} else {
				// RETURNING queries are lazy; executeAsOne() is what runs the INSERT.
				val accessId = queries.insertAccessReturningId(projectId, password, expiresAt).executeAsOne()
				sceneIds.forEach { sceneId ->
					sceneQueries.insertScene(accessId, sceneId)
				}
				accessId
			}
		}
	}

	suspend fun getSceneIdsForAccess(accessId: Long): List<Int> = withContext(ioDispatcher) {
		sceneQueries.getSceneIdsForAccess(accessId).executeAsList()
	}

	suspend fun getSceneIdsForAccessIds(accessIds: Collection<Long>): Map<Long, Set<Int>> =
		withContext(ioDispatcher) {
			if (accessIds.isEmpty()) return@withContext emptyMap()
			sceneQueries.getSceneIdsForAccessIds(accessIds).executeAsList()
				.groupBy({ it.access_id }, { it.scene_id })
				.mapValues { (_, ids) -> ids.toSet() }
		}

	suspend fun deleteAccess(projectId: Long) {
		withContext(ioDispatcher) {
			queries.transaction {
				// Children first, matching deleteAccessById: the explicit delete backs up
				// the cascade because tests disable FK enforcement.
				sceneQueries.deleteForProject(projectId)
				queries.deleteAccess(projectId)
			}
		}
	}

	suspend fun deleteAccessById(accessId: Long, projectId: Long): Boolean = withContext(ioDispatcher) {
		queries.transactionWithResult {
			// Parent first: the project_id scoping is the authorization check, and
			// scene rows must only go once it has passed. The explicit child delete
			// backs up the cascade because tests disable FK enforcement.
			val deleted = queries.deleteAccessById(accessId, projectId).executeAsOneOrNull() != null
			if (deleted) {
				sceneQueries.deleteForAccess(accessId)
			}
			deleted
		}
	}

	suspend fun deletePublicAccessForProject(projectId: Long) {
		withContext(ioDispatcher) {
			queries.deletePublicAccessForProject(projectId)
		}
	}

	suspend fun deleteAllAccessForUser(userId: Long) {
		withContext(ioDispatcher) {
			queries.transaction {
				sceneQueries.deleteAllForUser(userId)
				queries.deleteAllAccessForUser(userId)
			}
		}
	}

	suspend fun findPublicProjectByPenNameAndProjectName(
		penName: String,
		projectName: String
	): PublicProjectInfo? = withContext(ioDispatcher) {
		queries.findPublicProjectByPenNameAndProjectName(penName, projectName)
			.executeAsOneOrNull()
			?.let {
				PublicProjectInfo(
					projectUuid = it.project_uuid,
					userId = it.user_id,
					projectName = it.project_name,
					penName = it.pen_name ?: "",
					expiresAt = it.expires_at
				)
			}
	}

	suspend fun findPublicProjectByUuid(
		projectUuid: String
	): PublicProjectInfo? = withContext(ioDispatcher) {
		queries.findPublicProjectByUuid(projectUuid)
			.executeAsOneOrNull()
			?.let {
				PublicProjectInfo(
					projectUuid = it.project_uuid,
					userId = it.user_id,
					projectName = it.project_name,
					penName = it.pen_name ?: "",
					expiresAt = it.expires_at
				)
			}
	}

	suspend fun findProjectByPenNameProjectNameAndPassword(
		penName: String,
		projectName: String,
		password: String,
		now: Instant,
	): PublicProjectInfo? = withContext(ioDispatcher) {
		queries.findProjectByPenNameProjectNameAndPassword(penName, projectName, password, now)
			.executeAsOneOrNull()
			?.let {
				PublicProjectInfo(
					projectUuid = it.project_uuid,
					userId = it.user_id,
					projectName = it.project_name,
					penName = it.pen_name ?: "",
					expiresAt = it.expires_at,
					accessId = it.access_id,
				)
			}
	}

	suspend fun hasAnyAccessForProject(
		penName: String,
		projectName: String
	): Boolean = withContext(ioDispatcher) {
		queries.hasAnyAccessForProject(penName, projectName).executeAsOne()
	}

	suspend fun getPublishedStoriesByPenName(penName: String): List<PublishedStoryInfo> = withContext(ioDispatcher) {
		queries.getPublishedStoriesByPenName(penName).executeAsList().map {
			PublishedStoryInfo(
				projectUuid = it.project_uuid,
				projectName = it.project_name,
				publishedAt = it.published_at
			)
		}
	}

	suspend fun getCommunityFeedStories(page: Int, pageSize: Int): List<CommunityFeedStory> =
		withContext(ioDispatcher) {
			val offset = page * pageSize
			queries.getCommunityFeedStories(
				limit = pageSize.toLong(),
				offset = offset.toLong()
			).executeAsList().map {
				CommunityFeedStory(
					projectUuid = it.project_uuid,
					projectName = it.project_name,
					penName = it.pen_name ?: "",
					publishedAt = it.published_at
				)
			}
		}

	suspend fun countCommunityFeedStories(): Long = withContext(ioDispatcher) {
		queries.countCommunityFeedStories().executeAsOne()
	}
}
