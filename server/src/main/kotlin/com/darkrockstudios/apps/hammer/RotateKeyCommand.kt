package com.darkrockstudios.apps.hammer

import com.darkrockstudios.apps.hammer.secret.KeyRole
import com.darkrockstudios.apps.hammer.secret.KeyringManager
import com.darkrockstudios.apps.hammer.secret.buildSecretProvider
import kotlinx.cli.ArgType
import kotlinx.cli.ExperimentalCli
import kotlinx.cli.Subcommand
import kotlinx.cli.default
import okio.FileSystem
import okio.Path.Companion.toPath
import kotlin.system.exitProcess

@OptIn(ExperimentalCli::class)
class RotateKeyCommand : Subcommand(
	"rotate-key",
	"Add a new key generation to a role and make it active (offline rotation)",
) {
	private val roleArg by option(
		ArgType.Choice(listOf("content", "tokenHmac"), { it }),
		shortName = "r", fullName = "role",
		description = "Which role to rotate",
	).default("content")

	private val inPath by option(
		ArgType.String, shortName = "i", fullName = "in",
		description = "Keyring file to rotate; overrides the configured provider",
	)

	private val configPath by option(
		ArgType.String, shortName = "c", fullName = "config",
		description = "Server config; with no --in, the current keyring is read from its [secret] provider",
	)

	private val out by option(
		ArgType.String, shortName = "o", fullName = "out",
		description = "Write the rotated keyring here instead of stdout",
	)

	override fun execute() {
		val role = if (roleArg == "tokenHmac") KeyRole.TOKEN_HMAC else KeyRole.CONTENT
		val codec = cliKeyringCodec()
		val current = if (inPath != null) {
			val source = inPath!!.toPath()
			if (!FileSystem.SYSTEM.exists(source)) {
				System.err.println("No keyring file at $inPath")
				exitProcess(1)
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
			manager.keyringOrNull() ?: run {
				System.err.println(
					"No keyring found via the configured ${config.secret.provider} provider " +
						"(and no legacy server.secret to grandfather)."
				)
				exitProcess(1)
			}
		}
		val json = codec.serialize(codec.rotate(current, role))
		val target = out
		if (target != null) {
			val path = target.toPath()
			path.parent?.let { FileSystem.SYSTEM.createDirectories(it) }
			FileSystem.SYSTEM.write(path) { writeUtf8(json) }
			println("Wrote rotated keyring to $target")
		} else {
			println(json)
		}
		exitProcess(0)
	}
}