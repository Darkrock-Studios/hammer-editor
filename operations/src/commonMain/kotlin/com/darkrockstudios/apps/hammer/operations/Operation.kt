package com.darkrockstudios.apps.hammer.operations

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialInfo
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.serializer

/**
 * A named, typed unit of the public API. Names and input fields are append-only once shipped;
 * a rename is a new operation.
 */
interface Operation<I, O> {
	/** Dotted, e.g. `scene.read`. */
	val name: String

	/** English. Used for CLI help and by plugins that describe operations to others. */
	val description: String
	val input: KSerializer<I>
	val output: KSerializer<O>
	val access: Access

	val scope: OperationScope

	/** Override only when the input's valid values are known at runtime, such as registered export formats. */
	fun inputSchema(): JsonObject = jsonSchema(input.descriptor)

	/** For front ends that report success as a number, like the CLI: nonzero when [output] records a partial failure. */
	fun exitCode(output: O): Int = 0

	suspend fun run(context: OperationContext, input: I): O
}

@Serializable
enum class Access {
	@SerialName("read") Read,
	@SerialName("write") Write,
	@SerialName("destructive") Destructive,
}

/** What an operation reaches into. A plugin can be granted every Read or Write operation in a scope at once. */
@Serializable
enum class OperationScope {
	/** Projects and everything in them. */
	@SerialName("content") Content,

	/** The sync account: its credentials and the server. */
	@SerialName("account") Account,
}

/** Input of an operation that takes none; `{}` in JSON. */
@Serializable
data object NoInput

inline fun <reified I, reified O> operation(
	name: String,
	description: String,
	access: Access,
	scope: OperationScope,
	noinline run: suspend OperationContext.(I) -> O,
): Operation<I, O> = LambdaOperation(
	name = name,
	description = description,
	input = serializer(),
	output = serializer(),
	access = access,
	scope = scope,
	block = run,
)

@PublishedApi
internal class LambdaOperation<I, O>(
	override val name: String,
	override val description: String,
	override val input: KSerializer<I>,
	override val output: KSerializer<O>,
	override val access: Access,
	override val scope: OperationScope,
	private val block: suspend OperationContext.(I) -> O,
) : Operation<I, O> {
	override suspend fun run(context: OperationContext, input: I): O = context.block(input)
}

/** A failure the caller caused, reported to them as-is. Anything else thrown by an operation is a bug. */
class OperationException(val kind: Kind, message: String) : Exception(message) {
	enum class Kind {
		NotFound,
		InvalidInput,

		/** The sync server rejected the stored credentials; log in again. */
		Unauthorized,
	}
}

/**
 * Marks an input field front ends read from standard input rather than an option, such as a scene's
 * text. A [secret] field is never accepted as an option, so it cannot land in shell history.
 */
@OptIn(ExperimentalSerializationApi::class)
@SerialInfo
@Target(AnnotationTarget.PROPERTY)
annotation class FromStdin(val secret: Boolean = false)

/**
 * Marks input that changes a scene's text in place instead of through a draft: an operation's whole
 * input class, or one value of an enum field. Front ends that guard the manuscript, such as the MCP
 * plugin, can leave it out unless the writer allows live edits.
 */
@OptIn(ExperimentalSerializationApi::class)
@SerialInfo
@Target(AnnotationTarget.CLASS, AnnotationTarget.PROPERTY)
annotation class LiveEdit

fun notFound(message: String): Nothing = throw OperationException(OperationException.Kind.NotFound, message)
fun invalidInput(message: String): Nothing = throw OperationException(OperationException.Kind.InvalidInput, message)
