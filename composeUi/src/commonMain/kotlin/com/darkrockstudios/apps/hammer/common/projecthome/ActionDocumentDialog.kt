package com.darkrockstudios.apps.hammer.common.projecthome

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.common.components.projecthome.ProjectHome
import com.darkrockstudios.apps.hammer.common.compose.AnimatedDialog
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdFolioDivider
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineButton
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdMasthead
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdMastheadAction
import com.darkrockstudios.apps.hammer.common.compose.markdowneditor.MarkdownView
import com.darkrockstudios.apps.hammer.common.compose.rememberClipboardCopier
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.composeui.resources.Res
import com.darkrockstudios.apps.hammer.composeui.resources.plugin_document_close
import com.darkrockstudios.apps.hammer.composeui.resources.plugin_document_copied
import com.darkrockstudios.apps.hammer.composeui.resources.plugin_document_copy
import com.darkrockstudios.apps.hammer.composeui.resources.plugin_document_save

/** A project action's [ProjectHome.Document], to read, copy, or save as a note. */
@Composable
internal fun ActionDocumentDialog(
	document: ProjectHome.Document?,
	onSave: () -> Unit,
	onDismiss: () -> Unit,
) {
	// Kept while the dialog animates closed, after the document is gone.
	val last = remember { object { var document: ProjectHome.Document? = null } }
	if (document != null) last.document = document
	val shown = document ?: last.document
	var copied by remember(shown) { mutableStateOf(false) }
	val copy = rememberClipboardCopier()

	AnimatedDialog(
		visible = document != null,
		onCloseRequest = onDismiss,
	) {
		val current = shown ?: return@AnimatedDialog
		Surface(
			shape = RectangleShape,
			color = MaterialTheme.colorScheme.surface,
			contentColor = MaterialTheme.colorScheme.onSurface,
			shadowElevation = Ui.Elevation.LARGE,
			modifier = Modifier
				.padding(Ui.Padding.XL)
				.widthIn(max = 640.dp)
				.fillMaxWidth(),
		) {
			Column {
				HdMasthead(
					section = current.title.uppercase(),
					trailing = { HdMastheadAction(label = Res.string.plugin_document_close.get(), onClick = onDismiss) },
				)
				HdFolioDivider()
				MarkdownView(
					markdown = current.markdown,
					modifier = Modifier
						.fillMaxWidth()
						.heightIn(max = 520.dp)
						.verticalScroll(rememberScrollState())
						.padding(Ui.Padding.XL),
				)
				Row(
					modifier = Modifier
						.fillMaxWidth()
						.background(MaterialTheme.colorScheme.surfaceContainerLow)
						.padding(horizontal = Ui.Padding.XL, vertical = Ui.Padding.M),
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.spacedBy(Ui.Padding.M),
				) {
					Spacer(modifier = Modifier.weight(1f))
					HdHairlineButton(
						label = (if (copied) Res.string.plugin_document_copied else Res.string.plugin_document_copy).get(),
						onClick = {
							copy(current.markdown)
							copied = true
						},
					)
					HdHairlineButton(label = Res.string.plugin_document_save.get(), onClick = onSave, emphasised = true)
				}
			}
		}
	}
}
