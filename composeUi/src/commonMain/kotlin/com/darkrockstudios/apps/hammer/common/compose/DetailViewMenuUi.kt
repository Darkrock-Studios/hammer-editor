package com.darkrockstudios.apps.hammer.common.compose

import androidx.compose.runtime.Composable
import com.darkrockstudios.apps.hammer.common.data.MenuItemDescriptor

/**
 * Generic dropdown menu for detail view components.
 * Platform-specific implementation (Android shows menu, Desktop is empty).
 */
@Composable
expect fun DetailViewDropdownMenu(menuItems: Set<MenuItemDescriptor>)
