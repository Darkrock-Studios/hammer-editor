import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
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
import com.darkrockstudios.apps.hammer.common.storyeditor.scenelist.SCENE_LIST_ADD_SCENE_TAG
import com.darkrockstudios.apps.hammer.common.storyeditor.sceneeditor.SCENE_EDITOR_SAVE_TAG
import com.darkrockstudios.apps.hammer.common.storyeditor.sceneeditor.SCENE_EDITOR_TEXT_TAG
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Scene-editor happy path: create+select a scene, type into the editor, and save. The save action
 * only appears when the buffer is dirty, so its appearance/disappearance confirms the edit + save.
 */
@RunWith(AndroidJUnit4::class)
class SceneEditorWorkflowTest {

	@get:Rule
	val composeRule = createEmptyComposeRule()

	private lateinit var projectDef: ProjectDef
	private lateinit var scenario: ActivityScenario<ProjectRootActivity>

	@Before
	fun setup() {
		projectDef = EditorTestHarness.seedProject("E2E SceneEditor")
		scenario = EditorTestHarness.launchEditor(projectDef)
	}

	@Test
	fun editSceneTextThenSave() {
		composeRule.navigateTo(NAV_EDITOR_TAG)

		// Create a scene and open it.
		composeRule.waitUntil(10_000) {
			composeRule.onAllNodesWithTag(SCENE_LIST_ADD_BUTTON_TAG).fetchSemanticsNodes().isNotEmpty()
		}
		composeRule.onNodeWithTag(SCENE_LIST_ADD_BUTTON_TAG).performClick()
		composeRule.onNodeWithTag(SCENE_LIST_ADD_SCENE_TAG).performClick()
		composeRule.waitUntil(10_000) {
			composeRule.onAllNodesWithTag(CREATE_ITEM_NAME_FIELD_TAG).fetchSemanticsNodes().isNotEmpty()
		}
		composeRule.onNodeWithTag(CREATE_ITEM_NAME_FIELD_TAG).performTextInput("Opening")
		composeRule.onNodeWithTag(CREATE_ITEM_NAME_FIELD_TAG).performImeAction()

		// Creating a scene auto-opens it in the editor.
		composeRule.waitUntil(10_000) {
			composeRule.onAllNodesWithTag(SCENE_EDITOR_TEXT_TAG).fetchSemanticsNodes().isNotEmpty()
		}
		composeRule.typeIntoEditor(SCENE_EDITOR_TEXT_TAG, "Once upon a time")
		composeRule.waitUntil(10_000) {
			composeRule.onAllNodesWithTag(SCENE_EDITOR_SAVE_TAG).fetchSemanticsNodes().isNotEmpty()
		}

		// Saving clears the dirty buffer, so the save action disappears.
		composeRule.onNodeWithTag(SCENE_EDITOR_SAVE_TAG).performClick()
		composeRule.waitUntil(10_000) {
			composeRule.onAllNodesWithTag(SCENE_EDITOR_SAVE_TAG).fetchSemanticsNodes().isEmpty()
		}
	}

	@After
	fun tearDown() {
		EditorTestHarness.teardown(scenario, projectDef)
	}
}
