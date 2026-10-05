import com.darkrockstudios.build.DIGEST_SUFFIX
import com.darkrockstudios.build.UpdateAsset
import com.darkrockstudios.build.assetDigestLine
import com.darkrockstudios.build.parseAssetDigest
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

	private val installers = listOf(
		"hammer.msi", "hammer.exe", "hammer.dmg", "hammer.pkg", "hammer.deb", "hammer.rpm", "hammer.AppImage",
	)

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
	fun `a digest line round-trips through the sidecar`() {
		val file = tempDir().resolve("hammer.msi").apply { writeBytes(ByteArray(12_345) { (it * 3).toByte() }) }

		val asset = parseAssetDigest("hammer.msi", assetDigestLine(file) + "\n")

		assertEquals(UpdateAsset("hammer.msi", 12_345, sha512Base64(file)), asset)
	}

	@Test
	fun `a malformed sidecar is rejected`() {
		assertFailsWith<IllegalArgumentException> { parseAssetDigest("hammer.msi", "abc=") }
		assertFailsWith<IllegalStateException> { parseAssetDigest("hammer.msi", "abc= many") }
	}

	@Test
	fun `every platform file lists its installers from the sidecars`() {
		val dir = tempDir()
		installers.forEachIndexed { i, name -> dir.sidecar(name, "digest$i= ${100 + i}") }

		val files = updateMetadataFiles(dir, "3.12.0", "2026-10-20T18:04:11Z")

		assertEquals(setOf("latest.yml", "latest-mac.yml", "latest-linux.yml"), files.keys)
		assertEquals(listOf("hammer.msi", "hammer.exe"), urlsIn(files.getValue("latest.yml")))
		assertEquals(listOf("hammer.dmg", "hammer.pkg"), urlsIn(files.getValue("latest-mac.yml")))
		assertEquals(
			listOf("hammer.deb", "hammer.rpm", "hammer.AppImage"),
			urlsIn(files.getValue("latest-linux.yml")),
		)
		assertEquals("    sha512: digest0=", files.getValue("latest.yml").lines()[4])
		assertEquals("    size: 100", files.getValue("latest.yml").lines()[5])
	}

	@Test
	fun `the mac zip is listed first when present and skipped when absent`() {
		val dir = tempDir()
		installers.forEach { dir.sidecar(it, "x= 1") }

		assertNull(urlsIn(updateMetadataFiles(dir, "1", "d").getValue("latest-mac.yml")).firstOrNull { it == "hammer.zip" })

		dir.sidecar("hammer.zip", "z= 2")
		assertEquals(
			listOf("hammer.zip", "hammer.dmg", "hammer.pkg"),
			urlsIn(updateMetadataFiles(dir, "1", "d").getValue("latest-mac.yml")),
		)
	}

	@Test
	fun `a missing required installer fails instead of publishing a gap`() {
		val dir = tempDir()
		installers.filter { it != "hammer.exe" }.forEach { dir.sidecar(it, "x= 1") }

		val error = assertFailsWith<IllegalStateException> { updateMetadataFiles(dir, "1", "d") }
		assertEquals(true, error.message?.startsWith("latest.yml needs hammer.exe$DIGEST_SUFFIX"))
	}

	private fun File.sidecar(name: String, line: String) = resolve(name + DIGEST_SUFFIX).writeText(line)

	private fun urlsIn(yml: String): List<String> =
		yml.lines().filter { it.trim().startsWith("- url:") }.map { it.substringAfter("- url:").trim() }

	private fun tempDir(): File = Files.createTempDirectory("update-metadata-test").toFile().apply { deleteOnExit() }
}
