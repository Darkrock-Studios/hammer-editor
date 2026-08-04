package architecture

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Structural guard for the "one owner per persisted format" rule (ARCHITECTURE.md,
 * hard constraint 7): raw TOML file I/O may only appear inside a format's Data Source
 * file. A second inline writer forks the on-disk format and clobbers sibling fields on
 * write (a language-seed write and a tags write to `project_data.toml` once destroyed
 * each other exactly this way). Code that cannot reach a datasource instance must use
 * or add a scope-less helper in the datasource's own file, like
 * `loadStoredProjectData`/`saveStoredProjectData` in `ProjectDataDatasource.kt`.
 *
 * The only exemption is the migrator package: migrations transform historical on-disk
 * layouts that predate the current datasources, so raw access to old formats is their job.
 */
class PersistedFormatOwnershipTest {

	private val tomlIo = Regex("""\.(writeToml|readToml|readTomlOrNull)\s*[<(]""")

	@Test
	fun `raw TOML file IO stays inside datasource files`() {
		val root = File("src/commonMain/kotlin")
		assertTrue(
			root.isDirectory,
			"Expected the test working directory to be the :common module (got ${File("").absolutePath})",
		)

		val offenders = root.walkTopDown()
			.filter { it.isFile && it.extension == "kt" }
			.filter { tomlIo.containsMatchIn(it.readText()) }
			.map { it.relativeTo(root).invariantSeparatorsPath }
			.filterNot { it.substringAfterLast('/').contains("Datasource") }
			.filterNot { it.startsWith("com/darkrockstudios/apps/hammer/common/data/migrator/") }
			.sorted()
			.toList()

		assertEquals(
			emptyList(),
			offenders,
			"These files serialize a persisted format outside its owning Data Source. " +
				"Route through the datasource, or add a scope-less helper in the datasource's " +
				"file (see loadStoredProjectData/saveStoredProjectData).",
		)
	}
}
