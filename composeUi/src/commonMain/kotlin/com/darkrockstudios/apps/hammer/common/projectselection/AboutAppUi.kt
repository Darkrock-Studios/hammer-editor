package com.darkrockstudios.apps.hammer.common.projectselection

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.MR
import com.darkrockstudios.apps.hammer.common.components.projectselection.aboutapp.AboutApp
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.moko.get
import com.darkrockstudios.apps.hammer.common.util.getAppVersionString
import dev.icerock.moko.resources.ImageResource
import dev.icerock.moko.resources.compose.painterResource

@Composable
fun AboutAppUi(component: AboutApp, modifier: Modifier = Modifier) {
	var showLibraries by remember { mutableStateOf(false) }

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
						painter = painterResource(MR.images.hammer_icon),
						contentDescription = null
					)
					Text(
						text = MR.strings.app_name.get(),
						style = MaterialTheme.typography.displayLarge,
					)
				}

				Spacer(modifier = Modifier.size(Ui.Padding.M))

				Text(
					text = MR.strings.about_description.get(),
					style = MaterialTheme.typography.headlineLarge,
					fontStyle = FontStyle.Italic
				)

				Spacer(modifier = Modifier.size(Ui.Padding.M))

				Text(
					text = MR.strings.about_description_line_two.get(),
					style = MaterialTheme.typography.bodyLarge,
				)

				Spacer(modifier = Modifier.size(Ui.Padding.XL))

				Text(
					text = MR.strings.about_community_header.get(),
					style = MaterialTheme.typography.headlineLarge,
				)

				CommunityLink(MR.strings.about_community_discord_link.get(), MR.images.discord) {
					component.openDiscord()
				}

				CommunityLink(MR.strings.about_community_reddit_link.get(), MR.images.reddit) {
					component.openReddit()
				}

				CommunityLink(MR.strings.about_community_github_link.get(), MR.images.github) {
					component.openGithub()
				}

				Spacer(modifier = Modifier.size(Ui.Padding.XL))

				Text(
					text = MR.strings.about_attribution_header.get(),
					style = MaterialTheme.typography.headlineSmall,
				)
				Button({
					showLibraries = true
				}) {
					Text(MR.strings.about_attribution_libraries_button.get())
				}

				Spacer(modifier = Modifier.size(Ui.Padding.XL))

				Text(
					text = getAppVersionString(),
					style = MaterialTheme.typography.bodySmall,
				)
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
	icon: ImageResource,
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
			painterResource(icon),
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