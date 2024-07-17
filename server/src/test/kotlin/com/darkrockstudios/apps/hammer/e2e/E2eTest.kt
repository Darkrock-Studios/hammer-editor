package com.darkrockstudios.apps.hammer.e2e

import com.darkrockstudios.apps.hammer.ServerConfig
import com.darkrockstudios.apps.hammer.appMain
import io.ktor.server.engine.applicationEngineEnvironment
import io.ktor.server.engine.connector
import io.ktor.server.engine.embeddedServer
import io.ktor.server.jetty.Jetty
import io.ktor.server.jetty.JettyApplicationEngine
import okio.fakefilesystem.FakeFileSystem
import org.junit.After
import org.junit.Before

/**
 * Base class for End to End Tests.
 * This will start up and tear down a server running on
 * port 8080, and writing to a FakeFileSystem.
 */
abstract class E2eTest {

	private lateinit var server: JettyApplicationEngine

	@Before
	fun setup() {
		server = startServer()
	}

	@After
	fun tearDown() {
		server.stop(1, 1)
	}

	private fun startServer(): JettyApplicationEngine {
		val fileSystem = FakeFileSystem()
		val environment = applicationEngineEnvironment {
			connector {
				port = 8080
				host = "0.0.0.0"
			}

			val config = ServerConfig()
			module {
				appMain(config, fileSystem)
			}
		}

		return embeddedServer(
			Jetty,
			environment = environment
		).start()
	}
}