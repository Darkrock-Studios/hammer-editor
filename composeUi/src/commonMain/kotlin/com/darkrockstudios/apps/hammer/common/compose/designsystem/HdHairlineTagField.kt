package com.darkrockstudios.apps.hammer.common.compose.designsystem

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Tag chip field — labeled hairline-underline input that keeps its
 * value as a list of tags rather than a free-form string. Each tag
 * renders as an [HdTagChip] with a `×` remove affordance; the trailing
 * input adds a tag on Enter or comma and removes the last tag on
 * Backspace when empty.
 *
 *     TAGS                              ↵ to add
 *     [# animal ×] [# guide ×]  ritual,…
 *     ────────────────────────────────────────────
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HdHairlineTagField(
	label: String,
	tags: List<String>,
	onTagsChange: (List<String>) -> Unit,
	modifier: Modifier = Modifier,
	hint: String? = null,
	placeholder: String? = null,
	suggestTags: ((prefix: String) -> List<String>)? = null,
) {
	var draft by remember { mutableStateOf("") }

	val addTag: (String) -> Unit = { raw ->
		val candidate = raw.trim().removePrefix("#")
		if (candidate.isNotEmpty() && !tags.contains(candidate)) {
			onTagsChange(tags + candidate)
		}
		draft = ""
	}
	val addCurrent: () -> Boolean = add@{
		if (draft.trim().removePrefix("#").isEmpty()) return@add false
		addTag(draft)
		true
	}
	val removeLast: () -> Boolean = remove@{
		if (draft.isNotEmpty() || tags.isEmpty()) return@remove false
		onTagsChange(tags.dropLast(1))
		true
	}

	val suggestions: List<String> = if (suggestTags != null && draft.isNotBlank()) {
		val prefix = draft.trim().removePrefix("#")
		if (prefix.isEmpty()) emptyList()
		else suggestTags(prefix).filter { it !in tags }
	} else {
		emptyList()
	}

	Column(modifier = modifier.fillMaxWidth()) {
		Row(
			modifier = Modifier.fillMaxWidth(),
			verticalAlignment = Alignment.Bottom,
			horizontalArrangement = Arrangement.spacedBy(10.dp),
		) {
			HdMonoLabel(text = label)
			if (hint != null) {
				HdMonoLabel(
					text = hint,
					color = MaterialTheme.colorScheme.onSurfaceVariant,
				)
			}
		}

		FlowRow(
			modifier = Modifier
				.fillMaxWidth()
				.heightIn(min = 38.dp)
				.padding(top = 6.dp, bottom = 6.dp),
			horizontalArrangement = Arrangement.spacedBy(6.dp),
			verticalArrangement = Arrangement.spacedBy(6.dp),
		) {
			tags.forEach { tag ->
				HdTagChip(
					label = tag,
					active = true,
					onRemove = { onTagsChange(tags - tag) },
				)
			}
			val onSurface = MaterialTheme.colorScheme.onSurface
			val muted = MaterialTheme.colorScheme.onSurfaceVariant
			val textStyle = MaterialTheme.typography.bodyMedium.copy(color = onSurface)

			Box(
				modifier = Modifier
					.weight(1f, fill = true)
					.widthIn(min = 100.dp)
					.heightIn(min = 24.dp)
					.padding(vertical = 2.dp),
				contentAlignment = Alignment.CenterStart,
			) {
				BasicTextField(
					value = draft,
					onValueChange = { next ->
						val last = next.lastOrNull()
						if (last == ',' || last == ' ') {
							draft = next.dropLast(1)
							addCurrent()
						} else {
							draft = next
						}
					},
					singleLine = true,
					textStyle = textStyle,
					cursorBrush = SolidColor(onSurface),
					keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
					modifier = Modifier
						.fillMaxWidth()
						.heightIn(min = 24.dp)
						.onPreviewKeyEvent { event ->
							if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
							when (event.key) {
								Key.Enter, Key.NumPadEnter -> addCurrent()
								Key.Backspace -> removeLast()
								else -> false
							}
						},
				)
				if (draft.isEmpty() && tags.isEmpty() && placeholder != null) {
					Text(
						text = placeholder,
						style = textStyle.copy(color = muted),
					)
				}
			}
		}

		HorizontalDivider(
			thickness = Dp.Hairline,
			color = MaterialTheme.colorScheme.outlineVariant,
		)

		if (suggestions.isNotEmpty()) {
			FlowRow(
				modifier = Modifier
					.fillMaxWidth()
					.padding(top = 6.dp),
				horizontalArrangement = Arrangement.spacedBy(6.dp),
				verticalArrangement = Arrangement.spacedBy(6.dp),
			) {
				suggestions.forEach { tag ->
					HdTagChip(
						label = tag,
						active = false,
						onClick = { addTag(tag) },
					)
				}
			}
		}
	}
}
