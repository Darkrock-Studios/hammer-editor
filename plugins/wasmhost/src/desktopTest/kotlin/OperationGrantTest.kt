import com.darkrockstudios.apps.hammer.operations.Access
import com.darkrockstudios.apps.hammer.operations.OperationScope
import com.darkrockstudios.apps.hammer.operations.core.OperationDescriptor
import com.darkrockstudios.apps.hammer.operations.core.coreOperations
import com.darkrockstudios.apps.hammer.operations.core.descriptor
import com.darkrockstudios.apps.hammer.plugins.wasmhost.OperationGrant
import kotlinx.serialization.json.JsonObject
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OperationGrantTest {

	private fun op(name: String, access: Access, scope: OperationScope = OperationScope.Content) =
		OperationDescriptor(name, "", access, scope, JsonObject(emptyMap()), JsonObject(emptyMap()))

	@Test
	fun `entries parse as names or scopes`() {
		assertEquals(OperationGrant.Named("scene.read"), OperationGrant.parse("scene.read"))
		assertEquals(OperationGrant.Scoped(OperationScope.Content, Access.Write), OperationGrant.parse("content:write"))
		assertEquals(OperationGrant.Scoped(OperationScope.Account, Access.Read), OperationGrant.parse("account:read"))
		listOf("content:destructive", "content:prose", "content:", "content:read:all", "everything:read", "Scene.Read", "").forEach {
			assertNull(OperationGrant.parse(it))
		}
	}

	@Test
	fun `a scope covers its reads or writes, never destructive operations`() {
		val writes = OperationGrant.parse("content:write")!!
		assertTrue(writes.covers(op("scene.rename", Access.Write)))
		assertFalse(writes.covers(op("scene.read", Access.Read)))
		assertFalse(writes.covers(op("scene.delete", Access.Destructive)))
		assertFalse(writes.covers(op("account.login", Access.Write, OperationScope.Account)))
		assertTrue(OperationGrant.parse("scene.delete")!!.covers(op("scene.delete", Access.Destructive)))
	}

	@Test
	fun `a scope never covers writing a scene's prose, a name does`() {
		val core = coreOperations().associate { it.name to it.descriptor() }
		val writes = OperationGrant.parse("content:write")!!
		listOf("scene.write", "scene.append", "draft.apply", "project.import").forEach { name ->
			val op = core.getValue(name)
			assertFalse(writes.covers(op), name)
			assertTrue(OperationGrant.parse(name)!!.covers(op), name)
		}
		assertTrue(writes.covers(core.getValue("draft.create")))
	}
}
