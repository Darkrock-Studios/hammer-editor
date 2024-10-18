package repositories.timeline

import com.darkrockstudios.apps.hammer.base.http.readToml
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineContainer
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineDatasource
import com.darkrockstudios.apps.hammer.common.dependencyinjection.createTomlSerializer
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import getProject1Def
import kotlinx.coroutines.test.runTest
import net.peanuuutz.tomlkt.Toml
import okio.fakefilesystem.FakeFileSystem
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import utils.BaseTest
import kotlin.test.assertEquals

class TimeLineDatasourceTest : BaseTest() {

	lateinit var ffs: FakeFileSystem
	lateinit var toml: Toml
	lateinit var datasource: TimeLineDatasource

	@BeforeEach
	override fun setup() {
		super.setup()
		ffs = FakeFileSystem()
		toml = createTomlSerializer()
		datasource = TimeLineDatasource(ffs, toml)
		setupKoin(module {
			single { ffs }
			single { toml }
		})
	}

	@Test
	fun `Load Timeline`() = runTest(mainTestDispatcher) {
		val projDef = getProject1Def()
		val events = fakeEvents()
		writeEventsToDisk(projDef, events, ffs, toml)

		val timeline = datasource.loadTimeline(projDef)
		assertEquals(events, timeline.events)
	}

	@Test
	fun `Load Timeline when none exists`() = runTest(mainTestDispatcher) {
		val projDef = getProject1Def()
		val timeline = datasource.loadTimeline(projDef)
		assertEquals(emptyList(), timeline.events)
	}

	@Test
	fun `Store Timeline`() = runTest(mainTestDispatcher) {
		val projDef = getProject1Def()
		val events = fakeEvents()

		datasource.storeTimeline(
			TimeLineContainer(events),
			projDef
		)

		val filePath = TimeLineDatasource.getTimelineFilePath(projDef).toOkioPath()
		assertTrue(ffs.exists(filePath))

		val loadedContainer: TimeLineContainer = ffs.readToml(filePath, toml)
		assertEquals(events, loadedContainer.events)
	}
}