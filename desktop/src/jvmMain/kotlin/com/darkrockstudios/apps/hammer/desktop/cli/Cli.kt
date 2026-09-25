package com.darkrockstudios.apps.hammer.desktop.cli

import com.darkrockstudios.apps.hammer.common.getConfigDirectory
import com.darkrockstudios.apps.hammer.operations.Operation
import com.darkrockstudios.apps.hammer.operations.OperationException
import com.darkrockstudios.apps.hammer.operations.OperationRegistry
import com.darkrockstudios.apps.hammer.operations.STDIN_KEY
import com.darkrockstudios.apps.hammer.operations.cli.CliCommand
import com.darkrockstudios.apps.hammer.operations.cli.CliIo
import com.darkrockstudios.apps.hammer.operations.cli.Dispatcher
import com.darkrockstudios.apps.hammer.operations.core.OperationDescriptor
import com.darkrockstudios.apps.hammer.operations.jsonSchema
import com.darkrockstudios.apps.hammer.operations.plugin.PluginRegistry
import io.github.aakira.napier.Napier
import java.io.File
import kotlin.io.encoding.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okio.buffer
import okio.sink
import okio.source

/**
 * `hammer <operation words> [--field value]...`: every operation as a command, its input fields as
 * options, and its output as JSON on stdout. `hammer help` lists them.
 */
object Cli {
	const val EXIT_OK = 0
	const val EXIT_FAILURE = 1
	const val EXIT_UNAUTHORIZED = 3
	const val EXIT_NOT_FOUND = 4
	const val EXIT_BUSY = 5

	/** sysexits' EX_USAGE, clear of the small codes operations such as sync.run use for their own outcomes. */
	const val EXIT_USAGE = 64

	private val pretty = Json { prettyPrint = true }

	/** A CLI call starts with a command word; the app's own launch options all start with `-`. */
	fun isInvocation(args: Array<String>): Boolean = args.isNotEmpty() && !args[0].startsWith("-")

	fun run(args: List<String>, io: CliIo = systemIo()): Int {
		val plugins = HeadlessSession.pluginRegistry()
		val operations = plugins.operationRegistry
		return try {
			when {
				args.first() == HELP -> help(args.drop(1), operations, plugins.cliCommands, io)
				else -> plugins.cliCommands.firstOrNull { it.name == args.first() }
					?.let { command -> runBlocking { command.run(args.drop(1), io, HeadlessDispatcher(plugins)) } }
					?: runOperation(args, plugins, io)
			}
		} catch (e: UsageException) {
			io.stderr.writeUtf8("${e.message}\n")
			EXIT_USAGE
		} catch (e: OperationException) {
			io.stderr.writeUtf8("${e.message}\n")
			when (e.kind) {
				OperationException.Kind.NotFound -> EXIT_NOT_FOUND
				OperationException.Kind.InvalidInput -> EXIT_USAGE
				OperationException.Kind.Unauthorized -> EXIT_UNAUTHORIZED
			}
		} catch (e: HeadlessSession.Busy) {
			io.stderr.writeUtf8("${e.message}\n")
			EXIT_BUSY
		} catch (e: Forwarding.Refused) {
			io.stderr.writeUtf8("${e.message}\n")
			EXIT_BUSY
		} catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
			Napier.e(e) { "CLI call failed: $args" }
			io.stderr.writeUtf8("Failed: ${e.message}\n")
			EXIT_FAILURE
		} finally {
			io.stdout.flush()
			io.stderr.flush()
		}
	}

	private fun runOperation(args: List<String>, plugins: PluginRegistry, io: CliIo): Int {
		val registry = plugins.operationRegistry
		val words = args.takeWhile { !it.startsWith("--") }
		val op = registry.find(words.joinToString("."))
			?: throw UsageException("Unknown command '${words.joinToString(" ")}'. See 'hammer help'.")

		val options = parseOptions(args.drop(words.size))
		if (HELP in options.flags) {
			io.stdout.writeUtf8(operationHelp(op))
			return EXIT_OK
		}
		val input = withStdinFields(op, options.json?.jsonObject ?: buildInput(op, options.values), io)
		val (output, exitCode) = dispatch(plugins, op.name, input)
		writeOutput(op, output.jsonObject, options.out, io)
		return exitCode
	}

	private class Options(
		val values: Map<String, List<String?>>,
		val flags: Set<String>,
		val json: JsonElement?,
		val out: String?,
	)

	private fun parseOptions(args: List<String>): Options {
		val values = linkedMapOf<String, MutableList<String?>>()
		var json: JsonElement? = null
		var out: String? = null
		val flags = mutableSetOf<String>()
		var i = 0
		while (i < args.size) {
			val arg = args[i]
			if (!arg.startsWith("--")) throw UsageException("Unexpected '$arg'; options look like --name value")
			val (name, inline) = arg.removePrefix("--").split("=", limit = 2).let { it[0] to it.getOrNull(1) }
			val value = inline ?: args.getOrNull(i + 1)?.takeIf { !it.startsWith("--") }?.also { i++ }
			when (name) {
				HELP -> flags += HELP
				JSON_OPTION -> json = value?.let(::parseJson) ?: throw UsageException("--$JSON_OPTION needs a value")
				OUT_OPTION -> out = value ?: throw UsageException("--$OUT_OPTION needs a file, or - for stdout")
				else -> values.getOrPut(camelCase(name)) { mutableListOf() } += value
			}
			i++
		}
		return Options(values, flags, json, out)
	}

	/**
	 * Fills in the fields read from stdin that [input] lacks, and refuses a secret one given on the
	 * command line, whether as an option or inside `--json`.
	 */
	private fun withStdinFields(op: Operation<*, *>, input: JsonObject, io: CliIo): JsonObject {
		val properties = jsonSchema(op.input.descriptor)["properties"]?.jsonObject ?: JsonObject(emptyMap())
		val fromStdin = properties.filterValues { it.jsonObject[STDIN_KEY] != null }
		fromStdin.forEach { (name, schema) ->
			if (name in input && schema.isSecret()) {
				throw UsageException("${kebabCase(name)} is read from stdin or ${envName(name)}, never the command line")
			}
		}
		val missing = fromStdin.filterKeys { it !in input }
		val fromEnv = missing.mapValues { (name, schema) -> if (schema.isSecret()) System.getenv(envName(name)) else null }
		val fromStream = missing.filterKeys { fromEnv[it] == null }
		if (fromStream.size > 1) {
			throw UsageException("Only one of ${fromStream.keys.joinToString { kebabCase(it) }} can come from stdin; give the others as options")
		}
		return JsonObject(input + missing.mapValues { (name, schema) ->
			JsonPrimitive(fromEnv[name] ?: readStdin(name, schema.isSecret(), io))
		})
	}

	private fun JsonElement.isSecret() = jsonObject[STDIN_KEY] == JsonPrimitive(SECRET)

	/** A secret at a terminal is prompted for and read without echo; otherwise stdin is read to its end. */
	private fun readStdin(name: String, secret: Boolean, io: CliIo): String {
		val console = System.console()
		if (secret && console != null) {
			return console.readPassword("%s: ", kebabCase(name).replaceFirstChar { it.uppercase() })?.concatToString().orEmpty()
		}
		return io.stdin.readUtf8().removeSuffix("\n").removeSuffix("\r")
	}

	private fun buildInput(op: Operation<*, *>, values: Map<String, List<String?>>): JsonObject {
		val properties = jsonSchema(op.input.descriptor)["properties"]?.jsonObject ?: JsonObject(emptyMap())
		return JsonObject(
			values.mapValues { (name, given) ->
				val schema = properties[name]?.jsonObject?.nonNull()
					?: throw UsageException("'${op.name}' has no option --${kebabCase(name)}. See 'hammer ${op.name.replace('.', ' ')} --help'.")
				if (schema.type() == "array") {
					JsonArray(given.map { convert(name, it, schema["items"]!!.jsonObject.nonNull()) })
				} else {
					if (given.size > 1) throw UsageException("--${kebabCase(name)} given more than once")
					convert(name, given.single(), schema)
				}
			}
		)
	}

	private fun convert(name: String, raw: String?, schema: JsonObject): JsonElement {
		val option = "--${kebabCase(name)}"
		return when (schema.type()) {
			"boolean" -> when (raw) {
				null, "true" -> JsonPrimitive(true)
				"false" -> JsonPrimitive(false)
				else -> throw UsageException("$option is true or false")
			}

			"integer" -> JsonPrimitive(raw?.toLongOrNull() ?: throw UsageException("$option needs a whole number"))
			"number" -> JsonPrimitive(raw?.toDoubleOrNull() ?: throw UsageException("$option needs a number"))
			"string" -> JsonPrimitive(raw ?: throw UsageException("$option needs a value"))
			else -> parseJson(raw ?: throw UsageException("$option needs a JSON value"))
		}
	}

	/** Prints the output as JSON, or with [out], writes its one binary field to that file (or stdout for `-`). */
	private fun writeOutput(op: Operation<*, *>, output: JsonObject, out: String?, io: CliIo) {
		if (out == null) {
			io.stdout.writeUtf8(pretty.encodeToString(JsonElement.serializer(), output) + "\n")
			return
		}
		val properties = jsonSchema(op.output.descriptor)["properties"]?.jsonObject ?: JsonObject(emptyMap())
		val binary = properties.filterValues { it.jsonObject["contentEncoding"] == JsonPrimitive("base64") }.keys
		val field = binary.singleOrNull() ?: throw UsageException("'${op.name}' has no file output for --$OUT_OPTION")
		val bytes = Base64.decode(output.getValue(field).jsonPrimitive.content)
		if (out == STDOUT) {
			io.stdout.write(bytes)
		} else {
			File(out).writeBytes(bytes)
			io.stdout.writeUtf8(pretty.encodeToString(JsonElement.serializer(), JsonObject(output - field)) + "\n")
		}
	}

	private fun help(args: List<String>, registry: OperationRegistry, commands: List<CliCommand>, io: CliIo): Int {
		if (args.isNotEmpty()) {
			val op = registry.find(args.joinToString(".")) ?: throw UsageException("Unknown command '${args.joinToString(" ")}'")
			io.stdout.writeUtf8(operationHelp(op))
			return EXIT_OK
		}
		val rows = registry.operations.map { it.name.replace('.', ' ') to it.description } +
			commands.map { it.name to it.help }
		val width = rows.maxOf { it.first.length }
		io.stdout.writeUtf8(
			buildString {
				appendLine("Usage: hammer <command> [--option value]...")
				appendLine("Output is JSON. 'hammer <command> --help' lists a command's options.")
				appendLine()
				rows.forEach { (name, description) -> appendLine("  ${name.padEnd(width)}  $description") }
			}
		)
		return EXIT_OK
	}

	private fun operationHelp(op: Operation<*, *>): String {
		val schema = jsonSchema(op.input.descriptor)
		val required = schema["required"]?.jsonArray?.map { it.jsonPrimitive.content }.orEmpty().toSet()
		val properties = schema["properties"]?.jsonObject.orEmpty()
		return buildString {
			appendLine("hammer ${op.name.replace('.', ' ')}: ${op.description}")
			if (properties.isNotEmpty()) {
				appendLine()
				properties.forEach { (name, property) ->
					val nonNull = property.jsonObject.nonNull()
					val repeated = nonNull.type() == "array"
					val value = if (repeated) nonNull["items"]!!.jsonObject.nonNull() else nonNull
					val kind = value["enum"]?.jsonArray?.joinToString("|") { it.jsonPrimitive.content } ?: value.type()
					val repeat = if (repeated) ", repeatable" else ""
					val need = if (name in required) " (required)" else ""
					when (property.jsonObject[STDIN_KEY]) {
						JsonPrimitive(SECRET) -> appendLine("  <$name> from stdin or ${envName(name)}")
						null -> appendLine("  --${kebabCase(name)} <$kind>$repeat$need")
						else -> appendLine("  --${kebabCase(name)} <$kind>$repeat, or stdin")
					}
				}
			}
			appendLine()
			appendLine("  --$JSON_OPTION <object>  The whole input as JSON, instead of options")
			if (jsonSchema(op.output.descriptor).toString().contains("base64")) {
				appendLine("  --$OUT_OPTION <file>      Write the file to <file>, or - for stdout")
			}
		}
	}

	/** Runs [operation] in the open app when there is one, else headless; returns its output and exit code. */
	private fun dispatch(plugins: PluginRegistry, operation: String, input: JsonElement): Pair<JsonElement, Int> =
		Forwarding.dispatch(Forwarding.socketPath(File(getConfigDirectory())), operation, input)
			?: runBlocking {
				HeadlessSession.run(plugins) {
					val output = it.dispatch(operation, input)
					output to it.exitCode(operation, output)
				}
			}

	private class HeadlessDispatcher(private val plugins: PluginRegistry) : Dispatcher {
		override suspend fun dispatch(operation: String, input: JsonElement): JsonElement =
			withContext(Dispatchers.IO) { dispatch(plugins, operation, input).first }

		/**
		 * From the registry alone, so it works while the app holds the writer lock. Schemas that only
		 * runtime values narrow, such as project.export's formats, are left open here.
		 */
		override suspend fun operations(): List<OperationDescriptor> = plugins.operationRegistry.operations.map { op ->
			OperationDescriptor(
				name = op.name,
				description = op.description,
				access = op.access,
				agentVisible = op.agentVisible,
				input = jsonSchema(op.input.descriptor),
				output = jsonSchema(op.output.descriptor),
			)
		}
	}

	private class UsageException(message: String) : Exception(message)

	private fun parseJson(raw: String): JsonElement = try {
		Json.parseToJsonElement(raw)
	} catch (e: SerializationException) {
		throw UsageException("Invalid JSON: ${e.message}")
	}

	/** The schema of a nullable field without its null alternative. */
	private fun JsonObject.nonNull(): JsonObject =
		this["anyOf"]?.jsonArray?.map { it.jsonObject }?.firstOrNull { it["type"] != JsonPrimitive("null") } ?: this

	private fun JsonObject.type(): String? = (this["type"] as? JsonPrimitive)?.content ?: if (this["\$ref"] != null) "object" else null

	private fun camelCase(option: String) = option.split('-').mapIndexed { i, part ->
		if (i == 0) part else part.replaceFirstChar { it.uppercase() }
	}.joinToString("")

	/** Where a secret stdin field can come from instead, for scripts: `HAMMER_PASSWORD` for `password`. */
	private fun envName(field: String) = "HAMMER_" + kebabCase(field).replace('-', '_').uppercase()

	private fun kebabCase(field: String) = field.replace(Regex("([a-z0-9])([A-Z])"), "$1-$2").lowercase()

	private fun systemIo() = CliIo(
		stdin = System.`in`.source().buffer(),
		stdout = System.out.sink().buffer(),
		stderr = System.err.sink().buffer(),
	)

	private const val HELP = "help"
	private const val JSON_OPTION = "json"
	private const val OUT_OPTION = "out"
	private const val STDOUT = "-"
	private const val SECRET = "secret"
}
