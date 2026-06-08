package com.darkrockstudios.apps.hammer.common.compose.designsystem

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.common.compose.Ui

private val DefaultCompactThreshold = 420.dp
private val DefaultActionsStackThreshold = 480.dp

/**
 * The detail-view masthead row used by Notes, Timeline and Encyclopedia:
 * `§ SECTION  |  meta    [actions]`.
 *
 * [leading] renders first and always shows; [meta] renders next but hides
 * below [compactThreshold]; [actions] sits at the trailing edge. When the
 * screen is narrower than [actionsStackThreshold] and [stackActionsWhenNarrow]
 * is set, the actions drop onto their own end-aligned row so wide button pairs
 * (Save / Cancel) aren't squeezed against the label. All slots are direct
 * children of a [Row], so siblings are spaced by `Ui.Padding.L`.
 */
@Composable
fun HdDetailStampRow(
	leading: @Composable RowScope.() -> Unit,
	actions: @Composable RowScope.() -> Unit,
	modifier: Modifier = Modifier,
	meta: (@Composable RowScope.() -> Unit)? = null,
	stackActionsWhenNarrow: Boolean = true,
	compactThreshold: Dp = DefaultCompactThreshold,
	actionsStackThreshold: Dp = DefaultActionsStackThreshold,
	contentPadding: PaddingValues = PaddingValues(
		horizontal = Ui.Padding.XL,
		vertical = Ui.Padding.L,
	),
) {
	BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
		val isCompact = maxWidth < compactThreshold
		val stack = stackActionsWhenNarrow && maxWidth < actionsStackThreshold

		val title: @Composable RowScope.() -> Unit = {
			leading()
			if (!isCompact && meta != null) {
				meta()
			}
		}

		if (stack) {
			Column(
				modifier = Modifier
					.fillMaxWidth()
					.padding(contentPadding),
				verticalArrangement = Arrangement.spacedBy(Ui.Padding.L),
			) {
				Row(
					modifier = Modifier.fillMaxWidth(),
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.spacedBy(Ui.Padding.L),
					content = title,
				)
				Row(
					modifier = Modifier.fillMaxWidth(),
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.spacedBy(Ui.Padding.L, Alignment.End),
					content = actions,
				)
			}
		} else {
			Row(
				modifier = Modifier
					.fillMaxWidth()
					.padding(contentPadding),
				verticalAlignment = Alignment.CenterVertically,
				horizontalArrangement = Arrangement.spacedBy(Ui.Padding.L),
			) {
				title()
				Spacer(modifier = Modifier.weight(1f))
				actions()
			}
		}
	}
}
