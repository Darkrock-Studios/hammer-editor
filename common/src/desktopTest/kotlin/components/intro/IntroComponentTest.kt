package components.intro

import com.darkrockstudios.apps.hammer.common.components.intro.Intro
import com.darkrockstudios.apps.hammer.common.components.intro.IntroComponent
import com.darkrockstudios.apps.hammer.common.data.nux.NuxFilesystemDatasource
import com.darkrockstudios.apps.hammer.common.data.nux.NuxRepository
import com.darkrockstudios.apps.hammer.common.dependencyinjection.createTomlSerializer
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import okio.fakefilesystem.FakeFileSystem
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import utils.ComponentTest
import utils.inMemoryGlobalSettingsStore
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IntroComponentTest : ComponentTest() {

	private lateinit var fileSystem: FakeFileSystem
	private var doneCount = 0

	private val lastSlide = Intro.Slide.entries.lastIndex

	@BeforeEach
	override fun setup() {
		super.setup()
		fileSystem = FakeFileSystem()
		doneCount = 0
		setupKoin(module {
			single { NuxRepository(NuxFilesystemDatasource(fileSystem, createTomlSerializer()), inMemoryGlobalSettingsStore()) }
		})
	}

	private fun newComponent() = IntroComponent(context, onDone = { doneCount++ }).also { context.resume() }

	private fun introShownOnDisk() = NuxFilesystemDatasource(fileSystem, createTomlSerializer()).load().introShown

	@Test
	fun `Next and back step through the slides without running off either end`() = runTest(mainTestDispatcher) {
		val component = newComponent()

		component.back()
		val atStart = component.state.value.slide
		repeat(2) { component.next() }
		val afterTwo = component.state.value.slide
		component.back()

		assertEquals(0, atStart)
		assertEquals(2, afterTwo)
		assertEquals(1, component.state.value.slide)
	}

	@Test
	fun `Next on the last slide finishes the intro`() = runTest(mainTestDispatcher) {
		val component = newComponent()
		component.showSlide(lastSlide)

		component.next()
		advanceUntilIdle()

		assertEquals(1, doneCount)
		assertTrue(introShownOnDisk())
	}

	@Test
	fun `The back button steps back a slide, and is left alone on the first`() = runTest(mainTestDispatcher) {
		val component = newComponent()

		val handledOnFirst = context.back()
		component.next()
		val handledOnSecond = context.back()

		assertFalse(handledOnFirst)
		assertTrue(handledOnSecond)
		assertEquals(0, component.state.value.slide)
	}

	@Test
	fun `The slide survives process death`() = runTest(mainTestDispatcher) {
		val component = newComponent()
		component.showSlide(3)

		val restored = IntroComponent(context.saveAndRecreate(), onDone = {})

		assertEquals(3, restored.state.value.slide)
	}

	@Test
	fun `Leaving from any slide ends the intro for good`() = runTest(mainTestDispatcher) {
		val component = newComponent()
		component.next()

		component.leave()
		advanceUntilIdle()

		assertEquals(1, doneCount)
		assertTrue(introShownOnDisk())
	}
}
