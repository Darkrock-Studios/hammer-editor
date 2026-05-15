package com.darkrockstudios.apps.hammer.common.projectselection.about

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.darkrockstudios.apps.hammer.*
import com.darkrockstudios.apps.hammer.common.components.projectselection.aboutapp.AboutApp
import com.darkrockstudios.apps.hammer.common.compose.LocalScreenCharacteristic
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.designsystem.*
import com.darkrockstudios.apps.hammer.common.compose.icons.AboutIcons
import com.darkrockstudios.apps.hammer.common.compose.icons.Discord
import com.darkrockstudios.apps.hammer.common.compose.icons.Github
import com.darkrockstudios.apps.hammer.common.compose.icons.Reddit
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import korlibs.io.lang.format
import org.jetbrains.compose.resources.painterResource

private val MaxColumnWidth = 880.dp

@Composable
fun AboutAppUi(component: AboutApp, modifier: Modifier = Modifier) {
	var showLibraries by remember { mutableStateOf(false) }
	val state by component.state.subscribeAsState()
	val screen = LocalScreenCharacteristic.current
	val isCompact = screen.windowWidthClass == WindowWidthSizeClass.Compact

	val outerHorizontal: Dp = if (isCompact) Ui.Padding.XL else 56.dp
	val outerVertical: Dp = if (isCompact) Ui.Padding.L else 28.dp
	val sectionCount = 4 + platformAboutSectionCount

	Column(
		modifier = modifier
			.fillMaxSize()
			.background(MaterialTheme.colorScheme.surface),
	) {
		Breadcrumb(isCompact = isCompact)
		HdFolioDivider()

		Column(
			modifier = Modifier
				.weight(1f)
				.fillMaxWidth()
				.verticalScroll(rememberScrollState())
				.padding(horizontal = outerHorizontal, vertical = outerVertical),
		) {
			Box(
				modifier = Modifier
					.widthIn(max = MaxColumnWidth)
					.fillMaxWidth()
					.align(Alignment.CenterHorizontally),
			) {
				Column(verticalArrangement = Arrangement.spacedBy(64.dp)) {
					Hero(isCompact = isCompact)

					HdHairlineSection(
						section = 1,
						title = "MANUSCRIPT",
						contentSpacing = 12.dp,
					) {
						Text(
							text = Res.string.about_description_line_two.get(),
							style = MaterialTheme.typography.bodyLarge,
							color = MaterialTheme.colorScheme.onSurface,
						)
					}

					HdHairlineSection(
						section = 2,
						title = Res.string.about_community_header.get(),
						contentSpacing = 0.dp,
					) {
						CommunityLink(
							label = Res.string.about_community_discord_link.get(),
							icon = AboutIcons.Discord,
							onClick = component::openDiscord,
						)
						CommunityRowDivider()
						CommunityLink(
							label = Res.string.about_community_reddit_link.get(),
							icon = AboutIcons.Reddit,
							onClick = component::openReddit,
						)
						CommunityRowDivider()
						CommunityLink(
							label = Res.string.about_community_github_link.get(),
							icon = AboutIcons.Github,
							onClick = component::openGithub,
						)
					}

					VersionCard(state)

					HdHairlineSection(
						section = 4,
						title = Res.string.about_attribution_header.get(),
						contentSpacing = 14.dp,
					) {
						HdHairlineButton(
							label = Res.string.about_attribution_libraries_button.get(),
							onClick = { showLibraries = true },
						)
					}

					PlatformAboutSection(component, section = 5)

					Spacer(Modifier.height(8.dp))
				}
			}
		}

		FolioCaption(
			horizontalPadding = outerHorizontal,
			sectionCount = sectionCount,
		)
	}

	LibrariesUi(showLibraries) { showLibraries = false }
}

@Composable
private fun Breadcrumb(isCompact: Boolean) {
	val horizontal: Dp = if (isCompact) Ui.Padding.XL else 56.dp
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.padding(horizontal = horizontal, vertical = 14.dp),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(12.dp),
	) {
		HdMonoLabel(
			text = "HAMMER",
			color = MaterialTheme.colorScheme.onSurfaceVariant,
		)
		HdMonoLabel(
			text = "/",
			color = MaterialTheme.colorScheme.outlineVariant,
		)
		HdMonoLabel(
			text = "ABOUT",
			color = MaterialTheme.colorScheme.onSurface,
		)
	}
}

@Composable
private fun Hero(isCompact: Boolean) {
	Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
		HdMonoLabel(text = "§ 0 · ABOUT")
		Row(
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.spacedBy(16.dp),
		) {
			Image(
				painter = painterResource(Res.drawable.hammer_icon),
				contentDescription = null,
				modifier = Modifier.size(40.dp),
			)
			Text(
				text = Res.string.app_name.get(),
				style = if (isCompact) MaterialTheme.typography.displaySmall
				else MaterialTheme.typography.displayMedium,
				color = MaterialTheme.colorScheme.onSurface,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis,
			)
		}
		Text(
			text = Res.string.about_description.get(),
			style = MaterialTheme.typography.bodyLarge,
			color = MaterialTheme.colorScheme.onSurfaceVariant,
			fontStyle = FontStyle.Italic,
		)
	}
}

@Composable
private fun CommunityLink(
	label: String,
	icon: ImageVector,
	onClick: () -> Unit,
) {
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.clickable(onClickLabel = label, onClick = onClick)
			.padding(vertical = 14.dp, horizontal = 4.dp),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(14.dp),
	) {
		Icon(
			painter = rememberVectorPainter(icon),
			contentDescription = null,
			tint = MaterialTheme.colorScheme.primary,
			modifier = Modifier.size(16.dp),
		)
		Text(
			text = label,
			style = MaterialTheme.typography.bodyLarge,
			color = MaterialTheme.colorScheme.onSurface,
			modifier = Modifier.weight(1f),
		)
		HdMonoLabel(
			text = "↗",
			color = MaterialTheme.colorScheme.onSurfaceVariant,
		)
	}
}

@Composable
private fun CommunityRowDivider() {
	HorizontalDivider(
		thickness = Dp.Hairline,
		color = MaterialTheme.colorScheme.outlineVariant,
	)
}

@Composable
private fun VersionCard(state: AboutApp.State) {
	val statusGreeble = if (state.newVersionAvailable) "UPDATE AVAILABLE" else "UP TO DATE"
	val latestGreeble = state.latestVersion?.let { "LATEST $it" }
	val latest = state.latestVersion
	val message = if (state.newVersionAvailable && latest != null) {
		Res.string.about_version_new_available_message.get().format(latest)
	} else {
		Res.string.about_version_up_to_date.get()
	}
	val messageColor = if (state.newVersionAvailable) {
		MaterialTheme.colorScheme.primary
	} else {
		MaterialTheme.colorScheme.onSurface
	}

	HdCatalogueCard(
		topStart = "§ III · VERSION",
		topEnd = statusGreeble,
		bottomStart = state.currentVersion,
		bottomEnd = latestGreeble,
	) {
		Text(
			text = message,
			style = MaterialTheme.typography.bodyMedium,
			color = messageColor,
		)
	}
}

@Composable
private fun FolioCaption(
	horizontalPadding: Dp,
	sectionCount: Int,
) {
	HorizontalDivider(
		thickness = Dp.Hairline,
		color = MaterialTheme.colorScheme.outlineVariant,
	)
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.padding(horizontal = horizontalPadding, vertical = 10.dp),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
	) {
		HdMonoLabel(text = "ABOUT")
		HdMonoLabel(
			text = "·",
			color = MaterialTheme.colorScheme.outlineVariant,
		)
		HdMonoLabel(text = "§§ $sectionCount")
	}
}

@Composable
expect fun PlatformAboutSection(component: AboutApp, section: Int)

expect val platformAboutSectionCount: Int
