package com.darkrockstudios.apps.hammer

import com.darkrockstudios.apps.hammer.secret.KeyringManager
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.CliktError
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.options.option
import okio.FileSystem
import okio.Path.Companion.toPath

class MigrateSecretCommand : CliktCommand(name = "migrate-secret") {
	override fun help(context: Context) =
		"Build a keyring from a legacy server.secret; its value becomes content.v1 and tokenHmac.v1"

	private val inPath by option(
		"-i", "--in",
		help = "Path to the legacy server.secret; defaults to the standard hammer_data location",
	)

	private val out by option(
		"-o", "--out",
		help = "Write the keyring here instead of stdout",
	)

	override fun run() {
		val codec = cliKeyringCodec()
		val secretPath = inPath?.toPath() ?: KeyringManager.legacySecretPath()
		if (!FileSystem.SYSTEM.exists(secretPath)) {
			throw CliktError("No server.secret found at $secretPath. Pass --in <path> if it lives elsewhere.")
		}

		// Read verbatim — the running server grandfathers this exact string (trailing
		// newline included), so the emitted v1 must match it byte-for-byte or existing
		// data stops decrypting.
		val legacy = FileSystem.SYSTEM.read(secretPath) { readUtf8() }
		val keyring = codec.grandfather(legacy)
		try {
			keyring.validate()
		} catch (e: IllegalArgumentException) {
			throw CliktError("The secret at $secretPath produced an invalid keyring: ${e.message}")
		}

		val json = codec.serialize(keyring)
		val target = out
		if (target != null) {
			val path = target.toPath()
			path.parent?.let { FileSystem.SYSTEM.createDirectories(it) }
			FileSystem.SYSTEM.write(path) { writeUtf8(json) }
			echo("Wrote keyring (from $secretPath) to $target")
		} else {
			echo(json)
		}
		echo(
			"This keyring reuses the legacy secret as v1 for both roles. Place it in your configured " +
				"secret provider, set [encryption] mode = \"aes\", then optionally rotate to a fresh generation.",
			err = true,
		)
	}
}
