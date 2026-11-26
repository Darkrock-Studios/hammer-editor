package com.darkrockstudios.apps.hammer.common.compose.actions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalInputModeManager
import com.darkrockstudios.apps.hammer.common.components.actions.ActionId
import com.darkrockstudios.apps.hammer.common.components.actions.Keymap
import com.darkrockstudios.apps.hammer.common.components.actions.currentChordPlatform
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdMetaText

/** Chords show on desktop, and on touch platforms once a hardware keyboard is in use. */
@Composable
fun rememberShowChords(): Boolean =
	currentChordPlatform.showsChordsByDefault || LocalInputModeManager.current.inputMode == InputMode.Keyboard

/** The primary chord bound to [id], or nothing when chords are hidden or [id] has none. */
@Composable
fun ChordLabel(
	id: ActionId,
	modifier: Modifier = Modifier,
	color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
	if (!rememberShowChords()) return
	val keymap = LocalActionDispatcher.current?.keymap ?: Keymap.Default
	val chord = keymap.primaryChord(id) ?: return
	HdMetaText(text = chord.label(), modifier = modifier, color = color, maxLines = 1)
}

/** A tooltip's text: [label], then the chord of [id] where chords show. */
@Composable
fun ChordTooltipText(label: String, id: ActionId) {
	Row(
		horizontalArrangement = Arrangement.spacedBy(Ui.Padding.M),
		verticalAlignment = Alignment.CenterVertically,
	) {
		Text(label)
		// Tooltips draw on an inverse surface, so the chord takes the tooltip's own ink.
		ChordLabel(id, color = LocalContentColor.current)
	}
}
