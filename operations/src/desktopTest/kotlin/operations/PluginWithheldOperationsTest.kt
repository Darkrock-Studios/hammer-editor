package operations

import com.darkrockstudios.apps.hammer.operations.Access
import com.darkrockstudios.apps.hammer.operations.OperationScope
import com.darkrockstudios.apps.hammer.operations.PLUGIN_WITHHELD_OPERATIONS
import com.darkrockstudios.apps.hammer.operations.core.coreOperations
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class PluginWithheldOperationsTest {

	@Test
	fun `every operation that changes the sync account, syncs, or changes plugins is withheld from plugins`() {
		val changes = coreOperations()
			.filter { it.scope != OperationScope.Content && it.access != Access.Read }
			.map { it.name }
			.toSet()

		assertEquals(changes, PLUGIN_WITHHELD_OPERATIONS)
	}
}
