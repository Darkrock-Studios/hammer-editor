package com.darkrockstudios.apps.hammer.wear.data

import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.ideasrepository.IdeasRepository
import com.darkrockstudios.apps.hammer.common.data.isSuccess
import com.darkrockstudios.apps.hammer.common.data.notesrepository.NotesRepository
import com.darkrockstudios.apps.hammer.common.data.temporaryProjectTask
import org.koin.core.component.KoinComponent
import org.koin.core.parameter.parametersOf

class RepositoryCaptureWriter(
	private val ideasRepository: IdeasRepository,
) : CaptureWriter, KoinComponent {

	override suspend fun writeNote(projectDef: ProjectDef, text: String): Boolean {
		var saved = false
		temporaryProjectTask(projectDef) { projectScope ->
			val notesRepository: NotesRepository = projectScope.get { parametersOf(projectDef) }
			saved = isSuccess(notesRepository.createNote(text))
		}
		return saved
	}

	override suspend fun writeIdea(text: String): Boolean = isSuccess(ideasRepository.createIdea(text))
}
