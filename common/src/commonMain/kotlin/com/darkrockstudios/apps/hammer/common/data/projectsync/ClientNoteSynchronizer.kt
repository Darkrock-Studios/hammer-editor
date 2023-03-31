package com.darkrockstudios.apps.hammer.common.data.projectsync

import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.base.http.synchronizer.EntityConflictException
import com.darkrockstudios.apps.hammer.base.http.synchronizer.EntityHash
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.ProjectScoped
import com.darkrockstudios.apps.hammer.common.data.notesrepository.NotesRepository
import com.darkrockstudios.apps.hammer.common.data.notesrepository.note.NoteContent
import com.darkrockstudios.apps.hammer.common.data.projectInject
import com.darkrockstudios.apps.hammer.common.dependencyinjection.ProjectDefScope
import com.darkrockstudios.apps.hammer.common.server.ServerProjectApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import org.koin.core.component.inject

class ClientNoteSynchronizer(
	private val projectDef: ProjectDef
) : EntitySynchronizer<ApiProjectEntity.NoteEntity>, ProjectScoped {

	override val projectScope = ProjectDefScope(projectDef)
	private val notesRepository: NotesRepository by projectInject()
	private val serverProjectApi: ServerProjectApi by inject()

	val conflictResolution = Channel<ApiProjectEntity.NoteEntity>()

	override suspend fun prepareForSync() {
		notesRepository.loadNotes()
		notesRepository.notesListFlow.first()
	}

	override suspend fun ownsEntity(id: Int): Boolean {
		val notes = notesRepository.notesListFlow.first()
		return notes.any { it.note.id == id }
	}

	override suspend fun getEntityHash(id: Int): String? {
		val notes = notesRepository.getNotes()
		val noteContainer = notes.firstOrNull { it.note.id == id } ?: throw IllegalStateException("Note $id not found")
		return EntityHash.hashNote(
			id = noteContainer.note.id,
			created = noteContainer.note.created,
			content = noteContainer.note.content,
		)
	}

	override suspend fun storeEntity(
		serverEntity: ApiProjectEntity.NoteEntity,
		syncId: String,
		onLog: suspend (String?) -> Unit
	) {
		val updatedNote = NoteContent(
			id = serverEntity.id,
			created = serverEntity.created,
			content = serverEntity.content,
		)

		notesRepository.updateNote(
			noteContent = updatedNote,
			markForSync = false
		)
	}

	override suspend fun reIdEntity(oldId: Int, newId: Int) {
		notesRepository.reIdNote(oldId, newId)
	}

	override suspend fun finalizeSync() {
		notesRepository.loadNotes()
	}

	override suspend fun uploadEntity(
		id: Int,
		syncId: String,
		originalHash: String?,
		onConflict: EntityConflictHandler<ApiProjectEntity.NoteEntity>,
		onLog: suspend (String?) -> Unit
	): Boolean {
		val note = notesRepository.getNotes().first { it.note.id == id }.note
		val entity = ApiProjectEntity.NoteEntity(
			id = id,
			created = note.created,
			content = note.content,
		)

		val result = serverProjectApi.uploadEntity(
			projectDef = projectDef,
			entity = entity,
			originalHash = originalHash,
			syncId = syncId,
		)

		return if (result.isSuccess) {
			onLog("Uploaded Note $id")
			true
		} else {
			val exception = result.exceptionOrNull()
			val conflictException = exception as? EntityConflictException.NoteConflictException
			if (conflictException != null) {
				onLog("Conflict for note $id detected")
				onConflict(conflictException.entity)

				val resolvedEntity = conflictResolution.receive()
				val resolveResult = serverProjectApi.uploadEntity(projectDef, resolvedEntity, null, syncId, true)

				if (resolveResult.isSuccess) {
					onLog("Resolved conflict for note $id")
					storeEntity(resolvedEntity, syncId, onLog)
					true
				} else {
					onLog("Note conflict resolution failed for $id")
					false
				}
			} else {
				onLog("Failed to upload note $id")
				false
			}
		}
	}
}