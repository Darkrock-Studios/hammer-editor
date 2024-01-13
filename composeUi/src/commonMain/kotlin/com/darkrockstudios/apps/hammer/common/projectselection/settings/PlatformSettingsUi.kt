package com.darkrockstudios.apps.hammer.common.projectselection.settings

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import com.darkrockstudios.apps.hammer.common.components.projectselection.accountsettings.PlatformSettings

@Composable
expect fun ColumnScope.PlatformSettingsUi(component: PlatformSettings)