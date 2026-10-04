package components.storyeditor.sceneeditor

import com.darkrockstudios.apps.hammer.common.components.storyeditor.sceneeditor.EDITOR_TEXT_SIZES
import com.darkrockstudios.apps.hammer.common.components.storyeditor.sceneeditor.decreaseEditorTextSize
import com.darkrockstudios.apps.hammer.common.components.storyeditor.sceneeditor.increaseEditorTextSize
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class EditorTextSizeStepsTest {

	@Test
	fun `steps walk the ladder`() {
		assertEquals(18f, increaseEditorTextSize(16f))
		assertEquals(14f, decreaseEditorTextSize(16f))
	}

	@Test
	fun `a size off the ladder snaps to it`() {
		assertEquals(18f, increaseEditorTextSize(17f))
		assertEquals(16f, decreaseEditorTextSize(17f))
	}

	@Test
	fun `steps stop at the ends`() {
		assertEquals(EDITOR_TEXT_SIZES.last(), increaseEditorTextSize(EDITOR_TEXT_SIZES.last()))
		assertEquals(EDITOR_TEXT_SIZES.first(), decreaseEditorTextSize(EDITOR_TEXT_SIZES.first()))
		assertEquals(EDITOR_TEXT_SIZES.last(), increaseEditorTextSize(99f))
		assertEquals(EDITOR_TEXT_SIZES.first(), decreaseEditorTextSize(1f))
	}
}
