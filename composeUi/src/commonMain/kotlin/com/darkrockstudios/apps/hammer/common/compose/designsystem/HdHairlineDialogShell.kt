package com.darkrockstudios.apps.hammer.common.compose.designsystem

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.hd_dialog_close

/**
 * A titled [HdDialogFrame] with a padded body. Pair with
 * [com.darkrockstudios.apps.hammer.common.compose.AnimatedDialog] and pass its
 * `requestDismiss` as [onClose] so the exit animation plays. A null [onClose] drops the close
 * button, for dialogs whose own Cancel already dismisses them.
 */
@Composable
fun HdHairlineDialogShell(
	title: String,
	onClose: (() -> Unit)?,
	closeContentDescription: String? = null,
	modifier: Modifier = Modifier,
	maxWidth: Dp = Ui.MaxWidth.Dialog,
	content: @Composable ColumnScope.() -> Unit,
) {
	HdDialogFrame(modifier = modifier.widthIn(min = 320.dp), maxWidth = maxWidth) {
		HdDialogTitleRow(
			title = title,
			onClose = onClose,
			closeContentDescription = closeContentDescription ?: Res.string.hd_dialog_close.get(),
		)
		Column(
			modifier = Modifier.padding(start = Ui.Padding.XL, end = Ui.Padding.XL, bottom = Ui.Padding.XL),
			content = content,
		)
	}
}
