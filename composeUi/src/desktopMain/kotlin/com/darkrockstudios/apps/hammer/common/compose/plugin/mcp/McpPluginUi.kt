package com.darkrockstudios.apps.hammer.common.compose.plugin.mcp

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import com.darkrockstudios.apps.hammer.common.compose.plugin.PluginUi
import com.darkrockstudios.apps.hammer.common.compose.plugin.SettingLabels
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.composeui.resources.*
import com.darkrockstudios.apps.hammer.plugins.mcp.McpPlugin
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

object McpPluginUi : PluginUi {
	override val id = McpPlugin.ID
	override val name = Res.string.mcp_plugin_name

	override fun settingLabels(): Map<String, SettingLabels> = mapOf(
		McpPlugin.ENABLED to SettingLabels(label = Res.string.mcp_enabled_label, hint = Res.string.mcp_enabled_hint),
		McpPlugin.LIVE_EDITS to SettingLabels(label = Res.string.mcp_live_edits_label, hint = Res.string.mcp_live_edits_hint),
	)

	override val settingsPane: @Composable ColumnScope.() -> Unit = {
		Text(Res.string.mcp_config_intro.get(), style = MaterialTheme.typography.bodyMedium)
		SelectionContainer {
			Text(configSnippet(), style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
		}
	}

	/** What an MCP client's config needs to launch Hammer's server, from outside any package sandbox. */
	private fun configSnippet(): String {
		val flatpak = System.getenv("FLATPAK_ID")
		val snap = System.getenv("SNAP_NAME")
		val (command, args) = when {
			flatpak != null -> "flatpak" to listOf("run", flatpak, "mcp")
			snap != null -> snap to listOf("mcp")
			// Set by jpackage in packaged builds; a development run has no launcher to point at.
			else -> (System.getProperty("jpackage.app-path") ?: "hammer") to listOf("mcp")
		}
		val server = buildJsonObject {
			put("command", command)
			putJsonArray("args") { args.forEach { add(JsonPrimitive(it)) } }
		}
		return snippetJson.encodeToString(
			JsonObject.serializer(),
			buildJsonObject { putJsonObject("mcpServers") { put("hammer", server) } },
		)
	}

	private val snippetJson = Json { prettyPrint = true }
}
