package operations

import com.darkrockstudios.apps.hammer.common.data.ClientResult
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.SceneContent
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneEditorService
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.scenemetadata.SceneMetadata
import com.darkrockstudios.apps.hammer.common.data.temporaryProjectTask
import com.darkrockstudios.apps.hammer.operations.Access
import com.darkrockstudios.apps.hammer.operations.NoInput
import com.darkrockstudios.apps.hammer.operations.OperationException
import com.darkrockstudios.apps.hammer.operations.OperationRegistry
import com.darkrockstudios.apps.hammer.operations.OperationScope
import com.darkrockstudios.apps.hammer.operations.core.OperationList
import com.darkrockstudios.apps.hammer.operations.core.TermScene
import com.darkrockstudios.apps.hammer.operations.core.TextCountInput
import com.darkrockstudios.apps.hammer.operations.core.TextCounts
import com.darkrockstudios.apps.hammer.operations.core.TextFindInput
import com.darkrockstudios.apps.hammer.operations.core.TextMatches
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.core.component.get
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Runs text.count and text.find against the app's real Koin graph over a fake filesystem. */
class TextOperationsTest : KoinOperationsTest() {

	private lateinit var ids: Seeded

	private class Seeded(val chapter: Int, val ashes: Int, val leftHanded: Int, val registry: Int)

	@BeforeEach
	fun setUp() {
		ids = onTestThread { seed() }
	}

	/** In story order: Chapter One (Ashes on the Water, Left-Handed), then The Registry, and an archived scene. */
	private suspend fun seed(): Seeded {
		val created = get<ProjectsRepository>().createProject(PROJECT, seedDefaultLanguage = false)
		val def = (created as ClientResult.Success<ProjectDef>).data
		lateinit var seeded: Seeded
		temporaryProjectTask(def) { scope ->
			val scenes = scope.get<SceneEditorService>()
			val chapter = scenes.createGroup(null, "Chapter One")!!
			val ashes = scenes.createScene(chapter, "Ashes on the Water")!!
			scenes.storeSceneMarkdownRaw(
				SceneContent(ashes, markdown = "The harbor bells rang *till* dawn.\n\nStill the harbormaster waited until noon."),
			)
			scenes.storeMetadata(SceneMetadata(outline = "harbor harbor", notes = "harbor"), ashes.id)
			val leftHanded = scenes.createScene(chapter, "Left-Handed")!!
			scenes.storeSceneMarkdownRaw(SceneContent(leftHanded, markdown = "No bells here."))
			val registry = scenes.createScene(null, "The Registry")!!
			scenes.storeSceneMarkdownRaw(
				SceneContent(registry, markdown = "*E.V. E.V. E.V.* Till then, the harbor slept. Teo’s harbor."),
			)
			val cut = scenes.createScene(null, "Cut")!!
			scenes.storeSceneMarkdownRaw(SceneContent(cut, markdown = "The harbor, cut."))
			scenes.archiveScene(cut)
			seeded = Seeded(chapter.id, ashes.id, leftHanded.id, registry.id)
		}
		return seeded
	}

	private suspend fun count(input: TextCountInput) = run<TextCountInput, TextCounts>("text.count", input)

	private suspend fun find(input: TextFindInput) = run<TextFindInput, TextMatches>("text.find", input)

	@Test
	fun `text count counts each term in every scene, skipping outlines, notes, and archived scenes`() = onTestThread {
		val output = count(TextCountInput(PROJECT, terms = listOf("harbor", "till", "E.V.", "Teo's", "absent")))

		assertEquals(3, output.scenesScanned)
		assertEquals(listOf("harbor", "till", "E.V.", "Teo's", "absent"), output.terms.map { it.term })
		assertEquals(listOf(3, 2, 3, 1, 0), output.terms.map { it.total })
		assertEquals(
			listOf(TermScene(ids.ashes, "Ashes on the Water", 1), TermScene(ids.registry, "The Registry", 2)),
			output.terms[0].scenes,
		)
		assertTrue(output.terms[4].scenes.isEmpty())
	}

	@Test
	fun `text count honours case and whole words, and narrows by ids or group`() = onTestThread {
		val strict = count(TextCountInput(PROJECT, terms = listOf("Till"), caseSensitive = true))
		assertEquals(1, strict.terms.single().total)
		val loose = count(TextCountInput(PROJECT, terms = listOf("till"), wholeWord = false))
		assertEquals(3, loose.terms.single().total)

		val narrow = count(TextCountInput(PROJECT, terms = listOf("harbor"), ids = listOf(ids.registry)))
		assertEquals(1, narrow.scenesScanned)
		assertEquals(2, narrow.terms.single().total)

		val group = count(TextCountInput(PROJECT, terms = listOf("harbor"), group = ids.chapter))
		assertEquals(2, group.scenesScanned)
		assertEquals(listOf(ids.ashes), group.terms.single().scenes.map { it.id })
	}

	@Test
	fun `text find pages through matches with the full total`() = onTestThread {
		val first = find(TextFindInput(PROJECT, term = "harbor", limit = 2))
		assertEquals(3, first.total)
		assertEquals(2, first.next)
		assertEquals(listOf(ids.ashes, ids.registry), first.matches.map { it.id })
		assertEquals("The harbor bells rang till dawn.", first.matches[0].context)
		assertEquals("The Registry", first.matches[1].name)

		val second = find(TextFindInput(PROJECT, term = "harbor", limit = 2, offset = 2))
		assertEquals(3, second.total)
		assertNull(second.next)
		assertEquals(
			"E.V. E.V. E.V. Till then, the harbor slept. Teo's harbor.",
			second.matches.single().context,
		)

		val past = find(TextFindInput(PROJECT, term = "harbor", offset = 10))
		assertEquals(3, past.total)
		assertTrue(past.matches.isEmpty())
		assertNull(past.next)
	}

	@Test
	fun `bad input is refused with a plain message before anything is read`() = onTestThread {
		suspend fun refusal(block: suspend () -> Unit) = assertFailsWith<OperationException> { block() }
		suspend fun invalid(message: String, block: suspend () -> Unit) {
			val error = refusal(block)
			assertEquals(OperationException.Kind.InvalidInput, error.kind)
			assertEquals(message, error.message)
		}

		invalid("terms[1] is blank") { count(TextCountInput(PROJECT, terms = listOf("bell", " "))) }
		invalid("terms[0] is blank") { count(TextCountInput(PROJECT, terms = listOf("**"))) }
		invalid("terms must hold 1 to 50 terms") { count(TextCountInput(PROJECT, terms = emptyList())) }
		invalid("terms must hold 1 to 50 terms") { count(TextCountInput(PROJECT, terms = List(51) { "a" })) }
		invalid("term is blank") { find(TextFindInput(PROJECT, term = "")) }
		invalid("limit must be from 1 to 200") { find(TextFindInput(PROJECT, term = "a", limit = 201)) }
		invalid("limit must be from 1 to 200") { find(TextFindInput(PROJECT, term = "a", limit = 0)) }
		invalid("offset must be at least 0") { find(TextFindInput(PROJECT, term = "a", offset = -1)) }
		// Checked before the project is looked up.
		invalid("term is blank") { find(TextFindInput("Missing", term = "")) }
		invalid("Give group or ids, not both") {
			count(TextCountInput(PROJECT, terms = listOf("a"), group = ids.chapter, ids = listOf(ids.ashes)))
		}

		assertEquals(OperationException.Kind.NotFound, refusal { find(TextFindInput("Missing", term = "a")) }.kind)
		assertEquals(
			OperationException.Kind.NotFound,
			refusal { count(TextCountInput(PROJECT, terms = listOf("a"), group = ids.ashes)) }.kind,
		)
	}

	@Test
	fun `both are content reads, dispatched on JSON`() = onTestThread {
		val ops = run<NoInput, OperationList>("ops.list", NoInput).operations.filter { it.name.startsWith("text.") }
		assertEquals(listOf("text.count", "text.find"), ops.map { it.name })
		assertTrue(ops.all { it.access == Access.Read && it.scope == OperationScope.Content })
		assertEquals(
			listOf("project", "terms"),
			ops[0].input["required"]!!.jsonArray.map { it.jsonPrimitive.content },
		)

		val input = buildJsonObject {
			put("project", PROJECT)
			putJsonArray("terms") { add(JsonPrimitive("bells")) }
		}
		val counted = get<OperationRegistry>().dispatch("text.count", input).jsonObject
		assertEquals(2, counted["terms"]!!.jsonArray.single().jsonObject["total"]!!.jsonPrimitive.int)

		val found = get<OperationRegistry>().dispatch(
			"text.find",
			buildJsonObject {
				put("project", PROJECT)
				put("term", "bells")
			},
		).jsonObject
		assertEquals(JsonNull, found["next"])
	}

	private companion object {
		const val PROJECT = "Harbor"
	}
}
