package com.darkrockstudios.apps.hammer.secret

/** Human-readable keyring summary: key ids and the active selection, never key bytes. */
fun keyringSummary(keyring: Keyring): String = buildString {
	appendLine("schema: ${keyring.schema}")
	appendLine(roleLine("content", keyring.content))
	append(roleLine("tokenHmac", keyring.tokenHmac))
}

private fun roleLine(role: String, keys: RoleKeys): String =
	"$role: active=${keys.active} keys=${keys.keys.keys.sorted()}"
