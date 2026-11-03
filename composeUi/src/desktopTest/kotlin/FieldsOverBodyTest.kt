import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTopPositionInRootIsEqualTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.common.timeline.FieldsOverBody
import org.junit.Rule
import org.junit.Test

class FieldsOverBodyTest {
	@get:Rule
	val compose = createComposeRule()

	/** Three 100dp fields over a body that needs 150dp, in a column [height] tall. */
	private fun show(height: Dp, withBody: Boolean = true) {
		compose.setContent {
			Box(modifier = Modifier.size(300.dp, height)) {
				FieldsOverBody(
					bodyMinHeight = 150.dp,
					modifier = Modifier.testTag("layout"),
					fields = {
						Column {
							repeat(3) { Box(modifier = Modifier.fillMaxWidth().height(100.dp).testTag("field-$it")) }
						}
					},
					body = if (withBody) {
						{ Box(modifier = Modifier.testTag("body")) }
					} else {
						null
					},
				)
			}
		}
	}

	@Test
	fun `When everything fits the body takes the rest`() {
		show(height = 600.dp)

		compose.onNodeWithTag("field-2").assertIsDisplayed()
		compose.onNodeWithTag("body").assertTopPositionInRootIsEqualTo(300.dp).assertHeightIsEqualTo(300.dp)
	}

	@Test
	fun `When the fields do not fit they scroll above a body of the minimum height`() {
		show(height = 350.dp)

		compose.onNodeWithTag("body").assertTopPositionInRootIsEqualTo(200.dp).assertHeightIsEqualTo(150.dp)
		compose.onNodeWithTag("field-2").performScrollTo().assertIsDisplayed()
		compose.onNodeWithTag("body").assertTopPositionInRootIsEqualTo(200.dp).assertHeightIsEqualTo(150.dp)
	}

	@Test
	fun `When even the body's minimum does not fit the fields keep a strip to scroll in`() {
		show(height = 160.dp)

		compose.onNodeWithTag("body").assertTopPositionInRootIsEqualTo(48.dp).assertHeightIsEqualTo(112.dp)
		compose.onNodeWithTag("field-2").performScrollTo().assertIsDisplayed()
	}

	@Test
	fun `Without a body the fields take only their own height`() {
		show(height = 600.dp, withBody = false)

		compose.onNodeWithTag("layout").assertHeightIsEqualTo(300.dp)
	}
}
