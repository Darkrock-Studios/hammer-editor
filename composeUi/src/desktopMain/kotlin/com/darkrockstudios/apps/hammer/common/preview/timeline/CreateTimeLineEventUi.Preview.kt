package com.darkrockstudios.apps.hammer.common.preview.timeline

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.base.timeline.TimelineKind
import com.darkrockstudios.apps.hammer.common.components.timeline.CreateTimeLineEvent
import com.darkrockstudios.apps.hammer.common.compose.rememberRootSnackbarHostState
import com.darkrockstudios.apps.hammer.common.compose.theme.AppTheme
import com.darkrockstudios.apps.hammer.common.data.entitycatalog.CatalogItem
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineEventError
import com.darkrockstudios.apps.hammer.common.preview.KoinApplicationPreview
import com.darkrockstudios.apps.hammer.common.preview.fakeProjectDef
import com.darkrockstudios.apps.hammer.common.preview.globalSettingsPreview
import com.darkrockstudios.apps.hammer.common.timeline.CreateTimeLineEventUi

@Preview
@Composable
fun ScreenCreateTimeLineEventUiPreview() {
	CreatePreview(
		PreviewCreateTimeLineEvent(
			CreateTimeLineEvent.State(
				projectDef = fakeProjectDef(),
				title = "The lamp goes dark",
				lane = "The Sea",
				milestone = true,
				references = previewCatalog.filter { it.id in setOf(21, 23, 12) },
				catalog = previewCatalog,
				lanes = previewLanes,
			)
		)
	)
}

@Preview
@Composable
fun ScreenCreateTimeLineEventUiNarrowPreview() {
	CreatePreview(
		PreviewCreateTimeLineEvent(CreateTimeLineEvent.State(projectDef = fakeProjectDef(), lanes = previewLanes)),
		narrow = true,
	)
}

@Preview
@Composable
fun ScreenCreateTimeLineEventUiScenePreview() {
	CreatePreview(
		PreviewCreateTimeLineEvent(
			CreateTimeLineEvent.State(
				projectDef = fakeProjectDef(),
				kind = TimelineKind.SCENE,
				subject = previewCatalog.first { it.id == 11 },
				lane = "Kee",
				catalog = previewCatalog,
				lanes = previewLanes,
			)
		)
	)
}

@Preview
@Composable
fun ScreenCreateTimeLineEventUiEntryPreview() {
	CreatePreview(
		PreviewCreateTimeLineEvent(
			CreateTimeLineEvent.State(
				projectDef = fakeProjectDef(),
				kind = TimelineKind.ENTRY,
				catalog = previewCatalog,
				lanes = previewLanes,
			)
		)
	)
}

@Composable
private fun CreatePreview(component: CreateTimeLineEvent, narrow: Boolean = false) {
	val scope = rememberCoroutineScope()

	KoinApplicationPreview {
		AppTheme(globalSettingsPreview) {
			Box(
				modifier = Modifier
					.background(MaterialTheme.colorScheme.background)
					.then(if (narrow) Modifier.size(width = 390.dp, height = 780.dp) else Modifier.fillMaxSize()),
			) {
				CreateTimeLineEventUi(
					component = component,
					scope = scope,
					modifier = Modifier,
					rootSnackbar = rememberRootSnackbarHostState(),
				)
			}
		}
	}
}

private class PreviewCreateTimeLineEvent(initial: CreateTimeLineEvent.State) : CreateTimeLineEvent {
	private val _state = MutableValue(initial)
	override val state: Value<CreateTimeLineEvent.State> = _state
	override val contentText: Value<String> = MutableValue(
		"The lamp goes dark for the first time and a ship runs aground."
	)

	override suspend fun create(label: String?, content: String, tags: Set<String>): TimeLineEventError =
		TimeLineEventError.NONE

	override fun setKind(kind: TimelineKind) {
		_state.value = _state.value.copy(kind = kind, subject = null)
	}

	override fun setSubject(item: CatalogItem?) {
		_state.value = _state.value.copy(subject = item)
	}

	override fun setLane(lane: String) {
		_state.value = _state.value.copy(lane = lane)
	}

	override fun setMilestone(milestone: Boolean) {
		_state.value = _state.value.copy(milestone = milestone)
	}

	override fun setTitle(title: String) {
		_state.value = _state.value.copy(title = title)
	}

	override fun addReference(item: CatalogItem) {
		_state.value = _state.value.copy(references = _state.value.references + item)
	}

	override fun removeReference(id: Int) {
		_state.value = _state.value.copy(references = _state.value.references.filterNot { it.id == id })
	}

	override fun suggestLanes(prefix: String): List<String> =
		_state.value.lanes.filter { it.startsWith(prefix, ignoreCase = true) && !it.equals(prefix, ignoreCase = true) }

	override fun closeCreation() {}
	override fun discardAndClose() {}
	override fun confirmDiscard() {}
	override fun cancelDiscard() {}
	override fun onContentChanged(newText: String) {}
	override fun clearContent() {}
	override fun suggestTags(prefix: String, limit: Int): List<String> = emptyList()
}
