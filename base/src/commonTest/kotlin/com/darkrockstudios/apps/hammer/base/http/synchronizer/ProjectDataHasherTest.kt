package com.darkrockstudios.apps.hammer.base.http.synchronizer

import com.darkrockstudios.apps.hammer.base.http.projectdata.ProjectData
import com.darkrockstudios.apps.hammer.base.http.projectdata.ProjectTheme
import com.darkrockstudios.apps.hammer.base.http.projectdata.WordCountGoal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class ProjectDataHasherTest {

	// Golden values captured before the tags field existed. Tag-less data must hash to these
	// exact literals forever: stored lastSyncedHash baselines and server-side row hashes were
	// computed by the old algorithm, and a drift here makes every synced project look edited.
	@Test
	fun `golden pin - default data hash never changes`() {
		assertEquals("pNjs6dfA3-OAO7-OtvCFPw", ProjectDataHasher.hash(ProjectData()))
	}

	@Test
	fun `golden pin - populated tag-less data hash never changes`() {
		val data = ProjectData(
			authorName = "Pat",
			theme = ProjectTheme(primary = "#FF112233", secondary = "#FFAABBCC"),
			wordCountGoal = WordCountGoal(WordCountGoal.Cadence.DAY, 500),
		)
		assertEquals("WEYYxe1FU1Tf_4kU69k4uw", ProjectDataHasher.hash(data))
	}

	@Test
	fun `tags affect hash`() {
		val untagged = ProjectData(authorName = "Pat")
		val tagged = untagged.copy(tags = setOf("fantasy"))
		assertNotEquals(ProjectDataHasher.hash(untagged), ProjectDataHasher.hash(tagged))
	}

	@Test
	fun `tag insertion order does not affect hash`() {
		val ab = ProjectData(tags = setOf("alpha", "beta"))
		val ba = ProjectData(tags = setOf("beta", "alpha"))
		assertEquals(
			ProjectDataHasher.hash(ab),
			ProjectDataHasher.hash(ba),
			"equal tag sets must hash identically regardless of insertion order",
		)
	}

	@Test
	fun `tag boundaries affect hash`() {
		val joined = ProjectData(tags = setOf("ab"))
		val split = ProjectData(tags = setOf("a", "b"))
		assertNotEquals(
			ProjectDataHasher.hash(joined),
			ProjectDataHasher.hash(split),
			"concatenation-equivalent tag sets must not collide",
		)
	}

	@Test
	fun `language affects hash`() {
		val unset = ProjectData(authorName = "Pat")
		val set = unset.copy(language = "en-US")
		assertNotEquals(ProjectDataHasher.hash(unset), ProjectDataHasher.hash(set))
	}

	@Test
	fun `language region affects hash`() {
		val en = ProjectData(language = "en")
		val enUs = ProjectData(language = "en-US")
		assertNotEquals(ProjectDataHasher.hash(en), ProjectDataHasher.hash(enUs))
	}

	@Test
	fun `null vs empty language distinguishable`() {
		val nullLanguage = ProjectData(language = null)
		val emptyLanguage = ProjectData(language = "")
		assertNotEquals(
			ProjectDataHasher.hash(nullLanguage),
			ProjectDataHasher.hash(emptyLanguage),
			"null and empty-string language must hash differently",
		)
	}

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
