package com.darkrockstudios.apps.hammer

import com.darkrockstudios.apps.hammer.utilities.cacheDirectory
import com.darkrockstudios.apps.hammer.utilities.getRootDataDirectory
import net.peanuuutz.tomlkt.Toml
import okio.Path
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

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

	@Test
	fun `resolve aborts when termsOfService points at a missing file`() {
		val fs = FakeFileSystem()
		val explicit = getRootDataDirectory(fs) / "custom.toml"
		writeConfig(fs, explicit, """termsOfService = "/data/tos.txt"""")

		val error = assertFailsWith<IllegalStateException> {
			resolveServerConfig(configPath = explicit.toString(), fileSystem = fs)
		}
		assertTrue(error.message.orEmpty().contains("/data/tos.txt"))
	}

	@Test
	fun `resolve aborts when termsOfService file is blank`() {
		val fs = FakeFileSystem()
		writeConfig(fs, "/data/tos.txt".toPath(), "   \n ")
		val explicit = getRootDataDirectory(fs) / "custom.toml"
		writeConfig(fs, explicit, """termsOfService = "/data/tos.txt"""")

		assertFailsWith<IllegalStateException> {
			resolveServerConfig(configPath = explicit.toString(), fileSystem = fs)
		}
	}

	@Test
	fun `resolve succeeds when termsOfService file exists and is non-blank`() {
		val fs = FakeFileSystem()
		writeConfig(fs, "/data/tos.txt".toPath(), "Be excellent to each other")
		val explicit = getRootDataDirectory(fs) / "custom.toml"
		writeConfig(fs, explicit, """termsOfService = "/data/tos.txt"""")

		val config = resolveServerConfig(configPath = explicit.toString(), fileSystem = fs)

		assertEquals("/data/tos.txt", config.termsOfService)
	}

	@Test
	fun `a relative termsOfService is resolved next to the config file`() {
		val fs = FakeFileSystem()
		val configDir = getRootDataDirectory(fs)
		writeConfig(fs, configDir / "tos.txt", "Be excellent to each other")
		val explicit = configDir / "custom.toml"
		writeConfig(fs, explicit, """termsOfService = "tos.txt"""")

		val config = resolveServerConfig(configPath = explicit.toString(), fileSystem = fs)

		assertEquals((configDir / "tos.txt").toString(), config.termsOfService)
	}

	@Test
	fun `privacyPolicy defaults to null`() {
		assertEquals(null, parse("").privacyPolicy)
	}

	@Test
	fun `privacyPolicy parses a file path`() {
		val config = parse("""privacyPolicy = "/srv/hammer/privacy.txt"""")
		assertEquals("/srv/hammer/privacy.txt", config.privacyPolicy)
	}

	@Test
	fun `resolve aborts when privacyPolicy points at a missing file`() {
		val fs = FakeFileSystem()
		val explicit = getRootDataDirectory(fs) / "custom.toml"
		writeConfig(fs, explicit, """privacyPolicy = "/data/privacy.txt"""")

		val error = assertFailsWith<IllegalStateException> {
			resolveServerConfig(configPath = explicit.toString(), fileSystem = fs)
		}
		assertTrue(error.message.orEmpty().contains("/data/privacy.txt"))
	}

	@Test
	fun `resolve aborts when privacyPolicy file is blank`() {
		val fs = FakeFileSystem()
		writeConfig(fs, "/data/privacy.txt".toPath(), "   \n ")
		val explicit = getRootDataDirectory(fs) / "custom.toml"
		writeConfig(fs, explicit, """privacyPolicy = "/data/privacy.txt"""")

		assertFailsWith<IllegalStateException> {
			resolveServerConfig(configPath = explicit.toString(), fileSystem = fs)
		}
	}

	@Test
	fun `resolve succeeds when privacyPolicy file exists and is non-blank`() {
		val fs = FakeFileSystem()
		writeConfig(fs, "/data/privacy.txt".toPath(), "We respect your privacy")
		val explicit = getRootDataDirectory(fs) / "custom.toml"
		writeConfig(fs, explicit, """privacyPolicy = "/data/privacy.txt"""")

		val config = resolveServerConfig(configPath = explicit.toString(), fileSystem = fs)

		assertEquals("/data/privacy.txt", config.privacyPolicy)
	}

	@Test
	fun `a relative privacyPolicy is resolved next to the config file`() {
		val fs = FakeFileSystem()
		val configDir = getRootDataDirectory(fs)
		writeConfig(fs, configDir / "privacy.txt", "We respect your privacy")
		val explicit = configDir / "custom.toml"
		writeConfig(fs, explicit, """privacyPolicy = "privacy.txt"""")

		val config = resolveServerConfig(configPath = explicit.toString(), fileSystem = fs)

		assertEquals((configDir / "privacy.txt").toString(), config.privacyPolicy)
	}

	@Test
	fun `cache defaults to a directory under the data directory`() {
		val fs = FakeFileSystem()

		val config = parse("")

		assertEquals(null, config.cache.directory)
		assertEquals(getRootDataDirectory(fs) / "cache" / "og", cacheDirectory(config.cache, fs, "og"))
	}

	@Test
	fun `a configured cache directory holds each named cache`() {
		val fs = FakeFileSystem()

		val config = parse(
			"""
			[cache]
			directory = "/var/tmp/hammer-cache"
			""".trimIndent()
		)

		assertEquals("/var/tmp/hammer-cache/story-html".toPath(), cacheDirectory(config.cache, fs, "story-html"))
	}

	@Test
	fun `maxSizeMb bounds each cache in bytes`() {
		val config = parse(
			"""
			[cache]
			maxSizeMb = 50
			""".trimIndent()
		)

		assertEquals(50L * 1024 * 1024, config.cache.maxSizeBytes)
	}

	@Test
	fun `a relative cache directory is resolved next to the config file`() {
		val fs = FakeFileSystem()
		val configDir = getRootDataDirectory(fs)
		val explicit = configDir / "custom.toml"
		writeConfig(
			fs, explicit,
			"""
			[cache]
			directory = "scratch"
			""".trimIndent()
		)

		val config = resolveServerConfig(configPath = explicit.toString(), fileSystem = fs)

		assertEquals((configDir / "scratch").toString(), config.cache.directory)
	}

	@Test
	fun `resolve creates a configured cache directory`() {
		val fs = FakeFileSystem()
		val explicit = getRootDataDirectory(fs) / "custom.toml"
		writeConfig(
			fs, explicit,
			"""
			[cache]
			directory = "/var/tmp/hammer-cache"
			""".trimIndent()
		)

		resolveServerConfig(configPath = explicit.toString(), fileSystem = fs)

		assertTrue(fs.metadataOrNull("/var/tmp/hammer-cache".toPath())?.isDirectory == true)
	}

	@Test
	fun `resolve aborts when the cache directory cannot be written`() {
		val fs = FakeFileSystem()
		writeConfig(fs, "/var/tmp/occupied".toPath(), "not a directory")
		val explicit = getRootDataDirectory(fs) / "custom.toml"
		writeConfig(
			fs, explicit,
			"""
			[cache]
			directory = "/var/tmp/occupied"
			""".trimIndent()
		)

		val error = assertFailsWith<IllegalStateException> {
			resolveServerConfig(configPath = explicit.toString(), fileSystem = fs)
		}
		assertTrue(error.message.orEmpty().contains("/var/tmp/occupied"))
	}

	@Test
	fun `resolve aborts when maxSizeMb is not positive`() {
		val fs = FakeFileSystem()
		val explicit = getRootDataDirectory(fs) / "custom.toml"
		writeConfig(
			fs, explicit,
			"""
			[cache]
			maxSizeMb = 0
			""".trimIndent()
		)

		assertFailsWith<IllegalArgumentException> {
			resolveServerConfig(configPath = explicit.toString(), fileSystem = fs)
		}
	}

	private fun writeConfig(fs: FakeFileSystem, path: Path, contents: String) {
		path.parent?.let { fs.createDirectories(it) }
		fs.write(path) { writeUtf8(contents) }
	}
}
