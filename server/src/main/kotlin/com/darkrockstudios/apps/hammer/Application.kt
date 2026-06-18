package com.darkrockstudios.apps.hammer

import com.darkrockstudios.apps.hammer.base.http.createTokenBase64
import com.darkrockstudios.apps.hammer.base.http.readToml
import com.darkrockstudios.apps.hammer.database.Database
import com.darkrockstudios.apps.hammer.dependencyinjection.mainModule
import com.darkrockstudios.apps.hammer.encryption.EncryptionBootstrap
import com.darkrockstudios.apps.hammer.frontend.configureFrontEnd
import com.darkrockstudios.apps.hammer.monitoring.configureApiMetrics
import com.darkrockstudios.apps.hammer.monitoring.configureMonitoringJob
import com.darkrockstudios.apps.hammer.monitoring.configureRouteTemplateCapture
import com.darkrockstudios.apps.hammer.patreon.configurePatreonPolling
import com.darkrockstudios.apps.hammer.plugins.SetupModePlugin
import com.darkrockstudios.apps.hammer.plugins.configureDependencyInjection
import com.darkrockstudios.apps.hammer.plugins.configureHTTP
import com.darkrockstudios.apps.hammer.plugins.configureLocalization
import com.darkrockstudios.apps.hammer.plugins.configureMonitoring
import com.darkrockstudios.apps.hammer.plugins.configureRouting
import com.darkrockstudios.apps.hammer.plugins.configureSecurity
import com.darkrockstudios.apps.hammer.plugins.configureSerialization
import com.darkrockstudios.apps.hammer.secret.KeyringCodec
import com.darkrockstudios.apps.hammer.utilities.loadPemAsKeyStore
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.connector
import io.ktor.server.engine.embeddedServer
import io.ktor.server.engine.sslConnector
import io.ktor.server.jetty.jakarta.Jetty
import io.ktor.server.jetty.jakarta.JettyApplicationEngineBase
import io.ktor.util.logging.KtorSimpleLogger
import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.ExperimentalCli
import kotlinx.coroutines.runBlocking
import net.peanuuutz.tomlkt.Toml
import okio.FileSystem
import okio.Path.Companion.toPath
import org.koin.core.module.Module
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import org.koin.ktor.ext.inject
import org.slf4j.event.Level
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import kotlin.system.exitProcess

@OptIn(ExperimentalCli::class)
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

	val convergeDryRunArg by parser.option(
		ArgType.Boolean,
		fullName = "converge-dry-run",
		description = "Report what encryption convergence would do (rows off-target, over-cap entities) and exit, writing nothing"
	)

	parser.subcommands(GenerateKeyringCommand(), InspectKeyringCommand(), RotateKeyCommand(), PruneKeyCommand())

	parser.parse(args)

	val logLevel = parseLogLevel(logLevelArg)

	val config: ServerConfig = configPathArg?.let {
		loadConfig(it)
	} ?: ServerConfig()

	config.storage.validate()
	config.analytics.validate()

	// Dry-run paths exit before the server starts (and never bind a port).
	if (migrateDryRunArg == true) {
		val exit = com.darkrockstudios.apps.hammer.database.migration.SqliteToPostgresMigrator
			.runDryRun(config.storage, FileSystem.SYSTEM)
		exitProcess(exit)
	}
	if (convergeDryRunArg == true) {
		exitProcess(runConvergeDryRun(config))
	}

	// Auto-run the one-time SQLite → Postgres migration if a legacy server.db is
	// found alongside the Postgres config. NoOp on fresh installs.
	runOneTimeSqliteToPostgresMigration(config)

	startServer(config, devModeArg ?: false, logLevel)
}

/**
 * Builds a standalone Koin graph (no HTTP engine), reports what encryption
 * convergence would do, and returns an exit code: 0 = would complete, 1 = some
 * entity would exceed the size cap and block convergence.
 */
private fun runConvergeDryRun(config: ServerConfig): Int {
	val koinApp = koinApplication {
		modules(
			mainModule(KtorSimpleLogger("ConvergeDryRun")),
			module { single { config } },
		)
	}
	val database: Database = koinApp.koin.get()
	database.initialize()
	return try {
		val report = runBlocking { koinApp.koin.get<EncryptionBootstrap>().dryRun() }
		println(
			"Convergence dry run: ${report.total} row(s) off target " +
				"(${report.storyEntities} entities, ${report.reviewScenes} review scenes)."
		)
		if (report.overCapEntities.isEmpty()) {
			println("No over-cap entities; convergence would complete.")
			0
		} else {
			println("${report.overCapEntities.size} entity(ies) would exceed the size cap and block convergence:")
			report.overCapEntities.forEach { println("  - $it") }
			1
		}
	} finally {
		database.close()
		koinApp.close()
	}
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
			exitProcess(1)
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

fun loadConfig(path: String): ServerConfig {
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
	logLevel: Level? = null,
) {
	configureDependencyInjection(config, addInModule)
	val encryptionBootstrap: EncryptionBootstrap by inject()
	runBlocking { encryptionBootstrap.run() }
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

fun cliKeyringCodec(): KeyringCodec =
	KeyringCodec(SecureRandom.getInstanceStrong(), createTokenBase64())

