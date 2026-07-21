package com.darkrockstudios.apps.hammer

import com.darkrockstudios.apps.hammer.account.configureTokenMaintenanceJob
import com.darkrockstudios.apps.hammer.admin.configureWhitelistExpiryJob
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
import com.darkrockstudios.apps.hammer.utilities.DevSelfSignedCert
import com.darkrockstudios.apps.hammer.utilities.DiskCache
import com.darkrockstudios.apps.hammer.utilities.cacheDirectory
import com.darkrockstudios.apps.hammer.utilities.configureDiskCachePruneJob
import com.darkrockstudios.apps.hammer.utilities.getRootDataDirectory
import com.darkrockstudios.apps.hammer.utilities.loadPemAsKeyStore
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.choice
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.connector
import io.ktor.server.engine.embeddedServer
import io.ktor.server.engine.sslConnector
import io.ktor.server.jetty.jakarta.Jetty
import io.ktor.server.jetty.jakarta.JettyApplicationEngineBase
import io.ktor.util.logging.KtorSimpleLogger
import kotlinx.coroutines.runBlocking
import net.peanuuutz.tomlkt.Toml
import okio.FileSystem
import okio.IOException
import okio.Path
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

fun main(args: Array<String>) = HammerServerCommand()
	.subcommands(
		GenerateKeyringCommand(),
		InspectKeyringCommand(),
		RotateKeyCommand(),
		PruneKeyCommand(),
		MigrateSecretCommand()
	)
	.main(args)

class HammerServerCommand : CliktCommand(name = "Hammer Server") {
	override val invokeWithoutSubcommand = true

	private val configPath by option("-c", "--config", help = "Server Config Path")
	private val devMode by option("-d", "--dev", help = "Run in development mode").flag()
	private val logLevelArg by option("-l", "--logLevel", help = "Log Level")
		.choice("TRACE", "DEBUG", "INFO", "WARN", "ERROR")
	private val migrateDryRun by option(
		"--migrate-dry-run",
		help = "Run the SQLite-to-Postgres migration in verify-only mode (rolls back, never renames server.db)",
	).flag()
	private val convergeDryRun by option(
		"--converge-dry-run",
		help = "Report what encryption convergence would do (rows off-target, over-cap entities) and exit, writing nothing",
	).flag()

	override fun run() {
		if (currentContext.invokedSubcommand != null) return

		val logLevel = parseLogLevel(logLevelArg)
		val config: ServerConfig = resolveServerConfig(configPath)

		config.storage.validate()
		config.analytics.validate()

		// Dry-run paths exit before the server starts (and never bind a port).
		if (migrateDryRun) {
			val exit = com.darkrockstudios.apps.hammer.database.migration.SqliteToPostgresMigrator
				.runDryRun(config.storage, FileSystem.SYSTEM)
			exitProcess(exit)
		}
		if (convergeDryRun) {
			exitProcess(runConvergeDryRun(config))
		}

		// Auto-run the one-time SQLite → Postgres migration if a legacy server.db is
		// found alongside the Postgres config. NoOp on fresh installs.
		runOneTimeSqliteToPostgresMigration(config)

		startServer(config, devMode, logLevel)
	}
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
				"(${report.storyEntities} entities, ${report.reviewScenes} review scenes, " +
				"${report.storyIdeas} ideas)."
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

const val DEFAULT_CONFIG_FILE_NAME = "config.toml"

private val configLogger = KtorSimpleLogger("HammerServer")

/**
 * Resolves the server config. An explicit `--config` path always wins. Absent that, a
 * `config.toml` in the data directory (`~/hammer_data/`) is loaded if present; otherwise
 * built-in defaults are used. A config file that exists but fails to parse aborts startup
 * rather than silently falling back to defaults.
 */
internal fun resolveServerConfig(
	configPath: String?,
	fileSystem: FileSystem = FileSystem.SYSTEM,
): ServerConfig {
	val configFile: Path? = if (configPath != null) {
		configPath.toPath()
	} else {
		val defaultConfig = getRootDataDirectory(fileSystem) / DEFAULT_CONFIG_FILE_NAME
		if (fileSystem.exists(defaultConfig)) {
			configLogger.info("Loading config from default location: $defaultConfig")
			defaultConfig
		} else {
			null
		}
	}

	val config = configFile?.let { loadConfig(fileSystem, it) } ?: ServerConfig()
	// Validated before resolution: a blank directory resolves to the config file's own directory,
	// which would look valid and quietly put the caches next to the database.
	config.cache.validate()

	val resolved = resolveCacheDirectory(resolveConfigFilePaths(config, configFile?.parent), configFile?.parent)
	validateConfigFiles(resolved, fileSystem)
	validateCacheDirectory(resolved.cache, fileSystem)
	return resolved
}

/** [CacheConfig.directory] follows the same relative-to-the-config-file rule as the file settings. */
private fun resolveCacheDirectory(config: ServerConfig, configDir: Path?): ServerConfig {
	val path = config.cache.directory?.toPath() ?: return config
	if (path.isAbsolute || configDir == null) return config
	return config.copy(cache = config.cache.copy(directory = configDir.resolve(path).toString()))
}

/**
 * Operator-supplied plaintext files configured by a path in `config.toml` ([ServerConfig.termsOfService],
 * [ServerConfig.privacyPolicy]). Each is resolved relative to the config file's directory and validated
 * at startup by the same rules.
 */
private class ConfigFileSetting(
	val name: String,
	val path: (ServerConfig) -> String?,
	val withPath: (ServerConfig, String) -> ServerConfig,
)

private val configFileSettings = listOf(
	ConfigFileSetting("termsOfService", { it.termsOfService }, { c, p -> c.copy(termsOfService = p) }),
	ConfigFileSetting("privacyPolicy", { it.privacyPolicy }, { c, p -> c.copy(privacyPolicy = p) }),
)

/**
 * A relative config-file path is resolved against the config file's own directory, so a bare
 * `tos.txt` sitting next to `config.toml` is found regardless of the working directory. Absolute
 * paths are left untouched.
 */
private fun resolveConfigFilePaths(config: ServerConfig, configDir: Path?): ServerConfig =
	configFileSettings.fold(config) { current, setting ->
		val path = setting.path(current)?.toPath() ?: return@fold current
		if (path.isAbsolute || configDir == null) current
		else setting.withPath(current, configDir.resolve(path).toString())
	}

/**
 * Every configured config-file path must resolve to a readable, non-blank file. Aborting startup on
 * a bad path keeps a misconfiguration from silently disabling the feature it gates.
 */
private fun validateConfigFiles(config: ServerConfig, fileSystem: FileSystem) {
	for (setting in configFileSettings) {
		val raw = setting.path(config) ?: continue
		val path = raw.toPath()

		val metadata = fileSystem.metadataOrNull(path)
		check(metadata?.isRegularFile == true) {
			"${setting.name} is set to \"$raw\" but no readable file exists there."
		}
		check(fileSystem.read(path) { readUtf8() }.isNotBlank()) {
			"${setting.name} file \"$raw\" is empty; provide the text or remove the setting."
		}
	}
}

/**
 * A configured cache directory must be creatable and writable. The caches treat every IO failure as
 * a miss, so an unusable directory would otherwise leave the server quietly re-rendering every page
 * — a config mistake that presents as Hammer being slow.
 */
private fun validateCacheDirectory(config: CacheConfig, fileSystem: FileSystem) {
	if (config.directory == null) return

	// Probed per cache rather than on the root alone: a writable root can still hold a
	// subdirectory left behind by another user, which is where the entries actually go.
	for (cache in DiskCache.entries) {
		val directory = cacheDirectory(config, fileSystem, cache)
		val probe = directory / ".write-probe"
		try {
			fileSystem.createDirectories(directory)
			fileSystem.write(probe) { writeUtf8("hammer") }
		} catch (e: IOException) {
			error("cache.directory \"${config.directory}\" is not writable: $directory (${e.message})")
		} finally {
			runCatching { fileSystem.delete(probe, mustExist = false) }
		}
	}
}

fun loadConfig(path: String): ServerConfig = loadConfig(FileSystem.SYSTEM, path.toPath())

fun loadConfig(fileSystem: FileSystem, path: Path): ServerConfig =
	fileSystem.readToml(path, Toml { ignoreUnknownKeys = true }, ServerConfig::class)

private fun startServer(config: ServerConfig, devMode: Boolean, logLevel: Level?) {
	System.setProperty("io.ktor.development", devMode.toString())

	// This is overkill most of the time
	//	if(devMode) {
	//		// Sets the log mode for SLFJ, if we ever move to Logback, we'll need to set this a different way
	//		System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "DEBUG");
	//	}

	embeddedServer(
		Jetty,
		configure = {
			configureServer(config, devMode)
		},
		module = {
			appMain(config, logLevel = logLevel)
		}
	).start(wait = true)
}

private fun JettyApplicationEngineBase.Configuration.configureServer(
	config: ServerConfig,
	devMode: Boolean,
) {
	require(config.bindHosts.isNotEmpty()) { "bindHosts must list at least one address" }

	// The plain connector stays so reverse-proxy deployments can forward plain HTTP to Hammer;
	// clients themselves are HTTPS-only and talk TLS to the proxy.
	config.bindHosts.forEach { bindHost ->
		connector {
			port = config.port
			host = bindHost
		}
	}

	val sslCert = config.sslCert
	if (sslCert != null) {
		require(sslCert.validate()) { "SSL config must have either keystore (path + storePassword) or PEM files (certChainPath + privateKeyPath)" }

		val keyStore = getKeyStore(sslCert)
		val alias = if (sslCert.usePem()) "server" else (sslCert.keyAlias ?: "")
		val storePass = if (sslCert.usePem()) "" else (sslCert.storePassword ?: "")
		val keyPass = if (sslCert.usePem()) "" else (sslCert.keyPassword ?: "")
		val keyStorePath = if (!sslCert.usePem() && sslCert.path != null) File(sslCert.path) else null

		bindSslConnectors(config, keyStore, alias, storePass, keyPass, config.sslPort, keyStorePath)
	} else if (devMode) {
		configureDevTlsConnector(config)
	}
}

/**
 * Binds a TLS connector backed by an auto-generated self-signed cert, for the dev loop where
 * no real certificate is configured. Never reached in production: absent an [ServerConfig.sslCert]
 * a production server serves plain HTTP only (the reverse-proxy deployment shape).
 */
private fun JettyApplicationEngineBase.Configuration.configureDevTlsConnector(config: ServerConfig) {
	val dev = DevSelfSignedCert.getOrCreate()
	// 443 is privileged on Linux/macOS; when the operator hasn't overridden the default, fall back
	// to a non-privileged port so the dev server boots without root.
	val port = if (config.sslPort == ServerConfig.DEFAULT_SSL_PORT) DEV_TLS_FALLBACK_PORT else config.sslPort
	configLogger.warn(
		"Dev mode: no sslCert configured, serving a self-signed certificate on port $port " +
			"(keystore: ${dev.path}). Clients must trust it — the desktop --dev client does so automatically."
	)
	bindSslConnectors(config, dev.keyStore, dev.alias, dev.password, dev.password, port, keyStorePath = null)
}

/** Binds one TLS connector per bind host, sharing the same keystore-backed configuration. */
private fun JettyApplicationEngineBase.Configuration.bindSslConnectors(
	config: ServerConfig,
	keyStore: KeyStore,
	alias: String,
	storePassword: String,
	keyPassword: String,
	sslPort: Int,
	keyStorePath: File?,
) {
	config.bindHosts.forEach { bindHost ->
		sslConnector(
			keyStore = keyStore,
			keyAlias = alias,
			keyStorePassword = { storePassword.toCharArray() },
			privateKeyPassword = { keyPassword.toCharArray() },
		) {
			if (keyStorePath != null) this.keyStorePath = keyStorePath
			host = bindHost
			port = sslPort
		}
	}
}

private const val DEV_TLS_FALLBACK_PORT = 8443

internal fun getKeyStore(sslConfig: SslCertConfig): KeyStore {
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
	configureTokenMaintenanceJob()
	configureWhitelistExpiryJob()
	configureDiskCachePruneJob()
}

fun cliKeyringCodec(): KeyringCodec =
	KeyringCodec(SecureRandom.getInstanceStrong(), createTokenBase64())

