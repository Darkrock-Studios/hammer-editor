package com.darkrockstudios.apps.hammer.database

import com.darkrockstudios.apps.hammer.Story_entity
import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.project.EntityDefinition
import com.darkrockstudios.apps.hammer.utilities.SResult
import com.darkrockstudios.apps.hammer.utilities.injectIoDispatcher
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent

class StoryEntityDao(
	database: Database,
) : KoinComponent {

	private val ioDispatcher by injectIoDispatcher()
	private val queries = database.serverDatabase.storyEntityQueries

	suspend fun findMaxId(userId: Long, projectId: Long): Long? = withContext(ioDispatcher) {
		return@withContext queries.findMaxEntityId(userId = userId, projectId = projectId)
			.executeAsOneOrNull()?.MAX
	}

	suspend fun selectAllForUser(userId: Long, projectId: Long): List<StoryEntity> =
		withContext(ioDispatcher) {
			val query = queries.getAllEntities(userId = userId, projectId = projectId)
			return@withContext query.executeAsList()
		}

	suspend fun getAllEntityDefs(userId: Long, projectId: Long): List<StoryEntity> =
		withContext(ioDispatcher) {
			val query = queries.getAllEntities(userId = userId, projectId = projectId)
			return@withContext query.executeAsList()
		}

	suspend fun checkExists(userId: Long, projectId: Long, id: Long): Boolean =
		withContext(ioDispatcher) {
			val query = queries.checkExists(userId = userId, projectId = projectId, id = id)
			return@withContext query.executeAsOne()
		}

	suspend fun getType(userId: Long, projectId: Long, id: Long): String? =
		withContext(ioDispatcher) {
			val query = queries.getType(userId = userId, projectId = projectId, id = id)
			return@withContext query.executeAsOneOrNull()
		}

	suspend fun getEntityDefs(userId: Long, projectId: Long): List<EntityDefinition> =
		withContext(ioDispatcher) {
			val defs = queries.getEntityDefs(userId, projectId).executeAsList().map {
				EntityDefinition(
					id = it.id.toInt(),
					type = ApiProjectEntity.Type.fromString(it.type)
						?: error("Invalid entity type. userId=$userId projectId=$projectId entityId=${it.id} entityType=${it.type}")
				)
			}
			return@withContext defs
		}

	suspend fun getEntityDefs(
		userId: Long,
		projectId: Long,
		type: ApiProjectEntity.Type
	): List<EntityDefinition> =
		withContext(ioDispatcher) {
			val defs =
				queries.getEntityDefsByType(userId, projectId, type.toStringId()).executeAsList()
					.map {
						EntityDefinition(
							id = it.id.toInt(),
							type = ApiProjectEntity.Type.fromString(it.type)
								?: error("Invalid entity type. userId=$userId projectId=$projectId entityId=${it.id} entityType=${it.type}")
						)
					}
			return@withContext defs
		}

	suspend fun insertNew(
		userId: Long,
		projectId: Long,
		id: Long,
		type: String,
		content: String,
		cipher: String,
		hash: String,
	) = withContext(ioDispatcher) {
		queries.insertNew(
			userId = userId,
			projectId = projectId,
			id = id,
			type = type,
			content = content,
			hash = hash,
			cipher = cipher
		)
	}

	suspend fun update(
		userId: Long,
		projectId: Long,
		id: Long,
		type: String,
		content: String,
		cipher: String,
		hash: String,
	): SResult<Unit> = withContext(ioDispatcher) {
		val curType =
			queries.getType(userId = userId, projectId = projectId, id = id).executeAsOne()
		if (type != curType) {
			SResult.failure<Unit>("Invalid entity type. userId=$userId projectId=$projectId entityId=${id} entityType=$type")
		} else {
			queries.update(
				userId = userId,
				projectId = projectId,
				id = id,
				content = content,
				hash = hash,
				cipher = cipher,
			)
			SResult.success(Unit)
		}
		SResult.success(Unit)
	}

	suspend fun upsert(
		userId: Long,
		projectId: Long,
		id: Long,
		type: String,
		content: String,
		cipher: String,
		hash: String,
	): SResult<Unit> = withContext(ioDispatcher) {
		return@withContext if (queries.checkExists(userId = userId, projectId, id = id)
				.executeAsOne()
		) {
			val curType =
				queries.getType(userId = userId, projectId = projectId, id = id).executeAsOne()
			if (type != curType) {
				SResult.failure<Unit>("Invalid entity type. userId=$userId projectId=$projectId entityId=$id entityType=$type")
			} else {
				queries.update(
					userId = userId,
					projectId = projectId,
					id = id,
					content = content,
					hash = hash,
					cipher = cipher,
				)
				SResult.success(Unit)
			}
		} else {
			queries.insertNew(
				userId = userId,
				projectId = projectId,
				id = id,
				type = type,
				content = content,
				hash = hash,
				cipher = cipher,
			)
			SResult.success(Unit)
		}
	}

	suspend fun deleteEntity(
		userId: Long,
		projectId: Long,
		id: Long,
	) = withContext(ioDispatcher) {
		queries.deleteEntity(userId = userId, projectId = projectId, id = id)
	}

	suspend fun getEntity(userId: Long, projectId: Long, id: Long): Story_entity? =
		withContext(ioDispatcher) {
			queries.getEntity(userId = userId, projectId = projectId, id = id).executeAsOneOrNull()
		}

	suspend fun getEntityHash(userId: Long, projectId: Long, id: Long): String? =
		withContext(ioDispatcher) {
			queries.getEntityHash(userId = userId, projectId = projectId, id = id)
				.executeAsOneOrNull()
		}
}