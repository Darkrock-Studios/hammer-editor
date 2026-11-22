package com.darkrockstudios.apps.hammer.common.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.darkrockstudios.apps.hammer.common.compose.theme.hammerMonoFontFamily
import com.darkrockstudios.apps.hammer.confirm_kind_alert
import com.darkrockstudios.apps.hammer.confirm_kind_confirm
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import androidx.compose.foundation.BorderStroke
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdDialogActionButtons
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineButton

/**
 * Card-style confirmation dialog with a left-side index gutter and a vertical mono caption
 * (DISCARD / DELETE / CONFIRM) that doubles as a redundant signal alongside accent color.
 * Built on the predictive-back gesture container, so callers get the same dismissal model
 * as [AnimatedDialog].
 *
 * Pass [cancelLabel] = null for a single-button info-alert variant.
 *
 * For a 3-button variant (e.g. save / discard / cancel) drop down to [IndexStripDialog]
 * directly and supply your own action row.
 *
 * @param visible drives enter/exit. Flip to false to begin the exit animation; the dialog
 *   stays mounted while the exit plays.
 * @param onDismiss handles ESC and committed predictive-back when [implicitDismiss] is true.
 *   Also serves as the default handler for the cancel button when [onCancel] is null.
 * @param onCancel explicit cancel-button handler. When null, the cancel button calls
 *   [onDismiss] instead.
 * @param implicitDismiss if false, ESC and predictive-back do nothing — the user must use
 *   an explicit button.
 * @param destructive paints the gutter rule and the confirm button in the error color.
 * @param kind short mono caption shown vertically in the gutter, from a `confirm_kind_*` string;
 *   uppercased there.
 */
@Composable
fun ConfirmationDialog(
	visible: Boolean,
	title: String,
	message: String,
	confirmLabel: String,
	onConfirm: () -> Unit,
	onDismiss: () -> Unit,
	cancelLabel: String? = null,
	onCancel: (() -> Unit)? = null,
	modifier: Modifier = Modifier,
	destructive: Boolean = false,
	kind: String = defaultConfirmKind(destructive),
	implicitDismiss: Boolean = true,
	onDismissed: () -> Unit = {},
) {
	IndexStripDialog(
		visible = visible,
		title = title,
		message = message,
		onDismiss = onDismiss,
		modifier = modifier,
		destructive = destructive,
		kind = kind,
		implicitDismiss = implicitDismiss,
		onDismissed = onDismissed,
	) {
		ConfirmationActions(
			confirmLabel = confirmLabel,
			onConfirm = onConfirm,
			cancelLabel = cancelLabel,
			onCancel = onCancel ?: onDismiss,
			destructive = destructive,
		)
	}
}

/** The card [ConfirmationDialog] shows, without the dialog window. */
@Composable
internal fun ConfirmationCard(
	title: String,
	message: String,
	confirmLabel: String,
	onConfirm: () -> Unit,
	cancelLabel: String?,
	onCancel: () -> Unit,
	modifier: Modifier = Modifier,
	destructive: Boolean = false,
	kind: String = defaultConfirmKind(destructive),
) {
	IndexStripCard(title = title, message = message, modifier = modifier, destructive = destructive, kind = kind) {
		ConfirmationActions(
			confirmLabel = confirmLabel,
			onConfirm = onConfirm,
			cancelLabel = cancelLabel,
			onCancel = onCancel,
			destructive = destructive,
		)
	}
}

@Composable
internal fun RowScope.ConfirmationActions(
	confirmLabel: String,
	onConfirm: () -> Unit,
	cancelLabel: String?,
	onCancel: () -> Unit,
	destructive: Boolean,
) {
	HdDialogActionButtons(
		cancelLabel = cancelLabel,
		onCancel = onCancel,
		confirmLabel = confirmLabel,
		onConfirm = onConfirm,
		destructive = destructive,
	)
}

/**
 * Lower-level shell for the index-strip alert layout. Use this when you need a custom action
 * row (e.g. the 3-button save/discard/cancel pattern). For the standard 2-button confirm
 * shape, prefer [ConfirmationDialog].
 *
 * The [actions] slot is a [Row] with [Arrangement.spacedBy] applied; drop in [HdHairlineButton]
 * children and they line up against the right edge.
 */
@Composable
fun IndexStripDialog(
	visible: Boolean,
	title: String,
	message: String,
	onDismiss: () -> Unit,
	modifier: Modifier = Modifier,
	destructive: Boolean = false,
	kind: String = defaultConfirmKind(destructive),
	implicitDismiss: Boolean = true,
	onDismissed: () -> Unit = {},
	actions: @Composable RowScope.() -> Unit,
) {
	AnimatedDialog(
		visible = visible,
		onCloseRequest = if (implicitDismiss) onDismiss else ({}),
		onDismissed = onDismissed,
		modifier = Modifier.fillMaxSize(),
		contentAlignment = Alignment.Center,
	) {
		IndexStripCard(
			title = title,
			message = message,
			modifier = modifier,
			destructive = destructive,
			kind = kind,
			actions = actions,
		)
	}
}

/** The card [IndexStripDialog] shows, without the dialog window. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun IndexStripCard(
	title: String,
	message: String,
	modifier: Modifier = Modifier,
	destructive: Boolean = false,
	kind: String = defaultConfirmKind(destructive),
	actions: @Composable RowScope.() -> Unit,
) {
	val accent = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
	val resolvedIndex = folioCodeFor(title)
	Surface(
		shape = RectangleShape,
		color = MaterialTheme.colorScheme.surface,
		contentColor = MaterialTheme.colorScheme.onSurface,
		border = BorderStroke(Dp.Hairline, MaterialTheme.colorScheme.outlineVariant),
		modifier = modifier
			.padding(horizontal = Ui.Padding.XL)
			.widthIn(max = 560.dp)
			.fillMaxWidth(),
	) {
		BoxWithConstraints {
			val gutterWidth = if (maxWidth < 380.dp) 60.dp else GutterWidth
			Row(
				modifier = Modifier
					.fillMaxWidth()
					.height(IntrinsicSize.Min),
			) {
				IndexGutter(
					index = resolvedIndex,
					kind = kind,
					accent = accent,
					width = gutterWidth
				)
				VerticalDivider(
					color = MaterialTheme.colorScheme.outlineVariant,
					thickness = 1.dp,
				)
				Column(modifier = Modifier.weight(1f)) {
					Column(
						modifier = Modifier.padding(
							start = 26.dp,
							end = 26.dp,
							top = 22.dp,
							bottom = 20.dp,
						),
						verticalArrangement = Arrangement.spacedBy(10.dp),
					) {
						Text(
							text = title,
							style = MaterialTheme.typography.titleLarge.copy(
								fontWeight = FontWeight.Normal,
								letterSpacing = (-0.22).sp,
								lineHeight = 25.sp,
							),
							color = MaterialTheme.colorScheme.onSurface,
						)
						if (message.isNotEmpty()) {
							Text(
								text = message,
								style = MaterialTheme.typography.bodyMedium.copy(
									lineHeight = 22.sp,
								),
								color = MaterialTheme.colorScheme.onSurfaceVariant,
							)
						}
					}
					HorizontalDivider(
						color = MaterialTheme.colorScheme.outlineVariant,
						thickness = 1.dp,
					)
					Row(
						modifier = Modifier
							.fillMaxWidth()
							.padding(start = 22.dp, end = 14.dp, top = 10.dp, bottom = 10.dp),
						verticalAlignment = Alignment.CenterVertically,
						horizontalArrangement = Arrangement.spacedBy(10.dp),
					) {
						FlowRow(
							modifier = Modifier.weight(1f),
							horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
							verticalArrangement = Arrangement.spacedBy(8.dp),
							itemVerticalAlignment = Alignment.CenterVertically,
						) {
							actions()
						}
					}
				}
			}
		}
	}
}

private val GutterWidth = 88.dp

@Composable
private fun IndexGutter(index: String, kind: String, accent: Color, width: Dp = GutterWidth) {
	val horizontalPadding = if (width < GutterWidth) 10.dp else 12.dp
	Column(
		modifier = Modifier
			.width(width)
			.fillMaxHeight()
			.background(MaterialTheme.colorScheme.surfaceContainerLow)
			.padding(horizontal = horizontalPadding, vertical = 20.dp),
		verticalArrangement = Arrangement.spacedBy(14.dp),
		horizontalAlignment = Alignment.Start,
	) {
		Box(
			modifier = Modifier
				.size(width = 18.dp, height = 2.dp)
				.background(accent),
		)
		Text(
			text = index,
			fontFamily = hammerMonoFontFamily(),
			fontSize = 10.sp,
			letterSpacing = 0.4.sp,
			color = MaterialTheme.colorScheme.onSurfaceVariant,
		)
		Spacer(modifier = Modifier.weight(1f))
		VerticalCaption(text = kind, color = accent)
	}
}

@Composable
private fun VerticalCaption(text: String, color: Color) {
	// Measure the unrotated text to derive the rotated layout box. This keeps the gutter size in
	// step with the user's font scale — at larger scales the Box grows along with the glyphs.
	// The inner Text uses `requiredWidth` to bypass the Box's narrower max-width constraint so
	// the unrotated layout fits one line; `rotate(-90f)` then flips it visually inside the Box.
	val style = TextStyle(
		fontFamily = hammerMonoFontFamily(),
		fontSize = 10.sp,
		fontWeight = FontWeight.Medium,
		letterSpacing = 1.8.sp,
		color = color,
	)
	val measurer = rememberTextMeasurer()
	val density = LocalDensity.current
	val caption = text.uppercase()
	val (boxWidth, boxHeight) = remember(caption, style, density) {
		val measured = measurer.measure(caption, style = style, maxLines = 1, softWrap = false)
		with(density) {
			measured.size.height.toDp() to measured.size.width.toDp()
		}
	}
	Box(
		modifier = Modifier.size(width = boxWidth, height = boxHeight),
		contentAlignment = Alignment.Center,
	) {
		Text(
			text = caption,
			maxLines = 1,
			softWrap = false,
			style = style,
			modifier = Modifier
				.requiredWidth(boxHeight)
				.rotate(-90f),
		)
	}
}

// Deterministic folio-style code derived from the dialog title. Same title always renders the
// same code, so a given dialog has a stable "position" in the imaginary manuscript.
private fun folioCodeFor(title: String): String {
	val h = title.hashCode() and Int.MAX_VALUE
	val number = (h % 100).toString().padStart(2, '0')
	val letter = 'A' + (h / 100) % 5
	return "$number.$letter"
}

@Composable
internal fun defaultConfirmKind(destructive: Boolean): String =
	if (destructive) Res.string.confirm_kind_alert.get() else Res.string.confirm_kind_confirm.get()
