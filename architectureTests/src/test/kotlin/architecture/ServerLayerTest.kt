package architecture

import org.junit.jupiter.api.Test

private fun isDatasource(name: String) =
	name.endsWith("Datasource") || name.endsWith("Dao")

private fun isRepository(name: String) = name.endsWith("Repository")

private fun isService(name: String) = name.endsWith("Service")

private fun isUseCase(name: String) = name.endsWith("UseCase")

private val routeFunction = Regex("""\bfun\s+Route\.""")

/** Enforces the server dependency rules from docs/ARCHITECTURE.md. */
class ServerLayerTest {
	private val scope by lazy { serverScope() }
	private val dependencies by lazy { scope.dependencies() }

	@Test
	fun `data sources never reference upward`() = assertNoViolations(
		"A server Data Source may not reference a Repository, Service or Use Case",
		dependencies.filter {
			isDatasource(it.owner) && (isRepository(it.target) || isService(it.target) || isUseCase(it.target))
		},
	)

	@Test
	fun `repositories never reference upward`() = assertNoViolations(
		"A server Repository may not reference a Service or Use Case",
		dependencies.filter { isRepository(it.owner) && (isService(it.target) || isUseCase(it.target)) },
	)

	@Test
	fun `services never reference siblings or upward`() = assertNoViolations(
		"A server Service may only reference Repositories and Data Sources",
		dependencies.filter { isService(it.owner) && (isService(it.target) || isUseCase(it.target)) },
	)

	@Test
	fun `repositories never reference other repositories`() = assertNoViolations(
		"A server Repository may not reference another Repository",
		dependencies.filter { isRepository(it.owner) && isRepository(it.target) },
	)

	// Route handlers are extension functions on Route, in files of any name. The whole file is
	// searched because some name a Data Source by its qualified name, with no import.
	@Test
	fun `routes never reference data sources`() = assertNoViolations(
		"Server Routes may not reference a Data Source, go through a Repository",
		scope.files
			.filter { routeFunction.containsMatchIn(it.text) }
			.flatMap { file ->
				typeName.findAll(file.text.replace(comment, ""))
					.map { it.value }
					.filter(::isDatasource)
					.distinct()
					.map { Dependency(file.name, it, lazy = false) }
			},
	)
}
