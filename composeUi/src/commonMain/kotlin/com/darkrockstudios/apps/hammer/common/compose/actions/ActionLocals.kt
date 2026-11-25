package com.darkrockstudios.apps.hammer.common.compose.actions

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import com.darkrockstudios.apps.hammer.common.components.actions.ActionDispatcher
import com.darkrockstudios.apps.hammer.common.components.actions.ActionHandler
import com.darkrockstudios.apps.hammer.common.components.actions.ActionId
import com.darkrockstudios.apps.hammer.common.components.actions.ActionLayer
import com.darkrockstudios.apps.hammer.common.components.actions.ActionRegistrar
import com.darkrockstudios.apps.hammer.common.components.actions.ActionRegistry

val LocalActionRegistrar: ProvidableCompositionLocal<ActionRegistrar?> = staticCompositionLocalOf { null }
val LocalActionDispatcher: ProvidableCompositionLocal<ActionDispatcher?> = staticCompositionLocalOf { null }

@Composable
fun ProvideActions(registry: ActionRegistry, content: @Composable () -> Unit) {
	CompositionLocalProvider(
		LocalActionRegistrar provides registry,
		LocalActionDispatcher provides registry,
		content = content,
	)
}

/**
 * Registers [handlers] while composed. Recompositions do not re-register: the registered
 * handlers call whichever lambdas the latest composition passed.
 */
@Composable
fun RegisterActions(
	layer: ActionLayer,
	handlers: Map<ActionId, ActionHandler>,
	blocksBelow: Boolean = false,
	enabled: Boolean = true,
) {
	val registrar = LocalActionRegistrar.current
	val latest by rememberUpdatedState(handlers)
	DisposableEffect(registrar, layer, blocksBelow, enabled, handlers.keys) {
		val registration = if (registrar != null && enabled) {
			registrar.register(
				layer,
				handlers.keys.associateWith { id ->
					ActionHandler(
						isEnabled = { latest[id]?.isEnabled?.invoke() ?: false },
						perform = { latest[id]?.perform?.invoke() },
					)
				},
				blocksBelow,
			)
		} else {
			null
		}
		onDispose { registration?.unregister() }
	}
}
