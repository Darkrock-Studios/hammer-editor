package com.darkrockstudios.apps.hammer.common.components.timeline

import co.touchlab.kermit.Logger
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.getAndUpdate
import com.arkivanov.decompose.value.subscribe
import com.arkivanov.decompose.value.update
import com.arkivanov.essenty.backhandler.BackCallback
import com.darkrockstudios.apps.hammer.base.timeline.StoryDate
import com.darkrockstudios.apps.hammer.base.timeline.TimelineKind
import com.darkrockstudios.apps.hammer.base.timeline.TimelineMode
import com.darkrockstudios.apps.hammer.common.components.ProjectComponentBase
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.entitycatalog.CatalogItem
import com.darkrockstudios.apps.hammer.common.data.entitycatalog.CatalogKind
import com.darkrockstudios.apps.hammer.common.data.entitycatalog.CatalogRef
import com.darkrockstudios.apps.hammer.common.data.entitycatalog.EntityCatalog
import com.darkrockstudios.apps.hammer.common.data.entitycatalog.find
import com.darkrockstudios.apps.hammer.common.data.projectInject
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineEventError
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineRepository
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimelineEventDraft
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimelineResult
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimelineService
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

/** Creates on timeline [timelineId], with its mode and calendar. */
class CreateTimeLineEventComponent(
	componentContext: ComponentContext,
	projectDef: ProjectDef,
	private val timelineId: String,
	private val onClose: () -> Unit,
) : ProjectComponentBase(projectDef, componentContext), CreateTimeLineEvent {

	private val timelineService: TimelineService by projectInject()
	private val timeLineRepository: TimeLineRepository by projectInject()
	private val entityCatalog: EntityCatalog by projectInject()

	private val restored: PlacementDraft? = stateKeeper.consume(DRAFT_KEY, PlacementDraft.serializer())

	// Picks restored after process death, resolved once the catalog has loaded.
	private var pendingSubject: CatalogRef? = restored?.subject?.toRef()
	private var pendingReferences: List<CatalogRef> = restored?.references.orEmpty().mapNotNull { it.toRef() }

	private val _state = MutableValue(
		CreateTimeLineEvent.State(
			projectDef = projectDef,
			kind = restored?.kind ?: TimelineKind.EVENT,
			title = restored?.title.orEmpty(),
			lane = restored?.lane.orEmpty(),
			milestone = restored?.milestone ?: false,
			start = restored?.start,
			end = restored?.end,
		)
	)
	override val state: Value<CreateTimeLineEvent.State> = _state

	private val _contentText = MutableValue(restored?.contentText.orEmpty())
	override val contentText: Value<String> = _contentText

	private val backButtonHandler = BackCallback(isEnabled = false) {
		confirmDiscard()
	}

	init {
		stateKeeper.register(DRAFT_KEY, PlacementDraft.serializer()) {
			val current = _state.value
			PlacementDraft(
				kind = current.kind,
				subject = current.subject?.ref?.let(::SavedRef) ?: pendingSubject?.let(::SavedRef),
				title = current.title,
				lane = current.lane,
				milestone = current.milestone,
				references = (current.references.map { it.ref } + pendingReferences).map(::SavedRef),
				contentText = _contentText.value,
				start = current.start,
				end = current.end,
			)
		}
	}

	override fun onCreate() {
		super.onCreate()
		backHandler.register(backButtonHandler)

		contentText.subscribe(lifecycle) { updateBackHandler() }
		state.subscribe(lifecycle) { updateBackHandler() }

		watchSpellCheckAllowed { allowed ->
			_state.getAndUpdate { it.copy(spellCheckAllowed = allowed) }
		}

		loadChoices()
		watchDefinition()
	}

	private fun watchDefinition() {
		scope.launch {
			timelineService.timelineFlow(timelineId).collect { view ->
				val placed = view.events.mapNotNullTo(HashSet()) { event ->
					val subject = event.subject ?: return@mapNotNullTo null
					when (event.kind) {
						TimelineKind.SCENE -> CatalogRef(CatalogKind.Scene, subject)
						TimelineKind.ENTRY -> CatalogRef(CatalogKind.Entry, subject)
						TimelineKind.EVENT -> null
					}
				}
				withContext(dispatcherMain) {
					_state.getAndUpdate { it.copy(mode = view.def.mode, calendar = view.def.calendar, placedSubjects = placed) }
				}
			}
		}
	}

	private fun updateBackHandler() {
		backButtonHandler.isEnabled = isDirty()
	}

	private fun isDirty(): Boolean {
		val current = _state.value
		return _contentText.value.isNotBlank() ||
			current.subject != null ||
			current.title.isNotBlank() ||
			current.lane.isNotBlank() ||
			current.milestone ||
			current.references.isNotEmpty() ||
			current.start != null ||
			current.end != null
	}

	private fun loadChoices() {
		scope.launch {
			val lanes = timelineService.lanes(timelineId)
			val catalog = entityCatalog.items(setOf(CatalogKind.Scene, CatalogKind.Entry))
			withContext(dispatcherMain) {
				val subject = pendingSubject?.let { catalog.find(it) }
				val references = pendingReferences.mapNotNull { catalog.find(it) }
				pendingSubject = null
				pendingReferences = emptyList()
				_state.getAndUpdate {
					it.copy(
						catalog = catalog,
						lanes = lanes,
						subject = it.subject ?: subject,
						references = (it.references + references).distinctBy { item -> item.ref },
					)
				}
			}
		}
	}

	override fun onContentChanged(newText: String) {
		_contentText.update { newText }
	}

	override fun setKind(kind: TimelineKind) {
		_state.getAndUpdate {
			if (it.kind == kind) it else it.copy(kind = kind, subject = null)
		}
	}

	override fun setSubject(item: CatalogItem?) {
		_state.getAndUpdate { it.copy(subject = item) }
	}

	override fun setLane(lane: String) {
		_state.getAndUpdate { it.copy(lane = lane) }
	}

	override fun setMilestone(milestone: Boolean) {
		_state.getAndUpdate { it.copy(milestone = milestone) }
	}

	override fun setTitle(title: String) {
		_state.getAndUpdate { it.copy(title = title) }
	}

	override fun setStart(start: StoryDate?) {
		_state.getAndUpdate { it.copy(start = start) }
	}

	override fun setEnd(end: StoryDate?) {
		_state.getAndUpdate { it.copy(end = end) }
	}

	override fun addReference(item: CatalogItem) {
		_state.getAndUpdate {
			if (it.references.any { ref -> ref.ref == item.ref }) it else it.copy(references = it.references + item)
		}
	}

	override fun removeReference(id: Int) {
		_state.getAndUpdate { it.copy(references = it.references.filterNot { ref -> ref.id == id }) }
	}

	override fun suggestLanes(prefix: String): List<String> = suggestLanes(_state.value.lanes, prefix)

	override suspend fun create(
		label: String?,
		content: String,
		tags: Set<String>,
	): TimeLineEventError {
		val draft = _state.value
		val shownWhen = label?.trim()?.ifEmpty { null }
		val dated = draft.mode == TimelineMode.DATED
		if (dated && draft.start == null) return TimeLineEventError.DATE_REQUIRED
		val start = if (dated) draft.start else null
		val end = if (dated) draft.end else null

		val result = when (draft.kind) {
			TimelineKind.EVENT -> {
				val tagError = timeLineRepository.validateTags(tags)
				if (!tagError.isSuccess) return tagError
				timelineService.createEvent(
					TimelineEventDraft(
						kind = TimelineKind.EVENT,
						title = draft.title.trim().ifEmpty { null },
						label = shownWhen,
						content = content,
						tags = tags,
						references = draft.references.mapTo(LinkedHashSet()) { it.id },
						lane = draft.lane,
						milestone = draft.milestone,
						start = start,
						end = end,
						timeline = timelineId,
					)
				)
			}

			TimelineKind.SCENE -> {
				val subject = draft.subject ?: return TimeLineEventError.SUBJECT_REQUIRED
				timelineService.placeScene(subject.id, start, end, draft.lane, draft.milestone, timelineId, shownWhen)
			}

			TimelineKind.ENTRY -> {
				val subject = draft.subject ?: return TimeLineEventError.SUBJECT_REQUIRED
				timelineService.placeEntry(subject.id, start, end, draft.lane, draft.milestone, timelineId, shownWhen)
			}
		}

		val created = when (result) {
			is TimelineResult.Invalid -> return result.error
			is TimelineResult.Ok -> result.event
		}
		Logger.i { "Time Line event created! ${created?.id}" }

		withContext(dispatcherMain) {
			_contentText.update { "" }
			_state.getAndUpdate {
				it.cleared()
			}
		}
		return TimeLineEventError.NONE
	}

	private fun CreateTimeLineEvent.State.cleared() = copy(
		subject = null,
		title = "",
		lane = "",
		milestone = false,
		references = emptyList(),
		start = null,
		end = null,
	)

	override fun confirmDiscard() {
		_state.getAndUpdate { it.copy(confirmDiscard = true) }
	}

	override fun cancelDiscard() {
		_state.getAndUpdate { it.copy(confirmDiscard = false) }
	}

	override fun closeCreation() {
		if (isDirty()) {
			confirmDiscard()
		} else {
			onClose()
		}
	}

	override fun discardAndClose() {
		_contentText.update { "" }
		_state.getAndUpdate {
			it.cleared().copy(confirmDiscard = false)
		}
		onClose()
	}

	@Serializable
	private data class SavedRef(val kind: String, val id: Int) {
		constructor(ref: CatalogRef) : this(ref.kind.id, ref.id)

		fun toRef(): CatalogRef? = CatalogKind.of(kind)?.let { CatalogRef(it, id) }
	}

	@Serializable
	private data class PlacementDraft(
		val kind: TimelineKind,
		val subject: SavedRef?,
		val title: String,
		val lane: String,
		val milestone: Boolean,
		val references: List<SavedRef>,
		val contentText: String,
		val start: StoryDate? = null,
		val end: StoryDate? = null,
	)

	private companion object {
		const val DRAFT_KEY = "create-timeline-event-draft"
	}
}

private const val MAX_LANE_SUGGESTIONS = 8

/** [lanes] that start with [prefix], then those that contain it, ignoring case; never an exact match. */
internal fun suggestLanes(lanes: List<String>, prefix: String): List<String> {
	val needle = prefix.trim()
	val candidates = lanes.filterNot { it.equals(needle, ignoreCase = true) }
	if (needle.isEmpty()) return candidates.take(MAX_LANE_SUGGESTIONS)
	val (starts, rest) = candidates.partition { it.startsWith(needle, ignoreCase = true) }
	return (starts + rest.filter { it.contains(needle, ignoreCase = true) }).take(MAX_LANE_SUGGESTIONS)
}
