package com.darkrockstudios.apps.hammer.common.commandpalette

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.cheat_sheet_editor_hint
import com.darkrockstudios.apps.hammer.cheat_sheet_title
import com.darkrockstudios.apps.hammer.common.components.commandpalette.CheatSheet
import com.darkrockstudios.apps.hammer.common.compose.AnimatedDialog
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineDialogShell
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdMetaText
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdSectionHeader
import com.darkrockstudios.apps.hammer.common.compose.focus.keyboardScroll
import com.darkrockstudios.apps.hammer.common.compose.resources.get

private const val DEAD_ROW_ALPHA = 0.5f
private val CheatSheetMaxHeight = 640.dp
internal const val CHEAT_SHEET_SCROLL_TAG = "cheat-sheet-scroll"

@Composable
fun CheatSheetUi(component: CheatSheet) {
	val state by component.state.subscribeAsState()

	AnimatedDialog(
		visible = state.open,
		onCloseRequest = component::dismiss,
		onDismissed = component::onDismissed,
		dismissOnTapOutside = true,
		// The component holds the barrier Esc closes.
		blocksActions = false,
	) {
		HdHairlineDialogShell(
			title = Res.string.cheat_sheet_title.get(),
			onClose = { requestDismiss() },
		) {
			val scrollState = rememberScrollState()
			val focusRequester = remember { FocusRequester() }
			LaunchedEffect(Unit) { focusRequester.requestFocus() }
			Column(
				modifier = Modifier
					.heightIn(max = CheatSheetMaxHeight)
					.testTag(CHEAT_SHEET_SCROLL_TAG)
					.keyboardScroll(scrollState, focusRequester)
					.verticalScroll(scrollState),
				verticalArrangement = Arrangement.spacedBy(Ui.Padding.XL),
			) {
				if (state.loaded) state.sections.forEachIndexed { index, section ->
					Column(verticalArrangement = Arrangement.spacedBy(Ui.Padding.S)) {
						HdSectionHeader(section = index + 1, title = section.category)
						section.rows.forEach { row -> CheatSheetRow(row) }
					}
				}
			}
		}
	}
}

@Composable
private fun CheatSheetRow(row: CheatSheet.Row) {
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.alpha(if (row.reach == CheatSheet.Reach.Dead) DEAD_ROW_ALPHA else 1f)
			.padding(vertical = Ui.Padding.S),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(Ui.Padding.L),
	) {
		Text(
			text = row.label,
			style = MaterialTheme.typography.bodyMedium,
			color = MaterialTheme.colorScheme.onSurface,
			modifier = Modifier.weight(1f),
		)
		if (row.reach == CheatSheet.Reach.Editor) {
			Text(
				text = Res.string.cheat_sheet_editor_hint.get(),
				style = MaterialTheme.typography.bodySmall,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
				maxLines = 1,
			)
		}
		row.chords.forEach { chord -> HdMetaText(text = chord.label(), maxLines = 1) }
	}
}
