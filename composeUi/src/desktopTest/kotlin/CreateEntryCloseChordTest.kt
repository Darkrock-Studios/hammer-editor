import androidx.compose.foundation.layout.Box
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.update
import com.darkrockstudios.apps.hammer.common.components.actions.ActionRegistry
import com.darkrockstudios.apps.hammer.common.components.actions.ChordKey
import com.darkrockstudios.apps.hammer.common.components.actions.HammerActions
import com.darkrockstudios.apps.hammer.common.components.actions.KeyChord
import com.darkrockstudios.apps.hammer.common.components.encyclopedia.CreateEntry
import com.darkrockstudios.apps.hammer.common.compose.actions.ProvideActions
import com.darkrockstudios.apps.hammer.common.compose.rememberRootSnackbarHostState
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EntryError
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EntryResult
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryType
import com.darkrockstudios.apps.hammer.common.encyclopedia.CreateEntryUi
import com.darkrockstudios.apps.hammer.common.encyclopedia.ENCYCLOPEDIA_CREATE_NAME_TAG
import com.darkrockstudios.apps.hammer.common.preview.KoinApplicationPreview
import com.darkrockstudios.apps.hammer.common.preview.fakeProjectDef
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CreateEntryCloseChordTest : BaseTest() {
	@get:Rule
	val compose = createComposeRule()

	private val registry = ActionRegistry()
	private var createShown by mutableStateOf(true)
	private var closes = 0

	private class FakeCreateEntry : CreateEntry {
		val created = mutableListOf<String>()
		private val _state = MutableValue(CreateEntry.State(projectDef = fakeProjectDef()))
		override val state: Value<CreateEntry.State> = _state

		override suspend fun createEntry(
			name: String,
			type: EntryType,
			text: String,
			tags: Set<String>,
			imagePath: String?,
			excludeFromDictionary: Boolean,
		) = EntryResult(EntryError.NONE).also { created += name }

		override fun confirmClose() = _state.update { it.copy(showConfirmClose = true) }
		override fun dismissConfirmClose() = _state.update { it.copy(showConfirmClose = false) }
		override fun suggestTags(prefix: String, limit: Int): List<String> = emptyList()
	}

	private fun show(component: CreateEntry) {
		compose.setContent {
			KoinApplicationPreview {
				ProvideActions(registry) {
					if (createShown) Box(modifier = Modifier.requiredSize(900.dp, 700.dp)) {
						CreateEntryUi(
							component = component,
							scope = scope,
							rootSnackbar = rememberRootSnackbarHostState(),
							modifier = Modifier,
							close = { closes++ },
						)
					}
				}
			}
		}
		compose.waitForIdle()
	}

	private fun pressModW(): Boolean = compose.runOnIdle { registry.dispatch(KeyChord.mod(ChordKey.W)) }

	@Test
	fun `Mod+W on a blank new entry closes it`() {
		val component = FakeCreateEntry()
		show(component)

		assertTrue(pressModW())

		assertEquals(1, closes)
		assertFalse(component.state.value.showConfirmClose)
	}

	@Test
	fun `Mod+W on a new entry with a name asks to discard it`() {
		val component = FakeCreateEntry()
		show(component)
		compose.onNodeWithTag(ENCYCLOPEDIA_CREATE_NAME_TAG).performTextInput("Alice")
		compose.waitForIdle()

		assertTrue(pressModW())

		assertTrue(component.state.value.showConfirmClose)
		assertEquals(0, closes)
	}

	@Test
	fun `Mod+W is not live once the new entry leaves`() {
		show(FakeCreateEntry())

		createShown = false
		compose.waitForIdle()

		assertFalse(registry.isLive(HammerActions.ItemClose.id))
	}

	@Test
	fun `a new entry opens with the caret in its name`() {
		show(FakeCreateEntry())
		compose.waitForIdle()

		compose.onNodeWithTag(ENCYCLOPEDIA_CREATE_NAME_TAG).assertIsFocused()
	}

	@Test
	fun `Mod+Enter creates the entry from any field`() {
		val component = FakeCreateEntry()
		show(component)
		compose.onNodeWithTag(ENCYCLOPEDIA_CREATE_NAME_TAG).performTextInput("Alice")
		compose.waitForIdle()

		val handled = compose.runOnIdle { registry.dispatch(KeyChord.mod(ChordKey.Enter)) }
		scope.testScheduler.runCurrent()
		compose.waitForIdle()

		assertTrue(handled)
		assertEquals(listOf("Alice"), component.created)
		assertEquals(1, closes)
	}
}
