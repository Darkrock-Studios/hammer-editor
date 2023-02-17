package com.darkrockstudios.apps.hammer.desktop

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.common.data.MenuDescriptor
import com.darkrockstudios.apps.hammer.common.data.ProjectDef

class ApplicationState {
	private val _windows = mutableStateOf<WindowState>(WindowState.ProjectSectionWindow())
	val windows: State<WindowState> = _windows

	private val _menu = MutableValue<Set<MenuDescriptor>>(emptySet())
	val menu: Value<Set<MenuDescriptor>> = _menu

	private val _shouldShowConfirmClose = MutableValue(CloseType.None)
	val shouldShowConfirmClose: Value<CloseType> = _shouldShowConfirmClose

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
		_windows.value = WindowState.ProjectWindow(projectDef)
	}

	fun closeProject() {
		_shouldShowConfirmClose.value = CloseType.None
		_windows.value = WindowState.ProjectSectionWindow()
	}

	fun showConfirmProjectClose(closeType: CloseType) {
		_shouldShowConfirmClose.value = closeType
	}

	fun dismissConfirmProjectClose() {
		_shouldShowConfirmClose.value = CloseType.None
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