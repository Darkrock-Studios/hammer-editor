package com.darkrockstudios.apps.hammer.common.compose.plugin

import com.darkrockstudios.apps.hammer.common.compose.plugin.style.StylePluginUi
import com.darkrockstudios.apps.hammer.operations.plugin.ClientPlugin
import com.darkrockstudios.apps.hammer.plugins.style.StylePlugin

/*
 * The plugins compiled into every platform's build, registered here because :composeUi is the one
 * module every app (and the iOS framework) is built from. A plugin with a UI half is listed in both.
 * Downstream distributions register theirs here; keep this file otherwise untouched so their patch
 * applies cleanly.
 */

fun installedPlugins(): List<ClientPlugin> = listOf(StylePlugin)

fun installedPluginUis(): List<PluginUi> = listOf(StylePluginUi)
