package com.darkrockstudios.apps.hammer.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.rememberWindowState
import com.russhwolf.settings.Settings
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import org.koin.java.KoinJavaComponent.getKoin
import kotlin.time.Duration.Companion.milliseconds

class WindowGeometryStore(private val settings: Settings) {

	enum class Window {
		ProjectSelect,
		ProjectRoot,
	}

	data class Geometry(val size: DpSize, val maximized: Boolean)

	fun load(window: Window, defaultSize: DpSize): Geometry {
		val w = settings.getFloatOrNull("${window.prefix}$WIDTH")
		val h = settings.getFloatOrNull("${window.prefix}$HEIGHT")
		val max = settings.getBoolean("${window.prefix}$MAXIMIZED", false)
		val size = if (w != null && h != null) coerceWindowSize(w.dp, h.dp) else defaultSize
		return Geometry(size, max)
	}

	fun save(window: Window, size: DpSize, maximized: Boolean) {
		if (!maximized) {
			settings.putFloat("${window.prefix}$WIDTH", size.width.value)
			settings.putFloat("${window.prefix}$HEIGHT", size.height.value)
		}
		settings.putBoolean("${window.prefix}$MAXIMIZED", maximized)
	}

	private val Window.prefix: String
		get() = when (this) {
			Window.ProjectSelect -> "desktop.window.projectSelect"
			Window.ProjectRoot -> "desktop.window.projectRoot"
		}

	companion object {
		private const val WIDTH = ".width"
		private const val HEIGHT = ".height"
		private const val MAXIMIZED = ".maximized"
	}
}

@OptIn(FlowPreview::class)
@Composable
fun rememberPersistedWindowState(
	window: WindowGeometryStore.Window,
	defaultSize: DpSize,
	startMinimized: Boolean = false,
): WindowState {
	val store = remember { getKoin().get<WindowGeometryStore>() }
	val initial = remember { store.load(window, defaultSize) }
	val windowState = rememberWindowState(
		size = initial.size,
		placement = if (initial.maximized) WindowPlacement.Maximized else WindowPlacement.Floating,
		isMinimized = startMinimized,
	)

	LaunchedEffect(windowState) {
		snapshotFlow { windowState.size to windowState.placement }
			.drop(1)
			.filter { (_, placement) -> placement != WindowPlacement.Fullscreen }
			.distinctUntilChanged()
			.debounce(500.milliseconds)
			.collect { (size, placement) ->
				store.save(
					window,
					size = size,
					maximized = placement == WindowPlacement.Maximized,
				)
			}
	}

	return windowState
}
