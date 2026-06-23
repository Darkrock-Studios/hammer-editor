package com.darkrockstudios.apps.hammer

import com.darkrockstudios.apps.hammer.secret.KeyRole
import com.darkrockstudios.apps.hammer.secret.KeyringManager
import com.darkrockstudios.apps.hammer.secret.buildSecretProvider
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.CliktError
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.choice
import okio.FileSystem
import okio.Path.Companion.toPath

class RotateKeyCommand : CliktCommand(name = "rotate-key") {
	override fun help(context: Context) =
		"Add a new key generation to a role and make it active (offline rotation)"

	private val roleArg by option("-r", "--role", help = "Which role to rotate")
		.choice("content", "tokenHmac").default("content")

	private val inPath by option(
		"-i", "--in",
		help = "Keyring file to rotate; overrides the configured provider",
	)

	private val configPath by option(
		"-c", "--config",
		help = "Server config; with no --in, the current keyring is read from its [secret] provider",
	)

	private val out by option(
		"-o", "--out",
		help = "Write the rotated keyring here instead of stdout",
	)

	override fun run() {
		val role = KeyRole.fromConfigName(roleArg)
		val codec = cliKeyringCodec()
		val current = if (inPath != null) {
			val source = inPath!!.toPath()
			if (!FileSystem.SYSTEM.exists(source)) {
				throw CliktError("No keyring file at $inPath")
			}
			codec.parse(FileSystem.SYSTEM.read(source) { readUtf8() })
		} else {
			val config = configPath?.let { loadConfig(it) } ?: ServerConfig()
			val manager = KeyringManager(
				buildSecretProvider(config.secret, FileSystem.SYSTEM),
				codec,
				FileSystem.SYSTEM,
				KeyringManager.legacySecretPath(),
			)
			manager.keyringOrNull() ?: throw CliktError(
				"No keyring found via the configured ${config.secret.provider} provider " +
					"(and no legacy server.secret to grandfather)."
			)
		}
		val json = codec.serialize(codec.rotate(current, role))
		val target = out
		if (target != null) {
			val path = target.toPath()
			path.parent?.let { FileSystem.SYSTEM.createDirectories(it) }
			FileSystem.SYSTEM.write(path) { writeUtf8(json) }
			echo("Wrote rotated keyring to $target")
		} else {
			echo(json)
		}
	}
}
