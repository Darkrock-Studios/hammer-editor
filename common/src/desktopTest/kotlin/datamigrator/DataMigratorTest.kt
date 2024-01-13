package datamigrator

import PROJECT_1_NAME
import PROJECT_2_NAME
import com.darkrockstudios.apps.hammer.common.components.storyeditor.metadata.Info
import com.darkrockstudios.apps.hammer.common.components.storyeditor.metadata.ProjectMetadata
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsRepository
import com.darkrockstudios.apps.hammer.common.data.migrator.DataMigrator
import com.darkrockstudios.apps.hammer.common.data.migrator.Migration
import com.darkrockstudios.apps.hammer.common.data.migrator.Migration0_1
import com.darkrockstudios.apps.hammer.common.data.migrator.PROJECT_DATA_VERSION
import com.darkrockstudios.apps.hammer.common.data.projectmetadatarepository.ProjectMetadataRepository
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository
import getProjectDef
import io.mockk.*
import io.mockk.impl.annotations.MockK
import kotlinx.datetime.Clock
import org.junit.Before
import org.koin.dsl.module
import utils.BaseTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DataMigratorTest : BaseTest() {

	@MockK(relaxed = true)
	private lateinit var globalSettingsRepository: GlobalSettingsRepository

	@MockK(relaxed = true)
	private lateinit var projectsRepository: ProjectsRepository

	@MockK(relaxed = true)
	private lateinit var projectMetadataRepository: ProjectMetadataRepository

	@Before
	override fun setup() {
		super.setup()
		MockKAnnotations.init(this, relaxUnitFun = true)
	}

	@Test
	fun `Check Projects Need Migration`() {
		every { projectsRepository.getProjects(any()) } returns
			listOf(
				getProjectDef(PROJECT_1_NAME),
				getProjectDef(PROJECT_2_NAME),
			)

		every {
			projectMetadataRepository.loadMetadata(getProjectDef(PROJECT_1_NAME))
		} returns ProjectMetadata(
			Info(
				created = Clock.System.now(),
				dataVersion = PROJECT_DATA_VERSION
			)
		)

		every {
			projectMetadataRepository.loadMetadata(getProjectDef(PROJECT_2_NAME))
		} returns ProjectMetadata(
			Info(
				created = Clock.System.now(),
				dataVersion = 0
			)
		)

		val migrator = DataMigrator(
			globalSettingsRepository = globalSettingsRepository,
			projectsRepository = projectsRepository,
			projectMetadataRepository = projectMetadataRepository,
		)

		val migrationNeeded = migrator.checkIfMigrationNeeded()
		assertTrue(migrationNeeded)
	}

	@Test
	fun `No Need Migration`() {
		every { projectsRepository.getProjects(any()) } returns
			listOf(
				getProjectDef(PROJECT_1_NAME),
				getProjectDef(PROJECT_2_NAME),
			)

		every {
			projectMetadataRepository.loadMetadata(getProjectDef(PROJECT_1_NAME))
		} returns ProjectMetadata(
			Info(
				created = Clock.System.now(),
				dataVersion = PROJECT_DATA_VERSION
			)
		)

		every {
			projectMetadataRepository.loadMetadata(getProjectDef(PROJECT_2_NAME))
		} returns ProjectMetadata(
			Info(
				created = Clock.System.now(),
				dataVersion = PROJECT_DATA_VERSION
			)
		)

		val migrator = DataMigrator(
			globalSettingsRepository = globalSettingsRepository,
			projectsRepository = projectsRepository,
			projectMetadataRepository = projectMetadataRepository,
		)

		val migrationNeeded = migrator.checkIfMigrationNeeded()
		assertFalse(migrationNeeded)
	}

	@Test
	fun `Handle Migration - No Need Migration`() {
		val mockMigrator = mockk<Migration0_1>()
		val testModule = module {
			factory<Migration0_1> { mockMigrator }
		}
		setupKoin(testModule)

		every { projectsRepository.getProjects(any()) } returns
			listOf(
				getProjectDef(PROJECT_1_NAME),
				getProjectDef(PROJECT_2_NAME),
			)

		every {
			projectMetadataRepository.loadMetadata(getProjectDef(PROJECT_1_NAME))
		} returns ProjectMetadata(
			Info(
				created = Clock.System.now(),
				dataVersion = PROJECT_DATA_VERSION
			)
		)

		every {
			projectMetadataRepository.loadMetadata(getProjectDef(PROJECT_2_NAME))
		} returns ProjectMetadata(
			Info(
				created = Clock.System.now(),
				dataVersion = PROJECT_DATA_VERSION
			)
		)

		val migrator = DataMigrator(
			globalSettingsRepository = globalSettingsRepository,
			projectsRepository = projectsRepository,
			projectMetadataRepository = projectMetadataRepository,
		)

		migrator.handleDataMigration()

		verify(exactly = 0) { mockMigrator.migrate(any()) }

		confirmVerified(mockMigrator)
	}

	@Test
	fun `Handle Migration - Migrate 1`() {
		val mockMigrator = mockk<Migration0_1>()
		every { mockMigrator.migrate(any()) } just Runs
		every { mockMigrator.toVersion } returns 1
		val testModule = module {
			factory<Migration0_1> { mockMigrator }
		}
		setupKoin(testModule)

		every { projectsRepository.getProjects(any()) } returns
			listOf(
				getProjectDef(PROJECT_1_NAME),
				getProjectDef(PROJECT_2_NAME),
			)

		val proj1Meta = ProjectMetadata(
			Info(
				created = Clock.System.now(),
				dataVersion = 0
			)
		)
		every {
			projectMetadataRepository.loadMetadata(getProjectDef(PROJECT_1_NAME))
		} returns proj1Meta

		every {
			projectMetadataRepository.loadMetadata(getProjectDef(PROJECT_2_NAME))
		} returns ProjectMetadata(
			Info(
				created = Clock.System.now(),
				dataVersion = PROJECT_DATA_VERSION
			)
		)

		val updateMetaSlot = CapturingSlot<(ProjectMetadata) -> ProjectMetadata>()
		every {
			projectMetadataRepository.updateMetadata(
				any(),
				capture(updateMetaSlot)
			)
		} just Runs

		val migrator = DataMigrator(
			globalSettingsRepository = globalSettingsRepository,
			projectsRepository = projectsRepository,
			projectMetadataRepository = projectMetadataRepository,
		)

		migrator.handleDataMigration()

		verify(exactly = 1) { mockMigrator.migrate(getProjectDef(PROJECT_1_NAME)) }
		assertTrue(updateMetaSlot.isCaptured)

		val updated = updateMetaSlot.captured.invoke(proj1Meta)
		assertEquals(1, updated.info.dataVersion)
	}

	@Test
	fun `Handle Migration - Migrate All`() {
		val maxVersion = 4

		val proj1def = getProjectDef(PROJECT_1_NAME)

		val testModule = module {
		}
		setupKoin(testModule)

		every { projectsRepository.getProjects(any()) } returns
			listOf(
				proj1def,
				getProjectDef(PROJECT_2_NAME),
			)

		val proj1Meta = ProjectMetadata(
			Info(
				created = Clock.System.now(),
				dataVersion = 0
			)
		)
		every {
			projectMetadataRepository.loadMetadata(proj1def)
		} returns proj1Meta

		every {
			projectMetadataRepository.loadMetadata(getProjectDef(PROJECT_2_NAME))
		} returns ProjectMetadata(
			Info(
				created = Clock.System.now(),
				dataVersion = maxVersion
			)
		)

		val updateMetaSlot = CapturingSlot<(ProjectMetadata) -> ProjectMetadata>()
		every {
			projectMetadataRepository.updateMetadata(
				any(),
				capture(updateMetaSlot)
			)
		} just Runs

		val migrator1 = spyk(object : Migration {
			override val toVersion = 1
			override fun migrate(projectDef: ProjectDef) {}
		})
		val migrator2 = spyk(object : Migration {
			override val toVersion = 2
			override fun migrate(projectDef: ProjectDef) {}
		})
		val migrator3 = spyk(object : Migration {
			override val toVersion = 3
			override fun migrate(projectDef: ProjectDef) {}
		})
		val migrator4 = spyk(object : Migration {
			override val toVersion = 4
			override fun migrate(projectDef: ProjectDef) {}
		})

		val migrator = object : DataMigrator(
			globalSettingsRepository = globalSettingsRepository,
			projectsRepository = projectsRepository,
			projectMetadataRepository = projectMetadataRepository,
		) {
			override val latestProjectDataVersion = maxVersion
			override fun getMigrators(): Map<Int, Migration> {
				return mutableMapOf(
					1 to migrator1,
					2 to migrator2,
					3 to migrator3,
					4 to migrator4,
				)
			}
		}

		migrator.handleDataMigration()

		verify(exactly = 1) { migrator1.migrate(proj1def) }
		verify(exactly = 1) { migrator2.migrate(proj1def) }
		verify(exactly = 1) { migrator3.migrate(proj1def) }
		verify(exactly = 1) { migrator4.migrate(proj1def) }

		val updated = updateMetaSlot.captured.invoke(proj1Meta)
		assertEquals(4, updated.info.dataVersion)
	}

	@Test
	fun `Handle Migration - Migrate 2 to 4`() {
		val maxVersion = 4

		val proj1def = getProjectDef(PROJECT_1_NAME)

		val testModule = module {
		}
		setupKoin(testModule)

		every { projectsRepository.getProjects(any()) } returns
			listOf(
				proj1def,
				getProjectDef(PROJECT_2_NAME),
			)

		val proj1Meta = ProjectMetadata(
			Info(
				created = Clock.System.now(),
				dataVersion = 2
			)
		)
		every {
			projectMetadataRepository.loadMetadata(proj1def)
		} returns proj1Meta

		every {
			projectMetadataRepository.loadMetadata(getProjectDef(PROJECT_2_NAME))
		} returns ProjectMetadata(
			Info(
				created = Clock.System.now(),
				dataVersion = maxVersion
			)
		)

		val updateMetaSlot = CapturingSlot<(ProjectMetadata) -> ProjectMetadata>()
		every {
			projectMetadataRepository.updateMetadata(
				any(),
				capture(updateMetaSlot)
			)
		} just Runs

		val migrator1 = spyk(object : Migration {
			override val toVersion = 1
			override fun migrate(projectDef: ProjectDef) {}
		})
		val migrator2 = spyk(object : Migration {
			override val toVersion = 2
			override fun migrate(projectDef: ProjectDef) {}
		})
		val migrator3 = spyk(object : Migration {
			override val toVersion = 3
			override fun migrate(projectDef: ProjectDef) {}
		})
		val migrator4 = spyk(object : Migration {
			override val toVersion = 4
			override fun migrate(projectDef: ProjectDef) {}
		})

		val migrator = object : DataMigrator(
			globalSettingsRepository = globalSettingsRepository,
			projectsRepository = projectsRepository,
			projectMetadataRepository = projectMetadataRepository,
		) {
			override val latestProjectDataVersion = maxVersion
			override fun getMigrators(): Map<Int, Migration> {
				return mutableMapOf(
					1 to migrator1,
					2 to migrator2,
					3 to migrator3,
					4 to migrator4,
				)
			}
		}

		migrator.handleDataMigration()

		verify(exactly = 0) { migrator1.migrate(proj1def) }
		verify(exactly = 0) { migrator2.migrate(proj1def) }
		verify(exactly = 1) { migrator3.migrate(proj1def) }
		verify(exactly = 1) { migrator4.migrate(proj1def) }

		val updated = updateMetaSlot.captured.invoke(proj1Meta)
		assertEquals(4, updated.info.dataVersion)
	}
}