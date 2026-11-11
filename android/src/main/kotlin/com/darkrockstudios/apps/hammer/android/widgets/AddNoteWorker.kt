package com.darkrockstudios.apps.hammer.android.widgets

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import co.touchlab.kermit.Logger
import com.darkrockstudios.apps.hammer.android.isInternetConnected
import com.darkrockstudios.apps.hammer.common.data.isSuccess
import com.darkrockstudios.apps.hammer.common.data.notesrepository.NotesRepository
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.ClientProjectSynchronizer
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.SyncJournal
import com.darkrockstudios.apps.hammer.common.data.temporaryProjectTask
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
		val projectId = workerParams.inputData.getString(DATA_PROJECT_ID)
		val noteText = workerParams.inputData.getString(DATA_NOTE_TEXT)

		val projectDef = findWidgetProject(projectId)

		if (projectDef == null) {
			Logger.e("Project not found: $projectId")
			return Result.failure()
		} else if (noteText == null || noteText.isBlank()) {
			Logger.e("Note Text cannot be blank")
			return Result.failure()
		}

		mutex.withLock {
			temporaryProjectTask(projectDef) { projectScope ->

				val notesRepository: NotesRepository = projectScope.get { parametersOf(projectDef) }
				val result = notesRepository.createNote(noteText)

				Logger.d { "Note create in proj: ${projectDef.name} result: $result" }

				if (isSuccess(result) && isInternetConnected(context)) {
					val syncJournal: SyncJournal = projectScope.get()
					val synchronizer: ClientProjectSynchronizer = projectScope.get()
					if (syncJournal.isServerSynchronized() && projectsRepository.isSyncEnabled(projectDef)) {
						val success = synchronizer.sync(
							onProgress = { _, log ->
								log?.let {
									Logger.log(
										log.level.toSeverity(),
										"ProjectSync",
										null,
										"${log.projectName} - ${log.message}"
									)
								}
							},
							onLog = { log ->
								Logger.log(
									log.level.toSeverity(),
									"ProjectSync",
									null,
									"${log.projectName} - ${log.message}"
								)
							},
							onConflict = { _ -> Logger.e { "Error: Conflict on new only sync" } },
							onComplete = { Logger.d { "Sync Complete for Project: ${projectDef.name}" } },
							onUnauthorized = { Logger.e { "Error: Must reauthenticate with server." } },
							onlyNew = true
						)
						Logger.i { "Sync Success: $success" }
					}
				} else {
					Logger.i { "No internet connection, won't sync right now." }
				}
			}
		}

		return Result.success()
	}

	companion object {
		private val mutex = Mutex()
		const val DATA_PROJECT_ID = "project-id"
		const val DATA_NOTE_TEXT = "note-text"
	}
}