package com.darkrockstudios.apps.hammer

import com.darkrockstudios.apps.hammer.base.http.createTokenBase64
import com.darkrockstudios.apps.hammer.base.http.readToml
import com.darkrockstudios.apps.hammer.encryption.EncryptionModeGuard
import com.darkrockstudios.apps.hammer.frontend.configureFrontEnd
import com.darkrockstudios.apps.hammer.secret.KeyringCodec
import com.darkrockstudios.apps.hammer.secret.KeyringManager
import com.darkrockstudios.apps.hammer.secret.keyringSummary
import com.darkrockstudios.apps.hammer.monitoring.configureApiMetrics
import com.darkrockstudios.apps.hammer.monitoring.configureRouteTemplateCapture
import com.darkrockstudios.apps.hammer.monitoring.configureMonitoringJob
import com.darkrockstudios.apps.hammer.patreon.configurePatreonPolling
import com.darkrockstudios.apps.hammer.plugins.*
import com.darkrockstudios.apps.hammer.utilities.loadPemAsKeyStore
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.jetty.jakarta.*
import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.Subcommand
import kotlinx.cli.default
import kotlinx.coroutines.runBlocking
import net.peanuuutz.tomlkt.Toml
import okio.FileSystem
import okio.Path.Companion.toPath
import org.koin.core.module.Module
import org.koin.ktor.ext.inject
import org.slf4j.event.Level
import java.io.File
import java.security.KeyStore

fun main(args: Array<String>) {
	val parser = ArgParser("Hammer Server")
	val configPathArg by parser.option(
		ArgType.String,
		shortName = "c",
		fullName = "config",
		description = "Server Config Path"
	)
	val devModeArg by parser.option(
		ArgType.Boolean,
		shortName = "d",
		fullName = "dev",
		description = "Run in development mode"
	)
	val logLevelArg by parser.option(
		ArgType.Choice(listOf("TRACE", "DEBUG", "INFO", "WARN", "ERROR"), { it }),
		shortName = "l",
		fullName = "logLevel",
		description = "Log Level"
	)

	val migrateDryRunArg by parser.option(
		ArgType.Boolean,
		fullName = "migrate-dry-run",
		description = "Run the SQLite-to-Postgres migration in verify-only mode (rolls back, never renames server.db)"
	)

	parser.subcommands(GenerateKeyringCommand(), InspectKeyringCommand())

	parser.parse(args)

	val logLevel = parseLogLevel(logLevelArg)

	val config: ServerConfig = configPathArg?.let {
		loadConfig(it)
	} ?: ServerConfig()

	config.storage.validate()
	config.analytics.validate()

	// Dry-run path exits before the server starts.
	if (migrateDryRunArg == true) {
		val exit = com.darkrockstudios.apps.hammer.database.migration.SqliteToPostgresMigrator
			.runDryRun(config.storage, FileSystem.SYSTEM)
		kotlin.system.exitProcess(exit)
	}

	// Auto-run the one-time SQLite → Postgres migration if a legacy server.db is
	// found alongside the Postgres config. NoOp on fresh installs.
	runOneTimeSqliteToPostgresMigration(config)

	startServer(config, devModeArg ?: false, logLevel)
}

private fun runOneTimeSqliteToPostgresMigration(config: ServerConfig) {
	val migrator = com.darkrockstudios.apps.hammer.database.migration.SqliteToPostgresMigrator(
		config.storage,
		FileSystem.SYSTEM,
	)
	when (val result = migrator.run()) {
		is com.darkrockstudios.apps.hammer.database.migration.SqliteToPostgresMigrator.Result.NoOp -> { /* normal boot */ }
		is com.darkrockstudios.apps.hammer.database.migration.SqliteToPostgresMigrator.Result.Success -> {
			println("Migrated SQLite -> Postgres. Backup: ${result.backupPath}. Rows: ${result.rowCounts}")
		}
		is com.darkrockstudios.apps.hammer.database.migration.SqliteToPostgresMigrator.Result.Aborted -> {
			System.err.println("SQLite → Postgres migration aborted: ${result.reason}")
			kotlin.system.exitProcess(1)
		}
	}
}

private fun parseLogLevel(logLevelArg: String?): Level? {
	return try {
		logLevelArg?.let { Level.valueOf(it.uppercase()) }
	} catch (_: Exception) {
		null
	}
}

private fun loadConfig(path: String): ServerConfig {
	return FileSystem.SYSTEM.readToml(path.toPath(), Toml { ignoreUnknownKeys = true }, ServerConfig::class)
}

private fun startServer(config: ServerConfig, devMode: Boolean, logLevel: Level?) {
	System.setProperty("io.ktor.development", devMode.toString())

	// This is overkill most of the time
	//	if(devMode) {
	//		// Sets the log mode for SLFJ, if we ever move to Logback, we'll need to set this a different way
	//		System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "DEBUG");
	//	}

	val bindHost = "0.0.0.0"

	embeddedServer(
		Jetty,
		configure = {
			configureServer(config, bindHost)
		},
		module = {
			appMain(config, logLevel = logLevel)
		}
	).start(wait = true)
}

private fun JettyApplicationEngineBase.Configuration.configureServer(
	config: ServerConfig,
	bindHost: String
) {
	connector {
		port = config.port
		host = bindHost
	}

	config.sslCert?.apply {
		require(validate()) { "SSL config must have either keystore (path + storePassword) or PEM files (certChainPath + privateKeyPath)" }

		val keyStore = getKeyStore(this)
		val alias = if (usePem()) "server" else (keyAlias ?: "")
		val storePass = if (usePem()) "" else (storePassword ?: "")
		val keyPass = if (usePem()) "" else (keyPassword ?: "")

		sslConnector(
			keyStore = keyStore,
			keyAlias = alias,
			keyStorePassword = { storePass.toCharArray() },
			privateKeyPassword = { keyPass.toCharArray() }
		) {
			if (!usePem() && path != null) {
				this.keyStorePath = File(path)
			}
			host = bindHost
			port = config.sslPort
		}
	}
}

private fun getKeyStore(sslConfig: SslCertConfig): KeyStore {
	return if (sslConfig.usePem()) {
		loadPemAsKeyStore(
			certChainPath = sslConfig.certChainPath ?: error("PEM cert chain path not set"),
			privateKeyPath = sslConfig.privateKeyPath ?: error("PEM private key path not set"),
			keyAlias = "server",
			keyPassword = ""
		)
	} else {
		val certFile = File(sslConfig.path ?: error("Keystore path not set"))
		if (certFile.exists().not()) throw IllegalArgumentException("SSL Cert not found: ${sslConfig.path}")
		sslConfig.storePassword ?: error("Keystore password not set")
		KeyStore.getInstance(certFile, sslConfig.storePassword.toCharArray())
	}
}

fun Application.appMain(
	config: ServerConfig,
	addInModule: Module? = null,
	logLevel: Level? = null
) {
	configureDependencyInjection(config, addInModule)
	val database: com.darkrockstudios.apps.hammer.database.Database by inject()
	EncryptionModeGuard.verifyOnBoot(config.encryption.mode, database.serverDatabase)
	if (config.encryption.mode == EncryptionMode.AES) {
		val keyringManager: com.darkrockstudios.apps.hammer.secret.KeyringManager by inject()
		keyringManager.requireContentKey()
	}
	configureSerialization()
	configureMonitoring(logLevel)
	configureApiMetrics()
	configureRouteTemplateCapture()
	configureHTTP(config)
	configureSecurity()
	configureLocalization()
	install(SetupModePlugin)
	configureRouting(config)
	configureFrontEnd()
	configurePatreonPolling(config)
	configureMonitoringJob()
}

private fun cliKeyringCodec(): KeyringCodec =
	KeyringCodec(java.security.SecureRandom.getInstanceStrong(), createTokenBase64())

private class GenerateKeyringCommand : Subcommand(
	"generate-keyring",
	"Generate a fresh server keyring (both roles, active v1)",
) {
	private val out by option(
		ArgType.String, shortName = "o", fullName = "out",
		description = "Write the keyring to this file instead of stdout",
	)

	override fun execute() {
		val codec = cliKeyringCodec()
		val json = codec.serialize(codec.generate())
		val target = out
		if (target != null) {
			val path = target.toPath()
			path.parent?.let { FileSystem.SYSTEM.createDirectories(it) }
			FileSystem.SYSTEM.write(path) { writeUtf8(json) }
			println("Wrote keyring to $target")
		} else {
			println(json)
		}
		kotlin.system.exitProcess(0)
	}
}

private class InspectKeyringCommand : Subcommand(
	"inspect-keyring",
	"Show keyring key ids and active selections (never key bytes)",
) {
	private val inPath by option(
		ArgType.String, shortName = "i", fullName = "in",
		description = "Keyring file to inspect",
	).default(KeyringManager.defaultKeyringPath(FileSystem.SYSTEM).toString())

	override fun execute() {
		val path = inPath.toPath()
		if (!FileSystem.SYSTEM.exists(path)) {
			System.err.println("No keyring file at $inPath")
			kotlin.system.exitProcess(1)
		}
		val json = FileSystem.SYSTEM.read(path) { readUtf8() }
		val keyring = cliKeyringCodec().parse(json)
		println(keyringSummary(keyring))
		kotlin.system.exitProcess(0)
	}
}
