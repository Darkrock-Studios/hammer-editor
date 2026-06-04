package com.darkrockstudios.apps.hammer.common.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.darkrockstudios.apps.hammer.common.compose.theme.hammerMonoFontFamily

/**
 * Folio-masthead form dialog. Sibling to [ConfirmationDialog] — same square-cornered surface,
 * predictive-back model, and visual vocabulary; differs in chrome:
 *
 * ```
 *   § MARKER ─────────────── META
 *   ════ FolioDivider ════════════
 *   Display title
 *   <form fields>
 *   ─── footer rule ──────────────
 *   ⌘↵ confirm · ESC cancel  [Cancel] [Filled action]
 * ```
 *
 * Pair with [FormField] inside the body slot for the standard mono-caps-label / underline-input
 * field shape. Multi-field dialogs stack [FormField]s; the slot already applies vertical
 * spacing between children.
 *
 * @param marker short uppercase label rendered in the masthead (e.g. "§ RENAME"). The "§"
 *   prefix is convention — pass it in your string.
 * @param meta optional right-aligned mono caption (e.g. "PROJECT").
 * @param destructive paints the marker and confirm button in the error color.
 * @param confirmEnabled gates the confirm button; flip to false while validation fails.
 * @param keyboardHint mono cue at the left of the action bar; pass null to omit. Defaults to
 *   the standard "⌘↵ confirm · ESC cancel".
 */
@Composable
fun FormDialog(
	visible: Boolean,
	marker: String,
	title: String,
	confirmLabel: String,
	cancelLabel: String,
	onConfirm: () -> Unit,
	onCancel: () -> Unit,
	onDismiss: () -> Unit,
	modifier: Modifier = Modifier,
	meta: String? = null,
	destructive: Boolean = false,
	confirmEnabled: Boolean = true,
	keyboardHint: String? = "ESC cancel",
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
		val accent = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
		Surface(
			shape = RectangleShape,
			color = MaterialTheme.colorScheme.surface,
			contentColor = MaterialTheme.colorScheme.onSurface,
			shadowElevation = Ui.Elevation.LARGE,
			modifier = modifier
				.padding(horizontal = Ui.Padding.XL)
				.widthIn(max = 540.dp)
				.fillMaxWidth(),
		) {
			Column {
				// Masthead: § MARKER on the left, mono META on the right.
				Row(
					modifier = Modifier
						.fillMaxWidth()
						.padding(start = 26.dp, end = 26.dp, top = 16.dp, bottom = 14.dp),
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.spacedBy(12.dp),
				) {
					Text(
						text = marker,
						fontFamily = hammerMonoFontFamily(),
						fontSize = 10.sp,
						fontWeight = FontWeight.Medium,
						letterSpacing = 1.8.sp,
						color = if (destructive) accent else MaterialTheme.colorScheme.onSurfaceVariant,
					)
					Spacer(modifier = Modifier.weight(1f))
					if (!meta.isNullOrEmpty()) {
						Text(
							text = meta,
							fontFamily = hammerMonoFontFamily(),
							fontSize = 10.sp,
							letterSpacing = 1.8.sp,
							color = MaterialTheme.colorScheme.onSurfaceVariant,
						)
					}
				}
				FolioDivider()

				// Body: title + fields slot.
				Column(
					modifier = Modifier.padding(start = 26.dp, end = 26.dp, top = 22.dp, bottom = 26.dp),
					verticalArrangement = Arrangement.spacedBy(22.dp),
				) {
					Text(
						text = title,
						style = MaterialTheme.typography.headlineSmall.copy(
							fontWeight = FontWeight.Normal,
							letterSpacing = (-0.26).sp,
							lineHeight = 30.sp,
						),
						color = MaterialTheme.colorScheme.onSurface,
					)
					Column(
						verticalArrangement = Arrangement.spacedBy(20.dp),
						content = body,
					)
				}

				// Footer: keyboard hint + action buttons.
				HorizontalDivider(
					color = MaterialTheme.colorScheme.outlineVariant,
					thickness = 1.dp,
				)
				Row(
					modifier = Modifier
						.fillMaxWidth()
						.background(MaterialTheme.colorScheme.surfaceContainerLow)
						.padding(start = 22.dp, end = 14.dp, top = 10.dp, bottom = 10.dp),
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.spacedBy(10.dp),
				) {
					if (keyboardHint != null) {
						Text(
							text = keyboardHint,
							fontFamily = hammerMonoFontFamily(),
							fontSize = 10.sp,
							letterSpacing = 0.4.sp,
							color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
						)
					}
					Spacer(modifier = Modifier.weight(1f))
					TextButton(
						onClick = onCancel,
						shape = RoundedCornerShape(4.dp),
					) {
						Text(cancelLabel)
					}
					val confirmColors = if (destructive) {
						ButtonDefaults.buttonColors(
							containerColor = MaterialTheme.colorScheme.error,
							contentColor = MaterialTheme.colorScheme.onError,
						)
					} else {
						ButtonDefaults.buttonColors()
					}
					Button(
						onClick = onConfirm,
						enabled = confirmEnabled,
						shape = RoundedCornerShape(4.dp),
						colors = confirmColors,
					) {
						Text(confirmLabel)
					}
				}
			}
		}
	}
}

/**
 * Stacked masthead rule used after the §-marker row in [FormDialog] (and matches the
 * "section folio" treatment in the rest of the app's design language). 2dp strong rule,
 * 2dp gap, 1dp regular rule.
 */
@Composable
fun FolioDivider() {
	Box(
		modifier = Modifier
			.fillMaxWidth()
			.height(2.dp)
			.background(MaterialTheme.colorScheme.outline),
	)
	Spacer(modifier = Modifier.height(2.dp))
	HorizontalDivider(
		color = MaterialTheme.colorScheme.outlineVariant,
		thickness = 1.dp,
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
				keyboardOptions = KeyboardOptions(imeAction = imeAction),
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
							style = textStyle.copy(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)),
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
