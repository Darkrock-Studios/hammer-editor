package com.darkrockstudios.apps.hammer

import com.darkrockstudios.apps.hammer.secret.KeyringManager
import com.darkrockstudios.apps.hammer.secret.buildSecretProvider
import com.darkrockstudios.apps.hammer.secret.keyringSummary
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.CliktError
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.options.option
import okio.FileSystem
import okio.Path.Companion.toPath

class InspectKeyringCommand : CliktCommand(name = "inspect-keyring") {
	override fun help(context: Context) =
		"Show keyring key ids and active selections (never key bytes)"

	private val inPath by option(
		"-i", "--in",
		help = "Keyring file to inspect; overrides the configured provider",
	)

	private val configPath by option(
		"-c", "--config",
		help = "Server config; with no --in, the keyring is read from its [secret] provider",
	)

	override fun run() {
		val keyring = if (inPath != null) {
			val path = inPath!!.toPath()
			if (!FileSystem.SYSTEM.exists(path)) {
				throw CliktError("No keyring file at $inPath")
			}
			cliKeyringCodec().parse(FileSystem.SYSTEM.read(path) { readUtf8() })
		} else {
			val config = configPath?.let { loadConfig(it) } ?: ServerConfig()
			val manager = KeyringManager(
				buildSecretProvider(config.secret, FileSystem.SYSTEM),
				cliKeyringCodec(),
				FileSystem.SYSTEM,
				KeyringManager.legacySecretPath(),
			)
			manager.keyringOrNull() ?: throw CliktError(
				"No keyring found via the configured ${config.secret.provider} provider " +
					"(and no legacy server.secret to grandfather)."
			)
		}
		echo(keyringSummary(keyring))
	}
}
