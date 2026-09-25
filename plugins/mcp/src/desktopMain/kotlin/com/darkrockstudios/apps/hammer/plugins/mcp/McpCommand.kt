package com.darkrockstudios.apps.hammer.plugins.mcp

import com.darkrockstudios.apps.hammer.operations.OperationException
import com.darkrockstudios.apps.hammer.operations.OperationJson
import com.darkrockstudios.apps.hammer.operations.cli.CliCommand
import com.darkrockstudios.apps.hammer.operations.cli.CliIo
import com.darkrockstudios.apps.hammer.operations.cli.Dispatcher
import com.darkrockstudios.apps.hammer.operations.core.OperationDescriptor
import com.darkrockstudios.apps.hammer.operations.plugin.PluginSettingsDatasource
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.StdioServerTransport
import io.modelcontextprotocol.kotlin.sdk.shared.Transport
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.peanuuutz.tomlkt.Toml
import okio.FileSystem

/** [isEnabled] reads the plugin's Enable setting. */
internal class McpCommand(private val isEnabled: () -> Boolean = ::enabledInSettings) : CliCommand {
	override val name = McpPlugin.ID
	override val help = "Serve Hammer to AI agents over MCP on stdin and stdout. Turn it on in Settings first."

	override suspend fun run(args: List<String>, io: CliIo, dispatcher: Dispatcher): Int {
		if (!isEnabled()) {
			io.stderr.writeUtf8("MCP is off. Turn it on in Hammer's Settings, under Plugins.\n")
			return DISABLED
		}

		val server = Server(
			Implementation(name = "hammer", version = SERVER_VERSION),
			ServerOptions(ServerCapabilities(tools = ServerCapabilities.Tools(listChanged = false))),
		)
		dispatcher.operations().filter { it.agentVisible }
			.groupBy { toolName(it.name) }
			.forEach { (tool, operations) ->
				if (operations.size > 1) Napier.w { "MCP tool '$tool' would serve ${operations.map { it.name }}; offering none of them" }
				else server.addTool(tool, operations.single(), dispatcher)
			}

		val closed = CompletableDeferred<Unit>()
		val transport = ClosingTransport(
			StdioServerTransport(io.stdin.inputStream().asSource().buffered(), io.stdout.outputStream().asSink().buffered()),
			closed,
		)
		server.createSession(transport)
		closed.await()
		return 0
	}

	private fun Server.addTool(tool: String, op: OperationDescriptor, dispatcher: Dispatcher) = addTool(
		name = tool,
		description = op.description,
		inputSchema = ToolSchema(
			properties = op.input["properties"]?.jsonObject,
			required = op.input["required"]?.jsonArray?.map { it.jsonPrimitive.content },
			defs = op.input["\$defs"]?.jsonObject,
		),
	) { request ->
		try {
			val output = dispatcher.dispatch(op.name, request.arguments ?: JsonObject(emptyMap()))
			CallToolResult(content = listOf(TextContent(OperationJson.encodeToString(JsonElement.serializer(), output))))
		} catch (e: CancellationException) {
			throw e
		} catch (e: OperationException) {
			CallToolResult(content = listOf(TextContent(e.message.orEmpty())), isError = true)
		} catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
			// Hammer being busy lands here too; the agent sees the message and can tell the writer.
			Napier.e(e) { "MCP tool ${op.name} failed" }
			CallToolResult(content = listOf(TextContent(e.message ?: "Failed")), isError = true)
		}
	}

	/**
	 * Completes [closed] however the transport closes. Hooked into the handler the session installs on
	 * connect, so a close during connecting is not missed.
	 */
	private class ClosingTransport(private val delegate: Transport, private val closed: CompletableDeferred<Unit>) :
		Transport by delegate {
		override fun onClose(block: () -> Unit) = delegate.onClose {
			block()
			closed.complete(Unit)
		}
	}

	internal companion object {
		const val DISABLED = 3
		private const val SERVER_VERSION = "1"

		/** Operation names use dots, which some MCP clients reject in tool names. */
		fun toolName(operation: String): String = operation.replace('.', '_')
	}
}

// Read directly: a CLI command reaches Hammer's data only through the dispatcher, and this is the plugin's own file.
private fun enabledInSettings(): Boolean =
	PluginSettingsDatasource(FileSystem.SYSTEM, Toml { ignoreUnknownKeys = true })
		.loadDeclared(McpPlugin.ID, McpPlugin.settings())[McpPlugin.ENABLED]?.jsonPrimitive?.boolean == true
