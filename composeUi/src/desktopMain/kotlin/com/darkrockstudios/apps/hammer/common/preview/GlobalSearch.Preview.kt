package com.darkrockstudios.apps.hammer.common.preview

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.darkrockstudios.apps.hammer.common.components.globalsearch.AnnotatedSnippet
import com.darkrockstudios.apps.hammer.common.components.globalsearch.GlobalSearch
import com.darkrockstudios.apps.hammer.common.components.globalsearch.SearchResult
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryDef
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryType
import com.darkrockstudios.apps.hammer.common.globalsearch.GlobalSearchCard

/** Project search for "lamp", with a result of each kind, over a scrim. */
@Composable
internal fun GlobalSearchScrimPreview(state: GlobalSearch.State = previewSearchState) {
	Box(
		modifier = Modifier
			.fillMaxSize()
			.previewScrim(),
		contentAlignment = Alignment.TopCenter,
	) {
		GlobalSearchCard(
			state = state,
			onQueryChanged = {},
			onFilterChanged = {},
			onResultClick = {},
			onClose = {},
		)
	}
}

/** Project search before a usable query is typed. */
@Composable
internal fun GlobalSearchEmptyScrimPreview() {
	GlobalSearchScrimPreview(GlobalSearch.State(query = "l", parsedText = "l"))
}

private fun snippet(text: String, match: String): AnnotatedSnippet {
	val start = text.indexOf(match)
	return AnnotatedSnippet(text = text, matchStart = start, matchEnd = start + match.length)
}

private val previewSearchState: GlobalSearch.State by lazy {
	val project = fakeProjectDef()
	GlobalSearch.State(
		query = "lamp",
		parsedText = "lamp",
		results = listOf(
			SearchResult.Scene(
				sceneItem = SceneItem(project, SceneItem.Type.Scene, id = 7, name = "The Lamp Room", order = 3),
				title = "The Lamp Room",
				snippet = snippet("…she trimmed the wick and the lamp caught, throwing the gallery into long shadow…", "lamp"),
			),
			SearchResult.Note(
				noteId = 12,
				title = "Lighthouse mechanics",
				snippet = snippet("The lamp turns on a mercury bath, so it can spin with almost no friction.", "lamp"),
			),
			SearchResult.EncyclopediaEntry(
				entryDef = EntryDef(project, id = 4, type = EntryType.THING, name = "The great lamp"),
				title = "The great lamp",
				snippet = snippet("A first-order Fresnel lens, brought over from Paris in 1871.", "Fresnel"),
			),
			SearchResult.TimelineEvent(
				eventId = 9,
				title = "The lamp goes dark",
				snippet = snippet("For the first time in forty years the lamp goes dark, and the Mary Ellen runs aground.", "lamp"),
			),
		),
	)
}
