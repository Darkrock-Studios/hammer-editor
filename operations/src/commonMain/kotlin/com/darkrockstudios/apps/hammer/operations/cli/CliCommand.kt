package com.darkrockstudios.apps.hammer.operations.cli

import com.darkrockstudios.apps.hammer.operations.core.OperationDescriptor
import kotlinx.serialization.json.JsonElement
import okio.BufferedSink
import okio.BufferedSource

/**
 * A top-level CLI command a plugin adds, such as `hammer mcp`.
 */
interface CliCommand {
	/** The word after `hammer`. Must not be the first word of any operation's name. */
	val name: String
	val help: String

	/** Runs until done and returns the exit code. Reaches Hammer only through [dispatcher], never Koin. */
	suspend fun run(args: List<String>, io: CliIo, dispatcher: Dispatcher): Int
}

class CliIo(val stdin: BufferedSource, val stdout: BufferedSink, val stderr: BufferedSink)

/** Runs operations exactly as separate CLI calls would. */
interface Dispatcher {
	/** Throws [com.darkrockstudios.apps.hammer.operations.OperationException] for a caller's mistake. */
	suspend fun dispatch(operation: String, input: JsonElement): JsonElement
	suspend fun operations(): List<OperationDescriptor>
}
