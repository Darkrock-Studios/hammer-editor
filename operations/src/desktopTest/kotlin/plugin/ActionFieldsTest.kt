package plugin

import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryType
import com.darkrockstudios.apps.hammer.common.data.entitycatalog.CatalogFilter
import com.darkrockstudios.apps.hammer.common.data.entitycatalog.CatalogKind
import com.darkrockstudios.apps.hammer.common.data.entitycatalog.CatalogRef
import com.darkrockstudios.apps.hammer.operations.plugin.ActionField
import com.darkrockstudios.apps.hammer.operations.plugin.ActionPage
import com.darkrockstudios.apps.hammer.operations.plugin.FieldTable
import com.darkrockstudios.apps.hammer.operations.plugin.PageTable
import com.darkrockstudios.apps.hammer.operations.plugin.SettingDeclaration
import com.darkrockstudios.apps.hammer.operations.plugin.TextFormat
import com.darkrockstudios.apps.hammer.operations.plugin.entityRefs
import com.darkrockstudios.apps.hammer.operations.plugin.parseSettingDeclarations
import com.darkrockstudios.apps.hammer.operations.plugin.toActionFields
import com.darkrockstudios.apps.hammer.operations.plugin.toPageTable
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

	private val toml = Toml { ignoreUnknownKeys = true }
	private val json = Json { ignoreUnknownKeys = true }

	private fun fromToml(text: String) = toml.decodeFromString(Page.serializer(), text.trimIndent()).field.toActionFields()

	private fun fromJson(text: String) = json.decodeFromString(Page.serializer(), text).field.toActionFields()

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
				{"key": "g", "type": "scenes", "label": "G", "multiple": false},
				{"key": "h", "type": "choice", "label": "H", "default": null, "options": [{"value": "y", "label": "Y"}]}
			]}
			""",
		)
		assertEquals(
			listOf(
				JsonPrimitive(false), JsonPrimitive(3L), JsonPrimitive(0L), JsonPrimitive("x"), JsonPrimitive(""), JsonArray(emptyList()), JsonNull,
				JsonPrimitive("y"),
			),
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
		refused("""{"key": "a", "type": "scenes", "label": "A", "default": 3}""")
		refused("""{"key": "a", "type": "scenes", "label": "A", "multiple": false, "default": [3]}""")
		refused("""{"key": "a", "type": "int", "label": "A", "default": 3.5}""")
		refused("""{"key": "a", "type": "int", "label": "A", "default": 3.0}""")
		refused("""{"key": "a", "type": "int", "label": "A", "default": 1e20}""")
		refused("""{"key": "a", "type": "int", "label": "A", "default": "3"}""")
		refused("""{"key": "a", "type": "bool", "label": "A", "default": "true"}""")
		refused("""{"key": "a", "type": "choice", "label": "A", "default": 1, "options": [{"value": "1", "label": "One"}]}""")
		assertThrows<IllegalArgumentException> { fromToml("[[field]]\nkey = \"a\"\ntype = \"int\"\nlabel = \"A\"\ndefault = 3.0") }
		refused("""{"key": "", "type": "bool", "label": "A"}""")
		refused("""{"key": "a", "type": "bool", "label": "A"}, {"key": "a", "type": "text", "label": "B"}""")
	}

	@Test
	fun `an entity field reads the same from a TOML manifest as from a JSON page`() {
		val toml = fromToml(
			"""
			[[field]]
			key = "a"
			type = "entity"
			label = "First character"
			kinds = ["entry"]
			entry_types = ["person"]
			tags = ["pov"]
			required = true
			default = { kind = "entry", id = 12 }

			[[field]]
			key = "b"
			type = "entity"
			label = "Where"
			kinds = ["scene", "event"]
			multiple = true
			default = [{ kind = "scene", id = 3 }, { kind = "event", id = 4 }]
			""",
		)
		val json = fromJson(
			"""
			{"field": [
				{"key": "a", "type": "entity", "label": "First character", "kinds": ["entry"], "entry_types": ["person"], "tags": ["pov"],
				 "required": true, "default": {"kind": "entry", "id": 12}},
				{"key": "b", "type": "entity", "label": "Where", "kinds": ["scene", "event"], "multiple": true,
				 "default": [{"kind": "scene", "id": 3}, {"kind": "event", "id": 4}]}
			]}
			""",
		)

		listOf(toml, json).forEach { fields ->
			val (a, b) = fields.map { assertIs<ActionField.Entity>(it) }
			assertEquals(CatalogFilter(setOf(CatalogKind.Entry), setOf(EntryType.PERSON), setOf("pov")), a.filter)
			assertEquals(listOf(true, false), listOf(a.required, a.multiple))
			assertEquals(parsed("""{"kind": "entry", "id": 12}"""), a.default)
			assertEquals(CatalogFilter(setOf(CatalogKind.Scene, CatalogKind.Event)), b.filter)
			assertEquals(true, b.multiple)
			assertEquals(listOf(CatalogRef(CatalogKind.Scene, 3), CatalogRef(CatalogKind.Event, 4)), b.defaultRefs)
			assertEquals(parsed("""[{"kind": "scene", "id": 3}, {"kind": "event", "id": 4}]"""), b.default)
		}
	}

	@Test
	fun `an entity field takes every kind, one item, and nothing picked without saying`() {
		val (one, many) = fromJson(
			"""
			{"field": [
				{"key": "a", "type": "entity", "label": "A"},
				{"key": "b", "type": "entity", "label": "B", "multiple": true, "default": null}
			]}
			""",
		).map { assertIs<ActionField.Entity>(it) }

		assertEquals(CatalogFilter(), one.filter)
		assertEquals(CatalogKind.entries.toSet(), one.filter.kinds)
		assertEquals(listOf(false, false), listOf(one.multiple, one.required))
		assertEquals(JsonNull, one.default)
		assertEquals(JsonArray(emptyList()), many.default)
	}

	@Test
	fun `entity fields that cannot be shown are refused`() {
		fun refused(extra: String) = assertThrows<IllegalArgumentException> {
			fromJson("""{"field": [{"key": "a", "type": "entity", "label": "A", $extra}]}""")
		}
		refused(""""kinds": ["chapter"]""")
		refused(""""kinds": ["timeline"]""")
		refused(""""kinds": []""")
		refused(""""entry_types": ["monster"]""")
		refused(""""kinds": ["scene"], "entry_types": ["person"]""")
		refused(""""tags": [" "]""")
		refused(""""default": [{"kind": "entry", "id": 1}]""")
		refused(""""multiple": true, "default": {"kind": "entry", "id": 1}""")
		refused(""""default": 12""")
		refused(""""default": {"kind": "entry"}""")
		refused(""""default": {"kind": "entry", "id": "12"}""")
		refused(""""default": {"kind": "entry", "id": 1.5}""")
		refused(""""default": {"kind": "chapter", "id": 1}""")
		refused(""""multiple": true, "default": [{"kind": "entry", "id": 1}, 2]""")
	}

	@Test
	fun `an entity field is written back as a plugin would declare it`() {
		val field = ActionField.Entity(
			key = "a",
			label = "Who",
			hint = "Anyone",
			filter = CatalogFilter(setOf(CatalogKind.Entry, CatalogKind.Note), setOf(EntryType.PLACE), setOf("pov")),
			multiple = true,
			required = true,
			defaultRefs = listOf(CatalogRef(CatalogKind.Note, 2)),
		)
		val text = json.encodeToString(PageTable.serializer(), ActionPage(fields = listOf(field)).toPageTable())
		val back = assertIs<ActionField.Entity>(json.decodeFromString(PageTable.serializer(), text).toActionPage().fields.single())

		assertEquals(listOf(field.key, field.label, field.hint, field.multiple, field.required), listOf(back.key, back.label, back.hint, back.multiple, back.required))
		assertEquals(field.filter, back.filter)
		assertEquals(field.defaultRefs, back.defaultRefs)
	}

	@Test
	fun `an entity value is read leniently, passing over what is not an item`() {
		assertEquals(
			listOf(CatalogRef(CatalogKind.Entry, 1), CatalogRef(CatalogKind.Scene, 2)),
			entityRefs(parsed("""[{"kind": "entry", "id": 1}, {"kind": "entry", "id": 1}, 3, {"kind": "scene", "id": 2}, {"kind": "x", "id": 4}]""")),
		)
		assertEquals(listOf(CatalogRef(CatalogKind.Event, 5)), entityRefs(parsed("""{"kind": "event", "id": 5}""")))
		assertEquals(emptyList(), entityRefs(JsonNull))
		assertEquals(emptyList(), entityRefs(null))
	}

	private fun parsed(text: String) = json.parseToJsonElement(text)

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
