package com.darkrockstudios.apps.hammer.common.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.common.encyclopedia.AliasAddCard
import com.darkrockstudios.apps.hammer.common.storyeditor.sceneeditor.AddTagCard

/** The encyclopedia entry's add tags and add alias dialogs, stacked over a scrim. */
@Composable
internal fun EntryAddDialogsScrimPreview() {
	Column(
		modifier = Modifier
			.fillMaxSize()
			.background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f)),
		verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
		horizontalAlignment = Alignment.CenterHorizontally,
	) {
		AddTagCard(
			draft = "coastal go",
			onDraftChange = {},
			suggestions = listOf("gothic"),
			onSelectSuggestion = {},
			onDone = {},
		)
		AliasAddCard(
			alias = "",
			onAliasChange = {},
			onDone = {},
		)
	}
}
