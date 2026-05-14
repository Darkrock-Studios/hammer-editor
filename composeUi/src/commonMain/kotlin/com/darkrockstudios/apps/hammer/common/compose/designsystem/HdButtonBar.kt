package com.darkrockstudios.apps.hammer.common.compose.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.darkrockstudios.apps.hammer.common.compose.Ui

/**
 * Bottom action bar: cancel/dismiss on the left, primary action on the
 * right, separated by a hairline divider above. The standard footer of
 * a Hd form screen.
 */
@Composable
fun HdButtonBar(
	cancelLabel: String,
	primaryLabel: String,
	onCancel: () -> Unit,
	onPrimary: () -> Unit,
	modifier: Modifier = Modifier,
	primaryEnabled: Boolean = true,
	primaryDanger: Boolean = false,
	showDivider: Boolean = true,
) {
	if (showDivider) {
		HorizontalDivider(
			thickness = Dp.Hairline,
			color = MaterialTheme.colorScheme.outlineVariant,
		)
	}
	Row(
		modifier = modifier
			.fillMaxWidth()
			.padding(horizontal = Ui.Padding.XL, vertical = Ui.Padding.L),
		horizontalArrangement = Arrangement.SpaceBetween,
		verticalAlignment = Alignment.CenterVertically,
	) {
		HdHairlineButton(
			label = cancelLabel,
			onClick = onCancel,
		)
		HdHairlineButton(
			label = primaryLabel,
			emphasised = !primaryDanger,
			danger = primaryDanger,
			enabled = primaryEnabled,
			onClick = onPrimary,
		)
	}
}
