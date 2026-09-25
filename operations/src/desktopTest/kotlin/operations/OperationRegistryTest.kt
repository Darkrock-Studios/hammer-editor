package operations

import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.operations.Access
import com.darkrockstudios.apps.hammer.operations.OpenProject
import com.darkrockstudios.apps.hammer.operations.Operation
import com.darkrockstudios.apps.hammer.operations.OperationException
import com.darkrockstudios.apps.hammer.operations.OperationRegistry
import com.darkrockstudios.apps.hammer.operations.OperationScope
import com.darkrockstudios.apps.hammer.operations.ProjectResolver
import com.darkrockstudios.apps.hammer.operations.operation
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class OperationRegistryTest {

	@Serializable
	data class Greeting(val name: String, val excited: Boolean = false)

	@Serializable
	data class Reply(val text: String)

	private val greet = operation<Greeting, Reply>("greet", "Says hello.", Access.Read, OperationScope.Content) {
		Reply("Hello, ${it.name}${if (it.excited) "!" else "."}")
	}

	private fun registry(vararg ops: Operation<*, *>) =
		OperationRegistry(ops.toList(), NoProjects)

	@Test
	fun `dispatch decodes input and encodes output`() = runTest {
		val output = registry(greet).dispatch("greet", buildJsonObject { put("name", "Ada") })

		assertEquals(buildJsonObject { put("text", "Hello, Ada.") }, output)
	}

	@Test
	fun `unknown operations are not found`() = runTest {
		val error = assertFailsWith<OperationException> {
			registry(greet).dispatch("wave", buildJsonObject {})
		}
		assertEquals(OperationException.Kind.NotFound, error.kind)
	}

	@Test
	fun `unknown fields, missing fields, and wrong types are invalid input`() = runTest {
		val inputs = listOf(
			buildJsonObject {
				put("name", "Ada")
				put("volume", 11)
			},
			buildJsonObject { put("excited", true) },
			buildJsonObject { put("name", 3) },
		)
		inputs.forEach { input ->
			val error = assertFailsWith<OperationException> { registry(greet).dispatch("greet", input) }
			assertEquals(OperationException.Kind.InvalidInput, error.kind)
		}
	}

	@Test
	fun `operations are listed by name`() {
		val wave = operation<Greeting, Reply>("wave", "Waves.", Access.Read, OperationScope.Content) { Reply("~") }
		val apply = operation<Greeting, Reply>("a.b", "Nested.", Access.Read, OperationScope.Content) { Reply("") }

		assertEquals(listOf("a.b", "greet", "wave"), registry(wave, greet, apply).operations.map { it.name })
	}

	@Test
	fun `rejects duplicate and malformed names`() {
		assertThrows<IllegalArgumentException> { registry(greet, greet) }
		listOf("Greet", "greet.", ".greet", "greet..now", "greet now").forEach { name ->
			assertThrows<IllegalArgumentException> {
				registry(operation<Greeting, Reply>(name, "", Access.Read, OperationScope.Content) { Reply("") })
			}
		}
	}

	private object NoProjects : ProjectResolver {
		override fun resolve(project: String): ProjectDef = error("unused")
		override suspend fun <T> withProject(project: String, block: suspend (OpenProject) -> T): T = error("unused")
	}
}
