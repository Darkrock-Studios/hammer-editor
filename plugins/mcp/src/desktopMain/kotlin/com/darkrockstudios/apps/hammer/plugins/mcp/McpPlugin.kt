package com.darkrockstudios.apps.hammer.plugins.mcp

import com.darkrockstudios.apps.hammer.operations.cli.CliCommand
import com.darkrockstudios.apps.hammer.operations.plugin.ClientPlugin
import com.darkrockstudios.apps.hammer.operations.plugin.SettingDeclaration

/** Serves Hammer's agent-visible operations to AI agents, as `hammer mcp`. Off until enabled in Settings. */
object McpPlugin : ClientPlugin {
	const val ID = "mcp"
	const val ENABLED = "enabled"

	override val id = ID
	override val name = "MCP server"

	override fun settings(): List<SettingDeclaration> = listOf(
		SettingDeclaration.Toggle(
			key = ENABLED,
			label = "Let AI agents use Hammer",
			defaultValue = false,
			hint = "Agents can read your projects through 'hammer mcp'.",
		),
	)

	override fun cliCommands(): List<CliCommand> = listOf(McpCommand())
}
