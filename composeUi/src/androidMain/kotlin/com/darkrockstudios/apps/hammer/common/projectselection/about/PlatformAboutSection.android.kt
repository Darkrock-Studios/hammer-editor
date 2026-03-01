package com.darkrockstudios.apps.hammer.common.projectselection.about

import androidx.compose.runtime.Composable
import com.darkrockstudios.apps.hammer.common.components.projectselection.aboutapp.AboutApp

@Composable
actual fun PlatformAboutSection(component: AboutApp) {
	// No-op for Android - logs aren't written to disk
}
