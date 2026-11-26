package com.darkrockstudios.apps.hammer.common.notes

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.common.components.notes.BrowseNotes
import com.darkrockstudios.apps.hammer.common.compose.focus.GridRowStep
import com.darkrockstudios.apps.hammer.common.compose.focus.ListCursorFrame
import com.darkrockstudios.apps.hammer.common.compose.focus.listCursorStep
import com.darkrockstudios.apps.hammer.common.data.notesrepository.note.NoteContent
import com.darkrockstudios.apps.hammer.common.data.tagindex.TagCount
import com.darkrockstudios.apps.hammer.common.preview.fakeProjectDef
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

private class FakeBrowseNotes(notes: List<NoteContent>) : BrowseNotes {
	val viewed = mutableListOf<Int>()
	private val _state = MutableValue(BrowseNotes.State(projectDef = fakeProjectDef(), notes = notes))
	override val state: Value<BrowseNotes.State> = _state
	override val rankedTags: Value<List<TagCount>> = MutableValue(emptyList())
	override val visibleOrder: List<Int> = emptyList()
	override fun viewNote(noteId: Int) {
		viewed += noteId
	}

	override fun showCreate() {}
	override fun onVisibleOrderChanged(noteIds: List<Int>) {}

	fun showOnly(ids: Set<Int>) {
		_state.value = _state.value.copy(notes = _state.value.notes.filter { it.id in ids })
	}
}

/** Newest first, the list's default order. */
private val notes = listOf(
	NoteContent(id = 10, created = Instant.parse("2026-11-03T10:00:00Z"), content = "Arrival"),
	NoteContent(id = 20, created = Instant.parse("2026-11-02T10:00:00Z"), content = "Bridge"),
	NoteContent(id = 30, created = Instant.parse("2026-11-01T10:00:00Z"), content = "Cellar"),
)

@OptIn(ExperimentalTestApi::class)
class BrowseNotesKeyboardTest {

	@OptIn(ExperimentalSharedTransitionApi::class)
	private fun ComposeUiTest.show(component: BrowseNotes, width: Dp = 500.dp) {
		setContent {
			Box(modifier = Modifier.requiredSize(width, 900.dp)) {
				SharedTransitionLayout {
					AnimatedVisibility(visible = true) {
						BrowseNotesUi(
							component = component,
							sharedTransitionScope = this@SharedTransitionLayout,
							animatedVisibilityScope = this@AnimatedVisibility,
						)
					}
				}
			}
		}
		waitForIdle()
	}

	private fun ComposeUiTest.keys(keys: List<Key>) {
		onNodeWithTag(NOTES_GRID_TAG).requestFocus()
		onNodeWithTag(NOTES_GRID_TAG).performKeyInput { keys.forEach { pressKey(it) } }
		waitForIdle()
	}

	@Test
	fun `Down moves the cursor across the visible notes`() = runComposeUiTest {
		val component = FakeBrowseNotes(notes)
		show(component)

		keys(listOf(Key.DirectionDown, Key.DirectionDown, Key.Enter))
		keys(listOf(Key.DirectionDown, Key.DirectionDown, Key.Enter))

		assertEquals(listOf(20, 30), component.viewed)
	}

	@Test
	fun `Enter opens the note under the cursor`() = runComposeUiTest {
		val component = FakeBrowseNotes(notes)
		show(component)

		keys(listOf(Key.DirectionDown, Key.Enter))

		assertEquals(listOf(10), component.viewed)
	}

	@Test
	fun `Enter with no cursor opens nothing`() = runComposeUiTest {
		val component = FakeBrowseNotes(notes)
		show(component)

		keys(listOf(Key.Enter))

		assertEquals(emptyList(), component.viewed)
	}

	@Test
	fun `the cursor stays on a visible note after filtering`() = runComposeUiTest {
		val component = FakeBrowseNotes(notes)
		show(component)
		keys(listOf(Key.MoveEnd))

		runOnIdle { component.showOnly(setOf(10, 20)) }
		waitForIdle()
		keys(listOf(Key.Enter))

		assertEquals(listOf(20), component.viewed)
	}

	@Test
	fun `the cursor keeps its note when the sort changes`() = runComposeUiTest {
		val component = FakeBrowseNotes(notes)
		show(component)
		keys(listOf(Key.DirectionDown))

		onNodeWithText("SORT").performClick()
		onNodeWithText("Oldest").performClick()
		waitForIdle()
		keys(listOf(Key.Enter))

		assertEquals(listOf(10), component.viewed)
	}

	@Test
	fun `Down in two lanes moves to the note below`() = runComposeUiTest {
		val component = FakeBrowseNotes(notes)
		show(component, width = 900.dp)

		keys(listOf(Key.DirectionDown, Key.DirectionDown, Key.Enter))

		assertEquals(listOf(30), component.viewed)
	}

	@Test
	fun `clicking a note puts the cursor on it and focuses the grid`() = runComposeUiTest {
		val component = FakeBrowseNotes(notes)
		show(component)

		onNodeWithText("Bridge", substring = true).performMouseInput { click() }
		waitForIdle()
		onNodeWithTag(NOTES_GRID_TAG).assertIsFocused()
		onNodeWithTag(NOTES_GRID_TAG).performKeyInput { pressKey(Key.DirectionDown) }
		waitForIdle()
		onNode(
			SemanticsMatcher.keyIsDefined(ListCursorFrame) and hasAnyDescendant(hasText("Cellar", substring = true)),
			useUnmergedTree = true,
		).assertExists()
		onNodeWithTag(NOTES_GRID_TAG).performKeyInput { pressKey(Key.Enter) }
		waitForIdle()

		assertEquals(listOf(20, 30), component.viewed)
	}

	@Test
	fun `listCursorStep clamps at both ends`() {
		val oneStep = GridRowStep { current, _, down -> if (down) current + 1 else current - 1 }
		assertEquals(0, listCursorStep(0, 3, Key.DirectionUp))
		assertEquals(2, listCursorStep(2, 3, Key.MoveEnd))
		assertEquals(2, listCursorStep(2, 3, Key.DirectionDown))
		assertEquals(0, listCursorStep(-1, 3, Key.DirectionDown))
		assertEquals(1, listCursorStep(0, 3, Key.DirectionRight, oneStep))
		assertEquals(2, listCursorStep(2, 3, Key.DirectionDown, oneStep))
		assertNull(listCursorStep(0, 3, Key.DirectionRight))
		assertNull(listCursorStep(0, 3, Key.Tab))
		assertNull(listCursorStep(-1, 0, Key.DirectionDown))
	}
}
