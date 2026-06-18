package com.darkrockstudios.apps.hammer.secret

class KeyPruneException(message: String) : IllegalStateException(message)

data class PruneResult(
	val keyring: Keyring,
	/** Key ids removed from the role. */
	val pruned: List<String>,
	/** Content key ids left in place because rows are still encrypted with them. */
	val keptReferenced: List<String>,
)

/**
 * Removes unused, non-active key generations from a role, yielding a new keyring.
 *
 * A CONTENT generation is in-use while any row still carries its key id, so
 * [inUseContentKeyIds] (the key ids present in the database) decides what is safe
 * to drop. TOKEN_HMAC keys are never recorded per-row — only the active key
 * verifies tokens — so every non-active token generation is dead and pruned.
 *
 * The active generation is never removed (a role must keep a usable key). With
 * [targetKey] set, only that one generation is considered, and asking to prune the
 * active or a still-referenced generation is an error, not a silent no-op.
 */
class KeyPruner {
	fun prune(
		keyring: Keyring,
		role: KeyRole,
		inUseContentKeyIds: Set<String>,
		targetKey: String? = null,
	): PruneResult {
		val roleKeys = role.select(keyring)

		fun referenced(id: String): Boolean =
			role == KeyRole.CONTENT && id in inUseContentKeyIds

		if (targetKey != null) {
			if (targetKey !in roleKeys.keys) {
				throw KeyPruneException("Key '$targetKey' is not a ${role.configName} generation.")
			}
			if (targetKey == roleKeys.active) {
				throw KeyPruneException("Key '$targetKey' is the active ${role.configName} generation and cannot be pruned.")
			}
			if (referenced(targetKey)) {
				throw KeyPruneException(
					"Key '$targetKey' still has rows encrypted with it; run convergence before pruning it."
				)
			}
			return PruneResult(
				keyring = role.replace(keyring, roleKeys.copy(keys = roleKeys.keys - targetKey)),
				pruned = listOf(targetKey),
				keptReferenced = emptyList(),
			)
		}

		val pruned = mutableListOf<String>()
		val kept = mutableListOf<String>()
		val surviving = roleKeys.keys.filter { (id, _) ->
			when {
				id == roleKeys.active -> true
				referenced(id) -> { kept += id; true }
				else -> { pruned += id; false }
			}
		}
		return PruneResult(
			keyring = role.replace(keyring, roleKeys.copy(keys = surviving)),
			pruned = pruned.sorted(),
			keptReferenced = kept.sorted(),
		)
	}
}
