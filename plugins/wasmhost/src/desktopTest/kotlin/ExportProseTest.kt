import com.darkrockstudios.apps.hammer.operations.OperationJson
import com.darkrockstudios.apps.hammer.plugins.wasmhost.ExportBlock
import com.darkrockstudios.apps.hammer.plugins.wasmhost.proseOf
import kotlinx.serialization.builtins.ListSerializer
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/** The shape prose exporters, and the C kit's documentation, depend on. */
class ExportProseTest {

	private fun json(markdown: String) = OperationJson.encodeToString(ListSerializer(ExportBlock.serializer()), proseOf(markdown))

	@Test
	fun `blocks are keyed by type and spans leave out styles that are off`() {
		assertEquals("""[{"type":"paragraph","spans":[{"text":"A"}]},{"type":"blank"},{"type":"blank"},{"type":"paragraph","spans":[{"text":"B"}]}]""", json("A\n\n\nB"))
		assertEquals(
			"""[{"type":"paragraph","spans":[{"text":"She "},{"text":"ran","italic":true},{"text":"."}]},""" +
				"""{"type":"heading","level":2,"spans":[{"text":"Two"}]},{"type":"rule"},{"type":"code","code":"x = 1"}]""",
			json("She *ran*.\n\n## Two\n\n---\n\n```\nx = 1\n```"),
		)
	}

	@Test
	fun `lists, quotes, and tables`() {
		assertEquals(
			"""[{"type":"list","ordered":true,"items":[{"level":0,"ordered":true,"spans":[{"text":"One"}]},""" +
				"""{"level":1,"ordered":false,"spans":[{"text":"Two","bold":true}]}]},""" +
				"""{"type":"quote","paragraphs":[[{"text":"Said."}]]},""" +
				"""{"type":"table","header":[[{"text":"A"}],[{"text":"B"}]],"rows":[[[{"text":"1"}],[{"text":"2"}]]]}]""",
			json("1. One\n   - **Two**\n\n> Said.\n\n| A | B |\n| --- | --- |\n| 1 | 2 |"),
		)
	}
}
