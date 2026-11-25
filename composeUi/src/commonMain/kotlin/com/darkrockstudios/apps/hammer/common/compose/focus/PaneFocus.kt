package com.darkrockstudios.apps.hammer.common.compose.focus

import androidx.compose.foundation.focusGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.onFocusEvent
import com.darkrockstudios.apps.hammer.common.components.actions.ActionHandler
import com.darkrockstudios.apps.hammer.common.components.actions.ActionLayer
import com.darkrockstudios.apps.hammer.common.components.actions.HammerActions
import com.darkrockstudios.apps.hammer.common.compose.actions.RegisterActions

/** Ordinal is F6 order. */
enum class PaneId { List, Detail }

/**
 * The content panes F6 moves between. A pane composed twice (the outgoing and incoming screen of
 * a navigation) answers with its newest instance.
 */
@Stable
class PaneFocusState {

	internal class Pane(val id: PaneId) {
		val container = FocusRequester()
		val content = FocusRequester()
		var hasFocus by mutableStateOf(false)
		var containerFocused by mutableStateOf(false)

		/** The container is a stop only while asked for, so Tab never lands on it. */
		var containerFocusable = false

		fun onContainerFocus(state: FocusState) {
			hasFocus = state.hasFocus
			containerFocused = state.isFocused
			if (!state.isFocused) containerFocusable = false
		}
	}

	private val panes = mutableStateListOf<Pane>()
	private val entries = mutableStateListOf<Pair<PaneId, FocusRequester>>()
	private val reveals = mutableStateMapOf<PaneId, Int>()

	private fun pane(id: PaneId): Pane? = panes.lastOrNull { it.id == id }

	val registeredPanes: List<PaneId>
		get() = PaneId.entries.filter { id -> panes.any { it.id == id } }

	val focusedPane: PaneId?
		get() = panes.lastOrNull { it.hasFocus }?.id

	/** The pane container itself, not a child. */
	fun isContainerFocused(id: PaneId): Boolean = pane(id)?.containerFocused == true

	/**
	 * Focuses the child [id] last had focused, else its [paneEntry], else its first focusable
	 * child, else the container itself. [reveal] bumps [revealRequests].
	 */
	fun focus(id: PaneId, reveal: Boolean = false): Boolean {
		val target = pane(id) ?: return false
		if (reveal) reveals[id] = revealRequests(id) + 1
		if (target.content.restoreFocusedChild()) return true
		val entry = entries.lastOrNull { it.first == id }?.second
		if (entry != null && entry.requestFocus()) return true
		return target.content.requestFocus() || focusContainer(target)
	}

	/** Focuses the container of [id], leaving whichever child had focus. */
	fun focusContainer(id: PaneId): Boolean = pane(id)?.let(::focusContainer) ?: false

	private fun focusContainer(pane: Pane): Boolean {
		pane.containerFocusable = true
		val granted = pane.container.requestFocus()
		if (!granted) pane.containerFocusable = false
		return granted
	}

	fun cycle(forward: Boolean): Boolean {
		val order = registeredPanes
		if (order.isEmpty()) return false
		val current = order.indexOf(focusedPane)
		val next = when {
			current < 0 -> if (forward) 0 else order.lastIndex
			forward -> (current + 1) % order.size
			else -> (current - 1 + order.size) % order.size
		}
		return focus(order[next])
	}

	fun focusFirstIfNothingFocused(): Boolean {
		if (focusedPane != null) return false
		val first = registeredPanes.firstOrNull() ?: return false
		return focus(first)
	}

	/** Bumped by `focus(id, reveal = true)`, so the pane can bring its current item into view. */
	fun revealRequests(id: PaneId): Int = reveals[id] ?: 0

	internal fun attach(pane: Pane) {
		panes += pane
	}

	internal fun detach(pane: Pane) {
		panes.remove(pane)
	}

	internal fun addEntry(id: PaneId, requester: FocusRequester) {
		entries += id to requester
	}

	internal fun removeEntry(id: PaneId, requester: FocusRequester) {
		entries.remove(id to requester)
	}
}

val LocalPaneFocus: ProvidableCompositionLocal<PaneFocusState?> = staticCompositionLocalOf { null }

/** Provides [LocalPaneFocus] and answers `pane.next` / `pane.prev` (F6 / Shift+F6). */
@Composable
fun ProvidePaneFocus(content: @Composable () -> Unit) {
	val state = remember { PaneFocusState() }
	val handlers = remember(state) {
		val hasPanes = { state.registeredPanes.isNotEmpty() }
		mapOf(
			HammerActions.PaneNext.id to ActionHandler(isEnabled = hasPanes) { state.cycle(forward = true) },
			HammerActions.PanePrev.id to ActionHandler(isEnabled = hasPanes) { state.cycle(forward = false) },
		)
	}
	RegisterActions(ActionLayer.Root, handlers)
	CompositionLocalProvider(LocalPaneFocus provides state, content = content)
}

/**
 * Makes this a pane F6 can reach. While not [enabled] it keeps its focus nodes but is skipped.
 *
 * The child last focused is saved on every focus change inside the pane, because F6 moves focus
 * with requestFocus, which never runs a focus group's exit, so `focusRestorer` would not save it.
 * Restoring is one level deep: it lands on the pane's direct focusable child that held focus.
 */
@Composable
fun Modifier.focusPane(id: PaneId, enabled: Boolean = true): Modifier {
	val paneFocus = LocalPaneFocus.current ?: return this
	val pane = remember(paneFocus, id) { PaneFocusState.Pane(id) }
	DisposableEffect(paneFocus, pane, enabled) {
		if (enabled) paneFocus.attach(pane)
		onDispose { paneFocus.detach(pane) }
	}
	return this
		.focusRequester(pane.container)
		.onFocusChanged(pane::onContainerFocus)
		.focusProperties { canFocus = pane.containerFocusable }
		.focusTarget()
		.focusRequester(pane.content)
		.focusGroup()
		.onFocusEvent { if (it.hasFocus) pane.content.saveFocusedChild() }
}

/** The child F6 lands on in pane [id] when the pane has no focused child to go back to. */
@Composable
fun Modifier.paneEntry(id: PaneId): Modifier {
	val paneFocus = LocalPaneFocus.current ?: return this
	val requester = remember { FocusRequester() }
	DisposableEffect(paneFocus, id, requester) {
		paneFocus.addEntry(id, requester)
		onDispose { paneFocus.removeEntry(id, requester) }
	}
	return focusRequester(requester)
}
