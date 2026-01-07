package com.darkrockstudios.apps.hammer.common.projectselection

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.darkrockstudios.apps.hammer.*
import com.darkrockstudios.apps.hammer.common.components.projectselection.aboutapp.AboutApp
import com.darkrockstudios.apps.hammer.common.compose.LocalScreenCharacteristic
import com.darkrockstudios.apps.hammer.common.compose.SpacerL
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.icons.AboutIcons
import com.darkrockstudios.apps.hammer.common.compose.icons.Discord
import com.darkrockstudios.apps.hammer.common.compose.icons.Github
import com.darkrockstudios.apps.hammer.common.compose.icons.Reddit
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import org.jetbrains.compose.resources.painterResource

@Composable
fun AboutAppUi(component: AboutApp, modifier: Modifier = Modifier) {
	var showLibraries by remember { mutableStateOf(false) }
	val screen = LocalScreenCharacteristic.current
	val state by component.state.subscribeAsState()

	Box(modifier = modifier.fillMaxSize()) {
		ElevatedCard(
			modifier = Modifier.align(Alignment.Center)
		) {
			Column(
				modifier = Modifier.padding(Ui.Padding.XL).verticalScroll(rememberScrollState()),
			) {
				Row(
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.Center,
				) {
					Image(
						painter = painterResource(Res.drawable.hammer_icon),
						contentDescription = null
					)

					if(screen.windowWidthClass == WindowWidthSizeClass.Compact) {
						Text(
							text = Res.string.app_name.get(),
							style = MaterialTheme.typography.displaySmall,
						)
					} else {
						Text(
							text = Res.string.app_name.get(),
							style = MaterialTheme.typography.displayLarge,
						)
					}
				}

				Spacer(modifier = Modifier.size(Ui.Padding.M))

				Text(
					text = Res.string.about_description.get(),
					style = MaterialTheme.typography.headlineLarge,
					fontStyle = FontStyle.Italic
				)

				Spacer(modifier = Modifier.size(Ui.Padding.M))

				Text(
					text = Res.string.about_description_line_two.get(),
					style = MaterialTheme.typography.bodyLarge,
				)

				Spacer(modifier = Modifier.size(Ui.Padding.XL))

				Text(
					text = Res.string.about_community_header.get(),
					style = MaterialTheme.typography.headlineLarge,
				)

				CommunityLink(Res.string.about_community_discord_link.get(), AboutIcons.Discord) {
					component.openDiscord()
				}

				CommunityLink(Res.string.about_community_reddit_link.get(), AboutIcons.Reddit) {
					component.openReddit()
				}

				CommunityLink(Res.string.about_community_github_link.get(), AboutIcons.Github) {
					component.openGithub()
				}

				Spacer(modifier = Modifier.size(Ui.Padding.XL))

				Text(
					text = Res.string.about_attribution_header.get(),
					style = MaterialTheme.typography.headlineSmall,
				)
				Button({
					showLibraries = true
				}) {
					Text(Res.string.about_attribution_libraries_button.get())
				}

				Spacer(modifier = Modifier.size(Ui.Padding.XL))

				VersionStatus(state)
			}
		}
	}

	LibrariesUi(showLibraries) {
		showLibraries = false
	}
}

@Composable
private fun CommunityLink(
	label: String,
	icon: ImageVector,
	onClick: () -> Unit
) {
	Row(
		modifier = Modifier.padding(Ui.Padding.M).clickable(
			onClickLabel = label,
			onClick = onClick
		),
		verticalAlignment = Alignment.CenterVertically,
	) {
		Icon(
			rememberVectorPainter(icon),
			modifier = Modifier.size(12.dp),
			contentDescription = null,
			tint = MaterialTheme.colorScheme.tertiary
		)

		Spacer(modifier = Modifier.size(Ui.Padding.M))
		Text(
			label,
			style = MaterialTheme.typography.bodyLarge,
			color = MaterialTheme.colorScheme.tertiary,
			textDecoration = TextDecoration.Underline,
		)
	}
}

@Composable
private fun VersionStatus(state: AboutApp.State) {
	Row(verticalAlignment = Alignment.CenterVertically) {
		Text(
			text = state.currentVersion,
			style = MaterialTheme.typography.bodySmall,
		)

		SpacerL()

		// If we have latest version info, show status
		state.latestVersion?.let { latestVersion ->
			Spacer(modifier = Modifier.size(Ui.Padding.S))

			if (state.newVersionAvailable) {
				// Eye-catching callout for new version
				ElevatedCard(
					colors = CardDefaults.elevatedCardColors(
						containerColor = MaterialTheme.colorScheme.primaryContainer,
					),
					modifier = Modifier.padding(vertical = Ui.Padding.S)
				) {
					Row(
						modifier = Modifier.padding(Ui.Padding.M),
						verticalAlignment = Alignment.CenterVertically,
						horizontalArrangement = Arrangement.spacedBy(Ui.Padding.M)
					) {
						Icon(
							imageVector = Icons.Default.Info,
							contentDescription = null,
							tint = MaterialTheme.colorScheme.primary,
							modifier = Modifier.size(24.dp)
						)
						Column {
							Text(
								text = Res.string.about_version_new_available_title.get(),
								style = MaterialTheme.typography.titleMedium,
								color = MaterialTheme.colorScheme.onPrimaryContainer,
							)
							Text(
								text = Res.string.about_version_new_available_message.get().format(latestVersion),
								style = MaterialTheme.typography.bodyMedium,
								color = MaterialTheme.colorScheme.onPrimaryContainer,
							)
						}
					}
				}
			} else {
				// Up to date message
				Row(
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.spacedBy(Ui.Padding.S)
				) {
					Icon(
						imageVector = Icons.Default.CheckCircle,
						contentDescription = null,
						tint = MaterialTheme.colorScheme.tertiary,
						modifier = Modifier.size(16.dp)
					)
					Text(
						text = Res.string.about_version_up_to_date.get(),
						style = MaterialTheme.typography.bodyMedium,
						color = MaterialTheme.colorScheme.tertiary,
					)
				}
			}
		}
	}
}