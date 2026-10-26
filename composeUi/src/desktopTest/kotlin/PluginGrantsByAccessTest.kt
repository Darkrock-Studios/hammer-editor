import com.darkrockstudios.apps.hammer.common.projectselection.settings.grantsByAccess
import com.darkrockstudios.apps.hammer.operations.Access
import com.darkrockstudios.apps.hammer.operations.OperationScope
import com.darkrockstudios.apps.hammer.operations.core.coreOperations
import com.darkrockstudios.apps.hammer.plugins.wasmhost.OperationGrant
import org.junit.Test
import kotlin.test.assertEquals

class PluginGrantsByAccessTest {

	private val core = coreOperations().associate { it.name to it.access }

	private fun levels(vararg permissions: String) =
		grantsByAccess(permissions.map { OperationGrant.parse(it)!! }, core::get)

	@Test
	fun `named prose writes are listed apart from other changes`() {
		val levels = levels(
			"content:read",
			"content:write",
			"scene.rename",
			"scene.append",
			"scene.write",
			"draft.apply",
			"project.import",
			"scene.delete",
			"later.operation",
		)

		assertEquals(listOf(OperationGrant.Scoped(OperationScope.Content, Access.Read)), levels[Access.Read])
		assertEquals(
			listOf(
				OperationGrant.Scoped(OperationScope.Content, Access.Write),
				OperationGrant.Named("scene.rename"),
				OperationGrant.Named("later.operation"),
			),
			levels[Access.Write],
		)
		assertEquals(
			listOf("scene.append", "scene.write", "draft.apply", "project.import").map(OperationGrant::Named),
			levels[Access.Prose],
		)
		assertEquals(listOf(OperationGrant.Named("scene.delete")), levels[Access.Destructive])
	}
}
