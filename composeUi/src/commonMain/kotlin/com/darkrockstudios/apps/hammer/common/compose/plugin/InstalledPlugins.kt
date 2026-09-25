package com.darkrockstudios.apps.hammer.common.compose.plugin

import com.darkrockstudios.apps.hammer.common.compose.plugin.plaintext.PlainTextPluginUi
import com.darkrockstudios.apps.hammer.operations.plugin.ClientPlugin
import com.darkrockstudios.apps.hammer.plugins.plaintext.PlainTextPlugin

/*
 * The plugins compiled into every platform's build, registered here because :composeUi is the one
 * module every app (and the iOS framework) is built from. A plugin with a UI half is listed in both.
 * Desktop-only plugins go in :desktop's installedDesktopPlugins(). Downstream distributions register
 * theirs here; keep this file otherwise untouched so their patch applies cleanly.
 */

fun installedPlugins(): List<ClientPlugin> = listOf(PlainTextPlugin)

fun installedPluginUis(): List<PluginUi> = listOf(PlainTextPluginUi)
