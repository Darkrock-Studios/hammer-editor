package com.darkrockstudios.apps.hammer.common.help

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.common.compose.SelfOwnedDialog
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineButton
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineDialogShell
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdQuietButton
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.help_all_topics
import com.darkrockstudios.apps.hammer.help_button
import com.darkrockstudios.apps.hammer.help_close
import com.darkrockstudios.apps.hammer.help_title

const val HELP_BUTTON_TAG = "help-button"
const val HELP_ALL_TOPICS_TAG = "help-all-topics"

private val HelpDialogMaxHeight = 760.dp

/**
 * The "?" for a screen's masthead, and the help it opens: [screen]'s topics, and "All help topics"
 * to list every topic in the same dialog.
 */
@Composable
fun HelpButton(screen: HelpScreen, modifier: Modifier = Modifier) {
	var open by rememberSaveable { mutableStateOf(false) }
	HdQuietButton(
		onClick = { open = true },
		icon = Icons.AutoMirrored.Outlined.HelpOutline,
		contentDescription = Res.string.help_button.get(),
		modifier = modifier.testTag(HELP_BUTTON_TAG),
	)
	if (open) HelpDialog(screen = screen, onDismiss = { open = false })
}

/** [screen]'s help in a dialog, for screens that offer help from a menu rather than a "?". */
@Composable
fun HelpDialog(screen: HelpScreen, onDismiss: () -> Unit) {
	SelfOwnedDialog(onClosed = onDismiss) {
		HelpDialogContent(
			screen = screen,
			onClose = ::requestDismiss,
			modifier = Modifier.predictiveBackTransform(),
		)
	}
}

/** The help card without its dialog window, so previews and tests can draw it. */
@Composable
fun HelpDialogContent(screen: HelpScreen, onClose: () -> Unit, modifier: Modifier = Modifier) {
	var showingAll by rememberSaveable { mutableStateOf(false) }
	HelpCard(
		onClose = onClose,
		modifier = modifier,
		footer = if (showingAll) null else {
			{
				HdHairlineButton(
					label = Res.string.help_all_topics.get(),
					onClick = { showingAll = true },
					modifier = Modifier.testTag(HELP_ALL_TOPICS_TAG),
				)
			}
		},
	) {
		if (showingAll) HelpAllTopics() else HelpTopicList(topics = screen.topics)
	}
}

/** Every help topic in a dialog, for global help outside a project. */
@Composable
fun HelpAllTopicsDialog(onDismiss: () -> Unit) {
	SelfOwnedDialog(onClosed = onDismiss) {
		HelpAllTopicsContent(onClose = ::requestDismiss, modifier = Modifier.predictiveBackTransform())
	}
}

/** [HelpAllTopicsDialog]'s card without its dialog window, so previews can draw it. */
@Composable
fun HelpAllTopicsContent(onClose: () -> Unit, modifier: Modifier = Modifier) {
	HelpCard(onClose = onClose, modifier = modifier, footer = null) { HelpAllTopics() }
}

@Composable
private fun HelpCard(
	onClose: () -> Unit,
	modifier: Modifier,
	footer: (@Composable () -> Unit)?,
	content: @Composable ColumnScope.() -> Unit,
) {
	HdHairlineDialogShell(
		title = Res.string.help_title.get(),
		onClose = onClose,
		closeContentDescription = Res.string.help_close.get(),
		modifier = modifier.heightIn(max = HelpDialogMaxHeight),
	) {
		Column(
			modifier = Modifier
				.weight(1f, fill = false)
				.verticalScroll(rememberScrollState()),
			verticalArrangement = Arrangement.spacedBy(Ui.Padding.XL),
			content = content,
		)
		if (footer != null) {
			Spacer(Modifier.height(Ui.Padding.XL))
			Row(
				modifier = Modifier.fillMaxWidth(),
				horizontalArrangement = Arrangement.End,
				verticalAlignment = Alignment.CenterVertically,
			) {
				footer()
			}
		}
	}
}
