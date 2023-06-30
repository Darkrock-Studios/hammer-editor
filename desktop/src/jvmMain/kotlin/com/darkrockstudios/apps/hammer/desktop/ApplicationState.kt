package com.darkrockstudios.apps.hammer.desktop

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.common.data.MenuDescriptor
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.closeProjectScope
import com.darkrockstudios.apps.hammer.common.data.openProjectScope
import com.darkrockstudios.apps.hammer.common.dependencyinjection.ProjectDefScope
import kotlinx.coroutines.runBlocking
import org.koin.core.component.getScopeId
import org.koin.java.KoinJavaComponent

class ApplicationState {
	private val _windows = mutableStateOf<WindowState>(WindowState.ProjectSectionWindow())
	val windows: State<WindowState> = _windows

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

	fun openProject(projectDef: ProjectDef) {
		runBlocking {
			openProjectScope(projectDef)
		}

		_windows.value = WindowState.ProjectWindow(projectDef)
	}

	fun closeProject() {
		val def = (_windows.value as WindowState.ProjectWindow).projectDef
		closeProjectScope(KoinJavaComponent.getKoin().getScope(ProjectDefScope(def).getScopeId()), def)

		_closeRequest.value = CloseType.None
		_windows.value = WindowState.ProjectSectionWindow()
	}

	fun showConfirmProjectClose(closeType: CloseType) {
		_closeRequest.value = closeType
	}

	fun dismissConfirmProjectClose() {
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