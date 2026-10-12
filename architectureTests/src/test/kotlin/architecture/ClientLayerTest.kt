package architecture

import architecture.ClientLayer.*
import org.junit.jupiter.api.Test

private enum class ClientLayer {
	DATASOURCE, FOUNDATION, REPOSITORY, SERVICE, USE_CASE, COMPONENT
}

private val foundation = setOf("IdAllocator", "SyncJournal", "GlobalSettingsStore")

private fun layerOf(name: String): ClientLayer? = when {
	name in foundation -> FOUNDATION
	name.endsWith("Datasource") || name.endsWith("DataSource") -> DATASOURCE
	name.endsWith("Repository") -> REPOSITORY
	name.endsWith("Service") -> SERVICE
	name.endsWith("UseCase") -> USE_CASE
	// KoinComponent is the Koin marker interface, not a Decompose component.
	name.endsWith("Component") && name != "KoinComponent" -> COMPONENT
	else -> null
}

/**
 * Enforces the client dependency rules from docs/ARCHITECTURE.md. Layers are identified
 * by class name suffix because the code is packaged by feature, not by layer.
 */
class ClientLayerTest {
	private val dependencies by lazy {
		scopeOf(
			"common/src/commonMain",
			"common/src/androidMain",
			"common/src/desktopMain",
			"common/src/iosMain",
			"common/src/jvmMain",
		).dependencies()
	}

	private fun violations(from: ClientLayer, vararg forbidden: ClientLayer): List<Dependency> =
		dependencies.filter { layerOf(it.owner) == from && layerOf(it.target) in forbidden }

	@Test
	fun `data sources reference no other layer`() = assertNoViolations(
		"A Data Source may not reference any architecture layer, including other Data Sources",
		violations(DATASOURCE, *ClientLayer.entries.toTypedArray()),
		baseline = setOf(
			"IdeasDatasource -> GlobalSettingsStore",
			"IdeasSyncDatasource -> IdeasDatasource",
			"IosRebasingGlobalSettingsDatasource -> GlobalSettingsFilesystemDatasource",
			"SceneDraftIdDatasource -> SceneDraftsDatasource",
			"SceneDraftsDatasource -> SceneDatasource",
			"SyncDataDatasource -> IdAllocator",
		),
	)

	@Test
	fun `foundation primitives never reference upward`() = assertNoViolations(
		"A Foundation primitive may only reference Data Sources and other Foundation primitives",
		violations(FOUNDATION, REPOSITORY, SERVICE, USE_CASE, COMPONENT),
	)

	@Test
	fun `repositories reference only data sources and foundation`() = assertNoViolations(
		"A Repository may only reference Data Sources and Foundation primitives, never another Repository",
		violations(REPOSITORY, REPOSITORY, SERVICE, USE_CASE, COMPONENT),
		baseline = setOf(
			"ProjectBackupRepository -> ProjectsRepository",
			"ProjectSpellCheckRepository -> ProjectDataRepository",
			"ProjectSpellCheckRepository -> SpellCheckRepository",
			"ProjectsRepository -> ProjectDataRepository",
			"SceneDraftRepository -> SceneContentRepository",
		),
	)

	@Test
	fun `services never reference siblings or upward`() = assertNoViolations(
		"A Service may only reference Repositories, Foundation primitives and Data Sources",
		violations(SERVICE, SERVICE, USE_CASE, COMPONENT),
		baseline = setOf(
			"BackupManagerService -> AndroidShareService",
			"StatisticsService -> ReferenceIndexService",
			"TagIndexService -> BuildTagIndexUseCase",
		),
	)

	@Test
	fun `use cases never reference components`() = assertNoViolations(
		"A Use Case may not reference a Component",
		violations(USE_CASE, COMPONENT),
	)

	@Test
	fun `use cases inject sibling use cases lazily`() = assertNoViolations(
		"A Use Case must take another Use Case lazily (Lazy<T>, a () -> T lambda, or by inject())",
		violations(USE_CASE, USE_CASE).filterNot { it.lazy },
		baseline = setOf(
			"AutoConfirmReferencesUseCase -> ScrubInvalidReferencesUseCase",
			"BackfillEntryReferencesUseCase -> ScrubInvalidReferencesUseCase",
			"CleanupReferencesOnEntryDeleteUseCase -> ScrubInvalidReferencesUseCase",
			"RenameEntryUseCase -> BackfillEntryReferencesUseCase",
		),
	)

	@Test
	fun `components never reference data sources`() = assertNoViolations(
		"A Component may not reference a Data Source, go through a Repository",
		violations(COMPONENT, DATASOURCE),
		baseline = setOf(
			"ProjectsListComponent -> ProjectMetadataDatasource",
		),
	)
}
