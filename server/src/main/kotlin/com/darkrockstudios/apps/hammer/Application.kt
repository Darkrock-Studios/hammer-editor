package com.darkrockstudios.apps.hammer

import com.darkrockstudios.apps.hammer.base.http.readToml
import com.darkrockstudios.apps.hammer.plugins.configureDependencyInjection
import com.darkrockstudios.apps.hammer.plugins.configureHTTP
import com.darkrockstudios.apps.hammer.plugins.configureLocalization
import com.darkrockstudios.apps.hammer.plugins.configureMonitoring
import com.darkrockstudios.apps.hammer.plugins.configureRouting
import com.darkrockstudios.apps.hammer.plugins.configureSecurity
import com.darkrockstudios.apps.hammer.plugins.configureSerialization
import com.darkrockstudios.apps.hammer.plugins.kweb.configureKweb
import io.ktor.server.application.Application
import io.ktor.server.engine.applicationEngineEnvironment
import io.ktor.server.engine.connector
import io.ktor.server.engine.embeddedServer
import io.ktor.server.engine.sslConnector
import io.ktor.server.jetty.Jetty
import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import net.peanuuutz.tomlkt.Toml
import okio.FileSystem
import okio.Path.Companion.toPath
import java.io.File
import java.security.KeyStore

fun main(args: Array<String>) {
	val parser = ArgParser("server")
	val configPathArg by parser.option(
		ArgType.String,
		shortName = "c",
		fullName = "config",
		description = "Server Config Path"
	)

	parser.parse(args)

	val config: ServerConfig = configPathArg?.let {
		loadConfig(it)
	} ?: ServerConfig()

	startServer(config)
}

private fun loadConfig(path: String): ServerConfig {
	return FileSystem.SYSTEM.readToml(path.toPath(), Toml, ServerConfig::class)
}

private fun startServer(config: ServerConfig) {
	val environment = applicationEngineEnvironment {
		val bindHost = "0.0.0.0"
		connector {
			port = config.port
			host = bindHost
		}

		config.sslCert?.apply {
			sslConnector(
				keyStore = getKeyStore(this),
				keyAlias = keyAlias ?: "",
				keyStorePassword = { storePassword.toCharArray() },
				privateKeyPassword = { (keyPassword ?: "").toCharArray() }) {
				keyStorePath = File(path)
				host = bindHost
				port = config.sslPort
			}
		}

		module {
			appMain(config)
		}
		watchPaths = listOf("classes")
	}

	embeddedServer(
		Jetty,
		environment = environment
	).start(wait = true)
}

private fun getKeyStore(sslConfig: SslCertConfig): KeyStore {
	val certFile = File(sslConfig.path)
	if (certFile.exists().not()) throw IllegalArgumentException("SSL Cert not found")
	return KeyStore.getInstance(certFile, sslConfig.storePassword.toCharArray())
}

fun Application.appMain(config: ServerConfig) {
	configureDependencyInjection()
	configureSerialization()
	configureMonitoring()
	configureHTTP(config)
	configureSecurity()
	configureLocalization()
	configureRouting()
	configureKweb(config)
}
