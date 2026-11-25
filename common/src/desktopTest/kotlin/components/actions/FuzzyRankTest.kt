package components.actions

import com.darkrockstudios.apps.hammer.common.components.actions.ActionId
import com.darkrockstudios.apps.hammer.common.components.actions.PaletteEntry
import com.darkrockstudios.apps.hammer.common.components.actions.rankActions
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class FuzzyRankTest {

	private fun e(label: String) = PaletteEntry(ActionId(label), label, "cat", null)

	@Test
	fun `a blank query keeps table order`() {
		val entries = listOf(e("Save all"), e("Go to Notes"), e("New item"))

		val ranked = rankActions("  ", entries)

		assertEquals(entries, ranked)
	}

	@Test
	fun `prefix beats word start beats substring beats subsequence`() {
		val entries = listOf(e("New outline item"), e("Footnotes"), e("Go to Notes"), e("Notes go"))

		val ranked = rankActions("no", entries)

		assertEquals(listOf("Notes go", "Go to Notes", "Footnotes", "New outline item"), ranked.map { it.label })
	}

	@Test
	fun `non matches are dropped`() {
		val ranked = rankActions("xyz", listOf(e("Go to Notes")))

		assertEquals(emptyList(), ranked)
	}

	@Test
	fun `case is ignored`() {
		val ranked = rankActions("SAVE", listOf(e("Go to Notes"), e("save all")))

		assertEquals(listOf("save all"), ranked.map { it.label })
	}

	@Test
	fun `ties keep input order`() {
		val entries = listOf(e("Next item"), e("Next pane"), e("Next group"))

		val ranked = rankActions("next", entries)

		assertEquals(listOf("Next item", "Next pane", "Next group"), ranked.map { it.label })
	}

	@Test
	fun `a tighter subsequence ranks higher`() {
		val entries = listOf(e("View drafts"), e("Save all"))

		val ranked = rankActions("va", entries)

		assertEquals(listOf("Save all", "View drafts"), ranked.map { it.label })
	}
}
