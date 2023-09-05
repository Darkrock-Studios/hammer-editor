package com.darkrockstudios.apps.hammer.common.components.notes

import com.arkivanov.decompose.ComponentContext
import com.darkrockstudios.apps.hammer.common.components.ProjectComponentBase
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.notesrepository.NoteError
import com.darkrockstudios.apps.hammer.common.data.notesrepository.NotesRepository
import com.darkrockstudios.apps.hammer.common.data.projectInject

class CreateNoteComponent(
	componentContext: ComponentContext,
	projectDef: ProjectDef,
	private val dismissCreate: () -> Unit
) : ProjectComponentBase(projectDef, componentContext), CreateNote {

	private val notesRepository: NotesRepository by projectInject()

	override suspend fun createNote(noteText: String): NoteError {
		val result = notesRepository.createNote(noteText)
		if (result.isSuccess) {
			dismissCreate()
			notesRepository.loadNotes()
		}

		return result
	}

	override fun closeCreate() {
		dismissCreate()
	}
}