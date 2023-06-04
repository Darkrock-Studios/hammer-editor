package com.darkrockstudios.apps.hammer.common.data.projectsync.synchronizers

import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.base.http.EntityHash
import com.darkrockstudios.apps.hammer.base.http.EntityType
import com.darkrockstudios.apps.hammer.base.http.synchronizer.EntityHasher
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.ProjectScoped
import com.darkrockstudios.apps.hammer.common.data.notesrepository.NotesRepository
import com.darkrockstudios.apps.hammer.common.data.notesrepository.note.NoteContent
import com.darkrockstudios.apps.hammer.common.data.projectInject
import com.darkrockstudios.apps.hammer.common.data.projectsync.EntitySynchronizer
import com.darkrockstudios.apps.hammer.common.data.projectsync.OnSyncLog
import com.darkrockstudios.apps.hammer.common.data.projectsync.syncLogI
import com.darkrockstudios.apps.hammer.common.dependencyinjection.ProjectDefScope
import com.darkrockstudios.apps.hammer.common.server.ServerProjectApi
import kotlinx.coroutines.flow.first

class ClientNoteSynchronizer(
	projectDef: ProjectDef,
	serverProjectApi: ServerProjectApi
) : EntitySynchronizer<ApiProjectEntity.NoteEntity>(projectDef, serverProjectApi), ProjectScoped {

	override val projectScope = ProjectDefScope(projectDef)
	private val notesRepository: NotesRepository by projectInject()

	override suspend fun prepareForSync() {
		notesRepository.loadNotes()
		notesRepository.notesListFlow.first()
	}

	override suspend fun ownsEntity(id: Int): Boolean {
		val notes = notesRepository.notesListFlow.first()
		return notes.any { it.note.id == id }
	}

	override suspend fun getEntityHash(id: Int): String {
		val notes = notesRepository.getNotes()
		val noteContainer = notes.firstOrNull { it.note.id == id } ?: throw IllegalStateException("Note $id not found")
		return EntityHasher.hashNote(
			id = noteContainer.note.id,
			created = noteContainer.note.created,
			content = noteContainer.note.content,
		)
	}

	override suspend fun createEntityForId(id: Int): ApiProjectEntity.NoteEntity {
		val note = notesRepository.getNoteFromId(id)?.note ?: throw IllegalStateException("Note $id not found")
		return ApiProjectEntity.NoteEntity(
			id = id,
			created = note.created,
			content = note.content,
		)
	}

	override suspend fun storeEntity(
		serverEntity: ApiProjectEntity.NoteEntity,
		syncId: String,
		onLog: OnSyncLog
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

	override fun getEntityType() = EntityType.Note

	override suspend fun deleteEntityLocal(id: Int, onLog: OnSyncLog) {
		notesRepository.deleteNote(id)
		onLog(syncLogI("Deleted note ID $id from client", projectDef))
	}

	override suspend fun hashEntities(newIds: List<Int>): Set<EntityHash> {
		return notesRepository.getNotes()
			.filter { newIds.contains(it.note.id).not() }
			.map { note ->
				val hash = getEntityHash(note.note.id)
				EntityHash(note.note.id, hash)
			}.toSet()
	}
}