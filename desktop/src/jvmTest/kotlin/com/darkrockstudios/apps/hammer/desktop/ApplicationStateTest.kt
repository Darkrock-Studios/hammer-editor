package com.darkrockstudios.apps.hammer.desktop

import com.darkrockstudios.apps.hammer.common.components.projectroot.ProjectDeepLink
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import com.darkrockstudios.apps.hammer.desktop.shortcuts.NoOpQuickShortcuts
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class ApplicationStateTest {

	private val opened = mutableListOf<String>()
	private val closed = mutableListOf<String>()

	private fun project(name: String) = ProjectDef(name, HPath("/projects/$name", name, true))

	private fun state(initial: ProjectDef? = null) = ApplicationState(
		appScope = CoroutineScope(Dispatchers.Unconfined),
		quickShortcuts = NoOpQuickShortcuts(),
		initialProject = initial,
		openScope = { opened += it.name },
		closeScope = { closed += it.name },
	)

	private val ApplicationState.shown get() = (windows.value as? WindowState.ProjectWindow)?.projectDef?.name

	@Test
	fun `an open request from the projects list opens at once, at its deep link`() {
		val app = state()

		app.requestOpen(project("Storm"), ProjectDeepLink.Scene(3))

		assertEquals("Storm", app.shown)
		assertEquals(ProjectDeepLink.Scene(3), app.consumePendingDeepLink())
	}

	@Test
	fun `an open request for the open project goes to its deep link`() = runBlocking {
		val app = state(project("Storm"))
		val link = launch(Dispatchers.Unconfined) { assertEquals(ProjectDeepLink.Note(7), app.deepLinks.first()) }
		yield()

		app.requestOpen(project("Storm"), ProjectDeepLink.Note(7))

		link.join()
		assertEquals(listOf("Storm"), opened)
		assertEquals(ApplicationState.CloseType.None, app.closeRequest.value)
	}

	@Test
	fun `an open request for another project asks to close the open one, then opens it`() {
		val app = state(project("Storm"))

		app.requestOpen(project("Calm"), ProjectDeepLink.Scene(1))
		assertEquals(ApplicationState.CloseType.Project, app.closeRequest.value)
		assertEquals("Storm", app.shown)

		app.closeProject()

		assertEquals(listOf("Storm"), closed)
		assertEquals("Calm", app.shown)
		assertEquals(ProjectDeepLink.Scene(1), app.consumePendingDeepLink())
	}

	@Test
	fun `a cancelled close drops the open request`() {
		val app = state(project("Storm"))
		app.requestOpen(project("Calm"))

		app.dismissConfirmProjectClose()
		app.showConfirmProjectClose(ApplicationState.CloseType.Project)
		app.closeProject()

		assertEquals(null, app.shown)
		assertEquals(listOf("Storm"), opened)
	}

	@Test
	fun `an open request while quitting leaves the quit alone`() {
		val app = state(project("Storm"))
		app.showConfirmProjectClose(ApplicationState.CloseType.Application)

		app.requestOpen(project("Calm"))

		assertEquals(ApplicationState.CloseType.Application, app.closeRequest.value)
		app.closeProject()
		assertEquals(null, app.shown)
	}
}
