package com.darkrockstudios.apps.hammer.common.compose

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdDialogActions
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdDialogFrame
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdMetaText
import com.darkrockstudios.apps.hammer.common.compose.theme.hammerMonoFontFamily
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineField

/**
 * A form in a dialog: a square hairline frame with the title, the fields, and Cancel / confirm
 * at the bottom right, on an [HdDialogFrame]. Cancel stands in for a close button.
 *
 * ```
 *   Rename project
 *   In Fantasy                          (meta, optional)
 *
 *   Trilogy                             (subject, optional)
 *
 *   Name                                (an HdHairlineField)
 *   ___________________________________
 *
 *                       [Cancel] [Rename]
 * ```
 *
 * Put [HdHairlineField]s in the body slot; the slot already spaces its children.
 *
 * @param meta optional line under the title, for context the title doesn't carry (a group name).
 * @param subject optional thing the dialog acts on, set large above the body (see [FormDialogSubject]).
 * @param destructive paints the confirm button in the error color.
 * @param confirmEnabled gates the confirm button; flip to false while validation fails.
 */
@Composable
fun FormDialog(
	visible: Boolean,
	title: String,
	confirmLabel: String,
	cancelLabel: String,
	onConfirm: () -> Unit,
	onCancel: () -> Unit,
	onDismiss: () -> Unit,
	modifier: Modifier = Modifier,
	meta: String? = null,
	subject: String? = null,
	destructive: Boolean = false,
	confirmEnabled: Boolean = true,
	implicitDismiss: Boolean = true,
	onDismissed: () -> Unit = {},
	body: @Composable ColumnScope.() -> Unit,
) {
	AnimatedDialog(
		visible = visible,
		onCloseRequest = if (implicitDismiss) onDismiss else ({}),
		onDismissed = onDismissed,
		modifier = Modifier.fillMaxSize(),
		contentAlignment = Alignment.Center,
	) {
		FormDialogScaffold(
			title = title,
			confirmLabel = confirmLabel,
			cancelLabel = cancelLabel,
			onConfirm = onConfirm,
			onCancel = onCancel,
			modifier = modifier,
			meta = meta,
			subject = subject,
			destructive = destructive,
			confirmEnabled = confirmEnabled,
			body = body,
		)
	}
}

/**
 * The static frame of [FormDialog] without the [AnimatedDialog] wrapper. [FormDialog] animates
 * this in/out; render it directly (e.g. in a `@Preview`) to capture a settled frame, since the
 * Desktop preview renderer can't advance the enter animation.
 */
@Composable
fun FormDialogScaffold(
	title: String,
	confirmLabel: String,
	cancelLabel: String,
	onConfirm: () -> Unit,
	onCancel: () -> Unit,
	modifier: Modifier = Modifier,
	meta: String? = null,
	subject: String? = null,
	destructive: Boolean = false,
	confirmEnabled: Boolean = true,
	body: @Composable ColumnScope.() -> Unit,
) {
	HdDialogFrame(modifier = modifier) {
		Column(modifier = Modifier.padding(24.dp)) {
			Text(
				text = title,
				style = MaterialTheme.typography.titleLarge,
				color = MaterialTheme.colorScheme.onSurface,
			)
			if (!meta.isNullOrEmpty()) {
				Spacer(Modifier.height(4.dp))
				HdMetaText(text = meta)
			}
			Spacer(Modifier.height(24.dp))
			Column(
				modifier = Modifier.weight(1f, fill = false),
				verticalArrangement = Arrangement.spacedBy(20.dp),
			) {
				if (subject != null) FormDialogSubject(subject)
				body()
			}
			Spacer(Modifier.height(28.dp))
			HdDialogActions(
				cancelLabel = cancelLabel,
				onCancel = onCancel,
				confirmLabel = confirmLabel,
				onConfirm = onConfirm,
				confirmEnabled = confirmEnabled,
				destructive = destructive,
			)
		}
	}
}

/** The name of what a dialog acts on (a project, a group), set large in mono so it stands apart from the prose. */
@Composable
fun FormDialogSubject(text: String, modifier: Modifier = Modifier) {
	Text(
		text = text,
		style = MaterialTheme.typography.headlineSmall.copy(
			fontFamily = hammerMonoFontFamily(),
			letterSpacing = (-0.5).sp,
		),
		color = MaterialTheme.colorScheme.onSurface,
		modifier = modifier,
	)
}
