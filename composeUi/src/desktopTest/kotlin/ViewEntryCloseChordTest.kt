import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.common.components.actions.ActionRegistry
import com.darkrockstudios.apps.hammer.common.components.actions.ChordKey
import com.darkrockstudios.apps.hammer.common.components.actions.HammerActions
import com.darkrockstudios.apps.hammer.common.components.actions.KeyChord
import com.darkrockstudios.apps.hammer.common.components.encyclopedia.ViewEntry
import com.darkrockstudios.apps.hammer.common.compose.actions.ProvideActions
import com.darkrockstudios.apps.hammer.common.compose.rememberRootSnackbarHostState
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryContent
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryDef
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryType
import com.darkrockstudios.apps.hammer.common.encyclopedia.ViewEntryUi
import com.darkrockstudios.apps.hammer.common.preview.KoinApplicationPreview
import com.darkrockstudios.apps.hammer.common.preview.fakeProjectDef
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ViewEntryCloseChordTest : BaseTest() {
	@get:Rule
	val compose = createComposeRule()

	private val registry = ActionRegistry()
	private var entryShown by mutableStateOf(true)
	private var closes = 0
	private val content = EntryContent(id = 3, name = "Alice", type = EntryType.PERSON, text = "Curious.", tags = emptySet())

	private fun entry(editing: Boolean = false) = FakeViewEntry(
		ViewEntry.State(
			entryDef = EntryDef(fakeProjectDef(), id = content.id, type = content.type, name = content.name),
			content = content,
			editName = editing,
			editText = editing,
		)
	)

	@OptIn(ExperimentalSharedTransitionApi::class)
	private fun show(component: ViewEntry) {
		compose.setContent {
			KoinApplicationPreview {
				ProvideActions(registry) {
					if (entryShown) Box(modifier = Modifier.requiredSize(900.dp, 700.dp)) {
						SharedTransitionLayout {
							AnimatedVisibility(visible = true) {
								ViewEntryUi(
									component = component,
									scope = scope,
									rootSnackbar = rememberRootSnackbarHostState(),
									closeEntry = { closes++ },
									sharedTransitionScope = this@SharedTransitionLayout,
									animatedVisibilityScope = this@AnimatedVisibility,
								)
							}
						}
					}
				}
			}
		}
		compose.waitForIdle()
	}

	private fun pressModW(): Boolean = compose.runOnIdle { registry.dispatch(KeyChord.mod(ChordKey.W)) }

	@Test
	fun `Mod+W on a dirty entry edit asks before closing`() {
		val component = entry(editing = true)
		show(component)
		compose.onNode(hasText(content.name) and hasSetTextAction()).performTextReplacement("Alicia")
		compose.waitForIdle()

		assertTrue(pressModW())

		assertEquals(1, component.confirmCloseCalls)
		assertEquals(0, closes)
	}

	@Test
	fun `Mod+W on a clean entry closes it`() {
		val component = entry()
		show(component)

		assertTrue(pressModW())

		assertEquals(1, closes)
		assertEquals(0, component.confirmCloseCalls)
	}

	@Test
	fun `Mod+W on an unchanged edit ends it and closes`() {
		val component = entry(editing = true)
		show(component)

		assertTrue(pressModW())

		assertEquals(1, closes)
		assertFalse(component.state.value.editText)
	}

	@Test
	fun `Mod+W is not live once the entry leaves`() {
		show(entry())

		entryShown = false
		compose.waitForIdle()

		assertFalse(registry.isLive(HammerActions.ItemClose.id))
	}
}
