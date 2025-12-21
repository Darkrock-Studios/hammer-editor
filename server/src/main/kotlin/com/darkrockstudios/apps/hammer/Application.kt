package com.darkrockstudios.apps.hammer

import com.darkrockstudios.apps.hammer.base.http.readToml
import com.darkrockstudios.apps.hammer.datamigrator.DataMigrator
import com.darkrockstudios.apps.hammer.frontend.configureFrontEnd
import com.darkrockstudios.apps.hammer.plugins.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.jetty.jakarta.*
import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.coroutines.runBlocking
import net.peanuuutz.tomlkt.Toml
import okio.FileSystem
import okio.Path.Companion.toPath
import org.koin.core.module.Module
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

	parser.parse(args)

	val logLevel = parseLogLevel(logLevelArg)

	val config: ServerConfig = configPathArg?.let {
		loadConfig(it)
	} ?: ServerConfig()

	runDataMigrator()

	startServer(config, devModeArg ?: false, logLevel)
}

private fun parseLogLevel(logLevelArg: String?): Level? {
	return try {
		logLevelArg?.let { Level.valueOf(it.uppercase()) }
	} catch (_: Exception) {
		null
	}
}

private fun runDataMigrator() {
	val dataMigrator = DataMigrator()
	runBlocking {
		dataMigrator.runMigrations()
	}
}

private fun loadConfig(path: String): ServerConfig {
	return FileSystem.SYSTEM.readToml(path.toPath(), Toml, ServerConfig::class)
}

private fun startServer(config: ServerConfig, devMode: Boolean, logLevel: Level?) {
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
		sslConnector(
			keyStore = getKeyStore(this),
			keyAlias = keyAlias ?: "",
			keyStorePassword = { storePassword.toCharArray() },
			privateKeyPassword = { (keyPassword ?: "").toCharArray() }
		) {
			this.keyStorePath = File(path)
			host = bindHost
			port = config.sslPort
		}
	}
}

private fun getKeyStore(sslConfig: SslCertConfig): KeyStore {
	val certFile = File(sslConfig.path)
	if (certFile.exists().not()) throw IllegalArgumentException("SSL Cert not found")
	return KeyStore.getInstance(certFile, sslConfig.storePassword.toCharArray())
}

fun Application.appMain(
	config: ServerConfig,
	addInModule: Module? = null,
	logLevel: Level? = null
) {
	configureDependencyInjection(addInModule)
	configureSerialization()
	configureMonitoring(logLevel)
	configureHTTP(config)
	configureSecurity()
	configureLocalization()
	configureRouting()
	configureFrontEnd()
}
