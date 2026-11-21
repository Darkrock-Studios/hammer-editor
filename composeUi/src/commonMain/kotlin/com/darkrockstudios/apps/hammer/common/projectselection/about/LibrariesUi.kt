package com.darkrockstudios.apps.hammer.common.projectselection.about

import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdDialogFrame
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdDialogTitleRow
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.common.compose.AnimatedDialog
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdMetaText
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdMonoLabel
import com.darkrockstudios.apps.hammer.common.compose.rememberKoinInject
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.util.LibraryInfoProvider
import com.darkrockstudios.apps.hammer.project_libraries_count
import com.darkrockstudios.apps.hammer.project_libraries_dialog_title
import com.darkrockstudios.apps.hammer.project_libraries_license
import com.darkrockstudios.apps.hammer.project_libraries_website
import com.mikepenz.aboutlibraries.Libs
import com.mikepenz.aboutlibraries.entity.Library

@Composable
fun LibrariesUi(
	showLibraries: Boolean,
	close: () -> Unit
) {
	AnimatedDialog(visible = showLibraries, onCloseRequest = close) {
		val librariesInfo: LibraryInfoProvider = rememberKoinInject()
		val libs = remember { librariesInfo.getLibs() }
		LibrariesCard(libs = libs, onClose = { requestDismiss() })
	}
}

/** The open source libraries list without its dialog window. A row opens to its description and links. */
@Composable
internal fun LibrariesCard(
	libs: Libs,
	onClose: () -> Unit,
	modifier: Modifier = Modifier,
	initiallyOpen: String? = null,
) {
	val libraries = remember(libs) { libs.libraries.sortedBy { it.name.lowercase() } }
	var open by remember { mutableStateOf(initiallyOpen) }
	HdDialogFrame(
		modifier = modifier
			.heightIn(max = 720.dp)
			.fillMaxHeight(0.9f),
	) {
		Column {
			HdDialogTitleRow(
				title = Res.string.project_libraries_dialog_title.get(),
				meta = Res.string.project_libraries_count.get(libraries.size.toString()),
				onClose = onClose,
			)
			HorizontalDivider(
				thickness = Dp.Hairline,
				color = MaterialTheme.colorScheme.outlineVariant,
			)
			LazyColumn(modifier = Modifier.fillMaxWidth()) {
				items(libraries, key = { it.uniqueId }) { library ->
					LibraryRow(
						library = library,
						open = open == library.uniqueId,
						onToggle = { open = if (open == library.uniqueId) null else library.uniqueId },
					)
					HorizontalDivider(
						modifier = Modifier.padding(horizontal = Ui.Padding.XL),
						thickness = Dp.Hairline,
						color = MaterialTheme.colorScheme.outlineVariant,
					)
				}
			}
		}
	}
}

@Composable
private fun LibraryRow(library: Library, open: Boolean, onToggle: () -> Unit) {
	val by = library.developers.firstOrNull()?.name ?: library.organization?.name
	val meta = listOfNotNull(library.artifactVersion, by).joinToString(" · ")
	val license = library.licenses.firstOrNull()
	Column(
		modifier = Modifier
			.fillMaxWidth()
			.clickable(onClick = onToggle)
			.padding(horizontal = Ui.Padding.XL, vertical = Ui.Padding.M),
		verticalArrangement = Arrangement.spacedBy(6.dp),
	) {
		Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Ui.Padding.M)) {
			Column(modifier = Modifier.weight(1f)) {
				Text(library.name, style = MaterialTheme.typography.bodyLarge)
				if (meta.isNotEmpty()) HdMetaText(meta, maxLines = 1)
			}
			license?.let { HdMonoLabel(text = it.spdxId ?: it.name) }
		}
		if (open) {
			library.description?.takeIf { it.isNotBlank() && it != library.name }?.let {
				Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
			}
			Row(horizontalArrangement = Arrangement.spacedBy(Ui.Padding.L)) {
				library.website?.let { Link(Res.string.project_libraries_website.get(), it) }
				license?.url?.let { Link(Res.string.project_libraries_license.get(), it) }
			}
		}
	}
}

@Composable
private fun Link(label: String, url: String) {
	val uriHandler = LocalUriHandler.current
	Text(
		text = label,
		style = MaterialTheme.typography.bodySmall.copy(textDecoration = TextDecoration.Underline),
		color = MaterialTheme.colorScheme.primary,
		modifier = Modifier.clickable { uriHandler.openUri(url) },
	)
}
