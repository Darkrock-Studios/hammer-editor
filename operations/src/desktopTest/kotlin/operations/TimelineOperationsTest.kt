package operations

import com.darkrockstudios.apps.hammer.base.timeline.TimelineLimits
import com.darkrockstudios.apps.hammer.common.data.ClientResult
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.projectdata.ProjectDataRepository
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository
import com.darkrockstudios.apps.hammer.common.data.temporaryProjectTask
import com.darkrockstudios.apps.hammer.operations.OperationException
import com.darkrockstudios.apps.hammer.operations.OperationRegistry
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
import com.darkrockstudios.apps.hammer.operations.core.TimelineDefCreateInput
import com.darkrockstudios.apps.hammer.operations.core.TimelineDefDeleteInput
import com.darkrockstudios.apps.hammer.operations.core.TimelineDefDeleted
import com.darkrockstudios.apps.hammer.operations.core.TimelineDefDto
import com.darkrockstudios.apps.hammer.operations.core.TimelineEntry
import com.darkrockstudios.apps.hammer.operations.core.TimelineKindDto
import com.darkrockstudios.apps.hammer.operations.core.TimelineListInput
import com.darkrockstudios.apps.hammer.operations.core.TimelineModeDto
import com.darkrockstudios.apps.hammer.operations.core.TimelineMoveInput
import com.darkrockstudios.apps.hammer.operations.core.TimelinePlaceInput
import com.darkrockstudios.apps.hammer.operations.core.TimelineSetInput
import com.darkrockstudios.apps.hammer.operations.core.TimelineSettings
import com.darkrockstudios.apps.hammer.operations.core.TimelineSettingsWriteInput
import com.darkrockstudios.apps.hammer.operations.core.TimelineUpdateInput
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.core.component.get
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TimelineOperationsTest : KoinOperationsTest() {

	private lateinit var def: ProjectDef

	@BeforeEach
	fun setUp() = onTestThread {
		val created = get<ProjectsRepository>().createProject(PROJECT, seedDefaultLanguage = false)
		check(created is ClientResult.Success<ProjectDef>)
		def = created.data
	}

	private fun failsWith(kind: OperationException.Kind, block: suspend () -> Unit) = onTestThread {
		assertEquals(kind, assertFailsWith<OperationException> { block() }.kind)
	}

	private fun invalid(block: suspend () -> Unit) = failsWith(OperationException.Kind.InvalidInput, block)

	private suspend fun scene(name: String) =
		run<SceneCreateInput, SceneItemRef>("scene.create", SceneCreateInput(PROJECT, name))

	private suspend fun entry(name: String, kind: EntryKind) =
		run<EntryCreateInput, Entry>("entry.create", EntryCreateInput(PROJECT, name, kind, ""))

	private suspend fun create(content: String, start: Map<String, Long>? = null, timeline: String? = null) =
		run<TimelineCreateInput, TimelineEntry>(
			"timeline.create",
			TimelineCreateInput(PROJECT, content, start = start, timeline = timeline),
		)

	private suspend fun place(
		kind: TimelineKindDto,
		subject: Int,
		start: Map<String, Long>? = null,
		timeline: String? = null,
	) = run<TimelinePlaceInput, TimelineEntry>(
		"timeline.place",
		TimelinePlaceInput(PROJECT, kind, subject, start = start, timeline = timeline),
	)

	private suspend fun events(timeline: String? = null) =
		run<TimelineListInput, Timeline>("timeline.list", TimelineListInput(PROJECT, timeline)).events

	private suspend fun orphans() =
		run<TimelineListInput, Timeline>("timeline.list", TimelineListInput(PROJECT, orphaned = true)).events

	private suspend fun read(id: Int) = run<ProjectItemInput, TimelineEntry>("timeline.read", ProjectItemInput(PROJECT, id))

	private suspend fun set(input: TimelineSetInput) = run<TimelineSetInput, TimelineEntry>("timeline.set", input)

	private suspend fun readSettings() = run<ProjectInput, TimelineSettings>("timeline.settings.read", ProjectInput(PROJECT))

	private suspend fun newTimeline(
		name: String,
		mode: TimelineModeDto? = null,
		calendar: CalendarDto? = null,
		laneOrder: List<String> = emptyList(),
	) = run<TimelineDefCreateInput, TimelineDefDto>(
		"timeline.def.create",
		TimelineDefCreateInput(PROJECT, name, mode, calendar, laneOrder),
	)

	private suspend fun deleteTimeline(timeline: String) =
		run<TimelineDefDeleteInput, TimelineDefDeleted>("timeline.def.delete", TimelineDefDeleteInput(PROJECT, timeline))

	private suspend fun gregorian(name: String = "Before the evening") =
		newTimeline(name, TimelineModeDto.Dated, CalendarDto(CalendarPresetDto.Gregorian))

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

	@Test
	fun `timeline def create adds a timeline that settings read lists after main`() = onTestThread {
		val created = newTimeline(
			" Before the evening ",
			TimelineModeDto.Dated,
			CalendarDto(CalendarPresetDto.Gregorian),
			laneOrder = listOf("Col. Hayes", " Pike "),
		)

		assertEquals("Before the evening", created.name)
		assertEquals(TimelineModeDto.Dated to listOf("Col. Hayes", "Pike"), created.mode to created.laneOrder)
		assertTrue(created.id != "main")
		val settings = readSettings()
		assertEquals(listOf("main", created.id), settings.timelines.map { it.id })
		assertEquals(created, settings.timelines.last())
		assertEquals(0, settings.orphaned)
	}

	@Test
	fun `timeline def create refuses a taken name and a dated timeline without a calendar`() {
		onTestThread { newTimeline("Before") }
		invalid { newTimeline("before") }
		invalid { newTimeline("Main") }
		invalid { newTimeline("Later", TimelineModeDto.Dated) }
		invalid { newTimeline("Later", laneOrder = listOf(" ")) }
	}

	@Test
	fun `create, place, and list work on the timeline named, and entries say which`() = onTestThread {
		val other = newTimeline("Before").id
		val landfall = scene("Landfall").id
		val onMain = place(TimelineKindDto.Scene, landfall)
		val onOther = place(TimelineKindDto.Scene, landfall, timeline = other)
		val plain = create("Hayes arrives", timeline = other)

		assertEquals("main", onMain.timeline)
		assertEquals(other to other, onOther.timeline to plain.timeline)
		assertEquals(listOf(onMain.id), events().map { it.id })
		assertEquals(events(), events("main"))
		assertEquals(listOf(onOther.id, plain.id), events(other).map { it.id })
		assertEquals(other, read(plain.id).timeline)
	}

	@Test
	fun `main is spelled out on output`() = onTestThread {
		create("Landfall")

		val output = get<OperationRegistry>().dispatch("timeline.list", buildJsonObject { put("project", PROJECT) })

		val entry = output.jsonObject.getValue("events").jsonArray.single().jsonObject
		assertEquals(JsonPrimitive("main"), entry["timeline"])
	}

	@Test
	fun `a placement is refused only on a timeline it is already on`() {
		val (other, landfall) = onTestThread {
			val other = newTimeline("Before").id
			val landfall = scene("Landfall").id
			place(TimelineKindDto.Scene, landfall, timeline = other)
			other to landfall
		}
		invalid { place(TimelineKindDto.Scene, landfall, timeline = other) }
		onTestThread { place(TimelineKindDto.Scene, landfall, timeline = "main") }
	}

	@Test
	fun `an unknown timeline is not found`() {
		val event = onTestThread { create("Landfall") }
		val landfall = onTestThread { scene("Landfall").id }
		val notFound = OperationException.Kind.NotFound
		failsWith(notFound) { events("nope") }
		failsWith(notFound) { create("Storm", timeline = "nope") }
		failsWith(notFound) { place(TimelineKindDto.Scene, landfall, timeline = "nope") }
		failsWith(notFound) { set(TimelineSetInput(PROJECT, event.id, timeline = "nope")) }
		failsWith(notFound) { settings(TimelineSettingsWriteInput(PROJECT, name = "Story", timeline = "nope")) }
		failsWith(notFound) { deleteTimeline("nope") }
		val failure = onTestThread { assertFailsWith<OperationException> { events("nope") } }
		assertEquals("No timeline nope", failure.message)
	}

	@Test
	fun `index and move count only the event's own timeline`() = onTestThread {
		val other = newTimeline("Before").id
		create("Main one")
		create("Main two")
		val first = create("First", timeline = other)
		val second = create("Second", timeline = other)

		run<TimelineMoveInput, TimelineEntry>("timeline.move", TimelineMoveInput(PROJECT, second.id, 0))
		val third = run<TimelineCreateInput, TimelineEntry>(
			"timeline.create",
			TimelineCreateInput(PROJECT, "Third", index = 1, timeline = other),
		)

		assertEquals(listOf(second.id, third.id, first.id), events(other).map { it.id })
		assertEquals(listOf("Main one", "Main two"), events().map { it.content })
	}

	@Test
	fun `an index past the named timeline's end is invalid`() {
		val other = onTestThread {
			create("Main one")
			create("Main two")
			newTimeline("Before").id
		}
		invalid {
			run<TimelineCreateInput, TimelineEntry>("timeline.create", TimelineCreateInput(PROJECT, "Far", index = 1, timeline = other))
		}
	}

	@Test
	fun `timeline set moves an event, taking a start when its own does not fit the target`() {
		val (other, event) = onTestThread {
			gregorian().id to create("Hayes signs the note", start = mapOf("year" to 97L, "day" to 210L))
		}

		val refused = onTestThread {
			assertFailsWith<OperationException> { set(TimelineSetInput(PROJECT, event.id, timeline = other)) }
		}
		assertEquals("A date or calendar does not fit; see timeline.settings.read for the calendar", refused.message)
		assertEquals("main", onTestThread { read(event.id) }.timeline)

		val start = mapOf("year" to 1922L, "month" to 9L, "day" to 14L)
		val moved = onTestThread {
			set(TimelineSetInput(PROJECT, event.id, timeline = other, start = start, lane = "Col. Hayes"))
		}

		assertEquals(other, moved.timeline)
		assertEquals(start to "Col. Hayes", moved.start to moved.lane)
		assertEquals(emptyList(), onTestThread { events() })
		assertEquals(listOf(event.id), onTestThread { events(other) }.map { it.id })
	}

	@Test
	fun `timeline set refuses a move with a field that does not fit and leaves the event alone`() {
		val (other, before) = onTestThread {
			newTimeline("Before").id to create("Hayes signs the note").let { read(it.id) }
		}

		invalid { set(TimelineSetInput(PROJECT, before.id, timeline = other, label = "x".repeat(TimelineLimits.MAX_LABEL + 1))) }
		invalid { set(TimelineSetInput(PROJECT, before.id, timeline = other, lane = "x".repeat(TimelineLimits.MAX_LANE + 1))) }

		assertEquals(before, onTestThread { read(before.id) })
		assertEquals(emptyList(), onTestThread { events(other) })
	}

	@Test
	fun `timeline set applies a label and title together with the move`() = onTestThread {
		val other = newTimeline("Before").id
		val event = create("Hayes signs the note")

		val moved = set(TimelineSetInput(PROJECT, event.id, timeline = other, title = "The note", label = "Late autumn"))

		assertEquals(Triple(other, "The note", "Late autumn"), Triple(moved.timeline, moved.title, moved.label))
		assertEquals(moved, read(event.id))
		assertEquals(emptyList(), events())
	}

	@Test
	fun `timeline set takes main by name and leaves an event on its own timeline`() = onTestThread {
		val other = newTimeline("Before").id
		val event = create("Landfall", timeline = other)

		val laned = set(TimelineSetInput(PROJECT, event.id, timeline = other, lane = "Keep"))
		assertEquals(other to "Keep", laned.timeline to laned.lane)

		val back = set(TimelineSetInput(PROJECT, event.id, timeline = "main"))
		assertEquals("main" to "Keep", back.timeline to back.lane)
	}

	@Test
	fun `timeline settings write changes and renames only the timeline named`() {
		val other = onTestThread {
			val other = newTimeline("Before").id
			create("Main event", start = mapOf("year" to 3L))

			val written = settings(
				TimelineSettingsWriteInput(
					PROJECT,
					name = "Long ago",
					mode = TimelineModeDto.Dated,
					calendar = CalendarDto(CalendarPresetDto.Gregorian),
					laneOrder = listOf("Pike"),
					timeline = other,
				)
			)

			val (main, renamed) = written.timelines
			assertEquals(TimelineDefDto("main", "Main", TimelineModeDto.Manual, null, emptyList()), main)
			assertEquals(
				TimelineDefDto(other, "Long ago", TimelineModeDto.Dated, CalendarDto(CalendarPresetDto.Gregorian), listOf("Pike")),
				renamed,
			)
			assertEquals(mapOf("year" to 3L), events().single().start)
			other
		}

		invalid { settings(TimelineSettingsWriteInput(PROJECT, name = "main", timeline = other)) }
		onTestThread {
			settings(TimelineSettingsWriteInput(PROJECT, name = "Story", timeline = "main"))
			assertEquals(listOf("Story", "Long ago"), readSettings().timelines.map { it.name })
		}
	}

	@Test
	fun `timeline def delete deletes the timeline and its events but refuses main`() {
		invalid { deleteTimeline("main") }

		val (other, onOther, landfall) = onTestThread {
			val other = newTimeline("Before").id
			val landfall = scene("Landfall").id
			create("Kept")
			val onOther = listOf(create("Gone", timeline = other).id, place(TimelineKindDto.Scene, landfall, timeline = other).id)
			Triple(other, onOther, landfall)
		}

		val deleted = onTestThread { deleteTimeline(other) }

		assertEquals(TimelineDefDeleted(other, 2), deleted)
		assertEquals(listOf("main"), onTestThread { readSettings() }.timelines.map { it.id })
		assertEquals(listOf("Kept"), onTestThread { events() }.map { it.content })
		onTestThread { assertEquals(0, readSettings().orphaned) }
		onTestThread { assertEquals(listOf(), orphans()) }
		onOther.forEach { id -> failsWith(OperationException.Kind.NotFound) { read(id) } }
		failsWith(OperationException.Kind.NotFound) { deleteTimeline(other) }
		onTestThread { place(TimelineKindDto.Scene, landfall) }
	}

	@Test
	fun `events left on a deleted timeline are listed as orphaned, and can be read and moved`() {
		val (other, straggler) = onTestThread {
			val other = newTimeline("Before").id
			val straggler = create("Made offline", timeline = other)
			create("On main")
			temporaryProjectTask(def) { scope ->
				scope.get<ProjectDataRepository>().updateData { it.copy(deletedTimelines = it.deletedTimelines + other) }
			}
			other to straggler
		}

		onTestThread {
			assertEquals(listOf(straggler.id to other), orphans().map { it.id to it.timeline })
			assertEquals(1, readSettings().orphaned)
			assertEquals(listOf("main"), readSettings().timelines.map { it.id })
			assertEquals(other, read(straggler.id).timeline)
		}
		invalid { run<TimelineListInput, Timeline>("timeline.list", TimelineListInput(PROJECT, "main", orphaned = true)) }
		failsWith(OperationException.Kind.NotFound) { events(other) }

		val moved = onTestThread { set(TimelineSetInput(PROJECT, straggler.id, timeline = "main")) }
		assertEquals("main", moved.timeline)
		onTestThread { assertEquals(emptyList(), orphans()) }
	}

	private companion object {
		const val PROJECT = "Storm Novel"
	}
}
