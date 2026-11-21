package com.darkrockstudios.apps.hammer.common.compose.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.hd_dialog_close

/**
 * The square hairline sheet every dialog sits on: surface fill, one outlineVariant hairline, no
 * elevation. Pick [maxWidth] from the `Ui.MaxWidth.Dialog*` tokens. [content] lays itself out;
 * pair with [HdDialogTitleRow] for a titled dialog.
 */
@Composable
fun HdDialogFrame(
	modifier: Modifier = Modifier,
	maxWidth: Dp = Ui.MaxWidth.Dialog,
	content: @Composable ColumnScope.() -> Unit,
) {
	Surface(
		modifier = modifier
			.padding(Ui.Padding.L)
			.widthIn(max = maxWidth)
			.fillMaxWidth(),
		shape = RectangleShape,
		color = MaterialTheme.colorScheme.surface,
		contentColor = MaterialTheme.colorScheme.onSurface,
		border = BorderStroke(Dp.Hairline, MaterialTheme.colorScheme.outlineVariant),
	) {
		Column(content = content)
	}
}

/**
 * A dialog's title row: the title (and an optional [meta] line under it), any [trailing]
 * controls, then the close button. A null [onClose] drops the close, for dialogs whose own
 * Cancel or Done already dismisses them.
 */
@Composable
fun HdDialogTitleRow(
	title: String,
	onClose: (() -> Unit)?,
	modifier: Modifier = Modifier,
	meta: String? = null,
	closeContentDescription: String = Res.string.hd_dialog_close.get(),
	trailing: @Composable RowScope.() -> Unit = {},
) {
	Row(
		modifier = modifier
			.fillMaxWidth()
			.padding(
				start = Ui.Padding.XL,
				end = if (onClose != null) Ui.Padding.M else Ui.Padding.XL,
				top = Ui.Padding.L,
				bottom = if (onClose != null) Ui.Padding.S else Ui.Padding.L,
			),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(Ui.Padding.M),
	) {
		Column(modifier = Modifier.weight(1f)) {
			Text(
				text = title,
				style = MaterialTheme.typography.titleLarge,
				color = MaterialTheme.colorScheme.onSurface,
				maxLines = 2,
				overflow = TextOverflow.Ellipsis,
			)
			if (meta != null) HdMetaText(text = meta)
		}
		trailing()
		if (onClose != null) HdCloseButton(onClick = onClose, contentDescription = closeContentDescription)
	}
}

/** The quiet close X used in dialog and panel headers. */
@Composable
fun HdCloseButton(
	onClick: () -> Unit,
	modifier: Modifier = Modifier,
	contentDescription: String = Res.string.hd_dialog_close.get(),
) {
	IconButton(onClick = onClick, modifier = modifier) {
		Icon(
			imageVector = Icons.Filled.Close,
			contentDescription = contentDescription,
			tint = MaterialTheme.colorScheme.onSurfaceVariant,
		)
	}
}

/**
 * A dialog's Cancel and confirm buttons, set at the bottom right. [destructive] paints the
 * confirm as a danger action instead of the emphasised one.
 */
@Composable
fun HdDialogActions(
	cancelLabel: String,
	onCancel: () -> Unit,
	confirmLabel: String,
	onConfirm: () -> Unit,
	modifier: Modifier = Modifier,
	confirmEnabled: Boolean = true,
	cancelEnabled: Boolean = true,
	destructive: Boolean = false,
	confirmModifier: Modifier = Modifier,
) {
	Row(
		modifier = modifier.fillMaxWidth(),
		horizontalArrangement = Arrangement.spacedBy(Ui.Padding.M, Alignment.End),
	) {
		HdHairlineButton(label = cancelLabel, onClick = onCancel, enabled = cancelEnabled)
		HdHairlineButton(
			label = confirmLabel,
			onClick = onConfirm,
			enabled = confirmEnabled,
			emphasised = !destructive,
			danger = destructive,
			modifier = confirmModifier,
		)
	}
}
