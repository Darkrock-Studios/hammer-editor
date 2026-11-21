package com.darkrockstudios.apps.hammer.common.compose

import com.darkrockstudios.apps.hammer.common.compose.designsystem.hdPlaceholderColor
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineButton
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdMetaText
import com.darkrockstudios.apps.hammer.common.compose.theme.hammerMonoFontFamily

/**
 * A form in a dialog: a square hairline frame with the title, the fields, and Cancel / confirm
 * at the bottom right. Same frame as [com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineDialogShell];
 * Cancel stands in for its close button.
 *
 * ```
 *   Rename project
 *   In Fantasy                          (meta, optional)
 *
 *   Trilogy                             (subject, optional)
 *
 *   NAME
 *   <field>____________________________
 *
 *                       [Cancel] [Rename]
 * ```
 *
 * Pair with [FormField] inside the body slot; the slot already spaces its children.
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
	Surface(
		shape = RectangleShape,
		color = MaterialTheme.colorScheme.surface,
		contentColor = MaterialTheme.colorScheme.onSurface,
		modifier = modifier
			.padding(horizontal = Ui.Padding.XL)
			.widthIn(max = 540.dp)
			.fillMaxWidth()
			.border(Dp.Hairline, MaterialTheme.colorScheme.outlineVariant, RectangleShape),
	) {
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
			Row(
				modifier = Modifier.fillMaxWidth(),
				horizontalArrangement = Arrangement.spacedBy(Ui.Padding.M, Alignment.End),
			) {
				HdHairlineButton(label = cancelLabel, onClick = onCancel)
				HdHairlineButton(
					label = confirmLabel,
					onClick = onConfirm,
					enabled = confirmEnabled,
					emphasised = !destructive,
					danger = destructive,
				)
			}
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

/**
 * Underline form field with mono-caps label, body-sized text input, and helper/error text.
 *
 * Mirrors the design's `FormField` atom: column with 6dp internal gap, 1dp ruleStrong
 * underline (or error color when [error] is non-null), optional helper text below.
 *
 * Pass [autoFocus] = true on the first field of a dialog so it grabs focus on open.
 *
 * @param error error message; non-null/empty turns the underline red and shows the message
 *   below in error color, suppressing [helper].
 * @param helper hint text below the field; ignored when [error] is non-null.
 * @param onImeAction fires when the user presses the IME action key (Done/Next). Use this
 *   to submit the form on Enter.
 */
@Composable
fun FormField(
	value: String,
	onValueChange: (String) -> Unit,
	label: String,
	modifier: Modifier = Modifier,
	placeholder: String? = null,
	helper: String? = null,
	error: String? = null,
	autoFocus: Boolean = false,
	singleLine: Boolean = true,
	imeAction: ImeAction = ImeAction.Done,
	onImeAction: (() -> Unit)? = null,
	testTag: String? = null,
	autoCorrectEnabled: Boolean? = null,
) {
	val isError = !error.isNullOrEmpty()
	val underlineColor = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline
	val labelColor = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
	val focusRequester = remember { FocusRequester() }
	if (autoFocus) {
		LaunchedEffect(Unit) { focusRequester.requestFocus() }
	}

	Column(
		modifier = modifier,
		verticalArrangement = Arrangement.spacedBy(6.dp),
	) {
		Text(
			text = label,
			fontFamily = hammerMonoFontFamily(),
			fontSize = 10.sp,
			letterSpacing = 1.8.sp,
			color = labelColor,
		)
		Box(
			modifier = Modifier
				.fillMaxWidth()
				.padding(bottom = 4.dp),
		) {
			val textStyle = TextStyle(
				fontSize = 16.sp,
				fontWeight = FontWeight.Normal,
				color = MaterialTheme.colorScheme.onSurface,
				lineHeight = 22.sp,
			)
			BasicTextField(
				value = value,
				onValueChange = onValueChange,
				singleLine = singleLine,
				textStyle = textStyle,
				cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
				keyboardOptions = KeyboardOptions(imeAction = imeAction, autoCorrectEnabled = autoCorrectEnabled),
				keyboardActions = KeyboardActions(
					onDone = { onImeAction?.invoke() },
					onGo = { onImeAction?.invoke() },
					onSend = { onImeAction?.invoke() },
				),
				modifier = Modifier
					.fillMaxWidth()
					.padding(vertical = 6.dp)
					.focusRequester(focusRequester)
					.then(if (testTag != null) Modifier.testTag(testTag) else Modifier),
				decorationBox = { inner ->
					if (value.isEmpty() && !placeholder.isNullOrEmpty()) {
						Text(
							text = placeholder,
							style = textStyle.copy(color = hdPlaceholderColor),
						)
					}
					inner()
				},
			)
		}
		HorizontalDivider(color = underlineColor, thickness = 1.dp)
		val helperText = error?.takeIf { it.isNotEmpty() } ?: helper
		if (!helperText.isNullOrEmpty()) {
			Text(
				text = helperText,
				style = MaterialTheme.typography.bodySmall.copy(lineHeight = 17.sp),
				color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
			)
		}
	}
}
