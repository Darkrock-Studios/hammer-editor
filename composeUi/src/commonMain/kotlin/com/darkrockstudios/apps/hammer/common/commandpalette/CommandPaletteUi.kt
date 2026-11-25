package com.darkrockstudios.apps.hammer.common.commandpalette

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.common.components.actions.rankActions
import com.darkrockstudios.apps.hammer.common.components.commandpalette.CommandPalette
import com.darkrockstudios.apps.hammer.common.compose.actions.rememberShowChords
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdMetaText
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdSearchableListDialog
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.hd_dialog_close
import com.darkrockstudios.apps.hammer.palette_empty
import com.darkrockstudios.apps.hammer.palette_search_placeholder
import com.darkrockstudios.apps.hammer.palette_title

@Composable
fun CommandPaletteUi(component: CommandPalette) {
	val state by component.state.subscribeAsState()
	val showChords = rememberShowChords()
	val emptyLabel = Res.string.palette_empty.get()

	HdSearchableListDialog(
		visible = state.open,
		title = Res.string.palette_title.get(),
		items = state.entries,
		itemLabel = { it.label },
		itemKey = { it.id.value },
		itemTrailingContent = { entry ->
			val chord = entry.chord
			if (showChords && chord != null) HdMetaText(text = chord.label(), maxLines = 1)
		},
		filter = ::rankActions,
		keyboardSelection = true,
		autoFocusSearch = true,
		// The component holds the barrier, and must lift it before an action runs.
		blocksActions = false,
		onSelect = { component.run(it.id) },
		onDismiss = component::dismiss,
		onDismissed = component::onDismissed,
		closeContentDescription = Res.string.hd_dialog_close.get(),
		searchPlaceholder = Res.string.palette_search_placeholder.get(),
		// Blank while the labels resolve, so an empty list does not read as no matches.
		emptyLabel = if (state.loaded) emptyLabel else "",
	)
}
