package com.darkrockstudios.apps.hammer.common.components.timeline

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.getAndUpdate
import com.arkivanov.decompose.value.subscribe
import com.arkivanov.decompose.value.update
import com.arkivanov.essenty.backhandler.BackCallback
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.base.timeline.StoryDate
import com.darkrockstudios.apps.hammer.base.timeline.TimelineKind
import com.darkrockstudios.apps.hammer.common.components.ProjectComponentBase
import com.darkrockstudios.apps.hammer.common.components.images.ImagePickRequests
import com.darkrockstudios.apps.hammer.common.components.images.ImagePickTarget
import com.darkrockstudios.apps.hammer.common.components.images.pickImage
import com.darkrockstudios.apps.hammer.common.data.MenuDescriptor
import com.darkrockstudios.apps.hammer.common.data.MenuItemDescriptor
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EncyclopediaRepository
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryDef
import com.darkrockstudios.apps.hammer.common.data.entitycatalog.CatalogItem
import com.darkrockstudios.apps.hammer.common.data.entitycatalog.CatalogKind
import com.darkrockstudios.apps.hammer.common.data.entitycatalog.EntityCatalog
import com.darkrockstudios.apps.hammer.common.data.imagesrepository.ImagesService
import com.darkrockstudios.apps.hammer.common.data.imagesrepository.image.ImageDef
import com.darkrockstudios.apps.hammer.common.data.projectInject
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneRepository
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.SubjectSummary
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineEvent
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineEventError
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimelineResult
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimelineService
import com.darkrockstudios.apps.hammer.timeline_view_menu_delete
import com.darkrockstudios.apps.hammer.timeline_view_menu_group
import io.github.aakira.napier.Napier
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

class ViewTimeLineEventComponent(
	componentContext: ComponentContext,
	projectDef: ProjectDef,
	override val eventId: Int,
	private val onCloseEvent: () -> Unit,
	private val addMenu: (menu: MenuDescriptor) -> Unit,
	private val removeMenu: (id: String) -> Unit,
	private val updateShouldClose: () -> Unit,
	private val onShowGlobalSearchForTag: (String) -> Unit,
	private val onShowScene: (SceneItem) -> Unit,
	private val onShowEntry: (EntryDef) -> Unit,
) : ProjectComponentBase(projectDef, componentContext), ViewTimeLineEvent {

	private val timelineService: TimelineService by projectInject()
	private val entityCatalog: EntityCatalog by projectInject()
	private val sceneRepository: SceneRepository by projectInject()
	private val encyclopediaRepository: EncyclopediaRepository by projectInject()
	private val imagePickRequests: ImagePickRequests by projectInject()
	private val imagesService: ImagesService by projectInject()

	// A restored in-progress edit, only present after process death while editing.
	private val restoredDraft: SavedDraft? =
		stateKeeper.consume(DRAFT_KEY, SavedDraft.serializer())?.takeIf { it.isEditing }

	private val _state = MutableValue(
		ViewTimeLineEvent.State(
			tags = restoredDraft?.tags ?: emptySet(),
			isEditing = restoredDraft != null,
			title = restoredDraft?.title.orEmpty(),
			lane = restoredDraft?.lane.orEmpty(),
			milestone = restoredDraft?.milestone ?: false,
			referenceIds = restoredDraft?.references?.toSet().orEmpty(),
			start = restoredDraft?.start,
			end = restoredDraft?.end,
		)
	)
	override val state: Value<ViewTimeLineEvent.State> = _state

	private val _dateText = MutableValue(restoredDraft?.dateText ?: "")
	override val dateText: Value<String> = _dateText

	private val _contentText = MutableValue(restoredDraft?.contentText ?: "")
	override val contentText: Value<String> = _contentText

	private var catalogRequested = false

	private val backButtonHandler = BackCallback(isEnabled = false) {
		if (isEditingAndDirty()) {
			confirmDiscard()
		} else if (state.value.isEditing) {
			discardEdit()
		}
	}

	init {
		stateKeeper.register(DRAFT_KEY, SavedDraft.serializer()) {
			val current = _state.value
			SavedDraft(
				contentText = _contentText.value,
				dateText = _dateText.value,
				tags = current.tags,
				isEditing = current.isEditing,
				title = current.title,
				lane = current.lane,
				milestone = current.milestone,
				references = current.referenceIds.toList(),
				start = current.start,
				end = current.end,
			)
		}
	}

	override fun onCreate() {
		super.onCreate()

		backHandler.register(backButtonHandler)
		state.subscribe(lifecycle) {
			backButtonHandler.isEnabled = it.isEditing
		}

		watchTimeLine()
		watchSubject()

		watchSpellCheckAllowed { allowed ->
			_state.getAndUpdate { it.copy(spellCheckAllowed = allowed) }
		}
	}

	private fun watchTimeLine() {
		scope.launch {
			timelineService.timelineFlow.collect { view ->
				val updatedEvent = view.events.find { it.id == eventId }
				val lanes = timelineService.lanes()
				val changed = updatedEvent != state.value.event
				// A placement's subject and title come from watchSubject.
				val shown = when {
					!changed || updatedEvent?.kind?.isPlacement == true -> null
					updatedEvent == null -> ShownSubject(null, null, null)
					else -> ShownSubject(null, null, timelineService.displayTitle(updatedEvent))
				}
				if (updatedEvent?.kind == TimelineKind.EVENT) loadCatalogOnce()

				withContext(dispatcherMain) {
					_state.getAndUpdate {
						it.copy(
							lanes = lanes,
							laneIndex = updatedEvent?.lane?.let { lane -> lanes.indexOf(lane).takeIf { i -> i >= 0 } },
							mode = view.def.mode,
							calendar = view.def.calendar,
						)
					}
					if (changed) applyEvent(updatedEvent, shown)
				}
			}
		}
	}

	/** Shows a placement's subject as it is now and again whenever it changes. */
	private fun watchSubject() {
		scope.launch {
			timelineService.subjectUpdates(eventId).collect { update ->
				val image = subjectImage(update.summary)
				withContext(dispatcherMain) {
					_state.getAndUpdate { it.withSubject(ShownSubject(update.summary, image, update.displayTitle)) }
					refreshReferences()
				}
			}
		}
	}

	/** A subject as the view shows it; a null [displayTitle] keeps the current one. */
	private class ShownSubject(
		val summary: SubjectSummary?,
		val image: ViewTimeLineEvent.SubjectImage?,
		val displayTitle: String?,
	)

	private fun ViewTimeLineEvent.State.withSubject(shown: ShownSubject) = copy(
		subject = shown.summary,
		subjectImage = shown.image,
		displayTitle = shown.displayTitle ?: displayTitle,
	)

	/**
	 * Takes [event] as stored, with [shown] as its subject or, when null, the subject already shown.
	 * A dirty edit keeps its draft, otherwise the draft follows the event.
	 */
	private fun applyEvent(event: TimeLineEvent?, shown: ShownSubject?) {
		val keepDraft = isEditingAndDirty()
		_state.getAndUpdate {
			val placed = it.copy(event = event, kind = event?.kind ?: it.kind)
			val next = if (shown == null) placed else placed.withSubject(shown)
			if (keepDraft || event == null) next else next.withDraftFrom(event)
		}
		if (!keepDraft && event != null) {
			_contentText.update { event.content }
			_dateText.update { event.label ?: "" }
		}
		refreshReferences()
	}

	private fun ViewTimeLineEvent.State.withDraftFrom(event: TimeLineEvent) = copy(
		tags = event.tags,
		title = event.title.orEmpty(),
		lane = event.lane.orEmpty(),
		milestone = event.milestone,
		referenceIds = event.references,
		start = event.start,
		end = event.end,
	)

	private fun refreshReferences() {
		_state.getAndUpdate { it.copy(references = referencesOf(it)) }
	}

	private fun referencesOf(state: ViewTimeLineEvent.State): List<CatalogItem> =
		timelineReferenceItems(state.kind, state.subject, state.referenceIds, state.catalog)

	private fun loadCatalogOnce() {
		if (catalogRequested) return
		catalogRequested = true
		scope.launch {
			val catalog = entityCatalog.items(setOf(CatalogKind.Scene, CatalogKind.Entry))
			withContext(dispatcherMain) {
				_state.getAndUpdate { it.copy(catalog = catalog) }
				refreshReferences()
			}
		}
	}

	private suspend fun subjectImage(subject: SubjectSummary?): ViewTimeLineEvent.SubjectImage? {
		val imageId = (subject as? SubjectSummary.Entry)?.primaryImageId ?: return null
		return withContext(dispatcherIo) {
			val def = imagesService.findImage(imageId) ?: return@withContext null
			ViewTimeLineEvent.SubjectImage(
				name = def.name,
				path = imagesService.imagePath(def).path,
				version = imagesService.cacheVersion(def),
			)
		}
	}

	private fun getMenuId(): String {
		return "view-timeline-event"
	}

	override fun onEventTextChanged(text: String) {
		_contentText.update { text }
		updateShouldClose()
	}

	override fun onDateTextChanged(text: String) {
		_dateText.update { text }
		updateShouldClose()
	}

	override fun onTagsChanged(newTags: Set<String>) {
		_state.getAndUpdate { it.copy(tags = newTags) }
		updateShouldClose()
	}

	override fun onTitleChanged(text: String) {
		_state.getAndUpdate { it.copy(title = text) }
		updateShouldClose()
	}

	override fun onLaneChanged(text: String) {
		_state.getAndUpdate { it.copy(lane = text) }
		updateShouldClose()
	}

	override fun onMilestoneChanged(milestone: Boolean) {
		_state.getAndUpdate { it.copy(milestone = milestone) }
		updateShouldClose()
	}

	override fun onStartChanged(start: StoryDate?) {
		_state.getAndUpdate { it.copy(start = start) }
		updateShouldClose()
	}

	override fun onEndChanged(end: StoryDate?) {
		_state.getAndUpdate { it.copy(end = end) }
		updateShouldClose()
	}

	override fun addReference(item: CatalogItem) {
		if (item.id == eventId) return
		_state.getAndUpdate { it.copy(referenceIds = it.referenceIds + item.id) }
		refreshReferences()
		updateShouldClose()
	}

	override fun removeReference(id: Int) {
		_state.getAndUpdate { it.copy(referenceIds = it.referenceIds - id) }
		refreshReferences()
		updateShouldClose()
	}

	override fun suggestLanes(prefix: String): List<String> = suggestLanes(_state.value.lanes, prefix)

	override suspend fun removeTag(tag: String) {
		if (state.value.isEditing) return
		val event = state.value.event ?: return
		if (tag !in event.tags) return

		val result = timelineService.updateEvent(event.copy(tags = event.tags - tag))
		val stored = (result as? TimelineResult.Ok)?.event ?: return
		withContext(dispatcherMain) {
			_state.getAndUpdate { it.copy(event = stored, tags = stored.tags) }
		}
	}

	override fun showGlobalSearchForTag(tag: String) {
		onShowGlobalSearchForTag(tag)
	}

	override suspend fun saveEdit(): TimeLineEventError {
		val current = state.value
		val event = current.event ?: return TimeLineEventError.EVENT_NOT_FOUND
		val label = _dateText.value.trim().ifEmpty { null }
		val start = if (current.isDated) current.start else event.start
		val end = if (current.isDated) current.end else event.end

		val result = if (event.kind == TimelineKind.EVENT) {
			timelineService.updateEvent(
				event.copy(
					title = current.title.trim().ifEmpty { null },
					label = label,
					content = _contentText.value,
					tags = current.tags,
					references = current.referenceIds,
					lane = current.lane,
					milestone = current.milestone,
					start = start,
					end = end,
				)
			)
		} else {
			timelineService.updatePlacement(event.id, start, end, current.lane, current.milestone, label)
		}
		val stored = when (result) {
			is TimelineResult.Invalid -> return result.error
			is TimelineResult.Ok -> result.event
		}
		val subject = stored?.let { timelineService.resolveSubject(it) }
		val shown = ShownSubject(subject, subjectImage(subject), stored?.let { timelineService.displayTitle(it) })
		withContext(dispatcherMain) {
			_state.getAndUpdate { it.copy(isEditing = false) }
			applyEvent(stored, shown)
			updateShouldClose()
		}
		return TimeLineEventError.NONE
	}

	override fun openSubject() {
		when (val subject = state.value.subject) {
			is SubjectSummary.Scene -> onShowScene(subject.scene)
			is SubjectSummary.Entry -> onShowEntry(subject.entry)
			is SubjectSummary.Archived, SubjectSummary.Missing, null -> Unit
		}
	}

	override fun openReference(item: CatalogItem) {
		scope.launch {
			when (item.kind) {
				CatalogKind.Scene -> {
					// Only a live scene opens: archived scenes are not in the tree, and groups are skipped.
					val scene = sceneRepository.getSceneItemFromId(item.id)
						?.takeIf { it.type == SceneItem.Type.Scene } ?: return@launch
					withContext(dispatcherMain) { onShowScene(scene) }
				}

				CatalogKind.Entry -> {
					val entry = withContext(dispatcherIo) { encyclopediaRepository.findEntryDef(item.id) } ?: return@launch
					withContext(dispatcherMain) { onShowEntry(entry) }
				}

				else -> Unit
			}
		}
	}

	override fun startDeleteEvent() {
		_state.getAndUpdate { it.copy(confirmDelete = true) }
	}

	override fun endDeleteEvent() {
		_state.getAndUpdate { it.copy(confirmDelete = false) }
	}

	/** Closes the view once the event is gone, including when something else removed it first. */
	override suspend fun deleteEvent() {
		val result = timelineService.deleteEvent(eventId)
		val error = (result as? TimelineResult.Invalid)?.error
		val gone = error == null || error == TimeLineEventError.EVENT_NOT_FOUND
		if (!gone) Napier.w("Failed to delete event $eventId: $error")
		withContext(dispatcherMain) {
			endDeleteEvent()
			if (gone) closeEvent()
		}
	}

	private fun addEntryMenu() {
		val deleteEntry = MenuItemDescriptor(
			"view-timeline-event-delete",
			Res.string.timeline_view_menu_delete,
			"",
		) {
			startDeleteEvent()
		}

		val menuItems = setOf(deleteEntry)
		val menu = MenuDescriptor(
			getMenuId(),
			Res.string.timeline_view_menu_group,
			menuItems.toList()
		)
		addMenu(menu)
		_state.getAndUpdate { it.copy(menuItems = menuItems) }
	}

	override fun isEditingAndDirty(): Boolean {
		val current = state.value
		if (!current.isEditing) return false
		// Baseline not loaded yet (e.g. restored mid-edit): assume dirty so we don't silently discard.
		val event = current.event ?: return true
		val placementDirty = (event.label ?: "") != dateText.value ||
			(event.lane ?: "") != current.lane ||
			event.milestone != current.milestone ||
			event.start != current.start ||
			event.end != current.end
		if (event.kind.isPlacement) return placementDirty
		return placementDirty ||
			event.content != contentText.value ||
			event.tags != current.tags ||
			(event.title ?: "") != current.title ||
			event.references != current.referenceIds
	}

	private fun removeEntryMenu() {
		removeMenu(getMenuId())
		_state.getAndUpdate { it.copy(menuItems = emptySet()) }
	}

	override fun confirmDiscard() {
		if (isEditingAndDirty()) {
			_state.getAndUpdate { it.copy(confirmDiscard = true) }
		} else {
			discardEdit()
		}
	}

	override fun beginEdit() {
		_state.getAndUpdate { it.copy(isEditing = true) }
	}

	override fun discardEdit() {
		val event = _state.value.event
		_state.getAndUpdate {
			val next = it.copy(isEditing = false, confirmDiscard = false)
			if (event == null) next.copy(tags = emptySet()) else next.withDraftFrom(event)
		}
		_contentText.update { event?.content ?: "" }
		_dateText.update { event?.label ?: "" }
		refreshReferences()
		updateShouldClose()
	}

	override fun cancelDiscard() {
		_state.getAndUpdate { it.copy(confirmDiscard = false) }
	}

	override fun confirmClose() {
		if (isEditingAndDirty()) {
			_state.getAndUpdate { it.copy(confirmClose = true) }
		} else {
			closeEvent()
		}
	}

	override fun cancelClose() {
		_state.getAndUpdate { it.copy(confirmClose = false) }
	}

	override suspend fun pickImageForText(): ImageDef? =
		imagePickRequests.pickImage(ImagePickTarget.EventImage(eventId), imagesService)

	override fun closeEvent() {
		if (state.value.isEditing) discardEdit()
		onCloseEvent()
	}

	override fun onStart() {
		addEntryMenu()
	}

	override fun onStop() {
		removeEntryMenu()
	}

	@Serializable
	private data class SavedDraft(
		val contentText: String,
		val dateText: String,
		val tags: Set<String>,
		val isEditing: Boolean,
		val title: String = "",
		val lane: String = "",
		val milestone: Boolean = false,
		val references: List<Int> = emptyList(),
		val start: StoryDate? = null,
		val end: StoryDate? = null,
	)

	private companion object {
		const val DRAFT_KEY = "view-timeline-event-draft"
	}
}
