package com.darkrockstudios.apps.hammer.common.storyeditor.scenelist.scenetree

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyPress
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.common.components.actions.ActionRegistry
import com.darkrockstudios.apps.hammer.common.compose.actions.EscapeRouter
import com.darkrockstudios.apps.hammer.common.compose.actions.ProvideActions
import com.darkrockstudios.apps.hammer.common.compose.focus.ListCursorFrame
import com.darkrockstudios.apps.hammer.common.compose.focus.LocalPaneFocus
import com.darkrockstudios.apps.hammer.common.compose.focus.PaneId
import com.darkrockstudios.apps.hammer.common.compose.focus.ProvidePaneFocus
import com.darkrockstudios.apps.hammer.common.compose.focus.escapeToPane
import com.darkrockstudios.apps.hammer.common.compose.focus.focusPane
import com.darkrockstudios.apps.hammer.common.compose.focus.paneEntry
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.data.SceneSummary
import com.darkrockstudios.apps.hammer.common.data.tree.Tree
import kotlinx.collections.immutable.persistentSetOf
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

private const val TREE = "tree"
private const val EDITOR = "editor"
private fun treeRow(id: Int) = "row-$id"

private const val CHAPTER = 1
private const val PART = 2
private const val OPEN_SCENE = 3
private const val OTHER_SCENE = 4

@OptIn(ExperimentalTestApi::class, InternalComposeUiApi::class)
class SceneTreeEscapeTest {

	private lateinit var treeState: SceneTreeState
	private val router = EscapeRouter(ActionRegistry()) { true }

	/** Story's multi-pane layout: the tree beside an open scene's editor, under the window's Esc. */
	private fun ComposeUiTest.showStory() {
		setContent {
			ProvideActions(ActionRegistry()) {
				ProvidePaneFocus {
					val panes = LocalPaneFocus.current!!
					Box(Modifier.onPreviewKeyEvent(router::onPreviewKeyEvent).onKeyEvent(router::onKeyEvent)) {
						Row {
							Box(Modifier.size(300.dp, 400.dp).focusPane(PaneId.List)) {
								val state = rememberReorderableLazyListState(summary = summary(), moveItem = {})
								treeState = state
								SceneTree(
									state = state,
									modifier = Modifier.fillMaxSize().testTag(TREE).paneEntry(PaneId.List),
									itemUi = { node, _, _, draggable ->
										Box(draggable.fillMaxWidth().height(30.dp).testTag(treeRow(node.value.id)))
									},
									contentPadding = PaddingValues(),
									openSceneId = OPEN_SCENE,
								)
							}
							Box(Modifier.size(300.dp, 400.dp).escapeToPane { panes.escapeToList() }.focusPane(PaneId.Detail)) {
								var text by remember { mutableStateOf("Once upon a time") }
								BasicTextField(text, { text = it }, Modifier.size(200.dp, 40.dp).testTag(EDITOR))
							}
						}
					}
				}
			}
		}
		waitForIdle()
	}

	private val framed = SemanticsMatcher.keyIsDefined(ListCursorFrame)

	@Test
	fun `Esc from the editor shows the tree cursor on the open scene inside collapsed groups`() = runComposeUiTest {
		showStory()
		runOnIdle {
			treeState.toggleExpanded(PART)
			treeState.toggleExpanded(CHAPTER)
		}
		onNodeWithTag(EDITOR).performMouseInput { click() }
		waitForIdle()

		onNodeWithTag(EDITOR).performKeyPress(KeyEvent(Key.Escape, KeyEventType.KeyDown))
		onNodeWithTag(EDITOR).performKeyPress(KeyEvent(Key.Escape, KeyEventType.KeyUp))
		waitForIdle()

		onNodeWithTag(TREE).assertIsFocused()
		assertEquals(OPEN_SCENE, runOnIdle { treeState.cursorId })
		assertEquals(false, runOnIdle { treeState.collapsedNodes[CHAPTER] == true || treeState.collapsedNodes[PART] == true })
		onAllNodes(framed, useUnmergedTree = true).assertCountEquals(1)
		onNode(framed and hasAnyDescendant(hasTestTag(treeRow(OPEN_SCENE))), useUnmergedTree = true).assertExists()
	}
}

/**
 * 0 Root
 * ├─ 1 Chapter ─ 2 Part ─ 3 Open scene
 * └─ 4 Other scene
 */
private fun summary(): SceneSummary {
	val tree = Tree<SceneItem>()
	tree.setRoot(
		sceneNode(
			sceneItem(0, SceneItem.Type.Root, ""),
			sceneNode(
				sceneItem(CHAPTER, SceneItem.Type.Group, "Chapter"),
				sceneNode(
					sceneItem(PART, SceneItem.Type.Group, "Part"),
					sceneNode(sceneItem(OPEN_SCENE, SceneItem.Type.Scene, "Open scene")),
				),
			),
			sceneNode(sceneItem(OTHER_SCENE, SceneItem.Type.Scene, "Other scene")),
		)
	)
	return SceneSummary(sceneTree = tree.toImmutableTree(), hasDirtyBuffer = persistentSetOf())
}
