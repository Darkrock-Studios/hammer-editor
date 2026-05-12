package com.darkrockstudios.apps.hammer.common.compose.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.common.compose.theme.LocalHammerColors

/**
 * Label-over-value block with an amber left gutter when [conflict] is
 * true. Label picks up a `· CHANGED` suffix in the amber tone.
 */
@Composable
fun HdConflictField(
	label: String,
	modifier: Modifier = Modifier,
	conflict: Boolean = false,
	changedSuffix: String = "CHANGED",
	content: @Composable () -> Unit,
) {
	val amber = LocalHammerColors.current.warning
	Row(modifier = modifier.fillMaxWidth()) {
		if (conflict) {
			Box(
				modifier = Modifier
					.width(2.dp)
					.fillMaxHeight()
					.background(amber),
			)
		}
		Column(
			modifier = Modifier
				.fillMaxWidth()
				.padding(start = if (conflict) 12.dp else 0.dp),
		) {
			HdMonoLabel(
				text = if (conflict) "$label · $changedSuffix" else label,
				color = if (conflict) amber else MaterialTheme.colorScheme.onSurfaceVariant,
			)
			Spacer(modifier = Modifier.height(6.dp))
			content()
		}
	}
}

/** Default vertical gap between stacked [HdConflictField] blocks. */
val HdConflictFieldSpacing: Dp = 18.dp
