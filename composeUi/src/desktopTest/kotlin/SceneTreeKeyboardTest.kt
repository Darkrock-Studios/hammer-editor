package com.darkrockstudios.apps.hammer.common.storyeditor.scenelist.scenetree

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.common.compose.focus.ListCursorFrame
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.data.SceneSummary
import com.darkrockstudios.apps.hammer.common.data.tree.Tree
import kotlinx.collections.immutable.persistentSetOf
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

private const val TREE = "tree"
private const val EDITOR = "editor"
private fun row(id: Int) = "row-$id"

/**
 * 0 Root
 * ├─ 1 Preface
 * ├─ 2 Chapter 1 ─ 3 The Door Opens, 4 The Discovery
 * ├─ 5 Chapter 2 ─ 6 Part A ─ 7 The Vacuum
 * │              └ 8 Exploration
 * └─ 9 Overview
 */
private object KeyIds {
	const val PREFACE = 1
	const val CH1 = 2
	const val DOOR = 3
	const val DISCOVERY = 4
	const val CH2 = 5
	const val PART_A = 6
	const val VACUUM = 7
	const val EXPLORATION = 8
	const val OVERVIEW = 9
}

@OptIn(ExperimentalTestApi::class)
class SceneTreeKeyboardTest {

	private lateinit var treeState: SceneTreeState
	private val opened = mutableListOf<Int>()
	private var openScene by mutableStateOf<Int?>(null)

	private fun ComposeUiTest.showTree(height: Dp = 600.dp, withEditor: Boolean = false) {
		setContent {
			val state = rememberReorderableLazyListState(summary = summary(), moveItem = {})
			treeState = state
			Column(modifier = Modifier.size(400.dp, height)) {
				if (withEditor) {
					var text by remember { mutableStateOf("Once upon a time") }
					BasicTextField(
						value = text,
						onValueChange = { text = it },
						modifier = Modifier.fillMaxWidth().height(40.dp).testTag(EDITOR),
					)
				}
				Box(modifier = Modifier.weight(1f)) {
					SceneTree(
						state = state,
						modifier = Modifier.fillMaxSize().testTag(TREE),
						itemUi = { node, _, _, draggable ->
							Box(modifier = draggable.fillMaxWidth().height(30.dp).testTag(row(node.value.id)))
						},
						contentPadding = PaddingValues(),
						onOpen = { opened += it.id },
						openSceneId = openScene,
					)
				}
			}
		}
		waitForIdle()
	}

	private fun ComposeUiTest.keys(keys: List<Key>) {
		onNodeWithTag(TREE).requestFocus()
		onNodeWithTag(TREE).performKeyInput { keys.forEach { pressKey(it) } }
		waitForIdle()
	}

	private fun ComposeUiTest.cursor(): Int = runOnIdle { treeState.cursorId }

	private fun ComposeUiTest.isCollapsed(id: Int): Boolean = runOnIdle { treeState.collapsedNodes[id] == true }

	private val framed = SemanticsMatcher.keyIsDefined(ListCursorFrame)

	private fun ComposeUiTest.assertFrameOn(id: Int) {
		onAllNodes(framed, useUnmergedTree = true).assertCountEquals(1)
		onNode(framed and hasAnyDescendant(hasTestTag(row(id))), useUnmergedTree = true).assertExists()
	}

	private fun ComposeUiTest.clickRow(id: Int) {
		onNodeWithTag(row(id)).performMouseInput { click() }
		waitForIdle()
	}

	@Test
	fun `Down and Up move the cursor over visible rows`() = runComposeUiTest {
		showTree()
		runOnIdle { treeState.toggleExpanded(KeyIds.CH1) }

		keys(listOf(Key.DirectionDown))
		val second = cursor()
		keys(listOf(Key.DirectionDown))
		val third = cursor()
		keys(listOf(Key.DirectionUp))

		assertEquals(KeyIds.CH1, second)
		assertEquals(KeyIds.CH2, third, "Collapsed rows are skipped")
		assertEquals(KeyIds.CH1, cursor())
	}

	@Test
	fun `Right expands a collapsed group then enters it`() = runComposeUiTest {
		showTree()
		runOnIdle { treeState.toggleExpanded(KeyIds.CH1) }
		keys(listOf(Key.DirectionDown))

		keys(listOf(Key.DirectionRight))
		val expanded = !isCollapsed(KeyIds.CH1)
		val stayed = cursor()
		keys(listOf(Key.DirectionRight))

		assertTrue(expanded)
		assertEquals(KeyIds.CH1, stayed)
		assertEquals(KeyIds.DOOR, cursor())
	}

	@Test
	fun `Left collapses then climbs to the parent`() = runComposeUiTest {
		showTree()
		keys(listOf(Key.MoveEnd, Key.DirectionUp, Key.DirectionUp, Key.DirectionUp))
		val start = cursor()

		keys(listOf(Key.DirectionLeft))
		val collapsed = isCollapsed(KeyIds.PART_A)
		keys(listOf(Key.DirectionLeft))
		val parent = cursor()
		keys(listOf(Key.DirectionLeft, Key.DirectionLeft))

		assertEquals(KeyIds.PART_A, start)
		assertTrue(collapsed)
		assertEquals(KeyIds.CH2, parent)
		assertTrue(isCollapsed(KeyIds.CH2))
		assertEquals(KeyIds.CH2, cursor(), "A chapter has no parent to climb to")
	}

	@Test
	fun `Home and End jump to the ends`() = runComposeUiTest {
		showTree()

		keys(listOf(Key.MoveEnd))
		val end = cursor()
		keys(listOf(Key.MoveHome))

		assertEquals(KeyIds.OVERVIEW, end)
		assertEquals(KeyIds.PREFACE, cursor())
	}

	@Test
	fun `Enter on a scene opens it`() = runComposeUiTest {
		showTree()

		keys(listOf(Key.DirectionDown, Key.DirectionDown, Key.Enter))

		assertEquals(listOf(KeyIds.DOOR), opened)
	}

	@Test
	fun `Enter on a group toggles it`() = runComposeUiTest {
		showTree()
		keys(listOf(Key.DirectionDown))

		keys(listOf(Key.Enter))
		val collapsed = isCollapsed(KeyIds.CH1)
		keys(listOf(Key.Enter))

		assertTrue(collapsed)
		assertTrue(!isCollapsed(KeyIds.CH1))
		assertEquals(emptyList(), opened)
	}

	@Test
	fun `reveal expands the ancestors and puts the cursor on the scene`() = runComposeUiTest {
		showTree()
		runOnIdle {
			treeState.toggleExpanded(KeyIds.PART_A)
			treeState.toggleExpanded(KeyIds.CH2)
		}

		runOnIdle { treeState.revealCursor(KeyIds.VACUUM) }
		waitForIdle()

		assertTrue(!isCollapsed(KeyIds.CH2))
		assertTrue(!isCollapsed(KeyIds.PART_A))
		assertEquals(KeyIds.VACUUM, cursor())
		onNodeWithTag(row(KeyIds.VACUUM)).assertIsDisplayed()
	}

	@Test
	fun `reveal leaves a group it lands on collapsed`() = runComposeUiTest {
		showTree()
		runOnIdle {
			treeState.toggleExpanded(KeyIds.PART_A)
			treeState.toggleExpanded(KeyIds.CH2)
		}

		runOnIdle { treeState.revealCursor(KeyIds.PART_A) }
		waitForIdle()

		assertTrue(!isCollapsed(KeyIds.CH2))
		assertTrue(isCollapsed(KeyIds.PART_A))
		assertEquals(KeyIds.PART_A, cursor())
	}

	@Test
	fun `focusing the tree starts the cursor on the open scene`() = runComposeUiTest {
		openScene = KeyIds.EXPLORATION
		showTree()

		keys(emptyList())
		val start = cursor()
		keys(listOf(Key.DirectionDown))

		assertEquals(KeyIds.EXPLORATION, start)
		assertEquals(KeyIds.OVERVIEW, cursor())
	}

	@Test
	fun `focusing the tree with no open scene puts the cursor on the first row`() = runComposeUiTest {
		showTree()

		keys(emptyList())

		assertEquals(KeyIds.PREFACE, cursor())
		assertFrameOn(KeyIds.PREFACE)
	}

	@Test
	fun `focusing the tree frames the open scene`() = runComposeUiTest {
		openScene = KeyIds.EXPLORATION
		showTree()

		keys(emptyList())

		assertFrameOn(KeyIds.EXPLORATION)
	}

	@Test
	fun `an open scene in a collapsed group frames the group and leaves it collapsed`() = runComposeUiTest {
		openScene = KeyIds.VACUUM
		showTree()
		runOnIdle { treeState.toggleExpanded(KeyIds.PART_A) }

		keys(emptyList())

		assertTrue(isCollapsed(KeyIds.PART_A))
		assertFrameOn(KeyIds.PART_A)
	}

	@Test
	fun `the frame marks a group row`() = runComposeUiTest {
		showTree()

		keys(listOf(Key.DirectionDown))

		assertFrameOn(KeyIds.CH1)
	}

	@Test
	fun `the frame goes when the tree loses focus`() = runComposeUiTest {
		showTree(withEditor = true)
		keys(emptyList())

		onNodeWithTag(EDITOR).requestFocus()
		waitForIdle()

		onAllNodes(framed, useUnmergedTree = true).assertCountEquals(0)
	}

	@Test
	fun `clicking a row focuses the tree and puts the cursor on it`() = runComposeUiTest {
		showTree(withEditor = true)
		onNodeWithTag(EDITOR).requestFocus()

		clickRow(KeyIds.DOOR)
		onNodeWithTag(TREE).assertIsFocused()
		val clicked = cursor()
		onNodeWithTag(TREE).performKeyInput { pressKey(Key.DirectionDown) }
		waitForIdle()

		assertEquals(KeyIds.DOOR, clicked)
		assertEquals(KeyIds.DISCOVERY, cursor())
		assertFrameOn(KeyIds.DISCOVERY)
	}

	@Test
	fun `after a click the frame waits for a key`() = runComposeUiTest {
		showTree()

		clickRow(KeyIds.DOOR)
		val framesAfterClick = onAllNodes(framed, useUnmergedTree = true).fetchSemanticsNodes().size
		onNodeWithTag(TREE).performKeyInput { pressKey(Key.DirectionUp) }
		waitForIdle()

		assertEquals(0, framesAfterClick, "A mouse click puts Compose in touch mode")
		assertFrameOn(KeyIds.CH1)
	}

	@Test
	fun `clicking a group keeps the cursor on it after a scene opened elsewhere`() = runComposeUiTest {
		showTree(withEditor = true)
		onNodeWithTag(EDITOR).requestFocus()
		openScene = KeyIds.VACUUM
		waitForIdle()

		clickRow(KeyIds.CH1)
		val clicked = cursor()
		onNodeWithTag(TREE).performKeyInput { pressKey(Key.DirectionDown) }
		waitForIdle()

		assertEquals(KeyIds.CH1, clicked)
		assertEquals(KeyIds.DOOR, cursor())
	}

	@Test
	fun `the cursor moves to a scene opened since it last moved`() = runComposeUiTest {
		showTree(withEditor = true)
		keys(listOf(Key.DirectionDown))
		onNodeWithTag(EDITOR).requestFocus()

		openScene = KeyIds.DOOR
		waitForIdle()
		keys(emptyList())

		assertEquals(KeyIds.DOOR, cursor())
	}

	@Test
	fun `the cursor stays where it was moved while the open scene is unchanged`() = runComposeUiTest {
		openScene = KeyIds.DOOR
		showTree(withEditor = true)
		keys(listOf(Key.DirectionDown))
		onNodeWithTag(EDITOR).requestFocus()
		waitForIdle()

		keys(emptyList())

		assertEquals(KeyIds.DISCOVERY, cursor())
	}

	@Test
	fun `the cursor row scrolls into view`() = runComposeUiTest {
		showTree(height = 90.dp)

		keys(listOf(Key.MoveEnd))

		val last = runOnIdle {
			val info = treeState.listState.layoutInfo
			info.visibleItemsInfo.last().let { it.index to (it.offset + it.size <= info.viewportEndOffset) }
		}
		assertEquals(treeState.visibleNodes.lastIndex to true, last)
	}

	@Test
	fun `arrows in the editor do not move the tree cursor`() = runComposeUiTest {
		showTree(withEditor = true)
		keys(listOf(Key.DirectionDown))
		val before = cursor()

		onNodeWithTag(EDITOR).requestFocus()
		onNodeWithTag(EDITOR).performKeyInput {
			pressKey(Key.DirectionDown)
			pressKey(Key.DirectionDown)
			pressKey(Key.MoveEnd)
		}
		waitForIdle()

		assertEquals(KeyIds.CH1, before)
		assertEquals(before, cursor())
		assertNotEquals(KeyIds.OVERVIEW, cursor())
	}
}

private fun summary(): SceneSummary {
	val tree = Tree<SceneItem>()
	tree.setRoot(
		sceneNode(
			sceneItem(0, SceneItem.Type.Root, ""),
			sceneNode(sceneItem(KeyIds.PREFACE, SceneItem.Type.Scene, "Preface")),
			sceneNode(
				sceneItem(KeyIds.CH1, SceneItem.Type.Group, "Chapter 1"),
				sceneNode(sceneItem(KeyIds.DOOR, SceneItem.Type.Scene, "The Door Opens")),
				sceneNode(sceneItem(KeyIds.DISCOVERY, SceneItem.Type.Scene, "The Discovery")),
			),
			sceneNode(
				sceneItem(KeyIds.CH2, SceneItem.Type.Group, "Chapter 2"),
				sceneNode(
					sceneItem(KeyIds.PART_A, SceneItem.Type.Group, "Part A"),
					sceneNode(sceneItem(KeyIds.VACUUM, SceneItem.Type.Scene, "The Vacuum")),
				),
				sceneNode(sceneItem(KeyIds.EXPLORATION, SceneItem.Type.Scene, "Exploration")),
			),
			sceneNode(sceneItem(KeyIds.OVERVIEW, SceneItem.Type.Scene, "Overview")),
		)
	)
	return SceneSummary(sceneTree = tree.toImmutableTree(), hasDirtyBuffer = persistentSetOf())
}
