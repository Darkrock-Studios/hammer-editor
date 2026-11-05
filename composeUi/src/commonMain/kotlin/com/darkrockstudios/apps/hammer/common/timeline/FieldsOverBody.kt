package com.darkrockstudios.apps.hammer.common.timeline

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The least height an event's rich text body (format bar and text) is given: the format bar plus
 * about four lines of default size text inside the editor's padding. Less than that and a phone,
 * folded or with the keyboard up, leaves no room to write in.
 */
internal val EventBodyMinHeight: Dp = 180.dp

/** A touch target's height: the fields keep at least this, so they can still be scrolled. */
private val FieldsMinHeight: Dp = 48.dp

/**
 * [fields] stacked over [body], the body taking the height the fields leave:
 *
 *     ┌────────────────────┐
 *     │ fields (scrolling) │  as tall as they are, capped at the height less bodyMinHeight
 *     ├────────────────────┤
 *     │ body               │  the rest, at least bodyMinHeight
 *     └────────────────────┘
 *
 * Where everything fits this is a plain column with the body filling the rest. Where it does not,
 * the fields scroll in what is left above the body, so they scroll away while the body stays
 * usable. On a window too short even for [bodyMinHeight], the fields keep [FieldsMinHeight] (or half
 * the height, if less) and the body takes the rest. Give it a bounded height. With no [body] the
 * fields take only the height they need, up to all of it.
 */
@Composable
internal fun FieldsOverBody(
	bodyMinHeight: Dp,
	modifier: Modifier = Modifier,
	fieldsScroll: ScrollState = rememberScrollState(),
	fields: @Composable () -> Unit,
	body: (@Composable () -> Unit)?,
) {
	Layout(
		contents = listOf(
			{ Box(modifier = Modifier.verticalScroll(fieldsScroll)) { fields() } },
			{ if (body != null) Box(propagateMinConstraints = true) { body() } },
		),
		modifier = modifier,
	) { (fieldsMeasurables, bodyMeasurables), constraints ->
		val bodyMeasurable = bodyMeasurables.singleOrNull()
		val bodyMin = if (bodyMeasurable != null) bodyMinHeight.roundToPx() else 0
		val bounded = constraints.hasBoundedHeight
		val fieldsMax = if (bounded) {
			val floor = minOf(FieldsMinHeight.roundToPx(), constraints.maxHeight / 2)
			maxOf(constraints.maxHeight - bodyMin, floor)
		} else {
			Constraints.Infinity
		}
		val fieldsPlaceable = fieldsMeasurables.single().measure(constraints.copy(minHeight = 0, maxHeight = fieldsMax))
		val bodyHeight = if (bounded) {
			(constraints.maxHeight - fieldsPlaceable.height).coerceAtLeast(0)
		} else {
			bodyMin
		}
		val bodyPlaceable = bodyMeasurable?.measure(constraints.copy(minHeight = bodyHeight, maxHeight = bodyHeight))
		val width = if (constraints.hasBoundedWidth) {
			constraints.maxWidth
		} else {
			maxOf(fieldsPlaceable.width, bodyPlaceable?.width ?: 0)
		}
		val height = (fieldsPlaceable.height + (bodyPlaceable?.height ?: 0)).coerceIn(constraints.minHeight, constraints.maxHeight)
		layout(width, height) {
			fieldsPlaceable.place(0, 0)
			bodyPlaceable?.place(0, fieldsPlaceable.height)
		}
	}
}
