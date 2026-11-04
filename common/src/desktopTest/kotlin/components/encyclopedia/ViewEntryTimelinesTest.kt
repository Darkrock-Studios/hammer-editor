package components.encyclopedia

import com.darkrockstudios.apps.hammer.base.timeline.TimelineDef
import com.darkrockstudios.apps.hammer.common.components.encyclopedia.ViewEntryComponent
import com.darkrockstudios.apps.hammer.common.components.timeline.TimelinePlacementLink
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EncyclopediaService
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryDef
import com.darkrockstudios.apps.hammer.common.data.references.BackfillEntryReferencesUseCase
import com.darkrockstudios.apps.hammer.common.data.references.CleanupReferencesOnEntryDeleteUseCase
import com.darkrockstudios.apps.hammer.common.data.references.ReferenceIndexService
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneEditorService
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineEvent
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimelineDefResult
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimelineResult
import components.timeline.TimelineComponentTestBase
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.core.context.loadKoinModules
import org.koin.dsl.module
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** The entry view's Timelines row, over a real [com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimelineService]. */
class ViewEntryTimelinesTest : TimelineComponentTestBase() {

	private val shownEvents = mutableListOf<Int>()

	@BeforeEach
	override fun setup() {
		super.setup()
		val encyclopediaService = mockk<EncyclopediaService>(relaxed = true)
		every { encyclopediaService.entryChangedFlow } returns MutableSharedFlow()
		every { encyclopediaService.imageChangedFlow } returns MutableSharedFlow()
		coEvery { encyclopediaService.entryImageFiles(any()) } returns emptyList()

		val referenceIndexService = mockk<ReferenceIndexService>(relaxed = true)
		every { referenceIndexService.flowForEntry(any()) } returns emptyFlow()

		loadKoinModules(module {
			single { encyclopediaService }
			single { referenceIndexService }
			single<SceneEditorService> { mockk(relaxed = true) }
			single<BackfillEntryReferencesUseCase> { mockk(relaxed = true) }
			single<CleanupReferencesOnEntryDeleteUseCase> { mockk(relaxed = true) }
		})
	}

	private suspend fun entry(id: Int): EntryDef = encyclopediaRepository.findEntryDef(id)!!

	private fun TestScope.opened(entryDef: EntryDef): ViewEntryComponent {
		val component = ViewEntryComponent(
			componentContext = context,
			entryDef = entryDef,
			addMenu = {},
			removeMenu = {},
			closeEntry = {},
			showScene = {},
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
	fun `an event entry on two timelines lists both and follows a deletion`() = runTest(mainTestDispatcher) {
		givenProject()
		val backstory = timelineService.createTimeline("Backstory").def()
		val onMain = timelineService.placeEntry(TEA_PARTY, null, null, null, false).event()
		val onBackstory = timelineService.placeEntry(TEA_PARTY, null, null, null, false, label = "Years before", timeline = backstory.id).event()

		val component = opened(entry(TEA_PARTY))

		assertEquals(
			listOf(
				TimelinePlacementLink(onMain.id, TimelineDef.DEFAULT.name),
				TimelinePlacementLink(onBackstory.id, "Backstory", label = "Years before"),
			),
			component.state.value.timelines,
		)

		timelineService.deleteTimeline(backstory.id)
		advanceUntilIdle()

		assertEquals(listOf(onMain.id), component.state.value.timelines.map { it.eventId })
	}

	@Test
	fun `an entry placed later shows up in the row`() = runTest(mainTestDispatcher) {
		givenProject()
		val component = opened(entry(TEA_PARTY))
		assertEquals(emptyList(), component.state.value.timelines)

		val placed = timelineService.placeEntry(TEA_PARTY, null, null, null, false).event()
		advanceUntilIdle()

		assertEquals(listOf(placed.id), component.state.value.timelines.map { it.eventId })
	}

	@Test
	fun `a placement chip opens the event through the timeline deep link`() = runTest(mainTestDispatcher) {
		givenProject()
		val placed = timelineService.placeEntry(TEA_PARTY, null, null, null, false).event()
		val component = opened(entry(TEA_PARTY))

		component.showTimelinePlacement(component.state.value.timelines.single())

		assertEquals(listOf(placed.id), shownEvents)
	}

	@Test
	fun `an entry that is not an event lists no timelines`() = runTest(mainTestDispatcher) {
		givenProject()

		val component = opened(entry(ALICE))

		assertEquals(emptyList(), component.state.value.timelines)
	}
}
