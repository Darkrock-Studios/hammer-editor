import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.getAndUpdate
import com.darkrockstudios.apps.hammer.common.components.storyeditor.scenelist.SceneList
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HD_COACH_MARK_TAG
import com.darkrockstudios.apps.hammer.common.compose.rememberRootSnackbarHostState
import com.darkrockstudios.apps.hammer.common.data.MoveRequest
import com.darkrockstudios.apps.hammer.common.data.SceneBuffer
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.data.SceneSummary
import com.darkrockstudios.apps.hammer.common.data.nux.NuxHint
import com.darkrockstudios.apps.hammer.common.data.tree.Tree
import com.darkrockstudios.apps.hammer.common.data.tree.TreeNode
import com.darkrockstudios.apps.hammer.common.preview.KoinApplicationPreview
import com.darkrockstudios.apps.hammer.common.preview.fakeProjectDef
import com.darkrockstudios.apps.hammer.common.storyeditor.scenelist.SCENE_LIST_TREE_TAG
import com.darkrockstudios.apps.hammer.common.storyeditor.scenelist.SceneListUi
import com.darkrockstudios.apps.hammer.common.storyeditor.scenelist.sceneGroupTag
import com.darkrockstudios.apps.hammer.common.storyeditor.scenelist.sceneItemTag
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val CHAPTER_ID = 1
private const val SCENE_ID = 2
private const val PANE_TAG = "tips-pane"

class SceneListTipsTest : BaseTest() {

	@get:Rule
	val compose = createComposeRule()

	private var paneHeight by mutableStateOf(600.dp)

	private fun showSceneList(component: TipsFakeSceneList) {
		compose.setContent {
			KoinApplicationPreview {
				Box(modifier = Modifier.testTag(PANE_TAG).size(400.dp, paneHeight)) {
					SceneListUi(
						component = component,
						snackbarHostState = rememberRootSnackbarHostState(),
					)
				}
			}
		}
	}

	@Test
	fun `no tip is drawn while none is offered`() {
		showSceneList(TipsFakeSceneList(offered = emptyList()))

		compose.onNodeWithTag(HD_COACH_MARK_TAG).assertDoesNotExist()
	}

	@Test
	fun `the reorder tip points at the first row`() {
		showSceneList(TipsFakeSceneList(offered = listOf(NuxHint.SceneListReorder)))

		val chapter = compose.onNodeWithTag(sceneGroupTag(CHAPTER_ID)).getBoundsInRoot()
		val scene = compose.onNodeWithTag(sceneItemTag(SCENE_ID)).getBoundsInRoot()
		val callout = compose.onNodeWithTag(HD_COACH_MARK_TAG).assertIsDisplayed().getBoundsInRoot()

		compose.onNodeWithText("Drag to reorder").assertIsDisplayed()
		assertTrue(callout.top >= chapter.bottom)
		assertTrue(callout.top < scene.bottom)
	}

	@Test
	fun `the swipe tip points at a scene, not a chapter group`() {
		showSceneList(TipsFakeSceneList(offered = listOf(NuxHint.SceneListSwipeMenu, NuxHint.SceneListReorder)))

		val scene = compose.onNodeWithTag(sceneItemTag(SCENE_ID)).getBoundsInRoot()
		val callout = compose.onNodeWithTag(HD_COACH_MARK_TAG).assertIsDisplayed().getBoundsInRoot()

		compose.onNodeWithText("Swipe for options").assertIsDisplayed()
		assertTrue(callout.top >= scene.bottom)
	}

	@Test
	fun `the right-click tip points at a scene, not a chapter group`() {
		showSceneList(TipsFakeSceneList(offered = listOf(NuxHint.SceneListContextMenu, NuxHint.SceneListReorder)))

		val scene = compose.onNodeWithTag(sceneItemTag(SCENE_ID)).getBoundsInRoot()
		val callout = compose.onNodeWithTag(HD_COACH_MARK_TAG).assertIsDisplayed().getBoundsInRoot()

		compose.onNodeWithText("Right-click for options").assertIsDisplayed()
		assertTrue(callout.top >= scene.bottom)
	}

	@Test
	fun `the callout has no tip label`() {
		showSceneList(TipsFakeSceneList(offered = androidTips))

		compose.onNodeWithTag(HD_COACH_MARK_TAG).assertIsDisplayed()
		compose.onNodeWithText("Tip", ignoreCase = true).assertDoesNotExist()
	}

	@Test
	fun `a swipe tip with no scene in view gives way to the reorder tip`() {
		val component = TipsFakeSceneList(offered = androidTips, summary = sceneFarBelowSummary())
		showSceneList(component)

		compose.onNodeWithText("Drag to reorder").assertIsDisplayed()
		compose.onNodeWithText("Got it").performClick()

		assertEquals(listOf(NuxHint.SceneListReorder), component.dismissed)
	}

	@Test
	fun `got it dismisses the tip that was shown`() {
		val component = TipsFakeSceneList(offered = androidTips)
		showSceneList(component)

		compose.onNodeWithText("Got it").performClick()

		compose.onNodeWithTag(HD_COACH_MARK_TAG).assertDoesNotExist()
		assertEquals(listOf(NuxHint.SceneListSwipeMenu), component.dismissed)
	}

	@Test
	fun `turn off tips closes the tip without dismissing it`() {
		val component = TipsFakeSceneList(offered = androidTips)
		showSceneList(component)

		compose.onNodeWithText("Turn off tips").performClick()

		compose.onNodeWithTag(HD_COACH_MARK_TAG).assertDoesNotExist()
		assertTrue(component.tipsTurnedOff)
		assertTrue(component.dismissed.isEmpty())
	}

	@Test
	fun `a row cut off by the bottom of the list is not pointed at`() {
		val component = TipsFakeSceneList(offered = emptyList())
		showSceneList(component)
		shrinkPane { listTop, row -> listTop + row.height / 2 }

		component.offer(listOf(NuxHint.SceneListReorder))
		compose.waitForIdle()

		compose.onNodeWithTag(HD_COACH_MARK_TAG).assertDoesNotExist()
	}

	@Test
	fun `a row under the footer is not pointed at`() {
		val component = TipsFakeSceneList(offered = emptyList())
		showSceneList(component)
		shrinkPane { listTop, row -> listTop + row.height + 8.dp }

		component.offer(listOf(NuxHint.SceneListReorder))
		compose.waitForIdle()

		compose.onNodeWithTag(HD_COACH_MARK_TAG).assertDoesNotExist()
	}

	@Test
	fun `a row near the bottom is pointed at once the footer has scrolled away`() {
		val component = TipsFakeSceneList(offered = emptyList(), summary = chaptersAfterSceneSummary())
		showSceneList(component)
		val pane = compose.onNodeWithTag(PANE_TAG).getBoundsInRoot()
		val scene = compose.onNodeWithTag(sceneItemTag(SCENE_ID)).getBoundsInRoot()
		paneHeight = scene.bottom - pane.top + 2.dp
		compose.waitForIdle()

		// A short drag on the first row with a pause before release: a small scroll down, no fling.
		compose.onNodeWithTag(SCENE_LIST_TREE_TAG).performTouchInput {
			down(Offset(centerX, 30f))
			repeat(4) { moveBy(Offset(0f, -10f)) }
			advanceEventTime(200)
			up()
		}
		compose.waitForIdle()
		component.offer(listOf(NuxHint.SceneListSwipeMenu))
		compose.waitForIdle()

		compose.onNodeWithTag(HD_COACH_MARK_TAG).assertExists()
	}

	/** The tips an Android list offers. */
	private val androidTips = listOf(NuxHint.SceneListSwipeMenu, NuxHint.SceneListReorder)

	/** Resizes the pane to [height], given the list's top and the first row, both relative to the pane. */
	private fun shrinkPane(height: (listTop: Dp, firstRow: DpRect) -> Dp) {
		val pane = compose.onNodeWithTag(PANE_TAG).getBoundsInRoot()
		val listTop = compose.onNodeWithTag(SCENE_LIST_TREE_TAG).getBoundsInRoot().top - pane.top
		val row = compose.onNodeWithTag(sceneGroupTag(CHAPTER_ID)).getBoundsInRoot()
		paneHeight = height(listTop, row)
		compose.waitForIdle()
	}
}

private fun tipsScene(id: Int, name: String, type: SceneItem.Type = SceneItem.Type.Scene) = SceneItem(
	projectDef = fakeProjectDef(),
	type = type,
	id = id,
	name = name,
	order = id,
)

/** A chapter group holding one scene, so the first row is not a scene. */
private fun tipsSummary(): SceneSummary {
	val root = TreeNode(tipsScene(0, "Root", SceneItem.Type.Root))
	root.addChild(
		TreeNode(tipsScene(CHAPTER_ID, "Act One", SceneItem.Type.Group)).apply {
			addChild(TreeNode(tipsScene(SCENE_ID, "Arrival")))
		}
	)
	val tree = Tree<SceneItem>().apply { setRoot(root) }
	return SceneSummary(tree.toImmutableTree(), persistentSetOf())
}

/** The usual chapter and scene, then enough empty chapters for the list to scroll. */
private fun chaptersAfterSceneSummary(): SceneSummary {
	val root = TreeNode(tipsScene(0, "Root", SceneItem.Type.Root))
	root.addChild(
		TreeNode(tipsScene(CHAPTER_ID, "Act One", SceneItem.Type.Group)).apply {
			addChild(TreeNode(tipsScene(SCENE_ID, "Arrival")))
		}
	)
	(3..12).forEach { id -> root.addChild(TreeNode(tipsScene(id, "Chapter $id", SceneItem.Type.Group))) }
	val tree = Tree<SceneItem>().apply { setRoot(root) }
	return SceneSummary(tree.toImmutableTree(), persistentSetOf())
}

/** Enough empty chapter groups ahead of the only scene to push it off screen. */
private fun sceneFarBelowSummary(): SceneSummary {
	val root = TreeNode(tipsScene(0, "Root", SceneItem.Type.Root))
	val emptyChapters = 30
	(1..emptyChapters).forEach { id -> root.addChild(TreeNode(tipsScene(id, "Chapter $id", SceneItem.Type.Group))) }
	root.addChild(
		TreeNode(tipsScene(emptyChapters + 1, "Last", SceneItem.Type.Group)).apply {
			addChild(TreeNode(tipsScene(emptyChapters + 2, "Arrival")))
		}
	)
	val tree = Tree<SceneItem>().apply { setRoot(root) }
	return SceneSummary(tree.toImmutableTree(), persistentSetOf())
}

private class TipsFakeSceneList(
	offered: List<NuxHint>,
	summary: SceneSummary = tipsSummary(),
) : SceneList {
	private val _state = MutableValue(
		SceneList.State(
			projectDef = fakeProjectDef(),
			sceneSummary = summary,
			offeredHints = offered.toImmutableList(),
		)
	)
	override val state: Value<SceneList.State> = _state

	val dismissed = mutableListOf<NuxHint>()
	var tipsTurnedOff = false

	fun offer(hints: List<NuxHint>) {
		_state.getAndUpdate { it.copy(offeredHints = hints.toImmutableList()) }
	}

	override fun dismissHint(hint: NuxHint) {
		dismissed += hint
		_state.getAndUpdate { it.copy(offeredHints = persistentListOf()) }
	}

	override fun turnOffTips() {
		tipsTurnedOff = true
		_state.getAndUpdate { it.copy(offeredHints = persistentListOf()) }
	}

	override fun onSceneSelected(sceneDef: SceneItem) {}
	override suspend fun moveScene(moveRequest: MoveRequest) {}
	override fun loadScenes() {}
	override suspend fun createScene(parent: SceneItem?, sceneName: String) {}
	override suspend fun createGroup(parent: SceneItem?, groupName: String) {}
	override fun beginCreate(kind: SceneList.CreateRequest.Kind, parent: SceneItem?) {}
	override fun dismissCreate() {}
	override suspend fun deleteScene(scene: SceneItem) {}
	override suspend fun renameScene(scene: SceneItem, newName: String): Boolean = false
	override fun onSceneListUpdate(scenes: SceneSummary) {}
	override fun onSceneBufferUpdate(sceneBuffer: SceneBuffer) {}
	override fun showOutlineOverview() {}
	override fun showReaderMode() {}
	override suspend fun archiveScene(scene: SceneItem) {}
	override suspend fun unarchiveScene(scene: SceneItem) {}
	override fun showArchivedScenes() {}
	override fun dismissArchivedDialog() {}
}
