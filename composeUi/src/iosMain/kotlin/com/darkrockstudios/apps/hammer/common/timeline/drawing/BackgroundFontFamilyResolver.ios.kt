package com.darkrockstudios.apps.hammer.common.timeline.drawing

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.createFontFamilyResolver

// The Skia font cache behind the UI's resolver is not synchronized, so text measured on another
// thread needs a resolver, and so a font cache, of its own.
private val newBackgroundResolver: () -> FontFamily.Resolver = { createFontFamilyResolver() }

@Composable
internal actual fun rememberBackgroundFontFamilyResolverFactory(): () -> FontFamily.Resolver = newBackgroundResolver
