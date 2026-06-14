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
import com.darkrockstudios.apps.hammer.common.components.timeline.CreateTimeLineEvent
import com.darkrockstudios.apps.hammer.common.compose.rememberRootSnackbarHostState
import com.darkrockstudios.apps.hammer.common.compose.theme.AppTheme
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineEventError
import com.darkrockstudios.apps.hammer.common.preview.KoinApplicationPreview
import com.darkrockstudios.apps.hammer.common.preview.fakeProjectDef
import com.darkrockstudios.apps.hammer.common.preview.globalSettingsPreview
import com.darkrockstudios.apps.hammer.common.timeline.CreateTimeLineEventUi

@Preview
@Composable
fun CreateTimeLineEventUiPreview() {
	val scope = rememberCoroutineScope()

	KoinApplicationPreview {
		AppTheme(globalSettingsPreview) {
			Box(
				modifier = Modifier
					.background(MaterialTheme.colorScheme.background)
					.fillMaxSize(),
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

@Preview
@Composable
fun CreateTimeLineEventUiNarrowPreview() {
	val scope = rememberCoroutineScope()

	KoinApplicationPreview {
		AppTheme(globalSettingsPreview) {
			Box(
				modifier = Modifier
					.background(MaterialTheme.colorScheme.background)
					.size(width = 390.dp, height = 780.dp),
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

private val component = object : CreateTimeLineEvent {
	override val state: Value<CreateTimeLineEvent.State> = MutableValue(
		CreateTimeLineEvent.State(projectDef = fakeProjectDef())
	)
	override val contentText: Value<String> = MutableValue(
		"The lamp goes dark for the first time and a ship runs aground."
	)

	override suspend fun createEvent(
		dateText: String?,
		contentText: String,
		tags: Set<String>,
	): TimeLineEventError = TimeLineEventError.NONE

	override fun closeCreation() {}
	override fun confirmDiscard() {}
	override fun cancelDiscard() {}
	override fun onContentChanged(newText: String) {}
	override fun clearContent() {}
	override fun suggestTags(prefix: String, limit: Int): List<String> = emptyList()
}
