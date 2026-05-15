package com.darkrockstudios.apps.hammer.common.compose.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.darkrockstudios.apps.hammer.IBMPlexMono_Medium
import com.darkrockstudios.apps.hammer.IBMPlexMono_Regular
import com.darkrockstudios.apps.hammer.IBMPlexMono_SemiBold
import com.darkrockstudios.apps.hammer.Res
import org.jetbrains.compose.resources.Font

/**
 * IBM Plex Mono — shipped as a project resource so the common greeble glyphs
 * (`← → ↑ ↓ ↗ § · × ✓`) render identically on every platform. `FontFamily.Monospace`
 * falls back to whatever the host JVM/OS exposes, which on Linux JDKs lacks
 * the Unicode arrows used in breadcrumbs and "open" affordances.
 *
 * Plex Mono does NOT cover the dingbat glyphs the design system uses for
 * entry-type indicators (`☉ ◇ ✦ ⚑ ✶ ∗`); those still fall back to platform
 * fonts. Search uses a Material vector icon instead of `⌕` for the same reason.
 *
 * SIL Open Font License 1.1; see composeResources/font/LICENSE.txt.
 */
@Composable
fun hammerMonoFontFamily(): FontFamily = FontFamily(
	Font(Res.font.IBMPlexMono_Regular, FontWeight.Normal),
	Font(Res.font.IBMPlexMono_Medium, FontWeight.Medium),
	Font(Res.font.IBMPlexMono_SemiBold, FontWeight.SemiBold),
)
