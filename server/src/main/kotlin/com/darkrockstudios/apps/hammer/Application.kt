package com.darkrockstudios.apps.hammer

import com.darkrockstudios.apps.hammer.plugins.*
import com.darkrockstudios.apps.hammer.plugins.kweb.configureKweb
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.jetty.*
import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import java.io.File
import java.security.KeyStore

fun main(args: Array<String>) {
	val parser = ArgParser("server")
	val portArg by parser.option(ArgType.Int, shortName = "p", fullName = "port", description = "Port")
	val certPathArg by parser.option(
		ArgType.String,
		shortName = "c",
		fullName = "sslCert",
		description = "SSL Cert Path"
	)
	val certPasswordArg by parser.option(
		ArgType.String,
		shortName = "x",
		fullName = "sslPassword",
		description = "SSL Cert Password"
	)
	val certAliasArg by parser.option(
		ArgType.String,
		shortName = "z",
		fullName = "sslAlias",
		description = "SSL Cert Key Alias"
	)

	parser.parse(args)

	val port = portArg ?: 8080
	println(certPathArg)
	val keyStoreFile: File? = certPathArg?.let { return@let File(it) }

	startServer(port, keyStoreFile, certPasswordArg, certAliasArg)
}

private fun startServer(httpPort: Int, keyStoreFile: File?, certPasswordArg: String?, certAliasArg: String?) {
	val password = certPasswordArg ?: ""
	val keyStore = getKeyStore(keyStoreFile, certPasswordArg)

	val environment = applicationEngineEnvironment {
		connector {
			port = httpPort
			host = "0.0.0.0"
		}

		if (keyStore != null) {
			sslConnector(
				keyStore = keyStore,
				keyAlias = certAliasArg ?: "",
				keyStorePassword = { password.toCharArray() },
				privateKeyPassword = { password.toCharArray() }) {
				keyStorePath = keyStoreFile
				host = "0.0.0.0"
			}
		}

		module(Application::appMain)
		watchPaths = listOf("classes")
	}

	embeddedServer(
		Jetty,
		environment = environment
	).start(wait = true)
}

private fun getKeyStore(certFile: File?, certPasswordArg: String?): KeyStore? {
	return if (certFile != null) {
		if (certFile.exists().not()) throw IllegalArgumentException("SSL Cert not found")
		KeyStore.getInstance(certFile, certPasswordArg?.toCharArray())
	} else {
		null
	}
}

fun Application.appMain() {
	configureDependencyInjection()
	configureSerialization()
	configureMonitoring()
	configureHTTP()
	configureSecurity()
	configureRouting()
	configureKweb()
}
