package com.darkrockstudios.apps.hammer.common.encyclopedia

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Toys
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.MR
import com.darkrockstudios.apps.hammer.common.components.encyclopedia.BrowseEntries
import com.darkrockstudios.apps.hammer.common.compose.ImageItem
import com.darkrockstudios.apps.hammer.common.compose.TagRow
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.moko.get
import com.darkrockstudios.apps.hammer.common.compose.rememberIoDispatcher
import com.darkrockstudios.apps.hammer.common.compose.rememberMainDispatcher
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryContent
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryDef
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal fun getEntryTypeIcon(type: EntryType): ImageVector {
	return when (type) {
		EntryType.PERSON -> Icons.Filled.Person
		EntryType.PLACE -> Icons.Filled.Place
		EntryType.THING -> Icons.Filled.Toys
		EntryType.EVENT -> Icons.Filled.Event
		EntryType.IDEA -> Icons.Filled.Lightbulb
	}
}

@Composable
internal fun EncyclopediaEntryItem(
	entryDef: EntryDef,
	component: BrowseEntries,
	viewEntry: (EntryDef) -> Unit,
	scope: CoroutineScope,
	modifier: Modifier = Modifier,
	filterByType: (type: EntryType) -> Unit
) {
	val ioDispatcher = rememberIoDispatcher()
	val mainDispatcher = rememberMainDispatcher()
	var loadContentJob = remember<Job?> { null }
	var entryContent by remember { mutableStateOf<EntryContent?>(null) }
	var entryImagePath by remember { mutableStateOf<String?>(null) }

	LaunchedEffect(entryDef) {
		entryImagePath = null
		loadContentJob?.cancel()
		loadContentJob = scope.launch(ioDispatcher) {
			val imagePath = component.getImagePath(entryDef)
			val content = component.loadEntryContent(entryDef)
			withContext(mainDispatcher) {
				entryImagePath = imagePath
				entryContent = content
				loadContentJob = null
			}
		}
	}

	Card(
		modifier = modifier
			.fillMaxWidth()
			.padding(Ui.Padding.XL)
			.clickable { viewEntry(entryDef) },
		elevation = CardDefaults.elevatedCardElevation(Ui.Elevation.SMALL)
	) {
		Column(modifier = Modifier.fillMaxWidth()) {

			if (entryImagePath != null) {
				Box {
					ImageItem(
						path = entryImagePath,
						modifier = Modifier.fillMaxWidth().heightIn(64.dp, 256.dp),
						contentScale = ContentScale.FillWidth
					)
					AssistChip(
						onClick = { filterByType(entryDef.type) },
						label = { Text(entryDef.type.toStringResource().get()) },
						leadingIcon = {
							Icon(
								getEntryTypeIcon(entryDef.type),
								entryDef.type.toStringResource().get()
							)
						},
						modifier = Modifier.align(Alignment.BottomEnd).padding(end = Ui.Padding.L),
						colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
					)
				}
			} else {
				AssistChip(
					onClick = { filterByType(entryDef.type) },
					label = { Text(entryDef.type.toStringResource().get()) },
					leadingIcon = {
						Icon(
							getEntryTypeIcon(entryDef.type),
							entryDef.type.toStringResource().get()
						)
					},
					modifier = Modifier.align(Alignment.End).padding(end = Ui.Padding.L)
				)
			}

			Column(
				modifier = Modifier.padding(
					top = Ui.Padding.L,
					start = Ui.Padding.L,
					end = Ui.Padding.L
				)
			) {
				Text(
					entryDef.name,
					style = MaterialTheme.typography.headlineMedium
				)

				if (loadContentJob != null) {
					CircularProgressIndicator()
				} else {
					val content = entryContent
					if (content != null) {
						Text(
							content.text,
							style = MaterialTheme.typography.bodyMedium
						)

						Spacer(modifier = Modifier.size(Ui.Padding.L))

						TagRow(
							tags = content.tags,
						)
					} else {
						Text(MR.strings.encyclopedia_entry_load_error.get())
					}
				}
			}
		}
	}
}