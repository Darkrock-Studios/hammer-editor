package com.darkrockstudios.apps.hammer.common.compose.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.common.compose.CollapseWhileTyping
import com.darkrockstudios.apps.hammer.common.compose.Ui

object HdCreateModalDefaults {
	val BodyPadding = PaddingValues(horizontal = 28.dp, vertical = 22.dp)
	val BodySpacing = 22.dp
}

/**
 * Full-screen create form: a titled hairline sheet whose title collapses while the keyboard is up,
 * and a footer with optional [footerMeta] on the left and [actions] on the right. [content] lays out
 * its own body, padded with [HdCreateModalDefaults].
 */
@Composable
fun HdCreateModal(
	title: String,
	actions: @Composable RowScope.() -> Unit,
	modifier: Modifier = Modifier,
	footerMeta: (@Composable () -> Unit)? = null,
	content: @Composable ColumnScope.() -> Unit,
) {
	Box(
		modifier = Modifier.fillMaxSize().padding(Ui.Padding.XL),
		contentAlignment = Alignment.Center,
	) {
		Column(
			modifier = modifier
				.widthIn(max = Ui.MaxWidth.Modal)
				.fillMaxWidth()
				.background(MaterialTheme.colorScheme.surface, RectangleShape)
				.border(Dp.Hairline, MaterialTheme.colorScheme.outline, RectangleShape),
		) {
			CollapseWhileTyping {
				Column {
					Text(
						text = title,
						style = MaterialTheme.typography.titleLarge,
						color = MaterialTheme.colorScheme.onSurface,
						modifier = Modifier.padding(start = 28.dp, end = 28.dp, top = Ui.Padding.XL, bottom = Ui.Padding.L),
					)
					HorizontalDivider(thickness = Dp.Hairline, color = MaterialTheme.colorScheme.outlineVariant)
				}
			}

			content()

			HorizontalDivider(thickness = Dp.Hairline, color = MaterialTheme.colorScheme.outlineVariant)
			Row(
				modifier = Modifier
					.fillMaxWidth()
					.background(MaterialTheme.colorScheme.surfaceContainerLow)
					.padding(horizontal = 24.dp, vertical = 12.dp),
				verticalAlignment = Alignment.CenterVertically,
				horizontalArrangement = Arrangement.spacedBy(14.dp),
			) {
				footerMeta?.invoke()
				Spacer(modifier = Modifier.weight(1f))
				actions()
			}
		}
	}
}
