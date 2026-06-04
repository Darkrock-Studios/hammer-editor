import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.darkrockstudios.apps.hammer.android.ProjectRootActivity
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.projectroot.NAV_EDITOR_TAG
import com.darkrockstudios.apps.hammer.common.storyeditor.scenelist.CREATE_ITEM_NAME_FIELD_TAG
import com.darkrockstudios.apps.hammer.common.storyeditor.scenelist.SCENE_LIST_ADD_BUTTON_TAG
import com.darkrockstudios.apps.hammer.common.storyeditor.scenelist.SCENE_LIST_ADD_GROUP_TAG
import com.darkrockstudios.apps.hammer.common.storyeditor.scenelist.SCENE_LIST_ADD_SCENE_TAG
import com.darkrockstudios.apps.hammer.common.storyeditor.sceneeditor.SCENE_EDITOR_TEXT_TAG
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Scene-list happy path: create a group (stays in the list) and a scene (auto-opens the editor),
 * exercising the add menu + create dialog for both.
 */
@RunWith(AndroidJUnit4::class)
class SceneListWorkflowTest {

	@get:Rule
	val composeRule = createEmptyComposeRule()

	private lateinit var projectDef: ProjectDef
	private lateinit var scenario: ActivityScenario<ProjectRootActivity>

	@Before
	fun setup() {
		projectDef = EditorTestHarness.seedProject("E2E SceneList")
		scenario = EditorTestHarness.launchEditor(projectDef)
	}

	@Test
	fun createGroupAndSceneFromAddMenu() {
		composeRule.navigateTo(NAV_EDITOR_TAG)

		// A group has no editor, so it lands in the tree and the list stays put.
		addItem(SCENE_LIST_ADD_GROUP_TAG, "Act One")
		composeRule.waitUntil(10_000) {
			composeRule.onAllNodes(hasTestTagPrefix("scene-group-")).fetchSemanticsNodes().isNotEmpty()
		}
		composeRule.onAllNodes(hasTestTagPrefix("scene-group-")).onFirst().assertIsDisplayed()

		// Creating a scene auto-selects it and opens the editor.
		addItem(SCENE_LIST_ADD_SCENE_TAG, "Chapter One")
		composeRule.waitUntil(10_000) {
			composeRule.onAllNodesWithTag(SCENE_EDITOR_TEXT_TAG).fetchSemanticsNodes().isNotEmpty()
		}
		composeRule.onNodeWithTag(SCENE_EDITOR_TEXT_TAG).assertIsDisplayed()
	}

	// Open the add menu, pick scene/group, type a name, submit via the IME action.
	private fun addItem(menuItemTag: String, name: String) {
		composeRule.waitUntil(10_000) {
			composeRule.onAllNodesWithTag(SCENE_LIST_ADD_BUTTON_TAG).fetchSemanticsNodes().isNotEmpty()
		}
		composeRule.onNodeWithTag(SCENE_LIST_ADD_BUTTON_TAG).performClick()
		composeRule.onNodeWithTag(menuItemTag).performClick()
		composeRule.waitUntil(10_000) {
			composeRule.onAllNodesWithTag(CREATE_ITEM_NAME_FIELD_TAG).fetchSemanticsNodes().isNotEmpty()
		}
		composeRule.onNodeWithTag(CREATE_ITEM_NAME_FIELD_TAG).performTextInput(name)
		composeRule.onNodeWithTag(CREATE_ITEM_NAME_FIELD_TAG).performImeAction()
		composeRule.waitUntil(10_000) {
			composeRule.onAllNodesWithTag(CREATE_ITEM_NAME_FIELD_TAG).fetchSemanticsNodes().isEmpty()
		}
	}

	@After
	fun tearDown() {
		EditorTestHarness.teardown(scenario, projectDef)
	}
}
