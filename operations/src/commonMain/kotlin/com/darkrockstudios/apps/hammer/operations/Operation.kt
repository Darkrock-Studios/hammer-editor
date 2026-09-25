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
	/** Dotted, e.g. `scene.read`. Plugin operations are prefixed with the plugin id. */
	val name: String

	/** English. Used for CLI help and agent tool descriptions. */
	val description: String
	val input: KSerializer<I>
	val output: KSerializer<O>
	val access: Access

	/** Safe to offer to automated agents. The registry rejects Destructive operations that set it. */
	val agentVisible: Boolean get() = false

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

/** Input of an operation that takes none; `{}` in JSON. */
@Serializable
data object NoInput

inline fun <reified I, reified O> operation(
	name: String,
	description: String,
	access: Access,
	agentVisible: Boolean = false,
	noinline run: suspend OperationContext.(I) -> O,
): Operation<I, O> = LambdaOperation(
	name = name,
	description = description,
	input = serializer(),
	output = serializer(),
	access = access,
	agentVisible = agentVisible,
	block = run,
)

@PublishedApi
internal class LambdaOperation<I, O>(
	override val name: String,
	override val description: String,
	override val input: KSerializer<I>,
	override val output: KSerializer<O>,
	override val access: Access,
	override val agentVisible: Boolean,
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

fun notFound(message: String): Nothing = throw OperationException(OperationException.Kind.NotFound, message)
fun invalidInput(message: String): Nothing = throw OperationException(OperationException.Kind.InvalidInput, message)
