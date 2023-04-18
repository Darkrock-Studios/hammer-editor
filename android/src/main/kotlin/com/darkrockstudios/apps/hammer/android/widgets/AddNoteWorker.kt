package com.darkrockstudios.apps.hammer.android.widgets

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.darkrockstudios.apps.hammer.android.isInternetConnected
import com.darkrockstudios.apps.hammer.common.data.notesrepository.NoteError
import com.darkrockstudios.apps.hammer.common.data.notesrepository.NotesRepository
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository
import com.darkrockstudios.apps.hammer.common.data.projectsync.ClientProjectSynchronizer
import com.darkrockstudios.apps.hammer.common.data.temporaryProjectTask
import io.github.aakira.napier.Napier
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.parameter.parametersOf

class AddNoteWorker(
	private val context: Context,
	private val workerParams: WorkerParameters
) :
	CoroutineWorker(context, workerParams), KoinComponent {

	private val projectsRepository: ProjectsRepository by inject()

	override suspend fun doWork(): Result {
		val projectName = workerParams.inputData.getString(DATA_PROJECT_NAME)
		val noteText = workerParams.inputData.getString(DATA_NOTE_TEXT)

		val projectDef = projectsRepository.getProjects().find { it.name == projectName }

		if (projectDef == null) {
			Napier.e("Project not found: $projectName")
			return Result.failure()
		} else if (noteText == null || noteText.isBlank()) {
			Napier.e("Note Text cannot be blank")
			return Result.failure()
		}

		mutex.withLock {
			temporaryProjectTask(projectDef) { projectScope ->

				val notesRepository: NotesRepository = projectScope.get { parametersOf(projectDef) }
				val result = notesRepository.createNote(noteText)

				Napier.d { "Note create in proj: ${projectDef.name} result: $result" }

				if (result == NoteError.NONE && isInternetConnected(context)) {
					val synchronizer: ClientProjectSynchronizer = projectScope.get()
					if (synchronizer.isServerSynchronized()) {
						val success = synchronizer.sync(
							onProgress = { _, message -> message?.let { Napier.i { message } } },
							onLog = { message ->
								message?.let { Napier.i { it } }
							},
							onConflict = { _ -> Napier.e { "Error: Conflict on new only sync" } },
							onComplete = { Napier.d { "Sync Complete for Project: ${projectDef.name}" } },
							onlyNew = true
						)
						Napier.i { "Sync Success: $success" }
					}
				} else {
					Napier.i { "No internet connection, won't sync right now." }
				}
			}
		}

		return Result.success()
	}

	companion object {
		private val mutex = Mutex()
		const val DATA_PROJECT_NAME = "project-name"
		const val DATA_NOTE_TEXT = "note-text"
	}
}