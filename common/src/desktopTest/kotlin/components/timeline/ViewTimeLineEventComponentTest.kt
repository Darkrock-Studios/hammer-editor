package components.timeline

import PROJECT_EMPTY_NAME
import com.darkrockstudios.apps.hammer.common.components.timeline.ViewTimeLineEventComponent
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineContainer
import getProjectDef
import io.mockk.coEvery
import io.mockk.coVerify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import repositories.timeline.TimeLineTestBase
import repositories.timeline.fakeEvents
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ViewTimeLineEventComponentTest : TimeLineTestBase() {

	@Test
	fun `Update event`() = runTest {
		coEvery { timelineRepo.updateEvent(any()) } returns true

		val eventId = 0

		val originalEvents = fakeEvents()
		val timeline = TimeLineContainer(originalEvents)

		val component = ViewTimeLineEventComponent(
			componentContext = context,
			projectDef = getProjectDef(PROJECT_EMPTY_NAME),
			eventId = eventId,
			onCloseEvent = {},
			addMenu = {},
			removeMenu = {},
			updateShouldClose = {},
			onShowGlobalSearchForTag = {},
		)
		lifecycleCallbacks[1].onCreate()
		advanceUntilIdle()

		timelineRepoCollectCallback.captured.emit(timeline)
		advanceUntilIdle()

		val event = originalEvents.first()
		assertEquals(event, component.state.value.event, "Initial event did not load from the timeline")

		component.beginEdit()
		assertTrue(component.state.value.isEditing)

		val date = "updated date"
		val content = "updated content"
		val updatedEvent = event.copy(
			date = date,
			content = content
		)
		coEvery { timelineRepo.getTimelineEvent(eventId) } returns updatedEvent
		val success = component.storeEvent(updatedEvent)
		assertTrue(success, "Update event failed")

		// After the update, it should save back to repository
		coVerify(exactly = 1) { timelineRepo.updateEvent(updatedEvent) }
		assertFalse(component.state.value.isEditing, "storeEvent should exit edit mode")
		assertEquals(updatedEvent, component.state.value.event, "State should refresh to the stored event")
	}

	private fun newComponent(eventId: Int) = ViewTimeLineEventComponent(
		componentContext = context,
		projectDef = getProjectDef(PROJECT_EMPTY_NAME),
		eventId = eventId,
		onCloseEvent = {},
		addMenu = {},
		removeMenu = {},
		updateShouldClose = {},
		onShowGlobalSearchForTag = {},
	)

	@Test
	fun `An event changed elsewhere refreshes the open view`() = runTest {
		val events = fakeEvents()
		val component = newComponent(eventId = 0)
		lifecycleCallbacks[1].onCreate()
		advanceUntilIdle()

		timelineRepoCollectCallback.captured.emit(TimeLineContainer(events))
		advanceUntilIdle()
		assertEquals("Event 0", component.contentText.value)

		val synced = events.first().copy(date = "server date", content = "server content")
		timelineRepoCollectCallback.captured.emit(TimeLineContainer(listOf(synced) + events.drop(1)))
		advanceUntilIdle()

		assertEquals(synced, component.state.value.event)
		assertEquals("server content", component.contentText.value)
		assertEquals("server date", component.dateText.value)
	}

	@Test
	fun `An event changed elsewhere replaces an untouched edit`() = runTest {
		val events = fakeEvents()
		// Event 1 has no date, which must not read as a dirty edit.
		val component = newComponent(eventId = 1)
		lifecycleCallbacks[1].onCreate()
		advanceUntilIdle()

		timelineRepoCollectCallback.captured.emit(TimeLineContainer(events))
		advanceUntilIdle()

		component.beginEdit()
		assertFalse(component.isEditingAndDirty())

		val synced = events[1].copy(content = "server content")
		timelineRepoCollectCallback.captured.emit(
			TimeLineContainer(events.map { if (it.id == 1) synced else it })
		)
		advanceUntilIdle()

		assertTrue(component.state.value.isEditing)
		assertEquals("server content", component.contentText.value)
		assertFalse(component.isEditingAndDirty())
	}

	@Test
	fun `An event changed elsewhere while editing keeps the draft`() = runTest {
		val events = fakeEvents()
		val component = newComponent(eventId = 0)
		lifecycleCallbacks[1].onCreate()
		advanceUntilIdle()

		timelineRepoCollectCallback.captured.emit(TimeLineContainer(events))
		advanceUntilIdle()

		component.beginEdit()
		component.onEventTextChanged("my draft")

		val synced = events.first().copy(content = "server content")
		timelineRepoCollectCallback.captured.emit(TimeLineContainer(listOf(synced) + events.drop(1)))
		advanceUntilIdle()

		assertEquals(synced, component.state.value.event)
		assertEquals("my draft", component.contentText.value)

		component.discardEdit()
		assertEquals("server content", component.contentText.value)
	}
}