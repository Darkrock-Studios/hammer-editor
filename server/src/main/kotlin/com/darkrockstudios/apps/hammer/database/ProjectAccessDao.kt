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

	suspend fun deleteAccess(projectId: Long) {
		withContext(ioDispatcher) {
			queries.deleteAccess(projectId)
		}
	}

	suspend fun deleteAccessById(accessId: Long, projectId: Long): Boolean = withContext(ioDispatcher) {
		queries.deleteAccessById(accessId, projectId).executeAsOneOrNull() != null
	}

	suspend fun deletePublicAccessForProject(projectId: Long) {
		withContext(ioDispatcher) {
			queries.deletePublicAccessForProject(projectId)
		}
	}

	suspend fun deleteAllAccessForUser(userId: Long) {
		withContext(ioDispatcher) {
			queries.deleteAllAccessForUser(userId)
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
		password: String
	): PublicProjectInfo? = withContext(ioDispatcher) {
		queries.findProjectByPenNameProjectNameAndPassword(penName, projectName, password)
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
