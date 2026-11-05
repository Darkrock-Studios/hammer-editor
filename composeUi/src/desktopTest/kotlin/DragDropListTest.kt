import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.common.compose.reorderable.DragDropList
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

class DragDropListTest {
	@get:Rule
	val compose = createComposeRule()

	@Test
	fun `A long press behind top padding drags the item under the finger`() {
		val moves = mutableListOf<Pair<Int, Int>>()
		compose.setContent {
			DragDropList(
				items = listOf("A", "B", "C", "D", "E"),
				key = { _, item -> item },
				onMove = { from, to -> moves += from to to },
				modifier = Modifier.requiredSize(DpSize(300.dp, 600.dp)),
				contentPadding = PaddingValues(top = 120.dp),
			) { item, _ ->
				Text(item, modifier = Modifier.fillMaxWidth().height(60.dp).testTag(item))
			}
		}

		// Press the middle of A, then drag it down past B.
		compose.onNodeWithTag("A").performTouchInput { down(center) }
		compose.mainClock.advanceTimeBy(1_000)
		compose.onNodeWithTag("A").performTouchInput {
			repeat(10) { moveBy(Offset(0f, 7.dp.toPx())) }
			up()
		}
		compose.waitForIdle()

		assertEquals(listOf(0 to 1), moves)
	}

	@Test
	fun `A mouse drags the row it pressed as soon as it moves`() {
		val moves = mutableListOf<Pair<Int, Int>>()
		showList(moves)

		// No long press: press A and move straight off, down past B.
		compose.onNodeWithTag("A").performMouseInput {
			moveTo(center)
			press()
			repeat(10) { moveBy(Offset(0f, 7.dp.toPx())) }
			release()
		}
		compose.waitForIdle()

		assertEquals(listOf(0 to 1), moves)
	}

	@Test
	fun `A mouse click without moving reorders nothing`() {
		val moves = mutableListOf<Pair<Int, Int>>()
		showList(moves)

		compose.onNodeWithTag("A").performMouseInput { click() }
		compose.waitForIdle()

		assertEquals(emptyList(), moves)
	}

	private fun showList(moves: MutableList<Pair<Int, Int>>) {
		compose.setContent {
			DragDropList(
				items = listOf("A", "B", "C", "D", "E"),
				key = { _, item -> item },
				onMove = { from, to -> moves += from to to },
				modifier = Modifier.requiredSize(DpSize(300.dp, 600.dp)),
				contentPadding = PaddingValues(top = 120.dp),
			) { item, _ ->
				Text(item, modifier = Modifier.fillMaxWidth().height(60.dp).testTag(item))
			}
		}
	}
}
