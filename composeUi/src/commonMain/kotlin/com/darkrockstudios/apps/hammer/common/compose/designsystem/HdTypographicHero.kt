package com.darkrockstudios.apps.hammer.common.compose.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.darkrockstudios.apps.hammer.common.compose.theme.LocalHammerColors
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryType

/**
 * Imageless hero — the entry [name] becomes the artwork. A 6dp vertical
 * accent stripe in the type's color stands in for the missing image,
 * with a faint over-sized [EntryType.glyph] sitting behind the name as
 * texture. Use as the top zone of an entry card when no image is set.
 */
@Composable
fun HdTypographicHero(
	name: String,
	type: EntryType,
	modifier: Modifier = Modifier,
	height: Dp = 200.dp,
) {
	val accent = LocalHammerColors.current.colorFor(type)
	val wraps = name.split(' ').size > 2
	val titleStyle = if (wraps) {
		MaterialTheme.typography.displaySmall
	} else {
		MaterialTheme.typography.displayMedium
	}

	Box(
		modifier = modifier
			.height(height)
			.background(MaterialTheme.colorScheme.surfaceContainerLow)
			.clipToBounds(),
	) {
		// Left accent stripe replacing the image.
		Box(
			modifier = Modifier
				.width(6.dp)
				.fillMaxHeight()
				.background(accent),
		)

		// Faint glyph backdrop — large, low-opacity texture.
		Text(
			text = type.glyph(),
			style = TextStyle(
				fontSize = 220.sp,
				fontWeight = FontWeight.Light,
			),
			color = accent.copy(alpha = 0.07f),
			modifier = Modifier
				.align(Alignment.TopEnd)
				.offset(x = 10.dp, y = (-30).dp),
		)

		// Name at the bottom-left, big and light.
		Text(
			text = name,
			style = titleStyle.copy(
				fontWeight = FontWeight.Light,
				letterSpacing = (-0.03).sp,
			),
			color = MaterialTheme.colorScheme.onSurface,
			textAlign = TextAlign.Start,
			modifier = Modifier
				.align(Alignment.BottomStart)
				.fillMaxWidth()
				.padding(start = 22.dp, end = 16.dp, bottom = 18.dp, top = 16.dp),
		)
	}
}
