package com.darkrockstudios.apps.hammer.common.preview.projectselection

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdFolioDivider
import com.darkrockstudios.apps.hammer.common.compose.theme.AppTheme
import com.darkrockstudios.apps.hammer.common.preview.KoinApplicationPreview
import com.darkrockstudios.apps.hammer.common.preview.globalSettingsPreview
import com.darkrockstudios.apps.hammer.common.projectselection.Masthead
import com.darkrockstudios.apps.hammer.common.projectselection.ProjectsSortMode
import com.darkrockstudios.apps.hammer.common.projectselection.SearchStrip

/**
 * Compact (phone) masthead: entry count and sort pill drop out so the sync
 * button fits, and sort relocates into the revealed search strip.
 */
@Preview(widthDp = 400, heightDp = 200)
@Composable
fun ProjectListMastheadCompactPreview() {
	KoinApplicationPreview {
		AppTheme(globalSettingsPreview, true) {
			var sortMode by remember { mutableStateOf(ProjectsSortMode.LastAccessedDesc) }
			Column(
				modifier = Modifier
					.fillMaxSize()
					.background(MaterialTheme.colorScheme.background),
			) {
				Masthead(
					entryCount = 6,
					showEntryCount = false,
					isServerSynced = true,
					onSync = {},
					onCreate = {},
					showCreate = false,
					horizontalPadding = Ui.Padding.XL,
					sortMode = sortMode,
					onSortChange = { sortMode = it },
					showSort = false,
					searchActive = true,
					onToggleSearch = {},
				)
				HdFolioDivider()
				SearchStrip(
					query = "#fantasy",
					onQueryChange = {},
					parsedTags = listOf("fantasy"),
					onClose = {},
					horizontalPadding = Ui.Padding.XL,
					showSort = true,
					sortMode = sortMode,
					onSortChange = { sortMode = it },
				)
			}
		}
	}
}

/**
 * Expanded (tablet/desktop) masthead: entry count, sort pill, sync, and the
 * inline create button all sit in the header as before.
 */
@Preview(widthDp = 900, heightDp = 200)
@Composable
fun ProjectListMastheadExpandedPreview() {
	KoinApplicationPreview {
		AppTheme(globalSettingsPreview, true) {
			var sortMode by remember { mutableStateOf(ProjectsSortMode.LastAccessedDesc) }
			Column(
				modifier = Modifier
					.fillMaxSize()
					.background(MaterialTheme.colorScheme.background),
			) {
				Masthead(
					entryCount = 6,
					showEntryCount = true,
					isServerSynced = true,
					onSync = {},
					onCreate = {},
					showCreate = true,
					horizontalPadding = Ui.Padding.XXL,
					sortMode = sortMode,
					onSortChange = { sortMode = it },
					showSort = true,
					searchActive = false,
					onToggleSearch = {},
				)
				HdFolioDivider()
			}
		}
	}
}
