package util.zip

import com.darkrockstudios.apps.hammer.common.util.zip.unzipToDirectory
import com.darkrockstudios.apps.hammer.common.util.zip.zipDirectory
import kotlinx.coroutines.runBlocking
import okio.FileSystem
import okio.Path
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ZipUtilsTest {

	private lateinit var fileSystem: FileSystem
	private lateinit var tempDir: Path

	@BeforeEach
	fun setup() {
		fileSystem = FileSystem.SYSTEM
		tempDir = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "ZipUtilsTest_${kotlin.random.Random.nextInt(100000)}"
		fileSystem.createDirectories(tempDir)
	}

	@AfterEach
	fun tearDown() {
		fileSystem.deleteRecursively(tempDir)
	}

	@Test
	fun `zipDirectory creates a zip file containing every source file`() = runBlocking {
		val sourceDir = tempDir / "test-project"
		val zipFile = tempDir / "test-project.zip"
		val extractDir = tempDir / "extracted-basic"

		fileSystem.createDirectories(sourceDir)
		fileSystem.createDirectories(extractDir)
		fileSystem.write(sourceDir / "file1.txt") { writeUtf8("Hello World") }
		fileSystem.write(sourceDir / "file2.txt") { writeUtf8("Test content") }

		zipDirectory(
			fileSystem = fileSystem,
			sourceDirectory = sourceDir,
			destinationZip = zipFile,
		)

		assertTrue(fileSystem.exists(zipFile), "Zip file should be created")

		unzipToDirectory(
			fileSystem = fileSystem,
			zipPath = zipFile,
			destinationDirectory = extractDir,
		)

		assertEquals("Hello World", fileSystem.read(extractDir / sourceDir.name / "file1.txt") { readUtf8() })
		assertEquals("Test content", fileSystem.read(extractDir / sourceDir.name / "file2.txt") { readUtf8() })
	}

	@Test
	fun `zipDirectory includes dotfiles`() = runBlocking {
		val sourceDir = tempDir / "test-project"
		val zipFile = tempDir / "test-project.zip"
		val extractDir = tempDir / "extracted-all"

		fileSystem.createDirectories(sourceDir)
		fileSystem.createDirectories(extractDir)

		fileSystem.write(sourceDir / "visible.txt") { writeUtf8("Visible content") }
		fileSystem.write(sourceDir / ".hidden") { writeUtf8("Hidden content") }

		zipDirectory(
			fileSystem = fileSystem,
			sourceDirectory = sourceDir,
			destinationZip = zipFile,
		)

		unzipToDirectory(
			fileSystem = fileSystem,
			zipPath = zipFile,
			destinationDirectory = extractDir,
		)

		assertTrue(fileSystem.exists(zipFile), "Zip file should be created")
		assertTrue(
			fileSystem.exists(extractDir / sourceDir.name / ".hidden"),
			"Hidden file should be extracted"
		)
	}

	@Test
	fun `zipDirectory preserves nested directory structure`() = runBlocking {
		val sourceDir = tempDir / "test-project-nested"
		val zipFile = tempDir / "test-project-nested.zip"
		val extractDir = tempDir / "extracted-nested"

		fileSystem.createDirectories(sourceDir / "subdir1" / "subdir2")
		fileSystem.createDirectories(extractDir)
		fileSystem.write(sourceDir / "root.txt") { writeUtf8("Root file") }
		fileSystem.write(sourceDir / "subdir1" / "level1.txt") { writeUtf8("Level 1 file") }
		fileSystem.write(sourceDir / "subdir1" / "subdir2" / "level2.txt") { writeUtf8("Level 2 file") }

		zipDirectory(
			fileSystem = fileSystem,
			sourceDirectory = sourceDir,
			destinationZip = zipFile,
		)

		unzipToDirectory(
			fileSystem = fileSystem,
			zipPath = zipFile,
			destinationDirectory = extractDir,
		)

		val extractedRoot = extractDir / sourceDir.name
		assertEquals("Root file", fileSystem.read(extractedRoot / "root.txt") { readUtf8() })
		assertEquals("Level 1 file", fileSystem.read(extractedRoot / "subdir1" / "level1.txt") { readUtf8() })
		assertEquals(
			"Level 2 file",
			fileSystem.read(extractedRoot / "subdir1" / "subdir2" / "level2.txt") { readUtf8() },
		)
	}

	@Test
	fun `zipDirectory handles empty directory`() = runBlocking {
		val sourceDir = tempDir / "empty-project"
		val zipFile = tempDir / "empty-project.zip"

		fileSystem.createDirectories(sourceDir)

		zipDirectory(
			fileSystem = fileSystem,
			sourceDirectory = sourceDir,
			destinationZip = zipFile,
		)

		assertTrue(fileSystem.exists(zipFile), "Zip file should be created even for empty directory")
	}

	@Test
	fun `unzipToDirectory extracts files correctly`() = runBlocking {
		val sourceDir = tempDir / "test-project-unzip"
		val zipFile = tempDir / "test-project-unzip.zip"
		val extractDir = tempDir / "extracted-verify"

		fileSystem.createDirectories(sourceDir)
		fileSystem.createDirectories(extractDir)

		val testContent = "Hello World"
		fileSystem.write(sourceDir / "test.txt") { writeUtf8(testContent) }

		zipDirectory(
			fileSystem = fileSystem,
			sourceDirectory = sourceDir,
			destinationZip = zipFile,
		)

		unzipToDirectory(
			fileSystem = fileSystem,
			zipPath = zipFile,
			destinationDirectory = extractDir,
		)

		assertTrue(fileSystem.exists(extractDir), "Extract directory should exist")
		assertTrue(
			fileSystem.exists(extractDir / sourceDir.name / "test.txt"),
			"Extracted file should exist"
		)
	}

	@Test
	fun `zip and unzip round trip preserves file content`() = runBlocking {
		val sourceDir = tempDir / "round-trip"
		val zipFile = tempDir / "round-trip.zip"
		val extractDir = tempDir / "round-trip-extracted"

		fileSystem.createDirectories(sourceDir)
		fileSystem.createDirectories(extractDir)

		val testContent1 = "File 1 content"
		val testContent2 = "File 2 content with special chars: äöü"

		fileSystem.write(sourceDir / "file1.txt") { writeUtf8(testContent1) }
		fileSystem.write(sourceDir / "file2.txt") { writeUtf8(testContent2) }

		zipDirectory(
			fileSystem = fileSystem,
			sourceDirectory = sourceDir,
			destinationZip = zipFile,
		)

		unzipToDirectory(
			fileSystem = fileSystem,
			zipPath = zipFile,
			destinationDirectory = extractDir,
		)

		assertTrue(fileSystem.exists(zipFile), "Zip file should exist after round trip")
		assertTrue(fileSystem.read(extractDir / sourceDir.name / "file1.txt") { readUtf8() } == testContent1)
		assertTrue(fileSystem.read(extractDir / sourceDir.name / "file2.txt") { readUtf8() } == testContent2)
	}
}
