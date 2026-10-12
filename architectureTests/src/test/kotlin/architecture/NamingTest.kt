package architecture

import org.junit.jupiter.api.Test

/** Layers are identified by class name suffix, so the suffixes themselves have to stay consistent. */
class NamingTest {
	private val declarations by lazy {
		(clientScope() + serverScope()).classesAndInterfacesAndObjects(includeNested = true)
	}

	@Test
	fun `data sources are spelled Datasource`() = assertNoViolations(
		"Data Sources use the suffix Datasource, not DataSource",
		declarations.map { it.name }.filter { it.endsWith("DataSource") }.toSet(),
	)

	@Test
	fun `components live in a components package`() = assertNoViolations(
		"A class named *Component must live under a components package",
		clientScope().classes()
			.filter { it.name.endsWith("Component") }
			.filterNot { ".components" in it.packagee?.name.orEmpty() }
			.map { it.name }
			.toSet(),
	)

	@Test
	fun `foundation suffixes are reserved for foundation primitives`() = assertNoViolations(
		"Store, Journal and Allocator are reserved for the fixed Foundation set",
		clientScope().classesAndInterfacesAndObjects(includeNested = true)
			.map { it.name }
			.filter { it.endsWith("Store") || it.endsWith("Journal") || it.endsWith("Allocator") }
			.filterNot { it in foundation }
			.toSet(),
		// Auth token storage is a Data Source that predates the reserved suffixes.
		baseline = setOf(
			"AuthTokenStore",
			"EncryptedFileAuthTokenStore",
			"EncryptedSharedPrefsAuthTokenStore",
			"FileAuthTokenStore",
			"KeychainAuthTokenStore",
		),
	)
}
