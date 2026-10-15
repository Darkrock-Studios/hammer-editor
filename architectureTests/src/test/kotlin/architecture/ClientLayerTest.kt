package architecture

import architecture.ClientLayer.*
import org.junit.jupiter.api.Test

private enum class ClientLayer {
	DATASOURCE, FOUNDATION, REPOSITORY, SERVICE, USE_CASE, COMPONENT
}

internal val foundation = setOf("IdAllocator", "SyncJournal", "GlobalSettingsStore")

private fun layerOf(name: String): ClientLayer? = when {
	name in foundation -> FOUNDATION
	name.endsWith("Datasource") -> DATASOURCE
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
	private val dependencies by lazy { clientScope().dependencies() }

	private fun edges(from: ClientLayer, vararg forbidden: ClientLayer): List<Dependency> =
		dependencies.filter { layerOf(it.owner) == from && layerOf(it.target) in forbidden }

	@Test
	fun `data sources reference no other layer`() = assertNoViolations(
		"A Data Source may not reference any architecture layer, including other Data Sources",
		edges(DATASOURCE, *ClientLayer.entries.toTypedArray()),
	)

	@Test
	fun `foundation primitives never reference upward`() = assertNoViolations(
		"A Foundation primitive may only reference Data Sources and other Foundation primitives",
		edges(FOUNDATION, REPOSITORY, SERVICE, USE_CASE, COMPONENT),
	)

	@Test
	fun `repositories reference only data sources and foundation`() = assertNoViolations(
		"A Repository may only reference Data Sources and Foundation primitives, never another Repository",
		edges(REPOSITORY, REPOSITORY, SERVICE, USE_CASE, COMPONENT),
		baseline = setOf(
			"ProjectsRepository -> ProjectDataRepository",
			"SceneDraftRepository -> SceneContentRepository",
		),
	)

	@Test
	fun `services never reference siblings or upward`() = assertNoViolations(
		"A Service may only reference Repositories, Foundation primitives and Data Sources",
		edges(SERVICE, SERVICE, USE_CASE, COMPONENT),
		baseline = setOf(
			"StatisticsService -> ReferenceIndexService",
			"TagIndexService -> BuildTagIndexUseCase",
		),
	)

	@Test
	fun `use cases never reference components`() = assertNoViolations(
		"A Use Case may not reference a Component",
		edges(USE_CASE, COMPONENT),
	)

	// A lazily injected edge is the sanctioned way to break an unavoidable cycle, so it is not counted.
	@Test
	fun `use case graph is acyclic`() = assertNoViolations(
		"Use Cases may reference each other but must not form a cycle",
		findCycles(edges(USE_CASE, USE_CASE).filterNot { it.lazy }),
	)

	@Test
	fun `foundation graph is acyclic`() = assertNoViolations(
		"Foundation primitives may reference each other but must not form a cycle",
		findCycles(edges(FOUNDATION, FOUNDATION)),
	)

	@Test
	fun `components never reference data sources`() = assertNoViolations(
		"A Component may not reference a Data Source, go through a Repository",
		edges(COMPONENT, DATASOURCE),
		baseline = setOf(
			"ProjectsListComponent -> ProjectMetadataDatasource",
		),
	)
}
