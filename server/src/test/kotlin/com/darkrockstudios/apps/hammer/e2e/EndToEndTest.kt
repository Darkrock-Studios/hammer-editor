package com.darkrockstudios.apps.hammer.e2e

import com.darkrockstudios.apps.hammer.ServerConfig
import com.darkrockstudios.apps.hammer.appMain
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.applicationEngineEnvironment
import io.ktor.server.engine.connector
import io.ktor.server.engine.embeddedServer
import io.ktor.server.jetty.Jetty
import io.ktor.server.jetty.JettyApplicationEngine
import kotlinx.serialization.json.Json
import okio.fakefilesystem.FakeFileSystem
import org.junit.After
import org.junit.Before

/**
 * Base class for End to End Tests.
 * This will start up and tear down a server running on
 * port 8080, and writing to a FakeFileSystem.
 */
abstract class EndToEndTest {

	private lateinit var server: ApplicationEngine
	private lateinit var client: HttpClient
	protected fun client() = client

	@Before
	fun setup() {
		server = startServer()

		client = HttpClient {
			install(ContentNegotiation) {
				Json {
					prettyPrint = true
					ignoreUnknownKeys = true
				}
			}
		}
	}

	@After
	fun tearDown() {
		server.stop(1, 1)
	}

	protected fun route(path: String): String = "http://127.0.0.1:8080/$path"
	protected fun api(path: String): String = route("api/$path")

	private fun startServer(): JettyApplicationEngine {
		val fileSystem = FakeFileSystem()
		val environment = applicationEngineEnvironment {
			connector {
				port = 8080
				host = "0.0.0.0"
			}

			val config = ServerConfig()
			module {
				appMain(config, fileSystem, true)
			}
		}

		return embeddedServer(
			Jetty,
			environment = environment
		).start()
	}
}