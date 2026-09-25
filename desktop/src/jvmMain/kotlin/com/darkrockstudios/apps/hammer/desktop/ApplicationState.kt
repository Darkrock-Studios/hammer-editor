package com.darkrockstudios.apps.hammer.desktop

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.common.components.projectroot.ProjectDeepLink
import com.darkrockstudios.apps.hammer.common.data.MenuDescriptor
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.closeProjectScope
import com.darkrockstudios.apps.hammer.common.data.openProjectScope
import com.darkrockstudios.apps.hammer.common.dependencyinjection.ProjectDefScope
import com.darkrockstudios.apps.hammer.desktop.shortcuts.QuickShortcuts
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.koin.core.component.getScopeId
import org.koin.java.KoinJavaComponent

class ApplicationState(
	private val appScope: CoroutineScope,
	private val quickShortcuts: QuickShortcuts,
	initialProject: ProjectDef? = null,
	pendingDeepLink: ProjectDeepLink? = null,
	private val openScope: (ProjectDef) -> Unit = { runBlocking { openProjectScope(it) } },
	private val closeScope: (ProjectDef) -> Unit = { def ->
		closeProjectScope(KoinJavaComponent.getKoin().getScope(ProjectDefScope(def).getScopeId()), def)
	},
) {
	private val _windows = mutableStateOf<WindowState>(WindowState.ProjectSectionWindow())
	val windows: State<WindowState> = _windows

	private var _pendingDeepLink: ProjectDeepLink? = pendingDeepLink

	// A project to open once the open one has closed, for an open request that had to close it first.
	private var pendingOpen: Pair<ProjectDef, ProjectDeepLink?>? = null

	private val _raiseRequests = mutableStateOf(0)

	/** Goes up each time the app's window should come to the front, as when a second launch is handed to it. */
	val raiseRequests: State<Int> = _raiseRequests

	private val _deepLinks = MutableSharedFlow<ProjectDeepLink>(extraBufferCapacity = 1)

	/** Places for the open project's window to go, from open requests for the project already open. */
	val deepLinks: SharedFlow<ProjectDeepLink> = _deepLinks

	init {
		if (initialProject != null) {
			openProject(initialProject)
		}
	}

	fun consumePendingDeepLink(): ProjectDeepLink? {
		val link = _pendingDeepLink
		_pendingDeepLink = null
		return link
	}

	private val _menu = MutableValue<Set<MenuDescriptor>>(emptySet())
	val menu: Value<Set<MenuDescriptor>> = _menu

	private val _closeRequest = MutableValue(CloseType.None)
	val closeRequest: Value<CloseType> = _closeRequest

	fun addMenu(menuDescriptor: MenuDescriptor) {
		_menu.value = mutableSetOf<MenuDescriptor>().apply {
			addAll(_menu.value)
			add(menuDescriptor)
		}
	}

	fun removeMenu(menuId: String) {
		_menu.value = _menu.value.filter { it.id != menuId }.toSet()
	}

	fun raise() {
		_raiseRequests.value++
	}

	/**
	 * Opens [projectDef], going to [deepLink] once it is open. When another project is open, asks to
	 * close it first, the usual way, and opens this one if it closes. Ignored while a close is under way.
	 */
	fun requestOpen(projectDef: ProjectDef, deepLink: ProjectDeepLink? = null) {
		val current = _windows.value
		when {
			_closeRequest.value != CloseType.None -> Unit
			current !is WindowState.ProjectWindow -> {
				_pendingDeepLink = deepLink
				openProject(projectDef)
			}
			current.projectDef == projectDef -> deepLink?.let(_deepLinks::tryEmit)
			else -> {
				pendingOpen = projectDef to deepLink
				showConfirmProjectClose(CloseType.Project)
			}
		}
	}

	fun openProject(projectDef: ProjectDef) {
		openScope(projectDef)

		_windows.value = WindowState.ProjectWindow(projectDef)
		appScope.launch { quickShortcuts.refresh(excludeCurrent = projectDef) }
	}

	fun closeProject() {
		val def = (_windows.value as WindowState.ProjectWindow).projectDef
		closeScope(def)

		_closeRequest.value = CloseType.None
		_windows.value = WindowState.ProjectSectionWindow()
		pendingOpen?.let { (next, deepLink) ->
			pendingOpen = null
			_pendingDeepLink = deepLink
			openProject(next)
		}
	}

	fun showConfirmProjectClose(closeType: CloseType) {
		if (closeType != CloseType.Project) pendingOpen = null
		_closeRequest.value = closeType
	}

	fun dismissConfirmProjectClose() {
		pendingOpen = null
		_closeRequest.value = CloseType.None
	}

	enum class CloseType {
		Application,
		Project,
		None
	}
}

sealed class WindowState {
	data class ProjectSectionWindow(private val _data: Boolean = true) : WindowState()

	data class ProjectWindow(val projectDef: ProjectDef) : WindowState()
}