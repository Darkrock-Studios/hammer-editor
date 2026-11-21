package com.darkrockstudios.apps.hammer.common.preview.plugin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineProgressBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineButton
import com.darkrockstudios.apps.hammer.common.compose.plugin.PluginDialogSurface
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.composeui.resources.Res
import com.darkrockstudios.apps.hammer.composeui.resources.plugin_action_stop
import com.darkrockstudios.apps.hammer.composeui.resources.plugin_document_copy
import com.darkrockstudios.apps.hammer.composeui.resources.plugin_document_save

/**
 * A plugin action's page and its progress dialog, stacked over a scrim. Both share
 * [PluginDialogSurface]; the bodies stand in for what a plugin page draws.
 */
@Composable
internal fun PluginActionDialogsScrimPreview() {
	Column(
		modifier = Modifier
			.fillMaxSize()
			.background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f)),
		verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
		horizontalAlignment = Alignment.CenterHorizontally,
	) {
		PluginDialogSurface(title = "Point of view check", onClose = {}) {
			Text(
				text = "No point of view slips found in “The Harbour”. Every passage stays with the keeper.",
				style = MaterialTheme.typography.bodyLarge,
				modifier = Modifier.padding(Ui.Padding.XL),
			)
			Row(
				modifier = Modifier
					.fillMaxWidth()
					.background(MaterialTheme.colorScheme.surfaceContainerLow)
					.padding(horizontal = Ui.Padding.XL, vertical = Ui.Padding.M),
				horizontalArrangement = Arrangement.spacedBy(Ui.Padding.M),
			) {
				Spacer(modifier = Modifier.weight(1f))
				HdHairlineButton(label = Res.string.plugin_document_copy.get(), onClick = {})
				HdHairlineButton(label = Res.string.plugin_document_save.get(), onClick = {}, emphasised = true)
			}
		}
		PluginDialogSurface(title = "Export to EPUB", onClose = null) {
			Column(modifier = Modifier.padding(Ui.Padding.XL), verticalArrangement = Arrangement.spacedBy(Ui.Padding.M)) {
				HdHairlineProgressBar(progress = 0.6f, modifier = Modifier.fillMaxWidth())
				Text(text = "Packing chapter 3 of 5", style = MaterialTheme.typography.bodyMedium)
				Row(modifier = Modifier.fillMaxWidth()) {
					Spacer(modifier = Modifier.weight(1f))
					HdHairlineButton(label = Res.string.plugin_action_stop.get(), onClick = {})
				}
			}
		}
	}
}
