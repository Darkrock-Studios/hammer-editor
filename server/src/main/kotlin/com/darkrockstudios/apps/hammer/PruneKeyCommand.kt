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
import io.ktor.util.logging.KtorSimpleLogger
import kotlinx.cli.ArgType
import kotlinx.cli.ExperimentalCli
import kotlinx.cli.Subcommand
import kotlinx.cli.default
import okio.FileSystem
import okio.Path.Companion.toPath
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.system.exitProcess

@OptIn(ExperimentalCli::class)
class PruneKeyCommand : Subcommand(
	"prune-key",
	"Remove unused, non-active key generations from a role (offline cleanup after convergence)",
) {
	private val roleArg by option(
		ArgType.Choice(listOf("content", "tokenHmac"), { it }),
		shortName = "r", fullName = "role",
		description = "Which role to prune",
	).default("content")

	private val inPath by option(
		ArgType.String, shortName = "i", fullName = "in",
		description = "Keyring file to prune; overrides the configured provider",
	)

	private val configPath by option(
		ArgType.String, shortName = "c", fullName = "config",
		description = "Server config; supplies the keyring provider and the database to verify content keys against",
	)

	private val keyArg by option(
		ArgType.String, shortName = "k", fullName = "key",
		description = "Prune only this generation; fails if it is active or still has rows",
	)

	private val out by option(
		ArgType.String, shortName = "o", fullName = "out",
		description = "Write the pruned keyring here instead of stdout",
	)

	private val dryRun by option(
		ArgType.Boolean, fullName = "dry-run",
		description = "Report what would be pruned and write nothing",
	).default(false)

	override fun execute() {
		val role = KeyRole.fromConfigName(roleArg)
		val codec = cliKeyringCodec()
		val config = configPath?.let { loadConfig(it) }
		val keyring = loadKeyring(codec, config)

		val inUseContentKeyIds = if (role == KeyRole.CONTENT) {
			val cfg = config ?: run {
				System.err.println(
					"Pruning content keys needs database access to confirm which generations are unused. " +
						"Pass --config <serverConfig.toml>."
				)
				exitProcess(1)
			}
			scanInUseContentKeyIds(cfg)
		} else {
			emptySet()
		}

		val result = try {
			KeyPruner().prune(keyring, role, inUseContentKeyIds, keyArg)
		} catch (e: KeyPruneException) {
			System.err.println(e.message)
			exitProcess(1)
		}

		if (result.pruned.isEmpty()) {
			println("Nothing to prune: no unused non-active ${role.configName} generations.")
		} else {
			println("Pruned ${role.configName} generations: ${result.pruned.joinToString()}")
		}
		if (result.keptReferenced.isNotEmpty()) {
			println(
				"Kept (rows still encrypted with them — run convergence first): " +
					result.keptReferenced.joinToString()
			)
		}

		if (dryRun) {
			println("Dry run: keyring not written.")
			exitProcess(0)
		}

		val json = codec.serialize(result.keyring)
		val target = out
		if (target != null) {
			val path = target.toPath()
			path.parent?.let { FileSystem.SYSTEM.createDirectories(it) }
			FileSystem.SYSTEM.write(path) { writeUtf8(json) }
			println("Wrote pruned keyring to $target")
		} else {
			println(json)
		}
		exitProcess(0)
	}

	private fun loadKeyring(codec: KeyringCodec, config: ServerConfig?): Keyring {
		inPath?.let { path ->
			val source = path.toPath()
			if (!FileSystem.SYSTEM.exists(source)) {
				System.err.println("No keyring file at $path")
				exitProcess(1)
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
		return manager.keyringOrNull() ?: run {
			System.err.println(
				"No keyring found via the configured ${cfg.secret.provider} provider " +
					"(and no legacy server.secret to grandfather)."
			)
			exitProcess(1)
		}
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
				database.serverDatabase.reviewSceneQueries.distinctCiphers().executeAsList()
			tags.filterNotNull().mapNotNull { AesGcmContentEncryptor.keyIdForTag(it) }.toSet()
		} catch (e: Exception) {
			System.err.println("Could not read the database to verify which content keys are in use: ${e.message}")
			exitProcess(1)
		} finally {
			database.close()
			koinApp.close()
		}
	}
}
