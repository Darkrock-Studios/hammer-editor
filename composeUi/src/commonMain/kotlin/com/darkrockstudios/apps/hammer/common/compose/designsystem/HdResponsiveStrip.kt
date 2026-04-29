package com.darkrockstudios.apps.hammer.common.compose.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.LayoutScopeMarker
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Scope for [HdResponsiveStrip] — call [cell] inside the lambda to make a
 * child take an equal share of the row on wide screens, or full width when
 * stacked on narrow screens.
 */
@LayoutScopeMarker
interface HdResponsiveScope {
	fun Modifier.cell(): Modifier
}

/**
 * A row of equally-weighted children on wide screens that collapses into a
 * vertical stack on narrow screens. The standard "stats strip" layout in
 * the dashboard.
 *
 *     HdResponsiveStrip(isWide) {
 *         HdStatBlock(..., modifier = Modifier.cell())
 *         HdStatBlock(..., modifier = Modifier.cell())
 *     }
 *
 * Children that don't call `cell()` lay out at their intrinsic size in the
 * row (and full-width in the column) — useful for spacers / dividers.
 */
@Composable
fun HdResponsiveStrip(
	isWide: Boolean,
	modifier: Modifier = Modifier,
	rowSpacing: Dp = 24.dp,
	columnSpacing: Dp = 20.dp,
	content: @Composable HdResponsiveScope.() -> Unit,
) {
	if (isWide) {
		Row(
			modifier = modifier.fillMaxWidth(),
			horizontalArrangement = Arrangement.spacedBy(rowSpacing),
		) {
			val rowScope = this
			val scope = object : HdResponsiveScope {
				override fun Modifier.cell(): Modifier = with(rowScope) { this@cell.weight(1f) }
			}
			scope.content()
		}
	} else {
		Column(
			modifier = modifier.fillMaxWidth(),
			verticalArrangement = Arrangement.spacedBy(columnSpacing),
		) {
			val scope = object : HdResponsiveScope {
				override fun Modifier.cell(): Modifier = this.fillMaxWidth()
			}
			scope.content()
		}
	}
}
