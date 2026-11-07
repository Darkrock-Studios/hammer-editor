package components.storyeditor.scenemetadata

import com.darkrockstudios.apps.hammer.base.timeline.CalendarDef
import com.darkrockstudios.apps.hammer.base.timeline.CalendarPreset
import com.darkrockstudios.apps.hammer.base.timeline.StoryCalendar
import com.darkrockstudios.apps.hammer.base.timeline.StoryDate
import com.darkrockstudios.apps.hammer.base.timeline.TimelineDef
import com.darkrockstudios.apps.hammer.base.timeline.TimelineMode
import com.darkrockstudios.apps.hammer.common.components.storyeditor.sceneeditor.scenemetadata.SceneMetadataPanelComponent
import com.darkrockstudios.apps.hammer.common.components.timeline.TimelinePlacementLink
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EncyclopediaService
import com.darkrockstudios.apps.hammer.common.data.references.ScrubInvalidReferencesUseCase
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneEditorService
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.scenemetadata.SceneMetadata
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.StoryDateWords
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineEvent
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimelineDefResult
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimelineResult
import components.timeline.TimelineComponentTestBase
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.core.context.loadKoinModules
import org.koin.dsl.module
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** The scene metadata panel's Timelines row, over a real [com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimelineService]. */
class SceneMetadataPanelTimelinesTest : TimelineComponentTestBase() {

	private val shownEvents = mutableListOf<Int>()

	@BeforeEach
	override fun setup() {
		super.setup()
		val sceneEditor = mockk<SceneEditorService>(relaxed = true)
		every { sceneEditor.metadataUpdateFlow } returns MutableSharedFlow()
		every { sceneEditor.getSceneBuffer(any<SceneItem>()) } returns null
		every { sceneEditor.getSceneFilePathOrNull(any()) } returns null
		coEvery { sceneEditor.loadSceneMetadata(any()) } returns SceneMetadata()

		val encyclopediaService = mockk<EncyclopediaService>(relaxed = true)
		every { encyclopediaService.entryListFlow } returns MutableSharedFlow()
		coEvery { encyclopediaService.ensureEntriesLoaded() } returns emptyList()

		loadKoinModules(module {
			single { sceneEditor }
			single { encyclopediaService }
			single { ScrubInvalidReferencesUseCase(mockk(relaxed = true)) }
		})
	}

	private fun TestScope.opened(sceneId: Int): SceneMetadataPanelComponent {
		val component = SceneMetadataPanelComponent(
			componentContext = context,
			originalSceneItem = SceneItem(projectDef, SceneItem.Type.Scene, id = sceneId, name = "Scene $sceneId", order = 0),
			showEntry = {},
			onShowGlobalSearchForTag = {},
			onShowTimelineEvent = { shownEvents += it },
		)
		context.resume()
		advanceUntilIdle()
		return component
	}

	private fun TimelineResult.event(): TimeLineEvent = assertIs<TimelineResult.Ok>(this).event!!

	private fun TimelineDefResult.def(): TimelineDef = assertIs<TimelineDefResult.Ok>(this).def

	@Test
	fun `a scene on no timeline lists no timelines`() = runTest(mainTestDispatcher) {
		givenProject()
		timelineService.placeScene(5, null, null, null, false, timeline = TimelineDef.MAIN_ID).event()

		val component = opened(4)

		assertEquals(emptyList(), component.state.value.timelines)
	}

	@Test
	fun `a scene on two timelines lists main first and the dated one with its date`() = runTest(mainTestDispatcher) {
		givenProject()
		val gregorian = CalendarDef(CalendarPreset.GREGORIAN)
		val before = timelineService.createTimeline("Before the evening", TimelineMode.DATED, gregorian).def()
		val year = StoryDate(mapOf(StoryCalendar.YEAR to 1907L))
		val onBefore = timelineService.placeScene(4, year, null, null, false, timeline = before.id).event()
		val onMain = timelineService.placeScene(4, null, null, null, false, label = "Night", timeline = TimelineDef.MAIN_ID).event()

		val component = opened(4)

		assertEquals(
			listOf(
				TimelinePlacementLink(eventId = onMain.id, timelineName = TimelineDef.DEFAULT.name, label = "Night"),
				TimelinePlacementLink(onBefore.id, "Before the evening", start = year, calendar = gregorian),
			),
			component.state.value.timelines,
		)
	}

	@Test
	fun `placing moving and deleting update the row`() = runTest(mainTestDispatcher) {
		givenProject()
		val backstory = timelineService.createTimeline("Backstory").def()
		val component = opened(4)

		val onMain = timelineService.placeScene(4, null, null, null, false, timeline = TimelineDef.MAIN_ID).event()
		advanceUntilIdle()
		assertEquals(listOf("Main"), component.state.value.timelines.map { it.timelineName })

		timelineService.moveToTimeline(onMain.id, backstory.id, words = StoryDateWords.English)
		advanceUntilIdle()
		assertEquals(listOf("Backstory"), component.state.value.timelines.map { it.timelineName })

		timelineService.renameTimeline(backstory.id, "Long ago")
		advanceUntilIdle()
		assertEquals(listOf("Long ago"), component.state.value.timelines.map { it.timelineName })

		timelineService.deleteEvent(onMain.id)
		advanceUntilIdle()
		assertEquals(emptyList(), component.state.value.timelines)
	}

	@Test
	fun `a placement chip opens the event through the timeline deep link`() = runTest(mainTestDispatcher) {
		givenProject()
		val placed = timelineService.placeScene(4, null, null, null, false, timeline = TimelineDef.MAIN_ID).event()
		val component = opened(4)

		component.showTimelinePlacement(component.state.value.timelines.single())

		assertEquals(listOf(placed.id), shownEvents)
	}
}
