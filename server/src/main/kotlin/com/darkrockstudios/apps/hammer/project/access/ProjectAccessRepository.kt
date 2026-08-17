package com.darkrockstudios.apps.hammer.project.access

import com.darkrockstudios.apps.hammer.Project_access
import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.database.CommunityFeedStory
import com.darkrockstudios.apps.hammer.database.ProjectAccessDao
import com.darkrockstudios.apps.hammer.database.ProjectDao
import com.darkrockstudios.apps.hammer.database.PublishedStoryInfo
import com.darkrockstudios.apps.hammer.project.ProjectEntityDatasource
import com.darkrockstudios.apps.hammer.project.SceneSetResult
import com.darkrockstudios.apps.hammer.project.loadSceneSet
import com.darkrockstudios.apps.hammer.utilities.Msg
import com.darkrockstudios.apps.hammer.utilities.SResult
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.time.toJavaInstant

sealed class PublicProjectResult {
	/**
	 * [isPublic] records which door the caller came through: true for access granted with no
	 * password at all, false for a private share unlocked by one. Callers must not re-derive this
	 * from the request — a public story fetched with a stray `?p=` is still public, and the
	 * distinction gates indexing and whether the render may be written to disk.
	 */
	data class Success(
		val userId: Long,
		val projectUuid: ProjectId,
		val projectName: String,
		val penName: String,
		val isPublic: Boolean,
		/** Scene ids this share is limited to; null exposes the entire story. */
		val sceneIds: Set<Int>? = null,
	) : PublicProjectResult()

	data object NotFound : PublicProjectResult()

	data object PasswordRequired : PublicProjectResult()
}

data class AccessEntryInfo(
	val id: Long,
	val password: String?,
	val expiresAt: Instant?,
	val expiresAtFormatted: String?,
	val isExpired: Boolean,
	/** Number of still-existing scenes this share is limited to; null exposes the entire story. */
	val sceneCount: Int? = null,
) {
	val isRestricted: Boolean
		get() = sceneCount != null

	/** Restricted, but every selected scene has since been deleted: the link is dead. */
	val isSceneless: Boolean
		get() = sceneCount == 0

	/** Pluralization hook for the Mustache access list. */
	val isSingleScene: Boolean
		get() = sceneCount == 1
}

class ProjectAccessRepository(
	private val projectAccessDao: ProjectAccessDao,
	private val projectDao: ProjectDao,
	private val projectEntityDatasource: ProjectEntityDatasource,
	private val clock: Clock,
) {
	suspend fun getAccessForProject(userId: Long, projectUuid: ProjectId): Project_access? {
		val projectId = projectDao.getProjectId(userId, projectUuid)
		return projectAccessDao.getAccessForProject(projectId)
	}

	suspend fun setAccess(
		userId: Long,
		projectUuid: ProjectId,
		password: String? = null,
		expiresAt: Instant? = null,
	) {
		val projectId = projectDao.getProjectId(userId, projectUuid)
		projectAccessDao.updateAccess(projectId, password, expiresAt)
	}

	suspend fun deleteAccess(userId: Long, projectUuid: ProjectId) {
		val projectId = projectDao.getProjectId(userId, projectUuid)
		projectAccessDao.deleteAccess(projectId)
	}

	suspend fun deleteAccessById(userId: Long, projectUuid: ProjectId, accessId: Long): Boolean {
		val projectId = projectDao.getProjectId(userId, projectUuid)
		return projectAccessDao.deleteAccessById(accessId, projectId)
	}

	suspend fun isPublished(userId: Long, projectUuid: ProjectId): Boolean {
		val projectId = projectDao.getProjectId(userId, projectUuid)
		val access = projectAccessDao.getPublicAccessForProject(projectId)
		// Published means: has a record with null password
		return access != null
	}

	suspend fun hasAnyAccess(userId: Long, projectUuid: ProjectId): Boolean {
		val projectId = projectDao.getProjectId(userId, projectUuid)
		val allAccess = projectAccessDao.getAllAccessForProject(projectId)
		return allAccess.isNotEmpty()
	}

	suspend fun publish(userId: Long, projectUuid: ProjectId) {
		val projectId = projectDao.getProjectId(userId, projectUuid)
		// Only create public access if not already published
		val existing = projectAccessDao.getPublicAccessForProject(projectId)
		if (existing == null) {
			projectAccessDao.insertAccess(projectId, null, null)
		}
	}

	suspend fun unpublish(userId: Long, projectUuid: ProjectId) {
		val projectId = projectDao.getProjectId(userId, projectUuid)
		projectAccessDao.deletePublicAccessForProject(projectId)
	}

	/**
	 * [sceneIds] limits the share to those scenes; null shares the entire story. A non-null
	 * empty set is rejected rather than widened, so a caller that filtered its selection down
	 * to nothing can never accidentally expose the whole manuscript.
	 */
	suspend fun createPrivateAccess(
		userId: Long,
		projectUuid: ProjectId,
		password: String,
		expiresAt: Instant?,
		sceneIds: Set<Int>? = null,
	): SResult<Unit> {
		if (sceneIds != null && sceneIds.isEmpty()) {
			return SResult.failure("No scenes selected", Msg.r("story_toast_access_no_scenes"))
		}

		val projectDef = projectEntityDatasource.getProject(userId, projectUuid)
			?: return SResult.failure("Project not found", Msg.r("api_access_create_error_project_not_found"))
		if (sceneIds != null) {
			when (val loaded = projectEntityDatasource.loadSceneSet(userId, projectDef, sceneIds)) {
				is SceneSetResult.InvalidId -> return SResult.failure(
					"Invalid scene ${loaded.id}",
					Msg.r("api_access_create_error_invalid_scene"),
					loaded.exception,
				)

				is SceneSetResult.NotAScene -> return SResult.failure(
					"Entity ${loaded.id} is not a scene",
					Msg.r("api_access_create_error_invalid_scene")
				)

				is SceneSetResult.Success -> Unit
			}
		}

		val projectId = projectDao.getProjectId(userId, projectUuid)
		val accessId = projectAccessDao.insertAccessWithScenes(
			projectId = projectId,
			password = password,
			expiresAt = expiresAt,
			sceneIds = sceneIds ?: emptySet(),
			now = clock.now(),
		)
		// Null means another live share of this project already uses the password; the reader
		// lookup resolves a single row per password, so allowing it would strand one share.
		if (accessId == null) {
			return SResult.failure(
				"Password already used by another share",
				Msg.r("api_access_create_error_duplicate_password")
			)
		}
		return SResult.success(Unit)
	}

	suspend fun getPrivateAccessEntries(userId: Long, projectUuid: ProjectId): List<AccessEntryInfo> {
		val projectId = projectDao.getProjectId(userId, projectUuid)
		val entries = projectAccessDao.getPrivateAccessForProject(projectId)
		val restrictions = projectAccessDao.getSceneIdsForAccessIds(entries.map { it.id })
		// Scene counts reflect scenes that still exist, so a share whose selection has been
		// deleted out from under it reads as dead instead of quietly advertising stale scenes.
		val liveSceneIds = if (restrictions.isEmpty()) {
			emptySet()
		} else {
			val projectDef = projectEntityDatasource.getProject(userId, projectUuid)
			projectDef?.let { def ->
				projectEntityDatasource.getEntityDefsByType(
					userId = userId,
					projectDef = def,
					type = ApiProjectEntity.Type.SCENE,
				).map { it.id }.toSet()
			} ?: emptySet()
		}
		val now = clock.now()
		val formatter = DateTimeFormatter.ofPattern("MMM dd, yyyy").withZone(ZoneId.systemDefault())

		return entries.map { entry ->
			val expiresAt = entry.expires_at
			val isExpired = expiresAt != null && now > expiresAt
			val formattedDate = expiresAt?.let { formatter.format(it.toJavaInstant()) }

			AccessEntryInfo(
				id = entry.id,
				password = entry.access_password,
				expiresAt = expiresAt,
				expiresAtFormatted = formattedDate,
				isExpired = isExpired,
				sceneCount = restrictions[entry.id]?.let { ids -> ids.intersect(liveSceneIds).size },
			)
		}
	}

	suspend fun deleteAllAccessForUser(userId: Long) {
		projectAccessDao.deleteAllAccessForUser(userId)
	}

	suspend fun findPublicProject(penName: String, projectName: String): PublicProjectResult {
		val info = projectAccessDao.findPublicProjectByPenNameAndProjectName(penName, projectName)
			?: return PublicProjectResult.NotFound

		// Check expiration if set
		if (info.expiresAt != null && clock.now() > info.expiresAt) {
			return PublicProjectResult.NotFound
		}

		return PublicProjectResult.Success(
			userId = info.userId,
			projectUuid = ProjectId(info.projectUuid),
			projectName = info.projectName,
			penName = info.penName,
			isPublic = true,
		)
	}

	suspend fun findPublicProjectByUuid(projectUuid: ProjectId): PublicProjectResult {
		val info = projectAccessDao.findPublicProjectByUuid(projectUuid.id)
			?: return PublicProjectResult.NotFound

		if (info.expiresAt != null && clock.now() > info.expiresAt) {
			return PublicProjectResult.NotFound
		}

		return PublicProjectResult.Success(
			userId = info.userId,
			projectUuid = ProjectId(info.projectUuid),
			projectName = info.projectName,
			penName = info.penName,
			isPublic = true,
		)
	}

	suspend fun findAccessibleProject(
		penName: String,
		projectName: String,
		password: String?
	): PublicProjectResult {
		// First check for public access (no password required)
		val publicInfo = projectAccessDao.findPublicProjectByPenNameAndProjectName(penName, projectName)
		if (publicInfo != null) {
			val expired = publicInfo.expiresAt != null && clock.now() > publicInfo.expiresAt
			if (!expired) {
				return PublicProjectResult.Success(
					userId = publicInfo.userId,
					projectUuid = ProjectId(publicInfo.projectUuid),
					projectName = publicInfo.projectName,
					penName = publicInfo.penName,
					isPublic = true,
				)
			}
			// Public access expired, continue to check password access
		}

		// Check if any access exists at all
		val hasAnyAccess = projectAccessDao.hasAnyAccessForProject(penName, projectName)
		if (!hasAnyAccess) {
			return PublicProjectResult.NotFound
		}

		// If no password provided but there's private access available, require password
		if (password.isNullOrBlank()) {
			return PublicProjectResult.PasswordRequired
		}

		// Check password-protected access
		val passwordInfo = projectAccessDao.findProjectByPenNameProjectNameAndPassword(
			penName = penName,
			projectName = projectName,
			password = password,
			now = clock.now(),
		) ?: return PublicProjectResult.PasswordRequired

		// Check expiration if set
		if (passwordInfo.expiresAt != null && clock.now() > passwordInfo.expiresAt) {
			return PublicProjectResult.PasswordRequired
		}

		val sceneIds = passwordInfo.accessId
			?.let { projectAccessDao.getSceneIdsForAccess(it) }
			?.toSet()
			?.ifEmpty { null }

		return PublicProjectResult.Success(
			userId = passwordInfo.userId,
			projectUuid = ProjectId(passwordInfo.projectUuid),
			projectName = passwordInfo.projectName,
			penName = passwordInfo.penName,
			isPublic = false,
			sceneIds = sceneIds,
		)
	}

	suspend fun getPublishedStoriesByPenName(penName: String): List<PublishedStoryInfo> {
		return projectAccessDao.getPublishedStoriesByPenName(penName)
	}

	suspend fun getCommunityFeedStories(page: Int, pageSize: Int): List<CommunityFeedStory> {
		return projectAccessDao.getCommunityFeedStories(page, pageSize)
	}

	suspend fun countCommunityFeedStories(): Long {
		return projectAccessDao.countCommunityFeedStories()
	}
}
