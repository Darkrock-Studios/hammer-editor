package com.darkrockstudios.apps.hammer.common.projecteditor.drafts

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.Card
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.arkivanov.decompose.extensions.compose.jetbrains.subscribeAsState
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.data.drafts.DraftDef

@Composable
fun DraftsListUi(
    component: DraftsList,
    modifier: Modifier = Modifier,
) {
    val state by component.state.subscribeAsState()

    LaunchedEffect(state.sceneItem) {
        component.loadDrafts()
    }

    Column {
        Text("${state.sceneItem.name} Drafts:")

        LazyColumn(
            modifier = modifier.fillMaxWidth(),
            contentPadding = PaddingValues(Ui.PADDING)
        ) {
            state.apply {
                if (drafts.isEmpty()) {
                    item {
                        Text("No Drafts Found")
                    }
                }

                items(drafts.size) { index ->
                    DraftItem(draftDef = drafts[index], onDraftSelected = component::selectDraft)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DraftItem(
    draftDef: DraftDef,
    modifier: Modifier = Modifier,
    onDraftSelected: (DraftDef) -> Unit
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(Ui.PADDING)
            .combinedClickable(
                onClick = { onDraftSelected(draftDef) },
            ),
        elevation = Ui.ELEVATION
    ) {
        Text("${draftDef.draftSequence} - ${draftDef.draftName}")
    }
}