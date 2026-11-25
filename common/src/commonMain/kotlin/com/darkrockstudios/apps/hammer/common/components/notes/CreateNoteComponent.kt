package com.darkrockstudios.apps.hammer.common.components.notes

import com.darkrockstudios.apps.hammer.common.data.imagesrepository.image.ImageDef
import com.darkrockstudios.apps.hammer.common.data.imagesrepository.ImagesService
import com.darkrockstudios.apps.hammer.common.components.images.pickImage
import com.darkrockstudios.apps.hammer.common.components.images.ImagePickTarget
import com.darkrockstudios.apps.hammer.common.components.images.ImagePickRequests
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.getAndUpdate
import com.arkivanov.decompose.value.subscribe
import com.arkivanov.decompose.value.update
import com.arkivanov.essenty.backhandler.BackCallback
import com.darkrockstudios.apps.hammer.common.components.ProjectComponentBase
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.isSuccess
import com.darkrockstudios.apps.hammer.common.data.notesrepository.InvalidNote
import com.darkrockstudios.apps.hammer.common.data.notesrepository.NoteError
import com.darkrockstudios.apps.hammer.common.data.notesrepository.NotesRepository
import com.darkrockstudios.apps.hammer.common.data.projectInject
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

class CreateNoteComponent(
	componentContext: ComponentContext,
	projectDef: ProjectDef,
	private val dismissCreate: () -> Unit,
	private val updateShouldClose: () -> Unit
) : ProjectComponentBase(projectDef, componentContext), CreateNote {

	private val _state = MutableValue(CreateNote.State())
	override val state: Value<CreateNote.State> = _state

	private val _noteText = MutableValue(
		stateKeeper.consume(DRAFT_KEY, SavedDraft.serializer())?.noteText ?: ""
	)
	override val noteText: Value<String> = _noteText

	private val notesRepository: NotesRepository by projectInject()
	private val imagePickRequests: ImagePickRequests by projectInject()
	private val imagesService: ImagesService by projectInject()

	private val backButtonHandler = BackCallback(isEnabled = false) {
		confirmDiscard()
	}

	init {
		stateKeeper.register(DRAFT_KEY, SavedDraft.serializer()) {
			SavedDraft(noteText = _noteText.value)
		}
	}

	override fun onCreate() {
		super.onCreate()
		backHandler.register(backButtonHandler)

		// Enable back handler only when there's content that would be lost
		noteText.subscribe(lifecycle) {
			backButtonHandler.isEnabled = it.isNotBlank()
		}

		watchSpellCheckAllowed { allowed ->
			_state.getAndUpdate { it.copy(spellCheckAllowed = allowed) }
		}
	}

	// A new item has no id yet, so its pick has a target of its own.
	override suspend fun pickImageForText(): ImageDef? =
		imagePickRequests.pickImage(ImagePickTarget.NewNoteImage, imagesService)

	override fun onTextChanged(newText: String) {
		_noteText.update { newText }
		updateShouldClose()
	}

	override fun clearText() {
		_noteText.update { "" }
		updateShouldClose()
	}

	override suspend fun createNote(noteText: String, tags: Set<String>): NoteError =
		withContext(dispatcherDefault) {
			val result = notesRepository.createNote(noteText, tags)
			if (isSuccess(result)) {
				withContext(dispatcherMain) { dismissCreate() }
				notesRepository.loadNotes()
				NoteError.NONE
			} else {
				(result.exception as InvalidNote).error
			}
		}

	override fun confirmDiscard() {
		_state.getAndUpdate {
			it.copy(
				confirmDiscard = true
			)
		}
	}

	override fun cancelDiscard() {
		_state.getAndUpdate {
			it.copy(
				confirmDiscard = false
			)
		}
	}

	override fun closeCreate() {
		if (noteText.value.isNotBlank()) {
			confirmDiscard()
		} else {
			dismissCreate()
		}
	}

	@Serializable
	private data class SavedDraft(val noteText: String)

	private companion object {
		const val DRAFT_KEY = "create-note-draft"
	}
}