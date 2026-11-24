package com.darkrockstudios.apps.hammer.common.projectselection.about

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowOutward
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.about_community_studio_attribution
import com.darkrockstudios.apps.hammer.about_group_credits
import com.darkrockstudios.apps.hammer.about_libraries_desc
import com.darkrockstudios.apps.hammer.about_libraries_link
import com.darkrockstudios.apps.hammer.about_libraries_view_button
import com.darkrockstudios.apps.hammer.about_studio_meet_button
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineButton
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdSettingRow
import com.darkrockstudios.apps.hammer.about_attribution_studio_button
import com.darkrockstudios.apps.hammer.about_community_discord_link
import com.darkrockstudios.apps.hammer.about_community_github_link
import com.darkrockstudios.apps.hammer.about_community_header
import com.darkrockstudios.apps.hammer.about_community_reddit_link
import com.darkrockstudios.apps.hammer.about_description
import com.darkrockstudios.apps.hammer.about_group_updates
import com.darkrockstudios.apps.hammer.about_version_changes_button
import com.darkrockstudios.apps.hammer.about_version_github_button
import com.darkrockstudios.apps.hammer.app_name
import com.darkrockstudios.apps.hammer.common.components.projectselection.aboutapp.AboutApp
import com.darkrockstudios.apps.hammer.common.compose.LocalScreenCharacteristic
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.WindowWidthSizeClass
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdPage
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdQuietButton
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdSettingGroup
import com.darkrockstudios.apps.hammer.common.compose.designsystem.hdMetaStyle
import com.darkrockstudios.apps.hammer.common.compose.icons.AboutIcons
import com.darkrockstudios.apps.hammer.common.compose.icons.Discord
import com.darkrockstudios.apps.hammer.common.compose.icons.Github
import com.darkrockstudios.apps.hammer.common.compose.icons.Reddit
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.data.appupdate.AppUpdateState
import com.darkrockstudios.apps.hammer.common.data.changelog.supportsInAppChangelog
import com.darkrockstudios.apps.hammer.common.help.HelpAllTopicsDialog
import com.darkrockstudios.apps.hammer.about_help_button
import com.darkrockstudios.apps.hammer.hammer_icon
import com.darkrockstudios.apps.hammer.project_select_nav_about_app
import org.jetbrains.compose.resources.painterResource

private val MaxColumnWidth = 720.dp

const val ABOUT_HELP_BUTTON_TAG = "about-help"

/**
 * The app and its version, who made it, community links, where it stands on updates and, where
 * the platform has them, the tools for reporting a problem.
 */
@Composable
fun AboutAppUi(
	component: AboutApp,
	onShowStudio: () -> Unit,
	modifier: Modifier = Modifier,
) {
	var showLibraries by remember { mutableStateOf(false) }
	var showHelp by rememberSaveable { mutableStateOf(false) }
	val state by component.state.subscribeAsState()
	// One primary per surface: a pending update install or retry outranks help.
	val updateIsPrimary = state.appUpdate.hasPrimaryAction

	HdPage(
		title = Res.string.project_select_nav_about_app.get(),
		maxWidth = MaxColumnWidth,
		modifier = modifier,
		folio = listOf(Res.string.project_select_nav_about_app.get(), "§§ $AboutSectionCount"),
	) {
		Column(verticalArrangement = Arrangement.spacedBy(Ui.Padding.XL)) {
			Hero(version = state.currentVersion)
			HdHairlineButton(
				label = Res.string.about_help_button.get(),
				onClick = { showHelp = true },
				emphasised = !updateIsPrimary,
				modifier = Modifier.testTag(ABOUT_HELP_BUTTON_TAG),
			)
		}

		HdSettingGroup(title = Res.string.about_group_credits.get()) {
			HdSettingRow(
				title = Res.string.about_attribution_studio_button.get(),
				description = Res.string.about_community_studio_attribution.get(),
			) {
				HdHairlineButton(label = Res.string.about_studio_meet_button.get(), onClick = onShowStudio)
			}
			HdSettingRow(
				title = Res.string.about_libraries_link.get(),
				description = Res.string.about_libraries_desc.get(),
			) {
				HdHairlineButton(label = Res.string.about_libraries_view_button.get(), onClick = { showLibraries = true })
			}
		}

		HdSettingGroup(title = Res.string.about_community_header.get()) {
			QuietLinks {
				HdQuietButton(
					onClick = component::openDiscord,
					icon = AboutIcons.Discord,
					label = Res.string.about_community_discord_link.get(),
					trailingIcon = Icons.Outlined.ArrowOutward,
				)
				HdQuietButton(
					onClick = component::openReddit,
					icon = AboutIcons.Reddit,
					label = Res.string.about_community_reddit_link.get(),
					trailingIcon = Icons.Outlined.ArrowOutward,
				)
				HdQuietButton(
					onClick = component::openGithub,
					icon = AboutIcons.Github,
					label = Res.string.about_community_github_link.get(),
					trailingIcon = Icons.Outlined.ArrowOutward,
				)
			}
		}

		HdSettingGroup(title = Res.string.about_group_updates.get()) {
			if (state.appUpdate !is AppUpdateState.Unsupported) {
				AppUpdateStatus(
					state = state.appUpdate,
					onCheckForUpdate = component::checkForUpdate,
					onUpdateApp = component::updateApp,
				)
			}
			ButtonRow {
				if (supportsInAppChangelog) {
					HdHairlineButton(
						label = Res.string.about_version_changes_button.get(),
						onClick = component::viewChangelog,
					)
				}
				HdHairlineButton(
					label = Res.string.about_version_github_button.get(),
					onClick = component::openLatestRelease,
				)
			}
		}

		PlatformAboutSection(component)
	}

	LibrariesUi(showLibraries) { showLibraries = false }
	if (showHelp) HelpAllTopicsDialog(onDismiss = { showHelp = false })
}

/** The name and version, and the line under it. */
@Composable
private fun Hero(version: String) {
	val compact = LocalScreenCharacteristic.current.windowWidthClass == WindowWidthSizeClass.Compact
	Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
		Row(
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.spacedBy(16.dp),
		) {
			Image(
				painter = painterResource(Res.drawable.hammer_icon),
				contentDescription = null,
				modifier = Modifier.size(if (compact) 36.dp else 44.dp),
			)
			Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
				Text(
					text = Res.string.app_name.get(),
					style = if (compact) MaterialTheme.typography.displaySmall else MaterialTheme.typography.displayMedium,
					color = MaterialTheme.colorScheme.onSurface,
					maxLines = 1,
					overflow = TextOverflow.Ellipsis,
					modifier = Modifier.alignByBaseline(),
				)
				Text(
					text = version,
					style = hdMetaStyle().copy(fontSize = MaterialTheme.typography.titleMedium.fontSize),
					color = MaterialTheme.colorScheme.onSurfaceVariant,
					maxLines = 1,
					modifier = Modifier.alignByBaseline(),
				)
			}
		}
		Text(
			text = Res.string.about_description.get(),
			style = MaterialTheme.typography.bodyLarge,
			color = MaterialTheme.colorScheme.onSurfaceVariant,
			fontStyle = FontStyle.Italic,
		)
	}
}

/** Quiet buttons in a wrapping row, pulled back so their labels sit on the column's edge. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuietLinks(content: @Composable () -> Unit) {
	FlowRow(
		modifier = Modifier.offset(x = -Ui.Padding.M),
		horizontalArrangement = Arrangement.spacedBy(4.dp),
		verticalArrangement = Arrangement.spacedBy(4.dp),
	) {
		content()
	}
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ButtonRow(content: @Composable () -> Unit) {
	FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
		content()
	}
}

/** The Troubleshooting group, on platforms that keep logs; nothing where there are none. */
@Composable
expect fun PlatformAboutSection(component: AboutApp)

/** Credits, Community, Updates, and every platform's Troubleshooting group. */
private const val AboutSectionCount = 4
