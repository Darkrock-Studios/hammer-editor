package operations

import com.darkrockstudios.apps.hammer.operations.Access
import com.darkrockstudios.apps.hammer.operations.OperationScope
import com.darkrockstudios.apps.hammer.operations.PLUGIN_WITHHELD_OPERATIONS
import com.darkrockstudios.apps.hammer.operations.core.coreOperations
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class PluginWithheldOperationsTest {

	@Test
	fun `every operation that changes the sync account or syncs is withheld from plugins`() {
		val accountChanges = coreOperations()
			.filter { it.scope == OperationScope.Account && it.access != Access.Read }
			.map { it.name }
			.toSet()

		assertEquals(accountChanges, PLUGIN_WITHHELD_OPERATIONS)
	}
}
