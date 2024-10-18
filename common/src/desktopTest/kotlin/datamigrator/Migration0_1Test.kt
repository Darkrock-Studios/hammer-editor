package datamigrator

import MIGRATION_0_1
import MIGRATION_0_1_ALREADY
import MIGRATION_0_1_NO_TIMELINE
import com.darkrockstudios.apps.hammer.base.http.createJsonSerializer
import com.darkrockstudios.apps.hammer.base.http.readToml
import com.darkrockstudios.apps.hammer.common.data.migrator.Migration0_1
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineContainer
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineDatasource
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineEvent
import com.darkrockstudios.apps.hammer.common.dependencyinjection.createTomlSerializer
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import createProject
import getProjectDef
import io.mockk.MockKAnnotations
import okio.fakefilesystem.FakeFileSystem
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import utils.BaseTest
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class Migration0_1Test : BaseTest() {
	private lateinit var fakeFileSystem: FakeFileSystem

	@BeforeEach
	override fun setup() {
		super.setup()
		MockKAnnotations.init(this, relaxUnitFun = true)

		fakeFileSystem = FakeFileSystem()
	}

	@Test
	fun `Normal Migration`() {
		val toml = createTomlSerializer()

		val projDef = getProjectDef(MIGRATION_0_1)
		createProject(fakeFileSystem, MIGRATION_0_1)

		val migrator = Migration0_1(
			fakeFileSystem,
			toml,
			createJsonSerializer()
		)

		migrator.migrate(projDef)

		val path = TimeLineDatasource.getTimelineFilePath(projDef)
		val timeLineContainer = fakeFileSystem.readToml(path.toOkioPath(), toml, TimeLineContainer::class)

		fakeFileSystem.read(path.toOkioPath()) {
			val str = readUtf8()
			println(str)
		}

		assertEquals(3, timeLineContainer.events.size)

		assertEquals(
			TimeLineEvent(
				id = 1,
				order = 0,
				date = "Date 1",
				content = "Content 1",
			),
			timeLineContainer.events[0]
		)

		assertEquals(
			TimeLineEvent(
				id = 2,
				order = 1,
				date = "Date 2",
				content = "Content 2",
			),
			timeLineContainer.events[1]
		)

		assertEquals(
			TimeLineEvent(
				id = 3,
				order = 2,
				date = "Date 3",
				content = "Content 3",
			),
			timeLineContainer.events[2]
		)
	}

	@Test
	fun `Normal Migration - No Timeline`() {
		val toml = createTomlSerializer()

		val projDef = getProjectDef(MIGRATION_0_1_NO_TIMELINE)
		createProject(fakeFileSystem, MIGRATION_0_1_NO_TIMELINE)

		val migrator = Migration0_1(
			fakeFileSystem,
			toml,
			createJsonSerializer()
		)

		migrator.migrate(projDef)

		val path = TimeLineDatasource.getTimelineFilePath(projDef).toOkioPath()
		assertFalse(fakeFileSystem.exists(path))
	}

	@Test
	fun `Already Migrated`() {
		val toml = createTomlSerializer()

		val projDef = getProjectDef(MIGRATION_0_1_ALREADY)
		createProject(fakeFileSystem, MIGRATION_0_1_ALREADY)
		val path = TimeLineDatasource.getTimelineFilePath(projDef)
		val timeLineContainerPremigrate = fakeFileSystem.readToml(path.toOkioPath(), toml, TimeLineContainer::class)

		val migrator = Migration0_1(
			fakeFileSystem,
			toml,
			createJsonSerializer()
		)

		migrator.migrate(projDef)

		val timeLineContainer = fakeFileSystem.readToml(path.toOkioPath(), toml, TimeLineContainer::class)
		assertEquals(timeLineContainerPremigrate, timeLineContainer)
	}
}