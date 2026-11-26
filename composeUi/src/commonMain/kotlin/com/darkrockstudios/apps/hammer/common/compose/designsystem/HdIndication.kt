package com.darkrockstudios.apps.hammer.common.compose.designsystem

import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DelegatingNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch

private val FocusFrameWidth = 1.dp

/**
 * The theme's indication: the Material ripple, plus an ink frame just inside the control while
 * it holds focus in keyboard input mode, so Tab shows where it landed. A mouse press switches to
 * touch mode, so clicking never shows the frame. Every `clickable` that takes the default
 * indication gets it, which is every Hd control.
 */
/**
 * Provides [HdIndication] in the current color scheme. Call it inside every `MaterialTheme`,
 * which otherwise resets `LocalIndication` to the plain ripple.
 */
@Composable
fun ProvideHdIndication(content: @Composable () -> Unit) {
	CompositionLocalProvider(LocalIndication provides HdIndication(MaterialTheme.colorScheme.onSurface), content = content)
}

internal data class HdIndication(val frameColor: Color) : IndicationNodeFactory {
	override fun create(interactionSource: InteractionSource): DelegatableNode =
		HdIndicationNode(interactionSource, frameColor)
}

private class HdIndicationNode(
	private val interactionSource: InteractionSource,
	private val frameColor: Color,
) : DelegatingNode(), DrawModifierNode, CompositionLocalConsumerModifierNode {

	private var focused = false

	init {
		delegate(ripple().create(interactionSource))
	}

	override fun onAttach() {
		// Undispatched, so it is listening before the clickable that created this lazily emits
		// the focus that caused it.
		coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
			val focuses = mutableListOf<FocusInteraction.Focus>()
			interactionSource.interactions.collect { interaction ->
				when (interaction) {
					is FocusInteraction.Focus -> focuses += interaction
					is FocusInteraction.Unfocus -> focuses -= interaction.focus
				}
				val now = focuses.isNotEmpty()
				if (now != focused) {
					focused = now
					invalidateDraw()
				}
			}
		}
	}

	override fun ContentDrawScope.draw() {
		drawContent()
		// Read here so a change of input mode redraws.
		val keyboard = currentValueOf(LocalInputModeManager).inputMode == InputMode.Keyboard
		if (focused && keyboard) {
			// Inside the control's own hairline border, which draws over this one.
			val width = FocusFrameWidth.toPx()
			val inset = 1f + width / 2
			drawRect(
				color = frameColor,
				topLeft = Offset(inset, inset),
				size = Size(size.width - 2 * inset, size.height - 2 * inset),
				style = Stroke(width),
			)
		}
	}
}
