package com.darkrockstudios.apps.hammer.common.preview.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdSearchableListDialogContent
import com.darkrockstudios.apps.hammer.common.compose.theme.AppTheme
import com.darkrockstudios.apps.hammer.common.preview.globalSettingsPreview

private val languages = listOf(
	"de-DE" to "German (Germany)",
	"en-GB" to "English (United Kingdom)",
	"en-US" to "English (United States)",
	"fr-FR" to "French (France)",
	"pt-BR" to "Portuguese (Brazil)",
	"uk-UA" to "Ukrainian (Ukraine)",
)

@Composable
private fun DialogPreviewSurface(query: String) {
	AppTheme(globalSettingsPreview) {
		Box(
			modifier = Modifier
				.background(MaterialTheme.colorScheme.background)
				.fillMaxSize()
				.padding(24.dp),
			contentAlignment = Alignment.Center,
		) {
			HdSearchableListDialogContent(
				title = "Choose a language",
				query = query,
				onQueryChange = {},
				items = languages,
				itemLabel = { it.second },
				itemTrailing = { it.first.uppercase() },
				itemKey = { it.first },
				onSelect = {},
				onDismissRequest = {},
				closeContentDescription = "Close",
				searchPlaceholder = "Search languages",
				clearLabel = "Clear — not set",
				onClear = {},
				emptyLabel = "No languages match",
			)
		}
	}
}

@Preview(heightDp = 620)
@Composable
fun HdSearchableListDialogPreview() {
	DialogPreviewSurface(query = "")
}

@Preview(heightDp = 420)
@Composable
fun HdSearchableListDialogFilteredPreview() {
	DialogPreviewSurface(query = "english")
}

@Preview(heightDp = 360)
@Composable
fun HdSearchableListDialogEmptyPreview() {
	DialogPreviewSurface(query = "klingon")
}
