package operations

import com.darkrockstudios.apps.hammer.common.data.ClientResult
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository
import com.darkrockstudios.apps.hammer.operations.OperationException
import com.darkrockstudios.apps.hammer.operations.core.CalendarDto
import com.darkrockstudios.apps.hammer.operations.core.CalendarPresetDto
import com.darkrockstudios.apps.hammer.operations.core.Entry
import com.darkrockstudios.apps.hammer.operations.core.EntryCreateInput
import com.darkrockstudios.apps.hammer.operations.core.EntryKind
import com.darkrockstudios.apps.hammer.operations.core.EvenSpacingDto
import com.darkrockstudios.apps.hammer.operations.core.ProjectInput
import com.darkrockstudios.apps.hammer.operations.core.ProjectItemInput
import com.darkrockstudios.apps.hammer.operations.core.SceneCreateInput
import com.darkrockstudios.apps.hammer.operations.core.SceneItemRef
import com.darkrockstudios.apps.hammer.operations.core.Timeline
import com.darkrockstudios.apps.hammer.operations.core.TimelineCreateInput
import com.darkrockstudios.apps.hammer.operations.core.TimelineDefDto
import com.darkrockstudios.apps.hammer.operations.core.TimelineEntry
import com.darkrockstudios.apps.hammer.operations.core.TimelineKindDto
import com.darkrockstudios.apps.hammer.operations.core.TimelineModeDto
import com.darkrockstudios.apps.hammer.operations.core.TimelineMoveInput
import com.darkrockstudios.apps.hammer.operations.core.TimelinePlaceInput
import com.darkrockstudios.apps.hammer.operations.core.TimelineSetInput
import com.darkrockstudios.apps.hammer.operations.core.TimelineSettings
import com.darkrockstudios.apps.hammer.operations.core.TimelineSettingsWriteInput
import com.darkrockstudios.apps.hammer.operations.core.TimelineUpdateInput
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.core.component.get
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TimelineOperationsTest : KoinOperationsTest() {

	@BeforeEach
	fun setUp() = onTestThread {
		val created = get<ProjectsRepository>().createProject(PROJECT, seedDefaultLanguage = false)
		check(created is ClientResult.Success<ProjectDef>)
	}

	private fun failsWith(kind: OperationException.Kind, block: suspend () -> Unit) = onTestThread {
		assertEquals(kind, assertFailsWith<OperationException> { block() }.kind)
	}

	private fun invalid(block: suspend () -> Unit) = failsWith(OperationException.Kind.InvalidInput, block)

	private suspend fun scene(name: String) =
		run<SceneCreateInput, SceneItemRef>("scene.create", SceneCreateInput(PROJECT, name))

	private suspend fun entry(name: String, kind: EntryKind) =
		run<EntryCreateInput, Entry>("entry.create", EntryCreateInput(PROJECT, name, kind, ""))

	private suspend fun create(content: String, start: Map<String, Long>? = null) =
		run<TimelineCreateInput, TimelineEntry>("timeline.create", TimelineCreateInput(PROJECT, content, start = start))

	private suspend fun place(kind: TimelineKindDto, subject: Int, start: Map<String, Long>? = null) =
		run<TimelinePlaceInput, TimelineEntry>("timeline.place", TimelinePlaceInput(PROJECT, kind, subject, start = start))

	private suspend fun events() = run<ProjectInput, Timeline>("timeline.list", ProjectInput(PROJECT)).events

	private suspend fun settings(input: TimelineSettingsWriteInput) =
		run<TimelineSettingsWriteInput, TimelineSettings>("timeline.settings.write", input)

	@Test
	fun `scenes and event entries are placed, with their names`() = onTestThread {
		val landfall = place(TimelineKindDto.Scene, scene("Landfall").id)
		val storm = place(TimelineKindDto.Entry, entry("The Storm", EntryKind.Event).id)

		assertEquals(listOf("Landfall", "The Storm"), events().map { it.displayTitle })
		assertEquals(TimelineKindDto.Scene to "Landfall", landfall.kind to landfall.cachedTitle)
		assertEquals(TimelineKindDto.Entry, storm.kind)
	}

	@Test
	fun `placing a scene twice is invalid`() {
		val landfall = onTestThread { scene("Landfall") }
		onTestThread { place(TimelineKindDto.Scene, landfall.id) }
		invalid { place(TimelineKindDto.Scene, landfall.id) }
	}

	@Test
	fun `placing an archived scene asks for it to be unarchived`() = onTestThread {
		val landfall = scene("Landfall")
		run<ProjectItemInput, SceneItemRef>("scene.archive", ProjectItemInput(PROJECT, landfall.id))

		val failure = assertFailsWith<OperationException> { place(TimelineKindDto.Scene, landfall.id) }

		assertEquals(OperationException.Kind.InvalidInput, failure.kind)
		assertEquals("Unarchive the scene to place it on the timeline", failure.message)
	}

	@Test
	fun `placing a person entry is invalid`() {
		val alice = onTestThread { entry("Alice", EntryKind.Person) }
		invalid { place(TimelineKindDto.Entry, alice.id) }
	}

	@Test
	fun `placing a plain event or a missing subject fails`() {
		invalid { place(TimelineKindDto.Event, 1) }
		failsWith(OperationException.Kind.NotFound) { place(TimelineKindDto.Scene, 404) }
	}

	@Test
	fun `placements take no content`() {
		val placed = onTestThread { place(TimelineKindDto.Scene, scene("Landfall").id) }
		invalid { run<TimelineUpdateInput, TimelineEntry>("timeline.update", TimelineUpdateInput(PROJECT, placed.id, "Words")) }
		invalid { run<TimelineSetInput, TimelineEntry>("timeline.set", TimelineSetInput(PROJECT, placed.id, title = "Mine")) }

		val laned = onTestThread {
			run<TimelineUpdateInput, TimelineEntry>("timeline.update", TimelineUpdateInput(PROJECT, placed.id, "", lane = "Alice"))
		}
		assertEquals("Alice", laned.lane)
		assertEquals("", laned.content)
	}

	@Test
	fun `a placement's label is set and cleared, but not its title`() {
		val placed = onTestThread { place(TimelineKindDto.Scene, scene("Landfall").id) }

		val labeled = onTestThread {
			run<TimelineSetInput, TimelineEntry>("timeline.set", TimelineSetInput(PROJECT, placed.id, label = "Late autumn"))
		}
		assertEquals("Late autumn", labeled.label)
		assertEquals("Landfall", labeled.displayTitle)

		val cleared = onTestThread {
			run<TimelineSetInput, TimelineEntry>("timeline.set", TimelineSetInput(PROJECT, placed.id, clear = listOf("label")))
		}
		assertNull(cleared.label)

		val updated = onTestThread {
			run<TimelineUpdateInput, TimelineEntry>("timeline.update", TimelineUpdateInput(PROJECT, placed.id, "", label = "Dusk"))
		}
		assertEquals("Dusk", updated.label)

		invalid { run<TimelineSetInput, TimelineEntry>("timeline.set", TimelineSetInput(PROJECT, placed.id, clear = listOf("title"))) }
		invalid { run<TimelineUpdateInput, TimelineEntry>("timeline.update", TimelineUpdateInput(PROJECT, placed.id, "", title = "Mine")) }
	}

	@Test
	fun `timeline set changes single fields and clears a lane`() = onTestThread {
		val event = run<TimelineCreateInput, TimelineEntry>(
			"timeline.create",
			TimelineCreateInput(PROJECT, "Landfall", lane = "Keep", label = "Late autumn"),
		)

		val milestone = run<TimelineSetInput, TimelineEntry>("timeline.set", TimelineSetInput(PROJECT, event.id, milestone = true))
		assertTrue(milestone.milestone)
		assertEquals("Keep" to "Late autumn", milestone.lane to milestone.label)

		val cleared = run<TimelineSetInput, TimelineEntry>("timeline.set", TimelineSetInput(PROJECT, event.id, clear = listOf("lane")))
		assertNull(cleared.lane)
		assertEquals("Late autumn", cleared.label)
		assertEquals("Landfall", cleared.content)
	}

	@Test
	fun `timeline set rejects unknown and contradictory clears`() {
		val event = onTestThread { create("Landfall") }
		invalid { run<TimelineSetInput, TimelineEntry>("timeline.set", TimelineSetInput(PROJECT, event.id, clear = listOf("content"))) }
		invalid {
			run<TimelineSetInput, TimelineEntry>("timeline.set", TimelineSetInput(PROJECT, event.id, lane = "Keep", clear = listOf("lane")))
		}
	}

	@Test
	fun `timeline update takes label over date`() = onTestThread {
		val event = create("Landfall")

		val updated = run<TimelineUpdateInput, TimelineEntry>(
			"timeline.update",
			TimelineUpdateInput(PROJECT, event.id, "Landfall", date = "Day 2", label = "Day 3"),
		)

		assertEquals("Day 3" to "Day 3", updated.label to updated.date)
	}

	@Test
	fun `switching to dated spaces undated events and the list follows dates`() = onTestThread {
		val first = create("First")
		val second = create("Second")
		val early = create("Early", start = mapOf("year" to -5L))

		val written = settings(
			TimelineSettingsWriteInput(
				PROJECT,
				mode = TimelineModeDto.Dated,
				calendar = CalendarDto(CalendarPresetDto.Story, epochName = "Landing"),
				spacing = EvenSpacingDto(unit = "day", step = 2, origin = mapOf("year" to 97L)),
			)
		)

		assertEquals(TimelineModeDto.Dated, written.timelines.single().mode)
		val events = events()
		assertEquals(listOf(early.id, first.id, second.id), events.map { it.id })
		assertEquals(
			listOf(mapOf("year" to -5L), mapOf("year" to 97L, "day" to 0L), mapOf("year" to 97L, "day" to 2L)),
			events.map { it.start },
		)
	}

	@Test
	fun `switching to manual freezes the date order`() = onTestThread {
		val late = create("Late", start = mapOf("year" to 9L))
		val early = create("Early", start = mapOf("year" to 1L))
		settings(TimelineSettingsWriteInput(PROJECT, mode = TimelineModeDto.Dated, calendar = CalendarDto(CalendarPresetDto.Story)))

		settings(TimelineSettingsWriteInput(PROJECT, mode = TimelineModeDto.Manual))

		val events = events()
		assertEquals(listOf(early.id to 0, late.id to 1), events.map { it.id to it.order })
		run<TimelineMoveInput, TimelineEntry>("timeline.move", TimelineMoveInput(PROJECT, late.id, 0))
		assertEquals(listOf(late.id, early.id), events().map { it.id })
	}

	@Test
	fun `switching to manual writes the dates of unlabelled events as labels`() = onTestThread {
		val dated = create("Storm", start = mapOf("year" to 9L, "day" to 3L))
		settings(TimelineSettingsWriteInput(PROJECT, mode = TimelineModeDto.Dated, calendar = CalendarDto(CalendarPresetDto.Story)))

		settings(TimelineSettingsWriteInput(PROJECT, mode = TimelineModeDto.Manual))

		val event = events().single { it.id == dated.id }
		assertEquals("Year 9 · Day 3", event.label)
		assertEquals(mapOf("year" to 9L, "day" to 3L), event.start)
	}

	@Test
	fun `timeline settings default to a manual main timeline`() = onTestThread {
		val settings = run<ProjectInput, TimelineSettings>("timeline.settings.read", ProjectInput(PROJECT))

		assertEquals(listOf(TimelineDefDto("main", "Main", TimelineModeDto.Manual, null, emptyList())), settings.timelines)
	}

	@Test
	fun `timeline settings take a name and lane order`() = onTestThread {
		settings(TimelineSettingsWriteInput(PROJECT, name = "Story", laneOrder = listOf("Alice", " Queen ")))

		val def = run<ProjectInput, TimelineSettings>("timeline.settings.read", ProjectInput(PROJECT)).timelines.single()
		assertEquals("Story" to listOf("Alice", "Queen"), def.name to def.laneOrder)
	}

	@Test
	fun `switching to dated needs a calendar, and spacing only applies then`() {
		invalid { settings(TimelineSettingsWriteInput(PROJECT, mode = TimelineModeDto.Dated)) }
		invalid {
			settings(TimelineSettingsWriteInput(PROJECT, spacing = EvenSpacingDto(unit = "day", origin = mapOf("year" to 1L))))
		}
		invalid {
			settings(
				TimelineSettingsWriteInput(
					PROJECT,
					calendar = CalendarDto(CalendarPresetDto.Gregorian, coarsestUnit = "hour"),
				)
			)
		}
	}

	@Test
	fun `moving on a dated timeline is invalid`() {
		val first = onTestThread {
			val first = create("First")
			create("Second")
			settings(TimelineSettingsWriteInput(PROJECT, mode = TimelineModeDto.Dated, calendar = CalendarDto(CalendarPresetDto.Story)))
			first
		}
		invalid { run<TimelineMoveInput, TimelineEntry>("timeline.move", TimelineMoveInput(PROJECT, first.id, 1)) }
		invalid { run<TimelineCreateInput, TimelineEntry>("timeline.create", TimelineCreateInput(PROJECT, "Third", index = 0)) }
		invalid { create("Undated") }
	}

	private companion object {
		const val PROJECT = "Storm Novel"
	}
}
