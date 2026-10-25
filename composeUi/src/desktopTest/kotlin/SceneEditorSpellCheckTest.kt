import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.darkrockstudios.apps.hammer.common.compose.rememberRootSnackbarHostState
import com.darkrockstudios.apps.hammer.common.preview.KoinApplicationPreview
import com.darkrockstudios.apps.hammer.common.preview.sceneeditor.fakeFocusMode
import com.darkrockstudios.apps.hammer.common.preview.sceneeditor.fakeSceneEditor
import com.darkrockstudios.apps.hammer.common.storyeditor.focusmode.FocusModeUi
import com.darkrockstudios.apps.hammer.common.storyeditor.sceneeditor.SceneEditorUi
import com.darkrockstudios.libs.platformspellchecker.PlatformSpellChecker
import com.darkrockstudios.libs.platformspellchecker.PlatformSpellCheckerFactory
import com.darkrockstudios.libs.platformspellchecker.SpLocale
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

class SceneEditorSpellCheckTest {

	@get:Rule
	val compose = createComposeRule()

	// The squiggle is the only pure red the editor draws.
	private fun squigglePixels(): Int {
		val pixels = compose.onRoot().captureToImage().toPixelMap()
		var count = 0
		for (x in 0 until pixels.width) {
			for (y in 0 until pixels.height) {
				val c = pixels[x, y]
				if (c.red > 0.8f && c.green < 0.3f && c.blue < 0.3f) count++
			}
		}
		return count
	}

	private fun englishChecker(): PlatformSpellChecker {
		val factory = PlatformSpellCheckerFactory()
		assumeTrue("en-US dictionary not available", factory.hasLanguage(SpLocale.EN_US))
		return runBlocking { factory.createSpellChecker(SpLocale.EN_US) }
	}

	@Test
	fun `a misspelling in the loaded scene is underlined`() {
		val component = fakeSceneEditor(markdown = TYPO_TEXT, spellChecker = englishChecker())

		compose.setContent {
			KoinApplicationPreview {
				SceneEditorUi(component, rememberRootSnackbarHostState())
			}
		}

		compose.waitUntil(timeoutMillis = 10_000) { squigglePixels() > 0 }
	}

	@Test
	fun `a misspelling in the loaded scene is underlined in focus mode`() {
		val component = fakeFocusMode(markdown = TYPO_TEXT, spellChecker = englishChecker())

		compose.setContent {
			KoinApplicationPreview {
				FocusModeUi(component)
			}
		}

		compose.waitUntil(timeoutMillis = 10_000) { squigglePixels() > 0 }
	}

	private companion object {
		const val TYPO_TEXT = "This sentense has a tyypo in it."
	}
}
