package plugin

import com.darkrockstudios.apps.hammer.operations.plugin.ActionField
import com.darkrockstudios.apps.hammer.operations.plugin.FieldTable
import com.darkrockstudios.apps.hammer.operations.plugin.SettingDeclaration
import com.darkrockstudios.apps.hammer.operations.plugin.TextFormat
import com.darkrockstudios.apps.hammer.operations.plugin.parseSettingDeclarations
import com.darkrockstudios.apps.hammer.operations.plugin.toActionFields
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import net.peanuuutz.tomlkt.Toml
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ActionFieldsTest {

	@Serializable
	private class Page(val field: List<FieldTable> = emptyList())

	private fun fromToml(toml: String) =
		Toml { ignoreUnknownKeys = true }.decodeFromString(Page.serializer(), toml.trimIndent()).field.toActionFields()

	private fun fromJson(json: String) =
		Json { ignoreUnknownKeys = true }.decodeFromString(Page.serializer(), json).field.toActionFields()

	@Test
	fun `a field reads the same from a TOML manifest as from a JSON page`() {
		val toml = fromToml(
			"""
			[[field]]
			key = "count"
			type = "int"
			label = "How many"
			default = 5
			min = 1
			max = 10

			[[field]]
			key = "loud"
			type = "bool"
			label = "Loud"
			default = true

			[[field]]
			key = "style"
			type = "choice"
			label = "Style"
			default = "norse"
			options = [{ value = "elvish", label = "Elvish" }, { value = "norse", label = "Norse" }]

			[[field]]
			key = "answer"
			type = "text"
			label = "Answer"
			format = "rich"
			required = true
			default = "Once, *long* ago."

			[[field]]
			key = "scene"
			type = "scenes"
			label = "Scene"
			multiple = false
			default = 4
			""",
		)
		val json = fromJson(
			"""
			{"field": [
				{"key": "count", "type": "int", "label": "How many", "default": 5, "min": 1, "max": 10},
				{"key": "loud", "type": "bool", "label": "Loud", "default": true},
				{"key": "style", "type": "choice", "label": "Style", "default": "norse",
				 "options": [{"value": "elvish", "label": "Elvish"}, {"value": "norse", "label": "Norse"}]},
				{"key": "answer", "type": "text", "label": "Answer", "format": "rich", "required": true, "default": "Once, *long* ago."},
				{"key": "scene", "type": "scenes", "label": "Scene", "multiple": false, "default": 4}
			]}
			""",
		)

		listOf(toml, json).forEach { fields ->
			assertEquals(
				listOf(JsonPrimitive(5L), JsonPrimitive(true), JsonPrimitive("norse"), JsonPrimitive("Once, *long* ago."), JsonPrimitive(4)),
				fields.map { it.default },
			)
			val answer = assertIs<ActionField.Text>(fields[3])
			assertEquals(TextFormat.Rich, answer.format)
			assertEquals(true, answer.required)
			assertIs<SettingDeclaration.Number>(assertIs<ActionField.Setting>(fields[0]).declaration)
		}
	}

	@Test
	fun `missing defaults are false, the minimum or 0, the first option, empty text, and no scenes`() {
		val fields = fromJson(
			"""
			{"field": [
				{"key": "a", "type": "bool", "label": "A"},
				{"key": "b", "type": "int", "label": "B", "min": 3},
				{"key": "c", "type": "int", "label": "C"},
				{"key": "d", "type": "choice", "label": "D", "options": [{"value": "x", "label": "X"}]},
				{"key": "e", "type": "text", "label": "E"},
				{"key": "f", "type": "scenes", "label": "F"},
				{"key": "g", "type": "scenes", "label": "G", "multiple": false}
			]}
			""",
		)
		assertEquals(
			listOf(JsonPrimitive(false), JsonPrimitive(3L), JsonPrimitive(0L), JsonPrimitive("x"), JsonPrimitive(""), JsonArray(emptyList()), JsonNull),
			fields.map { it.default },
		)
		assertEquals(TextFormat.Plain, assertIs<ActionField.Text>(fields[4]).format)
	}

	@Test
	fun `fields that cannot be shown are refused`() {
		fun refused(field: String) = assertThrows<IllegalArgumentException> { fromJson("""{"field": [$field]}""") }
		refused("""{"key": "a", "type": "colour", "label": "A"}""")
		refused("""{"key": "a", "type": "string", "label": "A"}""")
		refused("""{"key": "a", "type": "text", "label": "A", "format": "html"}""")
		refused("""{"key": "a", "type": "text", "label": "A", "default": 3}""")
		refused("""{"key": "a", "type": "int", "label": "A", "default": 20, "max": 10}""")
		refused("""{"key": "a", "type": "choice", "label": "A"}""")
		refused("""{"key": "a", "type": "choice", "label": "A", "default": "z", "options": [{"value": "x", "label": "X"}]}""")
		refused("""{"key": "a", "type": "scenes", "label": "A", "default": ["one"]}""")
		refused("""{"key": "", "type": "bool", "label": "A"}""")
		refused("""{"key": "a", "type": "bool", "label": "A"}, {"key": "a", "type": "text", "label": "B"}""")
	}

	@Test
	fun `settings still take strings, with their defaults from TOML`() {
		val settings = parseSettingDeclarations(
			"""
			[[setting]]
			key = "name"
			type = "string"
			label = "Name"
			default = "Mara"
			""".trimIndent()
		)
		assertEquals("Mara", assertIs<SettingDeclaration.Text>(settings.single()).defaultValue)
	}
}
