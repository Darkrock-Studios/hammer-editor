import com.darkrockstudios.apps.hammer.operations.Access
import com.darkrockstudios.apps.hammer.operations.OperationScope
import com.darkrockstudios.apps.hammer.operations.core.OperationDescriptor
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
		listOf("content:destructive", "content:", "content:read:all", "everything:read", "Scene.Read", "").forEach {
			assertNull(OperationGrant.parse(it))
		}
	}

	@Test
	fun `a scope covers its reads or writes, never destructive operations`() {
		val writes = OperationGrant.parse("content:write")!!
		assertTrue(writes.covers(op("scene.write", Access.Write)))
		assertFalse(writes.covers(op("scene.read", Access.Read)))
		assertFalse(writes.covers(op("scene.delete", Access.Destructive)))
		assertFalse(writes.covers(op("account.login", Access.Write, OperationScope.Account)))
		assertTrue(OperationGrant.parse("scene.delete")!!.covers(op("scene.delete", Access.Destructive)))
	}
}
