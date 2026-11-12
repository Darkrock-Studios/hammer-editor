package com.darkrockstudios.apps.hammer.integration.scenarios

import com.darkrockstudios.apps.hammer.common.data.ExampleProjectRepository
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsStore
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository
import com.darkrockstudios.apps.hammer.common.util.zip.unzipBytesToDirectory
import com.darkrockstudios.apps.hammer.integration.RoundTripTestBase
import io.github.aakira.napier.Antilog
import io.github.aakira.napier.LogLevel
import io.github.aakira.napier.Napier
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.koin.test.get
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The bundled example project syncs as it ships: every entity goes up once from the first device,
 * and a second device's first sync only downloads. A heal re-upload would mean the bundle left a
 * field for the client to backfill on store.
 */
class ExampleProjectSyncTest : RoundTripTestBase() {

	@Test
	@Timeout(value = 120)
	fun `bundled example uploads once and a second device needs no heal`() = runBlocking {
		val zipBytes = ExampleProjectRepository::class.java
			.getResourceAsStream("/raw/${ExampleProjectRepository.EXAMPLE_PROJECT_FILE_NAME}")!!
			.use { it.readBytes() }
		unzipBytesToDirectory(fileSystem, zipBytes, projectsRoot)
		val projectDef = assertNotNull(get<ProjectsRepository>().findProject(ExampleProjectRepository.PROJECT_NAME))
		val heals = HealLog().also { Napier.base(it) }

		get<GlobalSettingsStore>().updateServerSettings(makeServerSettings())
		val a = openClient(projectDef.name)
		val wire = tapWire()
		assertTrue(a.syncNoConflict(), "first device sync should succeed")
		val localIds = clientEntityHashes(a).keys
		val uploaded = wire.entitiesUploaded()
		assertTrue(localIds.isNotEmpty())
		assertEquals(localIds, uploaded.toSet(), "first sync uploads every entity")
		assertEquals(uploaded.size, uploaded.toSet().size, "first sync uploads each entity once")
		assertEquals(emptyList(), wire.entitiesPulled(), "first sync downloads nothing")
		assertEquals(emptyList(), heals.messages)
		assertConverged(projectDef.name, a)
		assertResyncSilent(a)

		val b = secondDeviceFor(a, "${ExampleProjectRepository.PROJECT_NAME} B")
		wire.reset()
		assertTrue(b.syncNoConflict(), "second device sync should succeed")
		assertEquals(localIds, wire.entitiesPulled().toSet(), "second device downloads every entity")
		assertEquals(emptyList(), wire.entitiesUploaded(), "second device's first sync re-uploads nothing")
		assertEquals(emptyList(), heals.messages)

		assertConverged(projectDef.name, a, b)
		assertResyncSilent(b)
		assertResyncSilent(a)
		Napier.takeLogarithm(heals)
	}

	private class HealLog : Antilog() {
		val messages: MutableList<String> = java.util.Collections.synchronizedList(mutableListOf())

		override fun performLog(priority: LogLevel, tag: String?, throwable: Throwable?, message: String?) {
			if (message != null && message.contains("Healing server entity")) messages += message
		}
	}
}
