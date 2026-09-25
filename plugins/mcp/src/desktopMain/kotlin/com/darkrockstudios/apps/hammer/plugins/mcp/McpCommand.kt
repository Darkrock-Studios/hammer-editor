package com.darkrockstudios.apps.hammer.plugins.mcp

import com.darkrockstudios.apps.hammer.operations.LIVE_EDIT_KEY
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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.peanuuutz.tomlkt.Toml
import okio.FileSystem

/** [settings] reads the plugin's settings, once per server run. */
internal class McpCommand(private val settings: () -> McpSettings = ::savedSettings) : CliCommand {
	override val name = McpPlugin.ID
	override val help = "Serve Hammer to AI agents over MCP on stdin and stdout. Turn it on in Settings first."

	override suspend fun run(args: List<String>, io: CliIo, dispatcher: Dispatcher): Int {
		val settings = settings()
		if (!settings.enabled) {
			io.stderr.writeUtf8("MCP is off. Turn it on in Hammer's Settings, under Plugins.\n")
			return DISABLED
		}

		val server = Server(
			Implementation(name = "hammer", version = SERVER_VERSION),
			ServerOptions(ServerCapabilities(tools = ServerCapabilities.Tools(listChanged = false))),
		)
		dispatcher.operations().filter { it.agentVisible }
			.mapNotNull { if (settings.liveEdits) Tool(it, emptyMap()) else withoutLiveEdits(it) }
			.groupBy { toolName(it.op.name) }
			.forEach { (name, tools) ->
				if (tools.size > 1) Napier.w { "MCP tool '$name' would serve ${tools.map { it.op.name }}; offering none of them" }
				else server.addTool(name, tools.single(), dispatcher)
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

	/** An operation offered as a tool, and the values of its input fields it refuses. */
	private class Tool(val op: OperationDescriptor, val refused: Map<String, Set<JsonElement>>)

	/**
	 * [op] without its live edits: null when its input, or a field's object, is one; otherwise with the
	 * live values of its enum fields dropped from its schema and refused when called. Only top-level
	 * fields are looked at.
	 */
	private fun withoutLiveEdits(op: OperationDescriptor): Tool? {
		if (op.input[LIVE_EDIT_KEY] == JsonPrimitive(true)) return null
		val properties = op.input["properties"]?.jsonObject ?: return Tool(op, emptyMap())
		val markers = properties.mapNotNull { (field, schema) -> schema.jsonObject[LIVE_EDIT_KEY]?.let { field to it } }.toMap()
		if (markers.values.any { it !is JsonArray }) return null
		val refused = markers.mapValues { (_, values) -> values.jsonArray.toSet() }
		if (refused.isEmpty()) return Tool(op, emptyMap())

		val narrowed = properties.mapValues { (field, schema) ->
			val live = refused[field] ?: return@mapValues schema
			val allowed = schema.jsonObject.getValue("enum").jsonArray.filter { it !in live }
			JsonObject(schema.jsonObject - LIVE_EDIT_KEY + ("enum" to JsonArray(allowed)))
		}
		val note = refused.keys.joinToString(" ") { field ->
			"Live edits are off, so $field can only be ${narrowed.getValue(field).jsonObject.getValue("enum").jsonArray.joinToString(" or ")}."
		}
		return Tool(
			op.copy(description = "${op.description} $note", input = JsonObject(op.input + ("properties" to JsonObject(narrowed)))),
			refused,
		)
	}

	private fun Server.addTool(name: String, tool: Tool, dispatcher: Dispatcher) = addTool(
		name = name,
		description = tool.op.description,
		inputSchema = ToolSchema(
			properties = tool.op.input["properties"]?.jsonObject,
			required = tool.op.input["required"]?.jsonArray?.map { it.jsonPrimitive.content },
			defs = tool.op.input["\$defs"]?.jsonObject,
		),
	) { request ->
		val op = tool.op
		val arguments = request.arguments ?: JsonObject(emptyMap())
		val refused = tool.refused.keys.firstOrNull { arguments[it] in tool.refused.getValue(it) }
		if (refused != null) {
			val message = "Live edits are off in Hammer's settings, so $refused cannot be ${arguments[refused]}."
			return@addTool CallToolResult(content = listOf(TextContent(message)), isError = true)
		}
		try {
			val output = dispatcher.dispatch(op.name, arguments)
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

internal data class McpSettings(val enabled: Boolean, val liveEdits: Boolean)

// Read directly: a CLI command reaches Hammer's data only through the dispatcher, and this is the plugin's own file.
private fun savedSettings(): McpSettings {
	val saved = PluginSettingsDatasource(FileSystem.SYSTEM, Toml { ignoreUnknownKeys = true })
		.loadDeclared(McpPlugin.ID, McpPlugin.settings())
	fun toggle(key: String) = saved[key]?.jsonPrimitive?.boolean == true
	return McpSettings(enabled = toggle(McpPlugin.ENABLED), liveEdits = toggle(McpPlugin.LIVE_EDITS))
}
