package com.darkrockstudios.apps.hammer.base.http.synchronizer

import com.darkrockstudios.apps.hammer.base.http.projectdata.ProjectData
import com.darkrockstudios.apps.hammer.base.http.projectdata.ProjectTheme
import com.darkrockstudios.apps.hammer.base.http.projectdata.WordCountGoal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class ProjectDataHasherTest {

	@Test
	fun `same input produces same hash`() {
		val data = ProjectData(
			authorName = "Pat",
			theme = ProjectTheme(primary = "#FF112233", secondary = "#FFAABBCC"),
			wordCountGoal = WordCountGoal(WordCountGoal.Cadence.DAY, 500),
		)
		assertEquals(ProjectDataHasher.hash(data), ProjectDataHasher.hash(data))
	}

	@Test
	fun `empty default has stable hash`() {
		assertEquals(ProjectDataHasher.hash(ProjectData()), ProjectDataHasher.hash(ProjectData()))
	}

	@Test
	fun `null vs empty author distinguishable`() {
		val nullAuthor = ProjectData(authorName = null)
		val emptyAuthor = ProjectData(authorName = "")
		assertNotEquals(
			ProjectDataHasher.hash(nullAuthor),
			ProjectDataHasher.hash(emptyAuthor),
			"null and empty-string author must hash differently — empty string is an explicit user choice",
		)
	}

	@Test
	fun `theme content changes hash`() {
		val a = ProjectData(theme = ProjectTheme("#000000", "#111111"))
		val b = ProjectData(theme = ProjectTheme("#000000", "#222222"))
		assertNotEquals(ProjectDataHasher.hash(a), ProjectDataHasher.hash(b))
	}

	@Test
	fun `null theme distinguishable from theme with empty colors`() {
		val nullTheme = ProjectData(theme = null)
		val emptyTheme = ProjectData(theme = ProjectTheme("", ""))
		assertNotEquals(
			ProjectDataHasher.hash(nullTheme),
			ProjectDataHasher.hash(emptyTheme),
			"presence-byte must distinguish null sub-object from one with empty fields",
		)
	}

	@Test
	fun `cadence change affects hash`() {
		val day = ProjectData(wordCountGoal = WordCountGoal(WordCountGoal.Cadence.DAY, 500))
		val week = ProjectData(wordCountGoal = WordCountGoal(WordCountGoal.Cadence.WEEK, 500))
		assertNotEquals(ProjectDataHasher.hash(day), ProjectDataHasher.hash(week))
	}

	@Test
	fun `count change affects hash`() {
		val a = ProjectData(wordCountGoal = WordCountGoal(WordCountGoal.Cadence.DAY, 500))
		val b = ProjectData(wordCountGoal = WordCountGoal(WordCountGoal.Cadence.DAY, 600))
		assertNotEquals(ProjectDataHasher.hash(a), ProjectDataHasher.hash(b))
	}

	@Test
	fun `clearing one field gives different hash`() {
		val full = ProjectData(
			authorName = "Pat",
			theme = ProjectTheme("#FF000000", "#FFFFFFFF"),
			wordCountGoal = WordCountGoal(WordCountGoal.Cadence.DAY, 500),
		)
		val noTheme = full.copy(theme = null)
		assertNotEquals(ProjectDataHasher.hash(full), ProjectDataHasher.hash(noTheme))
	}
}
