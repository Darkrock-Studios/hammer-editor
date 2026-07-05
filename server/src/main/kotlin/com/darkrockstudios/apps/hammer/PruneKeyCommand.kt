package com.darkrockstudios.apps.hammer

import com.darkrockstudios.apps.hammer.database.Database
import com.darkrockstudios.apps.hammer.dependencyinjection.mainModule
import com.darkrockstudios.apps.hammer.encryption.AesGcmContentEncryptor
import com.darkrockstudios.apps.hammer.secret.KeyPruneException
import com.darkrockstudios.apps.hammer.secret.KeyPruner
import com.darkrockstudios.apps.hammer.secret.KeyRole
import com.darkrockstudios.apps.hammer.secret.Keyring
import com.darkrockstudios.apps.hammer.secret.KeyringCodec
import com.darkrockstudios.apps.hammer.secret.KeyringManager
import com.darkrockstudios.apps.hammer.secret.buildSecretProvider
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.CliktError
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.choice
import io.ktor.util.logging.KtorSimpleLogger
import okio.FileSystem
import okio.Path.Companion.toPath
import org.koin.dsl.koinApplication
import org.koin.dsl.module

class PruneKeyCommand : CliktCommand(name = "prune-key") {
	override fun help(context: Context) =
		"Remove unused, non-active key generations from a role (offline cleanup after convergence)"

	private val roleArg by option("-r", "--role", help = "Which role to prune")
		.choice("content", "tokenHmac").default("content")

	private val inPath by option(
		"-i", "--in",
		help = "Keyring file to prune; overrides the configured provider",
	)

	private val configPath by option(
		"-c", "--config",
		help = "Server config; supplies the keyring provider and the database to verify content keys against",
	)

	private val keyArg by option(
		"-k", "--key",
		help = "Prune only this generation; fails if it is active or still has rows",
	)

	private val out by option(
		"-o", "--out",
		help = "Write the pruned keyring here instead of stdout",
	)

	private val dryRun by option(
		"--dry-run",
		help = "Report what would be pruned and write nothing",
	).flag()

	override fun run() {
		val role = KeyRole.fromConfigName(roleArg)
		val codec = cliKeyringCodec()
		val config = configPath?.let { loadConfig(it) }
		val keyring = loadKeyring(codec, config)

		val inUseContentKeyIds = if (role == KeyRole.CONTENT) {
			val cfg = config ?: throw CliktError(
				"Pruning content keys needs database access to confirm which generations are unused. " +
					"Pass --config <serverConfig.toml>."
			)
			scanInUseContentKeyIds(cfg)
		} else {
			emptySet()
		}

		val result = try {
			KeyPruner().prune(keyring, role, inUseContentKeyIds, keyArg)
		} catch (e: KeyPruneException) {
			throw CliktError(e.message)
		}

		if (result.pruned.isEmpty()) {
			echo("Nothing to prune: no unused non-active ${role.configName} generations.")
		} else {
			echo("Pruned ${role.configName} generations: ${result.pruned.joinToString()}")
		}
		if (result.keptReferenced.isNotEmpty()) {
			echo(
				"Kept (rows still encrypted with them — run convergence first): " +
					result.keptReferenced.joinToString()
			)
		}

		if (dryRun) {
			echo("Dry run: keyring not written.")
			return
		}

		val json = codec.serialize(result.keyring)
		val target = out
		if (target != null) {
			val path = target.toPath()
			path.parent?.let { FileSystem.SYSTEM.createDirectories(it) }
			FileSystem.SYSTEM.write(path) { writeUtf8(json) }
			echo("Wrote pruned keyring to $target")
		} else {
			echo(json)
		}
	}

	private fun loadKeyring(codec: KeyringCodec, config: ServerConfig?): Keyring {
		inPath?.let { path ->
			val source = path.toPath()
			if (!FileSystem.SYSTEM.exists(source)) {
				throw CliktError("No keyring file at $path")
			}
			return codec.parse(FileSystem.SYSTEM.read(source) { readUtf8() })
		}

		val cfg = config ?: ServerConfig()
		val manager = KeyringManager(
			buildSecretProvider(cfg.secret, FileSystem.SYSTEM),
			codec,
			FileSystem.SYSTEM,
			KeyringManager.legacySecretPath(),
		)
		return manager.keyringOrNull() ?: throw CliktError(
			"No keyring found via the configured ${cfg.secret.provider} provider " +
				"(and no legacy server.secret to grandfather)."
		)
	}

	private fun scanInUseContentKeyIds(config: ServerConfig): Set<String> {
		val koinApp = koinApplication {
			modules(
				mainModule(KtorSimpleLogger("PruneKey")),
				module { single { config } },
			)
		}
		val database: Database = koinApp.koin.get()
		return try {
			database.initialize()
			val tags = database.serverDatabase.storyEntityQueries.distinctCiphers().executeAsList() +
				database.serverDatabase.reviewSceneQueries.distinctCiphers().executeAsList() +
				database.serverDatabase.storyIdeaQueries.distinctCiphers().executeAsList()
			tags.filterNotNull().mapNotNull { AesGcmContentEncryptor.keyIdForTag(it) }.toSet()
		} catch (e: Exception) {
			throw CliktError("Could not read the database to verify which content keys are in use: ${e.message}")
		} finally {
			database.close()
			koinApp.close()
		}
	}
}
