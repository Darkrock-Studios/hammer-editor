package components.projecthome

import com.darkrockstudios.apps.hammer.common.components.projecthome.ProjectSettings
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class ProjectTagsCleaningTest {

	@Test
	fun `valid tags pass through unchanged`() {
		assertEquals(
			setOf("fantasy", "sci-fi", "draft_2"),
			ProjectSettings.cleanProjectTags(setOf("fantasy", "sci-fi", "draft_2")),
		)
	}

	@Test
	fun `hash prefix and whitespace are stripped`() {
		assertEquals(
			setOf("fantasy"),
			ProjectSettings.cleanProjectTags(setOf(" #fantasy ")),
		)
	}

	@Test
	fun `tags failing the pattern are dropped`() {
		assertEquals(
			setOf("ok"),
			ProjectSettings.cleanProjectTags(setOf("ok", "not ok", "nope!")),
		)
	}

	@Test
	fun `over-long tags are dropped`() {
		val tooLong = "a".repeat(ProjectSettings.MAX_TAG_SIZE + 1)
		val maxed = "a".repeat(ProjectSettings.MAX_TAG_SIZE)
		assertEquals(
			setOf(maxed),
			ProjectSettings.cleanProjectTags(setOf(tooLong, maxed)),
		)
	}
}
