package com.darkrockstudios.apps.hammer.common.data.projectdata

import com.darkrockstudios.apps.hammer.base.http.projectdata.ProjectData
import com.darkrockstudios.apps.hammer.base.timeline.CalendarDef
import com.darkrockstudios.apps.hammer.base.timeline.CalendarPreset
import com.darkrockstudios.apps.hammer.base.timeline.TimelineDef
import com.darkrockstudios.apps.hammer.base.timeline.TimelineMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProjectDataMergeTest {

	@Test
	fun `merged dictionary words are the union of both sides`() {
		val local = ProjectData(dictionaryWords = setOf("Kvothe"))
		val server = ProjectData(dictionaryWords = setOf("Denna", "Imre"))

		assertEquals(setOf("Kvothe", "Denna", "Imre"), mergeDictionaryWords(local, server))
	}

	@Test
	fun `merging keeps a word this build would not accept as input`() {
		// The merged set is uploaded, so stripping it here would delete a newer build's word
		// server-side for every other device.
		val local = ProjectData(dictionaryWords = setOf("Kvothe"))
		val server = ProjectData(dictionaryWords = setOf("Chandrian sign"))

		assertEquals(setOf("Kvothe", "Chandrian sign"), mergeDictionaryWords(local, server))
	}

	@Test
	fun `differsOutsideAutoMerged ignores the dictionary and nothing else`() {
		val base = ProjectData(authorName = "Pat", dictionaryWords = setOf("a"))

		assertFalse(base.differsOutsideAutoMerged(base.copy(dictionaryWords = setOf("b"))))
		assertTrue(base.differsOutsideAutoMerged(base.copy(authorName = "Sam")))
	}

	@Test
	fun `reapplyLocalEdits keeps changed fields and takes the rest from incoming`() {
		val base = ProjectData(authorName = "Pat", language = "en")
		val local = base.copy(authorName = "Edited")
		val incoming = ProjectData(authorName = "Server", language = "fr")

		assertEquals(
			ProjectData(authorName = "Edited", language = "fr"),
			reapplyLocalEdits(base, local, incoming),
		)
	}

	@Test
	fun `reapplyLocalEdits carries dictionary additions and removals as a delta`() {
		val base = ProjectData(dictionaryWords = setOf("keep", "drop"))
		val local = base.copy(dictionaryWords = setOf("keep", "added"))
		val incoming = ProjectData(dictionaryWords = setOf("keep", "drop", "server"))

		assertEquals(
			setOf("keep", "server", "added"),
			reapplyLocalEdits(base, local, incoming).dictionaryWords,
		)
	}

	@Test
	fun `reapplyLocalEdits keeps a local cover change`() {
		val base = ProjectData(coverImageId = 1)
		val local = base.copy(coverImageId = 2)
		val incoming = ProjectData(coverImageId = 3)

		assertEquals(2, reapplyLocalEdits(base, local, incoming).coverImageId)
	}

	@Test
	fun `reapplyLocalEdits keeps a locally cleared cover`() {
		val base = ProjectData(coverImageId = 1)
		val local = base.copy(coverImageId = null)
		val incoming = ProjectData(coverImageId = 3)

		assertEquals(null, reapplyLocalEdits(base, local, incoming).coverImageId)
	}

	@Test
	fun `reapplyLocalEdits takes the incoming cover when unchanged locally`() {
		val base = ProjectData(coverImageId = 1, authorName = "Pat")
		val local = base.copy(authorName = "Edited")
		val incoming = ProjectData(coverImageId = 3)

		assertEquals(3, reapplyLocalEdits(base, local, incoming).coverImageId)
	}

	private val main = TimelineDef.DEFAULT
	private val dated = main.copy(
		mode = TimelineMode.DATED,
		calendar = CalendarDef(CalendarPreset.STORY, epochName = "Landing"),
	)
	private val second = TimelineDef(id = "b2c3d4e5", name = "Second")
	private val third = TimelineDef(id = "c3d4e5f6", name = "Third")

	private fun reapplyTimelines(
		base: List<TimelineDef>,
		local: List<TimelineDef>,
		incoming: List<TimelineDef>,
	): List<TimelineDef> = reapplyLocalEdits(
		ProjectData(timelines = base),
		ProjectData(timelines = local),
		ProjectData(timelines = incoming),
	).timelines

	@Test
	fun `a timeline added locally survives incoming`() {
		assertEquals(
			listOf(main, second),
			reapplyTimelines(base = listOf(main), local = listOf(main, second), incoming = listOf(main)),
		)
	}

	@Test
	fun `a timeline changed locally beats the incoming change`() {
		val incomingChange = main.copy(laneOrder = listOf("Alice"))

		assertEquals(
			listOf(dated),
			reapplyTimelines(base = listOf(main), local = listOf(dated), incoming = listOf(incomingChange)),
		)
	}

	@Test
	fun `a timeline removed locally is removed`() {
		assertEquals(
			listOf(main),
			reapplyTimelines(base = listOf(main, second), local = listOf(main), incoming = listOf(main, second)),
		)
	}

	@Test
	fun `a timeline untouched locally takes incoming`() {
		assertEquals(
			listOf(dated),
			reapplyTimelines(base = listOf(main), local = listOf(main), incoming = listOf(dated)),
		)
	}

	@Test
	fun `a timeline removed incoming stays removed when untouched locally`() {
		assertEquals(
			listOf(main),
			reapplyTimelines(base = listOf(main, second), local = listOf(main, second), incoming = listOf(main)),
		)
	}

	@Test
	fun `timelines added on both sides are both kept`() {
		assertEquals(
			listOf(main, third, second),
			reapplyTimelines(base = listOf(main), local = listOf(main, second), incoming = listOf(main, third)),
		)
	}

	@Test
	fun `merged timelines follow incoming order then local-only defs`() {
		val renamedSecond = second.copy(name = "Renamed")

		assertEquals(
			listOf(third, renamedSecond, main),
			reapplyTimelines(
				base = listOf(second),
				local = listOf(renamedSecond, main),
				incoming = listOf(third, second),
			),
		)
	}

	@Test
	fun `timelines by choice keep a def only one side has`() {
		assertEquals(listOf(main, second), mergeTimelinesByChoice(listOf(main), listOf(second), emptySet(), preferLocal = true))
		assertEquals(listOf(second, main), mergeTimelinesByChoice(listOf(main), listOf(second), emptySet(), preferLocal = false))
	}

	@Test
	fun `timelines by choice take the chosen side on a same-id difference`() {
		assertEquals(listOf(dated), mergeTimelinesByChoice(listOf(dated), listOf(main), emptySet(), preferLocal = true))
		assertEquals(listOf(main), mergeTimelinesByChoice(listOf(dated), listOf(main), emptySet(), preferLocal = false))
	}

	@Test
	fun `an incoming tombstone beats a local edit of the deleted def`() {
		val renamed = second.copy(name = "Renamed")
		val merged = reapplyLocalEdits(
			base = ProjectData(timelines = listOf(main, second)),
			local = ProjectData(timelines = listOf(main, renamed)),
			incoming = ProjectData(timelines = listOf(main), deletedTimelines = setOf(second.id)),
		)

		assertEquals(listOf(main), merged.timelines)
		assertEquals(setOf(second.id), merged.deletedTimelines)
	}

	@Test
	fun `a local tombstone beats an incoming edit of the deleted def`() {
		val renamed = second.copy(name = "Renamed")
		val merged = reapplyLocalEdits(
			base = ProjectData(timelines = listOf(main, second)),
			local = ProjectData(timelines = listOf(main), deletedTimelines = setOf(second.id)),
			incoming = ProjectData(timelines = listOf(main, renamed)),
		)

		assertEquals(listOf(main), merged.timelines)
		assertEquals(setOf(second.id), merged.deletedTimelines)
	}

	@Test
	fun `reapplyLocalEdits unions tombstones from base and local and incoming`() {
		val merged = reapplyLocalEdits(
			base = ProjectData(deletedTimelines = setOf("aaaaaaaa")),
			local = ProjectData(deletedTimelines = setOf("aaaaaaaa", "bbbbbbbb")),
			incoming = ProjectData(deletedTimelines = setOf("cccccccc")),
		)

		assertEquals(setOf("aaaaaaaa", "bbbbbbbb", "cccccccc"), merged.deletedTimelines)
	}

	@Test
	fun `a tombstone beats the chosen side on the conflict screen`() {
		assertEquals(
			listOf(main),
			mergeTimelinesByChoice(listOf(main, second), listOf(main), setOf(second.id), preferLocal = true),
		)
		assertEquals(
			listOf(main, third),
			mergeTimelinesByChoice(listOf(main, second), listOf(main, third), setOf(second.id), preferLocal = false),
		)
	}

	@Test
	fun `a main tombstone is ignored`() {
		assertEquals(
			setOf(second.id),
			mergeDeletedTimelines(ProjectData(deletedTimelines = setOf(TimelineDef.MAIN_ID, second.id))),
		)
		val merged = reapplyLocalEdits(
			base = ProjectData(timelines = listOf(main)),
			local = ProjectData(timelines = listOf(dated)),
			incoming = ProjectData(timelines = listOf(main), deletedTimelines = setOf(TimelineDef.MAIN_ID)),
		)
		assertEquals(listOf(dated), merged.timelines)
		assertEquals(emptySet(), merged.deletedTimelines)
		assertEquals(listOf(main), listOf(main).withoutDeleted(setOf(TimelineDef.MAIN_ID)))
	}

	@Test
	fun `a deletion against an edit of the deleted def auto-merges`() {
		val deleter = ProjectData(authorName = "Pat", timelines = listOf(main), deletedTimelines = setOf(second.id))
		val editor = ProjectData(authorName = "Pat", timelines = listOf(main, second.copy(name = "Renamed")))

		assertFalse(editor.differsOutsideAutoMerged(deleter))
		assertFalse(deleter.differsOutsideAutoMerged(editor))
		assertEquals(deleter, editor.withAutoMerged(deleter))
	}

	@Test
	fun `auto-merge unions tombstones and still sees other differences`() {
		val local = ProjectData(timelines = listOf(main, third), deletedTimelines = setOf(second.id))
		val server = ProjectData(timelines = listOf(main, second), deletedTimelines = setOf("dddddddd"))

		assertEquals(
			local.copy(deletedTimelines = setOf(second.id, "dddddddd")),
			local.withAutoMerged(server),
		)
		// third exists only locally and is not tombstoned, so the timelines still conflict.
		assertTrue(local.differsOutsideAutoMerged(server))
		assertFalse(
			local.differsOutsideAutoMerged(server.copy(timelines = listOf(main, third))),
		)
	}
}
