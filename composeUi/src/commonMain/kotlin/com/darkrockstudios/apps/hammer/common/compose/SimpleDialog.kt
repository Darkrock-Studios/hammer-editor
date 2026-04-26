package com.darkrockstudios.apps.hammer.common.compose

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.window.DialogProperties
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.close_dialog_button
import com.darkrockstudios.apps.hammer.common.compose.resources.get

/**
 * Card-style modal dialog with title bar, close button, fade+scale animation, and predictive-back
 * gesture support.
 *
 * @param onCloseRequest fires when the user requests dismissal (X button, ESC, scrim tap, or
 *   committed predictive-back gesture). Typical handling: flip your `visible` state to false. The
 *   dialog stays mounted while the exit animation plays.
 * @param visible drives the enter/exit transition. When this flips to false the dialog animates
 *   out before unmounting.
 * @param onDismissed fires after the exit animation completes — use this to dismiss a Decompose
 *   modal slot once the animation has played out, so the slot transition doesn't yank the dialog
 *   mid-animation. No-op by default.
 */
@Composable
fun SimpleDialog(
	onCloseRequest: () -> Unit,
	visible: Boolean,
	title: String,
	modifier: Modifier = Modifier,
	dialogContainerModifier: Modifier = Modifier,
	overridePlatformWidth: Boolean = false,
	contentAlignment: Alignment = Alignment.Center,
	dismissOnTapOutside: Boolean = false,
	onDismissed: () -> Unit = {},
	content: @Composable ColumnScope.() -> Unit
) {
	var renderInternal by remember { mutableStateOf(visible) }
	LaunchedEffect(visible) { if (visible) renderInternal = true }

	if (!renderInternal) return

	AnimatedDialogContainer(
		isOpen = visible,
		onDismissRequest = onCloseRequest,
		onClosed = {
			renderInternal = false
			onDismissed()
		},
		properties = DialogProperties(usePlatformDefaultWidth = !overridePlatformWidth),
	) {
		val cardModifier = modifier.animateContentSize().let { base ->
			if (dismissOnTapOutside) base.clickable(
				interactionSource = remember { MutableInteractionSource() },
				indication = null,
				onClick = {},
			) else base
		}

		val cardContent = @Composable {
			Card(modifier = cardModifier) {
				Column(modifier = Modifier.padding(Ui.Padding.XL)) {
					Row(
						modifier = Modifier.fillMaxWidth(),
						horizontalArrangement = Arrangement.SpaceBetween,
						verticalAlignment = Alignment.CenterVertically
					) {
						Text(
							text = title,
							style = MaterialTheme.typography.titleLarge,
							fontWeight = FontWeight.Bold,
							modifier = Modifier
								.weight(1f)
								.padding(Ui.Padding.XL)
						)

						Icon(
							Icons.Default.Close,
							contentDescription = Res.string.close_dialog_button.get(),
							modifier = Modifier
								.padding(Ui.Padding.L)
								.clickable { requestDismiss() }
						)
					}
					Spacer(modifier = Modifier.size(Ui.Padding.L))
					content()
				}
			}
		}

		if (dismissOnTapOutside) {
			Box(
				modifier = Modifier
					.fillMaxSize()
					.clickable(
						interactionSource = remember { MutableInteractionSource() },
						indication = null,
						onClick = { requestDismiss() },
					),
				contentAlignment = contentAlignment,
			) {
				Box(
					modifier = dialogContainerModifier.predictiveBackTransform(),
					contentAlignment = contentAlignment,
				) {
					cardContent()
				}
			}
		} else {
			Box(
				modifier = dialogContainerModifier.predictiveBackTransform(),
				contentAlignment = contentAlignment,
			) {
				cardContent()
			}
		}
	}
}
