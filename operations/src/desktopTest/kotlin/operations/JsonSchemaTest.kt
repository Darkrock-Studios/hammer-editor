package operations

import com.darkrockstudios.apps.hammer.operations.Base64Bytes
import com.darkrockstudios.apps.hammer.operations.jsonSchema
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class JsonSchemaTest {

	@Serializable
	enum class Mood {
		@SerialName("calm") Calm,
		@SerialName("stormy") Stormy,
	}

	@Serializable
	class Sample(
		val name: String,
		val count: Int,
		val ratio: Double,
		val mood: Mood,
		val tags: List<String>,
		val totals: Map<String, Int>,
		val note: String? = null,
		@Serializable(with = Base64Bytes::class)
		val content: ByteArray,
	)

	@Serializable
	data class Node(val name: String, val children: List<Node>)

	@Serializable
	data class Forest(val first: Node, val second: Node)

	private fun parse(json: String): JsonObject = Json.parseToJsonElement(json) as JsonObject

	@Test
	fun `describes primitives, collections, enums, nullables, and bytes`() {
		val expected = parse(
			"""
			{
			  "type": "object",
			  "properties": {
			    "name": {"type": "string"},
			    "count": {"type": "integer"},
			    "ratio": {"type": "number"},
			    "mood": {"type": "string", "enum": ["calm", "stormy"]},
			    "tags": {"type": "array", "items": {"type": "string"}},
			    "totals": {"type": "object", "additionalProperties": {"type": "integer"}},
			    "note": {"anyOf": [{"type": "string"}, {"type": "null"}]},
			    "content": {"type": "string", "contentEncoding": "base64"}
			  },
			  "required": ["name", "count", "ratio", "mood", "tags", "totals", "content"],
			  "additionalProperties": false
			}
			"""
		)

		assertEquals(expected, jsonSchema(Sample.serializer().descriptor))
	}

	@Test
	fun `recursive types are referenced from defs`() {
		val node = parse(
			"""
			{
			  "type": "object",
			  "properties": {
			    "name": {"type": "string"},
			    "children": {"type": "array", "items": {"${'$'}ref": "#/${'$'}defs/operations.JsonSchemaTest.Node"}}
			  },
			  "required": ["name", "children"],
			  "additionalProperties": false
			}
			"""
		)
		val ref = parse("""{"${'$'}ref": "#/${'$'}defs/operations.JsonSchemaTest.Node"}""")

		val schema = jsonSchema(Forest.serializer().descriptor)

		assertEquals(ref, schema["properties"].let { it as JsonObject }["first"])
		assertEquals(ref, schema["properties"].let { it as JsonObject }["second"])
		assertEquals(node, (schema["\$defs"] as JsonObject)["operations.JsonSchemaTest.Node"])
	}
}
