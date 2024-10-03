package com.darkrockstudios.apps.hammer.projects.datasource

import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.base.http.createJsonSerializer
import com.darkrockstudios.apps.hammer.database.ProjectDao
import com.darkrockstudios.apps.hammer.database.ProjectsDao
import com.darkrockstudios.apps.hammer.e2e.util.SqliteTestDatabase
import com.darkrockstudios.apps.hammer.project.ProjectDefinition
import com.darkrockstudios.apps.hammer.projects.ProjectsDatabaseDatasource
import com.darkrockstudios.apps.hammer.projects.ProjectsSyncData
import com.darkrockstudios.apps.hammer.utilities.sqliteDateTimeStringToInstant
import com.darkrockstudios.apps.hammer.utilities.toSqliteDateTimeString
import com.darkrockstudios.apps.hammer.utils.BaseTest
import com.darkrockstudios.apps.hammer.utils.TestClock
import korlibs.io.util.UUID
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class ProjectsDatabaseDatasourceTest : BaseTest() {

	private lateinit var testDatabase: SqliteTestDatabase
	private lateinit var json: Json
	private lateinit var clock: TestClock

	@BeforeEach
	override fun setup() {
		super.setup()
		json = createJsonSerializer()
		clock = TestClock(Clock.System)

		testDatabase = SqliteTestDatabase()
		testDatabase.initialize()

		setupKoin()
	}

	private fun createDatasource(): ProjectsDatabaseDatasource {
		return ProjectsDatabaseDatasource(
			projectDao = ProjectDao(testDatabase, clock),
			projectsDao = ProjectsDao(testDatabase),
		)
	}

	@Test
	fun `Create User Data`() = runTest {
		val userId = 1L

		// This does almost nothing in the new database world, so just make sure it doesn't throw
		val datasource = createDatasource()
		datasource.createUserData(userId)
	}

	@Test
	fun `Get Projects`() = runTest {
		val userId = 1L
		val projectName1 = "Test Project 1"
		val uuid1 = UUID.randomUUID().toString()
		val projectName2 = "Test Project 2"
		val uuid2 = UUID.randomUUID().toString()

		testDatabase.serverDatabase.accountQueries.createAccount(
			"test@test.com",
			"salt",
			"hash",
			true
		)
		testDatabase.serverDatabase.projectQueries.createProject(
			userId = userId,
			name = projectName1,
			uuid = uuid1,
		)
		testDatabase.serverDatabase.projectQueries.createProject(
			userId = userId,
			name = projectName2,
			uuid = uuid2,
		)

		val datasource = createDatasource()
		val projects = datasource.getProjects(userId)

		assertEquals(
			setOf(
				ProjectDefinition(projectName1, ProjectId(uuid1)),
				ProjectDefinition(projectName2, ProjectId(uuid2)),
			),
			projects
		)
	}

	@Test
	fun `Save SyncData`() = runTest {
		val userId = 1L

		testDatabase.serverDatabase.accountQueries.createAccount(
			"test@test.com",
			"salt",
			"hash",
			true
		)

		val syncData = ProjectsSyncData(
			lastSync = Instant.fromEpochSeconds(123),
			deletedProjects = setOf(
				ProjectId("project-id-1"),
				ProjectId("project-id-2"),
			)
		)

		val datasource = createDatasource()
		datasource.saveSyncData(userId, syncData)

		val lastSyncStr =
			testDatabase.serverDatabase.accountQueries.getLastSync(userId).executeAsOneOrNull()
		val lastSync = lastSyncStr?.let { sqliteDateTimeStringToInstant(lastSyncStr) }
		assertEquals(syncData.lastSync, lastSync)
		val deletedProjects =
			testDatabase.serverDatabase.deletedProjectQueries.getDeletedProjects(userId)
				.executeAsList()
				.map { ProjectId(it.uuid) }
				.toSet()
		assertEquals(syncData.deletedProjects, deletedProjects)
	}

	@Test
	fun `Load SyncData`() = runTest {
		val userId = 1L

		val instant = Instant.fromEpochSeconds(123)
		val instantLastUpdate = Instant.fromEpochSeconds(1234)
		testDatabase.serverDatabase.accountQueries.testInsertAccount(
			email = "test@test.com",
			salt = "salt",
			password_hash = "hash",
			is_admin = true,
			created = instant.toSqliteDateTimeString(),
			last_sync = instantLastUpdate.toSqliteDateTimeString()
		)
		testDatabase.serverDatabase.deletedProjectQueries.addDeletedProject(
			userId = userId,
			uuid = "project-id-1"
		)
		testDatabase.serverDatabase.deletedProjectQueries.addDeletedProject(
			userId = userId,
			uuid = "project-id-2"
		)

		val datasource = createDatasource()
		val loadedSyncData = datasource.loadSyncData(userId)

		assertEquals(
			setOf(
				ProjectId("project-id-1"),
				ProjectId("project-id-2"),
			), loadedSyncData.deletedProjects
		)
		assertEquals(
			instantLastUpdate, loadedSyncData.lastSync
		)
	}

	@Test
	fun `Update SyncData`() = runTest {
		val userId = 1L

		testDatabase.serverDatabase.accountQueries.createAccount(
			"test@test.com",
			"salt",
			"hash",
			true
		)
		testDatabase.serverDatabase.deletedProjectQueries.addDeletedProject(
			userId = userId,
			uuid = "project-id-1"
		)
		testDatabase.serverDatabase.deletedProjectQueries.addDeletedProject(
			userId = userId,
			uuid = "project-id-2"
		)


		val datasource = createDatasource()
		val loadedSyncData = datasource.updateSyncData(userId) { data ->
			data.copy(
				lastSync = Instant.fromEpochSeconds(456),
				deletedProjects = data.deletedProjects + ProjectId("project-id-3")
			)
		}

		val updatedSyncData = ProjectsSyncData(
			lastSync = Instant.fromEpochSeconds(456),
			deletedProjects = setOf(
				ProjectId("project-id-1"),
				ProjectId("project-id-2"),
				ProjectId("project-id-3"),
			)
		)

		assertEquals(updatedSyncData, loadedSyncData)
	}
}