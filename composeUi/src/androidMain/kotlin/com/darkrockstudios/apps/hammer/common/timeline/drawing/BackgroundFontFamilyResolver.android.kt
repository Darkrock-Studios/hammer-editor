package com.darkrockstudios.apps.hammer.common.timeline.drawing

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.createFontFamilyResolver

// The application context, since the resolver outlives any one activity.
@Composable
internal actual fun rememberBackgroundFontFamilyResolverFactory(): () -> FontFamily.Resolver {
	val app = LocalContext.current.applicationContext
	return remember(app) { { createFontFamilyResolver(app) } }
}
