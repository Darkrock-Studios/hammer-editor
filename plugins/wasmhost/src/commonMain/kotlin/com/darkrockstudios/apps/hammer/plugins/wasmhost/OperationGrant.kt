package com.darkrockstudios.apps.hammer.plugins.wasmhost

import com.darkrockstudios.apps.hammer.operations.Access
import com.darkrockstudios.apps.hammer.operations.OperationRegistry
import com.darkrockstudios.apps.hammer.operations.OperationScope
import com.darkrockstudios.apps.hammer.operations.core.OperationDescriptor

/**
 * One entry of a manifest's `permissions.operations`: an operation by name, such as `scene.read`, or
 * every Read or Write operation in a scope, such as `content:write`. Destructive operations are only
 * ever granted by name.
 */
sealed interface OperationGrant {
	fun covers(op: OperationDescriptor): Boolean

	data class Named(val name: String) : OperationGrant {
		override fun covers(op: OperationDescriptor) = op.name == name
	}

	data class Scoped(val scope: OperationScope, val access: Access) : OperationGrant {
		override fun covers(op: OperationDescriptor) = op.scope == scope && op.access == access
	}

	companion object {
		/** Null when [entry] is neither an operation name nor a scope with `read` or `write`. */
		fun parse(entry: String): OperationGrant? {
			if (OperationRegistry.isValidName(entry)) return Named(entry)
			val (scope, access) = entry.split(':').takeIf { it.size == 2 } ?: return null
			return Scoped(
				scope = OperationScope.entries.firstOrNull { it.name.lowercase() == scope } ?: return null,
				access = when (access) {
					"read" -> Access.Read
					"write" -> Access.Write
					else -> return null
				},
			)
		}
	}
}
