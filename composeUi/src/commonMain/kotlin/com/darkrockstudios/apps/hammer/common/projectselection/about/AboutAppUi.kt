package com.darkrockstudios.apps.hammer.common.projectselection.about

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.about_attribution_header
import com.darkrockstudios.apps.hammer.about_attribution_libraries_button
import com.darkrockstudios.apps.hammer.about_attribution_studio_button
import com.darkrockstudios.apps.hammer.about_community_discord_link
import com.darkrockstudios.apps.hammer.about_community_github_link
import com.darkrockstudios.apps.hammer.about_community_header
import com.darkrockstudios.apps.hammer.about_community_reddit_link
import com.darkrockstudios.apps.hammer.about_community_studio_attribution
import com.darkrockstudios.apps.hammer.about_description
import com.darkrockstudios.apps.hammer.about_studio_header
import com.darkrockstudios.apps.hammer.about_version_changes_button
import com.darkrockstudios.apps.hammer.about_version_github_button
import com.darkrockstudios.apps.hammer.app_name
import com.darkrockstudios.apps.hammer.common.components.projectselection.aboutapp.AboutApp
import com.darkrockstudios.apps.hammer.common.compose.LocalScreenCharacteristic
import com.darkrockstudios.apps.hammer.common.compose.MpScrollBarColumn
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdCatalogueCard
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdFolioDivider
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineButton
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineSection
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdMonoLabel
import com.darkrockstudios.apps.hammer.common.compose.icons.AboutIcons
import com.darkrockstudios.apps.hammer.common.compose.icons.Discord
import com.darkrockstudios.apps.hammer.common.compose.icons.Github
import com.darkrockstudios.apps.hammer.common.compose.icons.Reddit
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.compose.scrollBarOverlay
import com.darkrockstudios.apps.hammer.common.data.changelog.supportsInAppChangelog
import com.darkrockstudios.apps.hammer.hammer_icon
import org.jetbrains.compose.resources.painterResource

private val MaxColumnWidth = 880.dp

@Composable
fun AboutAppUi(
	component: AboutApp,
	onShowStudio: () -> Unit,
	modifier: Modifier = Modifier,
) {
	var showLibraries by remember { mutableStateOf(false) }
	val state by component.state.subscribeAsState()
	val screen = LocalScreenCharacteristic.current
	val isCompact = screen.windowWidthClass == WindowWidthSizeClass.Compact

	val outerHorizontal: Dp = if (isCompact) Ui.Padding.XL else 56.dp
	val outerVertical: Dp = if (isCompact) Ui.Padding.L else 28.dp
	val sectionCount = 4 + platformAboutSectionCount

	Box(modifier = modifier.fillMaxSize()) {
		Column(
			modifier = Modifier
				.fillMaxSize()
				.background(MaterialTheme.colorScheme.surface),
		) {
			Breadcrumb(isCompact = isCompact)
			HdFolioDivider()

			val scrollState = rememberScrollState()
			Box(
				modifier = Modifier
					.weight(1f)
					.fillMaxWidth(),
			) {
				Column(
					modifier = Modifier
						.fillMaxSize()
						.verticalScroll(scrollState)
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
								title = Res.string.about_studio_header.get(),
								contentSpacing = 14.dp,
							) {
								Text(
									text = Res.string.about_community_studio_attribution.get(),
									style = MaterialTheme.typography.bodyLarge,
									color = MaterialTheme.colorScheme.onSurface,
								)
								HdHairlineButton(
									label = Res.string.about_attribution_studio_button.get(),
									onClick = onShowStudio,
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

							VersionCard(
								state = state,
								onViewChangelog = component::viewChangelog,
								onOpenLatestRelease = component::openLatestRelease,
							)

							AttributionSection(
								onShowLibraries = { showLibraries = true },
							)

							PlatformAboutSection(component, section = 5)

							Spacer(Modifier.height(8.dp))
						}
					}
				}

				MpScrollBarColumn(
					modifier = scrollBarOverlay(),
					state = scrollState,
				)
			}

			FolioCaption(
				horizontalPadding = outerHorizontal,
				sectionCount = sectionCount,
			)
		}

		LibrariesUi(showLibraries) { showLibraries = false }
	}
}

@Composable
private fun AttributionSection(
	onShowLibraries: () -> Unit,
) {
	HdHairlineSection(
		section = 4,
		title = Res.string.about_attribution_header.get(),
		contentSpacing = 14.dp,
	) {
		HdHairlineButton(
			label = Res.string.about_attribution_libraries_button.get(),
			onClick = onShowLibraries,
		)
	}
}

@Composable
private fun Breadcrumb(isCompact: Boolean) {
	val horizontal: Dp = if (isCompact) Ui.Padding.XL else 56.dp
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.height(Ui.TOP_BAR_HEIGHT)
			.padding(horizontal = horizontal),
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VersionCard(
	state: AboutApp.State,
	onViewChangelog: () -> Unit,
	onOpenLatestRelease: () -> Unit,
) {
	HdCatalogueCard(
		topStart = "§ III · VERSION",
		topEnd = "INSTALLED",
		bottomStart = state.currentVersion,
	) {
		// FlowRow, not Row: the two labels wrap instead of clipping on a narrow screen.
		FlowRow(
			horizontalArrangement = Arrangement.spacedBy(14.dp),
			verticalArrangement = Arrangement.spacedBy(14.dp),
		) {
			if (supportsInAppChangelog) {
				HdHairlineButton(
					label = Res.string.about_version_changes_button.get(),
					onClick = onViewChangelog,
				)
			}
			HdHairlineButton(
				label = Res.string.about_version_github_button.get(),
				onClick = onOpenLatestRelease,
			)
		}
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
