package com.darkrockstudios.apps.hammer.e2e.util

import com.darkrockstudios.apps.hammer.ServerConfig
import com.darkrockstudios.apps.hammer.appMain
import com.darkrockstudios.apps.hammer.database.Database
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.applicationEngineEnvironment
import io.ktor.server.engine.connector
import io.ktor.server.engine.embeddedServer
import io.ktor.server.jetty.Jetty
import io.ktor.server.jetty.JettyApplicationEngine
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.fakefilesystem.FakeFileSystem
import org.junit.After
import org.junit.Before
import org.koin.dsl.bind

/**
 * Base class for End to End Tests.
 * This will start up and tear down a server running on
 * port 8080, and writing to a FakeFileSystem.
 */
abstract class EndToEndTest {

	protected lateinit var fileSystem: FakeFileSystem
	private lateinit var server: ApplicationEngine
	private lateinit var client: HttpClient
	private lateinit var testDatabase: SqliteTestDatabase

	protected fun client() = client
	protected fun database() = testDatabase

	@Before
	open fun setup() {
		fileSystem = FakeFileSystem()
		testDatabase = SqliteTestDatabase()
		testDatabase.initialize()

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

	fun doStartServer() {
		server = startServer()
	}

	private fun startServer(): JettyApplicationEngine {
		val environment = applicationEngineEnvironment {
			connector {
				port = 8080
				host = "0.0.0.0"
			}

			// Override the default database
			val testModule = org.koin.dsl.module {
				single { testDatabase } bind Database::class
				single { fileSystem } bind FileSystem::class
			}

			val config = ServerConfig()
			module {
				appMain(config, testModule)
			}
		}

		return embeddedServer(
			Jetty,
			environment = environment
		).start()
	}
}