package com.darkrockstudios.apps.hammer.common.data.id

import com.darkrockstudios.apps.hammer.base.http.EntityType
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.ProjectScoped
import com.darkrockstudios.apps.hammer.common.data.id.datasources.*
import com.darkrockstudios.apps.hammer.common.data.projectInject
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.SyncJournal
import com.darkrockstudios.apps.hammer.common.dependencyinjection.ProjectDefScope
import kotlinx.atomicfu.locks.reentrantLock
import kotlinx.atomicfu.locks.withLock
import org.koin.core.component.get
import kotlin.math.max

class IdAllocator(private val projectDef: ProjectDef) : ProjectScoped {
	override val projectScope = ProjectDefScope(projectDef)
	private val syncJournal: SyncJournal by projectInject()

	private val idDatasources: Set<IdDatasource> by lazy { getIdSources(projectScope) }

	private val mutex = reentrantLock()

	private var nextId: Int = -1

	fun peekNextId(): Int = nextId
	fun peekLastId(): Int = nextId - 1

	suspend fun findNextId() {
		mutex.withLock {
			var lastId = computeMaxId(projectDef, idDatasources)

			if (syncJournal.isServerSynchronized()) {
				syncJournal.deletedIds().maxOrNull()?.let { maxDeletedId ->
					lastId = max(lastId, maxDeletedId)
				}
			}

			nextId = if (lastId < 0) {
				1
			} else {
				lastId + 1
			}
		}
	}

	private suspend fun recordNewId(claimedId: Int) {
		if (syncJournal.isServerSynchronized()) {
			syncJournal.recordNewId(claimedId)
		}
	}

	suspend fun claimNextId(): Int {
		return mutex.withLock {
			val newSceneId = nextId
			recordNewId(newSceneId)
			nextId += 1
			newSceneId
		}
	}

	companion object {
		/**
		 * Every entity ID the project has on disk. Resolves the ID Data Sources from the
		 * project's Koin scope, so that scope must be open.
		 */
		fun findEntityIds(projectDef: ProjectDef): Set<Int> =
			getIdSources(projectDef).flatMapTo(mutableSetOf()) { it.findIds(projectDef) }

		/**
		 * The highest entity ID the project has on disk, or -1 if it has none. Stateless: it
		 * knows nothing of IDs that were claimed and then deleted, see [SyncJournal.deletedIds].
		 */
		private fun computeMaxId(projectDef: ProjectDef, idDatasources: Set<IdDatasource>): Int {
			return idDatasources.maxOfOrNull { it.findHighestId(projectDef) } ?: -1
		}

		private fun getIdSources(projectDef: ProjectDef): Set<IdDatasource> {
			val projectScope = ProjectDefScope(projectDef)
			return getIdSources(projectScope)
		}

		private fun getIdSources(projectScope: ProjectDefScope): Set<IdDatasource> {
			return EntityType.entries
				.map { entityType ->
					when (entityType) {
						EntityType.Scene -> projectScope.get<SceneIdDatasource>()
						EntityType.Note -> projectScope.get<NotesIdDatasource>()
						EntityType.TimelineEvent -> projectScope.get<TimeLineEventIdDatasource>()
						EntityType.EncyclopediaEntry -> projectScope.get<EncyclopediaIdDatasource>()
						EntityType.SceneDraft -> projectScope.get<SceneDraftIdDatasource>()
					}
				}.toSet()
		}
	}
}