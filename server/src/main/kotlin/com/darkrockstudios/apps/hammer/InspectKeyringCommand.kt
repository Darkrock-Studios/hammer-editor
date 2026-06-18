package com.darkrockstudios.apps.hammer

import com.darkrockstudios.apps.hammer.secret.KeyringManager
import com.darkrockstudios.apps.hammer.secret.buildSecretProvider
import com.darkrockstudios.apps.hammer.secret.keyringSummary
import kotlinx.cli.ArgType
import kotlinx.cli.ExperimentalCli
import kotlinx.cli.Subcommand
import okio.FileSystem
import okio.Path.Companion.toPath
import kotlin.system.exitProcess

@OptIn(ExperimentalCli::class)
class InspectKeyringCommand : Subcommand(
	"inspect-keyring",
	"Show keyring key ids and active selections (never key bytes)",
) {
	private val inPath by option(
		ArgType.String, shortName = "i", fullName = "in",
		description = "Keyring file to inspect; overrides the configured provider",
	)

	private val configPath by option(
		ArgType.String, shortName = "c", fullName = "config",
		description = "Server config; with no --in, the keyring is read from its [secret] provider",
	)

	override fun execute() {
		val keyring = if (inPath != null) {
			val path = inPath!!.toPath()
			if (!FileSystem.SYSTEM.exists(path)) {
				System.err.println("No keyring file at $inPath")
				exitProcess(1)
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
			manager.keyringOrNull() ?: run {
				System.err.println(
					"No keyring found via the configured ${config.secret.provider} provider " +
						"(and no legacy server.secret to grandfather)."
				)
				exitProcess(1)
			}
		}
		println(keyringSummary(keyring))
		exitProcess(0)
	}
}