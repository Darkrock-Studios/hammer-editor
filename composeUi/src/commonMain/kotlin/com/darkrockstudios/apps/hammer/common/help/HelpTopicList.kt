package com.darkrockstudios.apps.hammer.common.help

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowOutward
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdQuietButton
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdSettingGroup
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.help_watch_all
import com.darkrockstudios.apps.hammer.help_watch_video

fun helpTopicTag(topic: HelpTopic) = "help-topic-${topic.name}"
fun helpVideoTag(topic: HelpTopic) = "help-video-${topic.name}"
const val HELP_WATCH_ALL_TAG = "help-watch-all"

/**
 * Help topics, each a title, its blurb and a link to its video when it has one, under [title]
 * when given.
 *
 *     Getting started
 *     ─────────────────────────────
 *     What is Hammer
 *     A writing app for novels…
 *     ▷ Watch the video ↗
 */
@Composable
fun HelpTopicList(topics: List<HelpTopic>, modifier: Modifier = Modifier, title: String? = null) {
	HdSettingGroup(title = title, modifier = modifier) {
		topics.forEach { HelpTopicItem(it) }
	}
}

/**
 * Global help: the tutorial playlist, then every section's topics. Emits into the caller's column,
 * so the gap between the two is the caller's spacing.
 */
@Composable
fun ColumnScope.HelpAllTopics() {
	HelpWatchAllLink()
	HelpSectionList()
}

@Composable
private fun HelpSectionList() {
	Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Ui.Padding.XXL)) {
		for (section in HelpSection.entries) {
			HelpTopicList(topics = section.topics, title = section.title.get())
		}
	}
}

@Composable
private fun HelpTopicItem(topic: HelpTopic) {
	val uriHandler = LocalUriHandler.current
	Column(
		modifier = Modifier.fillMaxWidth().testTag(helpTopicTag(topic)),
		verticalArrangement = Arrangement.spacedBy(Ui.Padding.S),
	) {
		Text(
			text = topic.title.get(),
			style = MaterialTheme.typography.bodyLarge,
			color = MaterialTheme.colorScheme.onSurface,
		)
		Text(
			text = topic.blurb.get(),
			style = MaterialTheme.typography.bodyMedium,
			color = MaterialTheme.colorScheme.onSurfaceVariant,
		)
		val url = topic.videoUrl
		if (url != null) {
			HdQuietButton(
				onClick = { uriHandler.openUri(url) },
				icon = Icons.Outlined.PlayCircle,
				label = Res.string.help_watch_video.get(),
				trailingIcon = Icons.Outlined.ArrowOutward,
				modifier = Modifier.pullToEdge().testTag(helpVideoTag(topic)),
			)
		}
	}
}

@Composable
private fun HelpWatchAllLink() {
	val uriHandler = LocalUriHandler.current
	HdQuietButton(
		onClick = { uriHandler.openUri(HELP_PLAYLIST_URL) },
		icon = Icons.Outlined.PlayCircle,
		label = Res.string.help_watch_all.get(),
		trailingIcon = Icons.Outlined.ArrowOutward,
		modifier = Modifier.pullToEdge().testTag(HELP_WATCH_ALL_TAG),
	)
}

/** Pulls a labelled quiet button back by its inner padding, so its icon sits on the text edge. */
private fun Modifier.pullToEdge(): Modifier = offset(x = -Ui.Padding.M)
