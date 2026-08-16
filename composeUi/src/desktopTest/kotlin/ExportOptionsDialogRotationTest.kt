import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.darkrockstudios.apps.hammer.common.data.ExportOptions
import com.darkrockstudios.apps.hammer.common.data.ExportableScene
import com.darkrockstudios.apps.hammer.common.preview.KoinApplicationPreview
import com.darkrockstudios.apps.hammer.common.projecthome.ExportOptionsDialog
import com.darkrockstudios.apps.hammer.common.projecthome.exportSceneRowTag
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

/**
 * Rotation cover in the spirit of #885: the dialog holds no option state of its own, so a
 * configuration change (modeled by bumping the `key` while the option holder lives outside it,
 * like a retained Decompose component) must not lose the in-dialog scene selection.
 * `StateRestorationTester` would be the closer fit but is an unimplemented stub on desktop.
 */
class ExportOptionsDialogRotationTest : BaseTest() {

	@get:Rule
	val compose = createComposeRule()

	private val entries = listOf(
		ExportableScene(id = 1, name = "Scene 1", isGroup = false, depth = 0),
		ExportableScene(id = 2, name = "Group 2", isGroup = true, depth = 0),
		ExportableScene(id = 3, name = "Scene 3", isGroup = false, depth = 1),
	)

	@Test
	fun `An in-dialog scene selection survives the composition being recreated`() {
		// Stands in for ProjectHome.State.exportOptions in the retained component.
		var options by mutableStateOf(ExportOptions(sceneIds = emptySet()))
		val epoch = mutableStateOf(0)
		var buildCount = 0

		compose.setContent {
			// Koin stays outside the key: its teardown would otherwise race the rebuilt subtree.
			KoinApplicationPreview {
				key(epoch.value) {
					remember { buildCount++ }
					ExportOptionsDialog(
						visible = true,
						options = options,
						exportableScenes = entries,
						onOptionsChanged = { options = it },
						onCancel = {},
						onConfirm = {},
					)
				}
			}
		}

		compose.onNodeWithTag(exportSceneRowTag(3)).performClick()
		compose.waitForIdle()
		assertEquals(setOf(3), options.sceneIds)

		epoch.value++
		compose.waitForIdle()

		assertEquals(2, buildCount, "The subtree should have been thrown away and built again")
		compose.onNodeWithTag(exportSceneRowTag(3)).assertIsOn()
	}
}
