package com.darkrockstudios.apps.hammer.common.projectselection.about

import androidx.compose.runtime.Composable
import com.darkrockstudios.apps.hammer.common.components.projectselection.aboutapp.AboutApp

@Composable
actual fun PlatformAboutSection(component: AboutApp, section: Int) {
	// No-op for iOS - logs aren't written to disk
}

actual val platformAboutSectionCount: Int = 0
