package com.darkrockstudios.apps.hammer.common.storyeditor.scenelist.scenetree

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.common.data.MoveRequest
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.data.SceneSummary
import com.darkrockstudios.apps.hammer.common.data.tree.Tree
import com.darkrockstudios.apps.hammer.common.data.tree.TreeNode
import kotlinx.collections.immutable.persistentSetOf
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

private const val TREE_TAG = "tree"
private val RowHeight = 30.dp
private fun rowTag(id: Int) = "row-$id"

/**
 * Tree shape, mirroring the report in issue #837:
 *
 * 0 Root
 * ├─ 1 Preface
 * ├─ 2 Chapter 1 ─ 3 The Door Opens, 4 The Discovery
 * ├─ 5 Chapter 2 ─ 6 The Vacuum, 7 Exploration
 * ├─ 8 Chapter 3 ─ 9 More Cameras, 10 Verification, 11 Mouse Trap
 * ├─ 12 Chapter 4 ─ 13 Wildlife Expert
 * ├─ 14 Chapter 5 (empty)
 * └─ 15 Water, 16 Ghost Child, 17 Skeleton, 18 Bowl of Water, 19 Overview
 */
private object Ids {
	const val PREFACE = 1
	const val CH1 = 2
	const val DOOR_OPENS = 3
	const val DISCOVERY = 4
	const val CH2 = 5
	const val VACUUM = 6
	const val EXPLORATION = 7
	const val CH3 = 8
	const val MORE_CAMERAS = 9
	const val VERIFICATION = 10
	const val MOUSE_TRAP = 11
	const val CH4 = 12
	const val WILDLIFE = 13
	const val CH5 = 14
	const val WATER = 15
	const val GHOST_CHILD = 16
	const val SKELETON = 17
	const val BOWL = 18
	const val OVERVIEW = 19
}

class SceneTreeDragTest {

	@get:Rule
	val compose = createComposeRule()

	private var moveRequest: MoveRequest? = null
	private lateinit var treeState: SceneTreeState
	private var summary by mutableStateOf(buildSummary(camerasAtRoot = false))

	private fun showTree() {
		compose.setContent {
			val state = rememberReorderableLazyListState(
				summary = summary,
				moveItem = { moveRequest = it },
			)
			treeState = state
			LaunchedEffect(summary) { state.updateSummary(summary) }

			Box(modifier = Modifier.size(400.dp, 800.dp)) {
				SceneTree(
					state = state,
					modifier = Modifier.fillMaxSize().testTag(TREE_TAG),
					itemUi = { node, _, _, draggable ->
						Box(
							modifier = draggable.fillMaxWidth().height(RowHeight)
								.testTag(rowTag(node.value.id))
						)
					},
					contentPadding = PaddingValues(),
				)
			}
		}
	}

	/** Where the row actually sits on screen, which is where the user aims. */
	private fun visualCenterOf(id: Int): Offset {
		val bounds = compose.onNodeWithTag(rowTag(id)).getBoundsInRoot()
		return with(compose.density) {
			Offset(50f, (bounds.top + (bounds.bottom - bounds.top) / 2).toPx())
		}
	}

	/** The tree is taller than its rows, so this lands on the list but on no row. */
	private fun emptySpaceBelowRows(): Offset {
		val lastRow = compose.onNodeWithTag(rowTag(Ids.OVERVIEW)).getBoundsInRoot()
		return with(compose.density) { Offset(50f, (lastRow.bottom + 20.dp).toPx()) }
	}

	private fun longPressAt(offset: Offset) {
		compose.onNodeWithTag(TREE_TAG).performMouseInput { moveTo(offset); press() }
		compose.mainClock.advanceTimeBy(1_000)
	}

	@Test
	fun `long pressing a row grabs that row`() {
		showTree()

		longPressAt(visualCenterOf(Ids.MORE_CAMERAS))

		assertEquals(Ids.MORE_CAMERAS, treeState.selectedId)
	}

	@Test
	fun `long pressing a row grabs that row right after the tree changed`() {
		showTree()
		compose.mainClock.autoAdvance = false

		// A previous move landed and the list re-flowed. Every row must still be grabbable
		// where it is drawn, not where a settling animation is heading.
		summary = buildSummary(camerasAtRoot = true)
		compose.mainClock.advanceTimeByFrame()
		compose.mainClock.advanceTimeByFrame()

		longPressAt(visualCenterOf(Ids.VERIFICATION))

		assertEquals(Ids.VERIFICATION, treeState.selectedId, "Grabbed the wrong row")
	}

	@Test
	fun `dropping onto a settling list anchors to the row the user sees`() {
		showTree()
		compose.mainClock.autoAdvance = false

		compose.onNodeWithTag(TREE_TAG)
			.performMouseInput { moveTo(visualCenterOf(Ids.PREFACE)); press() }
		compose.mainClock.advanceTimeBy(1_000)

		// A sync re-flows the list while the drag is already under way.
		summary = buildSummary(camerasAtRoot = true)
		compose.mainClock.advanceTimeByFrame()
		compose.mainClock.advanceTimeByFrame()

		compose.onNodeWithTag(TREE_TAG)
			.performMouseInput { moveTo(visualCenterOf(Ids.VERIFICATION)) }
		compose.mainClock.advanceTimeByFrame()
		compose.onNodeWithTag(TREE_TAG).performMouseInput { release() }
		compose.mainClock.advanceTimeBy(1_000)

		val request = moveRequest
		assertNotNull(request, "No move was requested")
		assertEquals(
			Ids.VERIFICATION,
			treeState.getTree()[request.toPosition.coords.globalIndex].value.id,
			"Anchored to a row's destination instead of where it is drawn",
		)
	}

	@Test
	fun `a row disposed while pressed does not stay grabbable`() {
		showTree()

		// Press a row, then have it deleted out from under the finger.
		compose.onNodeWithTag(TREE_TAG)
			.performMouseInput { moveTo(visualCenterOf(Ids.MORE_CAMERAS)); press() }
		summary = buildSummary(camerasAtRoot = false, keepCameras = false)
		compose.mainClock.advanceTimeBy(1_000)
		compose.onNodeWithTag(TREE_TAG).performMouseInput { release() }

		longPressAt(emptySpaceBelowRows())

		assertEquals(
			SceneTreeState.NO_SELECTION,
			treeState.selectedId,
			"Empty space grabbed a scene",
		)
	}

	@Test
	fun `a second finger cannot retarget the drag`() {
		showTree()

		compose.onNodeWithTag(TREE_TAG).performTouchInput {
			down(0, visualCenterOf(Ids.CH3))
			down(1, visualCenterOf(Ids.OVERVIEW))
		}
		compose.mainClock.advanceTimeBy(1_000)

		assertEquals(Ids.CH3, treeState.selectedId, "The second finger retargeted the drag")
	}

	@Test
	fun `dragging a scene to the bottom of the list moves the scene that was grabbed`() {
		showTree()

		longPressAt(visualCenterOf(Ids.MORE_CAMERAS))
		compose.onNodeWithTag(TREE_TAG)
			.performMouseInput { moveTo(visualCenterOf(Ids.BOWL) + Offset(0f, 10f)) }
		compose.mainClock.advanceTimeBy(16)
		compose.onNodeWithTag(TREE_TAG).performMouseInput { release() }
		compose.waitForIdle()

		val request = moveRequest
		assertNotNull(request, "No move was requested")
		assertEquals(Ids.MORE_CAMERAS, request.id, "Wrong scene was moved")
		assertEquals(Ids.BOWL, treeState.getTree()[request.toPosition.coords.globalIndex].value.id)
		assertEquals(false, request.toPosition.before)
	}
}

/**
 * [camerasAtRoot] models "More Cameras" having been dragged out of Chapter 3 to the root, and
 * [keepCameras] models it having been deleted outright.
 */
private fun buildSummary(camerasAtRoot: Boolean, keepCameras: Boolean = true): SceneSummary {
	val moreCameras = sceneItem(Ids.MORE_CAMERAS, SceneItem.Type.Scene, "More Cameras")
	val chapter3Children = mutableListOf<TreeNode<SceneItem>>(
		sceneNode(sceneItem(Ids.VERIFICATION, SceneItem.Type.Scene, "Verification")),
		sceneNode(sceneItem(Ids.MOUSE_TRAP, SceneItem.Type.Scene, "Mouse Trap")),
	)
	val rootTail = mutableListOf<TreeNode<SceneItem>>()
	if (keepCameras) {
		if (camerasAtRoot) rootTail.add(sceneNode(moreCameras))
		else chapter3Children.add(0, sceneNode(moreCameras))
	}

	val tree = Tree<SceneItem>()
	tree.setRoot(
		sceneNode(
			sceneItem(0, SceneItem.Type.Root, ""),
			sceneNode(sceneItem(Ids.PREFACE, SceneItem.Type.Scene, "Preface")),
			sceneNode(
				sceneItem(Ids.CH1, SceneItem.Type.Group, "Chapter 1"),
				sceneNode(sceneItem(Ids.DOOR_OPENS, SceneItem.Type.Scene, "The Door Opens")),
				sceneNode(sceneItem(Ids.DISCOVERY, SceneItem.Type.Scene, "The Discovery")),
			),
			sceneNode(
				sceneItem(Ids.CH2, SceneItem.Type.Group, "Chapter 2"),
				sceneNode(sceneItem(Ids.VACUUM, SceneItem.Type.Scene, "The Vacuum")),
				sceneNode(sceneItem(Ids.EXPLORATION, SceneItem.Type.Scene, "Exploration")),
			),
			sceneNode(
				sceneItem(Ids.CH3, SceneItem.Type.Group, "Chapter 3"),
				*chapter3Children.toTypedArray(),
			),
			sceneNode(
				sceneItem(Ids.CH4, SceneItem.Type.Group, "Chapter 4"),
				sceneNode(sceneItem(Ids.WILDLIFE, SceneItem.Type.Scene, "Wildlife Expert")),
			),
			sceneNode(sceneItem(Ids.CH5, SceneItem.Type.Group, "Chapter 5")),
			sceneNode(sceneItem(Ids.WATER, SceneItem.Type.Scene, "Water, Apple, Insects")),
			sceneNode(sceneItem(Ids.GHOST_CHILD, SceneItem.Type.Scene, "The Ghost Child")),
			sceneNode(sceneItem(Ids.SKELETON, SceneItem.Type.Scene, "A black skeleton")),
			sceneNode(sceneItem(Ids.BOWL, SceneItem.Type.Scene, "A bowl of water")),
			*rootTail.toTypedArray(),
			sceneNode(sceneItem(Ids.OVERVIEW, SceneItem.Type.Scene, "Overview")),
		)
	)
	return SceneSummary(
		sceneTree = tree.toImmutableTree(),
		hasDirtyBuffer = persistentSetOf(),
	)
}
