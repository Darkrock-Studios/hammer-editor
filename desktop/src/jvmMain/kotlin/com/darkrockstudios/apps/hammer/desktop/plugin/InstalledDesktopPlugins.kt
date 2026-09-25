package com.darkrockstudios.apps.hammer.desktop.plugin

import com.darkrockstudios.apps.hammer.operations.plugin.ClientPlugin
import com.darkrockstudios.apps.hammer.plugins.mcp.McpPlugin

/** Plugins that only build for desktop. Cross-platform plugins go in `installedPlugins()`. */
fun installedDesktopPlugins(): List<ClientPlugin> = listOf(McpPlugin)
