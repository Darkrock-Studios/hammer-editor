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
 */
class PersistedFormatOwnershipTest {

	// Known pre-rule offenders, to be burned down. Do not add to this list: extend the
	// owning datasource's file with a helper instead.
	private val legacyOffenders = setOf(
		// Writes fabricated writing-activity logs owned by WritingActivityDatasource.
		"com/darkrockstudios/apps/hammer/common/data/ExampleProjectRepository.kt",
		// Second reader of the statistics cache format owned by StatisticsDatasource.
		"com/darkrockstudios/apps/hammer/common/data/projectstatistics/ProjectStatisticsCacheReader.kt",
	)

	private val tomlIo = Regex("""\.(writeToml|readToml|readTomlOrNull)\s*[<(]""")

	private fun sourceRoot(): File {
		val root = File("src/commonMain/kotlin")
		assertTrue(
			root.isDirectory,
			"Expected the test working directory to be the :common module (got ${File("").absolutePath})",
		)
		return root
	}

	private fun filesWithTomlIo(): Map<String, File> {
		val root = sourceRoot()
		return root.walkTopDown()
			.filter { it.isFile && it.extension == "kt" }
			.filter { tomlIo.containsMatchIn(it.readText()) }
			.associateBy { it.relativeTo(root).invariantSeparatorsPath }
	}

	@Test
	fun `raw TOML file IO stays inside datasource files`() {
		val offenders = filesWithTomlIo().keys
			.filterNot { it.substringAfterLast('/').contains("Datasource") }
			// Migrators transform historical on-disk layouts that predate the current
			// datasources; raw access to old formats is their job.
			.filterNot { it.startsWith("com/darkrockstudios/apps/hammer/common/data/migrator/") }
			.filterNot { it in legacyOffenders }
			.sorted()

		assertEquals(
			emptyList(),
			offenders,
			"These files serialize a persisted format outside its owning Data Source. " +
				"Route through the datasource, or add a scope-less helper in the datasource's " +
				"file (see loadStoredProjectData/saveStoredProjectData).",
		)
	}

	@Test
	fun `legacy offender list only shrinks`() {
		val current = filesWithTomlIo().keys
		val stale = legacyOffenders.filterNot { it in current }
		assertEquals(
			emptyList(),
			stale,
			"These legacy offenders no longer do raw TOML I/O; remove them from the list so it can't regress.",
		)
	}
}
