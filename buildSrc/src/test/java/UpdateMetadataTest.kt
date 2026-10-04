import com.darkrockstudios.build.UpdateAsset
import com.darkrockstudios.build.renderUpdateMetadata
import com.darkrockstudios.build.sha512Base64
import com.darkrockstudios.build.updateMetadataFiles
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class UpdateMetadataTest {

	@Test
	fun `renders the electron-builder layout Nucleus parses`() {
		val yml = renderUpdateMetadata(
			version = "3.12.0",
			releaseDate = "2026-10-20T18:04:11Z",
			assets = listOf(
				UpdateAsset("hammer.msi", 190034708, "abc="),
				UpdateAsset("hammer.exe", 190100000, "def="),
			),
		)

		val expected = """
			|version: 3.12.0
			|releaseDate: 2026-10-20T18:04:11Z
			|files:
			|  - url: hammer.msi
			|    sha512: abc=
			|    size: 190034708
			|  - url: hammer.exe
			|    sha512: def=
			|    size: 190100000
			|""".trimMargin()
		assertEquals(expected, yml)
	}

	@Test
	fun `sha512 is base64 of the digest`() {
		val file = tempDir().resolve("asset.bin").apply { writeBytes(ByteArray(100_000) { it.toByte() }) }

		val expected = Base64.getEncoder().encodeToString(
			MessageDigest.getInstance("SHA-512").digest(file.readBytes())
		)
		assertEquals(expected, sha512Base64(file))
	}

	@Test
	fun `every platform file lists its installers from the asset directory`() {
		val dir = tempDir()
		val names = listOf("hammer.msi", "hammer.exe", "hammer.dmg", "hammer.pkg", "hammer.deb", "hammer.rpm", "hammer.AppImage")
		names.forEachIndexed { i, name -> dir.resolve(name).writeText("installer $i") }

		val files = updateMetadataFiles(dir, "3.12.0", "2026-10-20T18:04:11Z")

		assertEquals(setOf("latest.yml", "latest-mac.yml", "latest-linux.yml"), files.keys)
		assertEquals(
			listOf("hammer.msi", "hammer.exe"),
			urlsIn(files.getValue("latest.yml")),
		)
		assertEquals(
			listOf("hammer.dmg", "hammer.pkg"),
			urlsIn(files.getValue("latest-mac.yml")),
		)
		assertEquals(
			listOf("hammer.deb", "hammer.rpm", "hammer.AppImage"),
			urlsIn(files.getValue("latest-linux.yml")),
		)
		assertEquals("    size: ${"installer 0".length}", files.getValue("latest.yml").lines()[5])
	}

	@Test
	fun `the mac zip is listed first when present and skipped when absent`() {
		val dir = tempDir()
		listOf("hammer.msi", "hammer.exe", "hammer.dmg", "hammer.pkg", "hammer.deb", "hammer.rpm", "hammer.AppImage")
			.forEach { dir.resolve(it).writeText(it) }

		assertNull(urlsIn(updateMetadataFiles(dir, "1", "d").getValue("latest-mac.yml")).firstOrNull { it == "hammer.zip" })

		dir.resolve("hammer.zip").writeText("zip")
		assertEquals(
			listOf("hammer.zip", "hammer.dmg", "hammer.pkg"),
			urlsIn(updateMetadataFiles(dir, "1", "d").getValue("latest-mac.yml")),
		)
	}

	@Test
	fun `a missing required installer fails instead of publishing a gap`() {
		val dir = tempDir()
		listOf("hammer.msi", "hammer.dmg", "hammer.pkg", "hammer.deb", "hammer.rpm", "hammer.AppImage")
			.forEach { dir.resolve(it).writeText(it) }

		val error = assertFailsWith<IllegalStateException> { updateMetadataFiles(dir, "1", "d") }
		assertEquals(true, error.message?.startsWith("latest.yml needs hammer.exe"))
	}

	private fun urlsIn(yml: String): List<String> =
		yml.lines().filter { it.trim().startsWith("- url:") }.map { it.substringAfter("- url:").trim() }

	private fun tempDir(): File = Files.createTempDirectory("update-metadata-test").toFile().apply { deleteOnExit() }
}
