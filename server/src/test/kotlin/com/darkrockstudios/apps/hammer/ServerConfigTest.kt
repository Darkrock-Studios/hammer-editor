package com.darkrockstudios.apps.hammer

import com.darkrockstudios.apps.hammer.utilities.getRootDataDirectory
import net.peanuuutz.tomlkt.Toml
import okio.Path
import okio.fakefilesystem.FakeFileSystem
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class ServerConfigTest {

	private val toml = Toml { ignoreUnknownKeys = true }

	private fun parse(tomlString: String): ServerConfig =
		toml.decodeFromString(ServerConfig.serializer(), tomlString)

	@Test
	fun `Omitted bindHosts defaults to all IPv4 interfaces`() {
		assertEquals(listOf("0.0.0.0"), parse("").bindHosts)
	}

	@Test
	fun `bindHosts parses loopback addresses`() {
		val config = parse("""bindHosts = ["127.0.0.1", "::1"]""")
		assertEquals(listOf("127.0.0.1", "::1"), config.bindHosts)
	}

	@Test
	fun `termsOfService defaults to null`() {
		assertEquals(null, parse("").termsOfService)
	}

	@Test
	fun `termsOfService parses a file path`() {
		val config = parse("""termsOfService = "/srv/hammer/tos.txt"""")
		assertEquals("/srv/hammer/tos.txt", config.termsOfService)
	}

	@Test
	fun `resolve falls back to defaults when no config file present`() {
		val fs = FakeFileSystem()

		val config = resolveServerConfig(configPath = null, fileSystem = fs)

		assertEquals(ServerConfig(), config)
	}

	@Test
	fun `resolve loads conventional config from data directory when present`() {
		val fs = FakeFileSystem()
		writeConfig(fs, getRootDataDirectory(fs) / DEFAULT_CONFIG_FILE_NAME, "port = 9999")

		val config = resolveServerConfig(configPath = null, fileSystem = fs)

		assertEquals(9999, config.port)
	}

	@Test
	fun `resolve prefers explicit config path over conventional location`() {
		val fs = FakeFileSystem()
		writeConfig(fs, getRootDataDirectory(fs) / DEFAULT_CONFIG_FILE_NAME, "port = 9999")
		val explicit = getRootDataDirectory(fs) / "custom.toml"
		writeConfig(fs, explicit, "port = 1234")

		val config = resolveServerConfig(configPath = explicit.toString(), fileSystem = fs)

		assertEquals(1234, config.port)
	}

	private fun writeConfig(fs: FakeFileSystem, path: Path, contents: String) {
		path.parent?.let { fs.createDirectories(it) }
		fs.write(path) { writeUtf8(contents) }
	}
}
