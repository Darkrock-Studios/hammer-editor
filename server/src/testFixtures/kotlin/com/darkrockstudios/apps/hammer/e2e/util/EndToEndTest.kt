package com.darkrockstudios.apps.hammer.e2e.util

import com.darkrockstudios.apps.hammer.EncryptionConfig
import com.darkrockstudios.apps.hammer.EncryptionMode
import com.darkrockstudios.apps.hammer.ServerConfig
import com.darkrockstudios.apps.hammer.appMain
import com.darkrockstudios.apps.hammer.base.http.createTokenBase64
import com.darkrockstudios.apps.hammer.database.Database
import com.darkrockstudios.apps.hammer.encryption.AesGcmContentEncryptor
import com.darkrockstudios.apps.hammer.encryption.SimpleFileBasedAesGcmKeyProvider
import com.darkrockstudios.apps.hammer.secret.FileSecretProvider
import com.darkrockstudios.apps.hammer.secret.KeyringCodec
import com.darkrockstudios.apps.hammer.secret.KeyringManager
import com.darkrockstudios.apps.hammer.utilities.ServerSecretManager
import com.darkrockstudios.apps.hammer.utilities.TokenHasher
import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.engine.*
import io.ktor.server.jetty.jakarta.*
import okio.FileSystem
import okio.fakefilesystem.FakeFileSystem
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.koin.dsl.bind
import java.security.SecureRandom
import kotlin.io.encoding.Base64

/**
 * Base class for End to End Tests.
 * This will start up and tear down a server running on
 * port 54321, and writing to a FakeFileSystem.
 */
abstract class EndToEndTest {

	companion object {
		const val TEST_PORT = 54321
	}

	protected lateinit var fileSystem: FakeFileSystem
	private lateinit var server: ApplicationEngine
	private lateinit var client: HttpClient
	private lateinit var testDatabase: SqliteTestDatabase
	private lateinit var base64: Base64
	private lateinit var contentEncryptor: AesGcmContentEncryptor
	private lateinit var tokenHasher: TokenHasher

	protected fun client() = client
	protected fun database() = testDatabase
	protected fun encryptor() = contentEncryptor
	protected fun tokenHasher() = tokenHasher

	@BeforeEach
	open fun setup() {
		fileSystem = FakeFileSystem()
		base64 = createTokenBase64()
		val secureRandom = SecureRandom()
		val serverSecretManager = ServerSecretManager(fileSystem, secureRandom)
		tokenHasher = TokenHasher(serverSecretManager, base64)

		// Write a keyring the booted server reads (file provider, default path, same fake FS),
		// so this test's encryptor and the server share content key material.
		val codec = KeyringCodec(secureRandom, base64)
		val keyringPath = KeyringManager.defaultKeyringPath(fileSystem)
		fileSystem.createDirectories(keyringPath.parent!!)
		fileSystem.write(keyringPath) { writeUtf8(codec.serialize(codec.generate())) }
		val keyringManager = KeyringManager(
			FileSecretProvider(fileSystem, keyringPath),
			codec, fileSystem, KeyringManager.legacySecretPath(fileSystem),
		)
		contentEncryptor = AesGcmContentEncryptor(
			SimpleFileBasedAesGcmKeyProvider(keyringManager, base64),
			secureRandom
		)
		testDatabase = SqliteTestDatabase()
		testDatabase.initialize()

		client = HttpClient {
			install(ContentNegotiation) {
				json()
			}
		}
	}

	@AfterEach
	fun tearDown() {
		// Close the per-test HttpClient before stopping the server so its engine threads and
		// connection pool don't accumulate across the suite (a leak that grows test-to-test).
		runCatching { client.close() }
		server.stop(1000, 3000)
	}

	protected fun route(path: String): String = "http://127.0.0.1:$TEST_PORT/$path"
	protected fun api(path: String): String = route("api/$path")

	fun doStartServer() {
		server = startServer()
	}

	private fun startServer(): ApplicationEngine {
		// Override the default database
		val testModule = org.koin.dsl.module {
			single { testDatabase } bind Database::class
			single { fileSystem } bind FileSystem::class
		}

		// These tests exercise the AES-at-rest path with a known secret, so pin it
		// explicitly rather than riding the plaintext default.
		val config = ServerConfig(encryption = EncryptionConfig(EncryptionMode.AES))

		val server = embeddedServer(
			Jetty,
			port = TEST_PORT,
			host = "0.0.0.0",
			module = {
				appMain(config, testModule)
			}
		)
		server.start()
		return server.engine
	}
}