package architecture

import org.junit.jupiter.api.Test

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
	fun `raw TOML file IO stays inside datasource files`() = assertNoViolations(
		"Only a Data Source file may serialize a persisted format. Route through the datasource, " +
			"or add a scope-less helper in the datasource's file (see loadStoredProjectData/saveStoredProjectData)",
		clientScope().files
			.filter { tomlIo.containsMatchIn(it.text) }
			.filterNot { "Datasource" in it.name }
			.filterNot { ".data.migrator" in it.packagee?.name.orEmpty() }
			.map { it.path.substringAfter("/common/src/") }
			.toSet(),
	)
}
