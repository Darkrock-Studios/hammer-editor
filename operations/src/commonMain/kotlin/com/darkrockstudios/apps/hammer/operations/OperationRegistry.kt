package com.darkrockstudios.apps.hammer.operations

import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.ProjectSyncGateRepository
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.serializer

/** Every operation in this process, core and plugin. The one place front ends dispatch through. */
class OperationRegistry(
	operations: List<Operation<*, *>>,
	private val projects: ProjectResolver,
	/** Looked up for each call, since a headless session restarts Koin between calls. */
	private val syncGate: () -> ProjectSyncGateRepository,
) {

	val operations: List<Operation<*, *>> = operations.sortedBy { it.name }

	private val byName: Map<String, Operation<*, *>> = this.operations.associateBy { it.name }

	private val context = OperationContext(projects, this)

	init {
		operations.forEach { op ->
			require(isValidName(op.name)) { "Invalid operation name '${op.name}'" }
		}
		val duplicates = operations.groupBy { it.name }.filterValues { it.size > 1 }.keys
		require(duplicates.isEmpty()) { "Duplicate operation names: $duplicates" }
	}

	fun find(name: String): Operation<*, *>? = byName[name]

	/**
	 * A write on project content keeps every project it names from syncing until it returns, and is
	 * refused as [OperationException.Kind.Busy] if one is syncing already.
	 */
	suspend fun <I, O> run(operation: Operation<I, O>, input: I): O {
		if (!operation.excludesSync) return operation.run(context, input)
		val writing = WritingProjects(projects, syncGate())
		return try {
			operation.run(OperationContext(writing, this), input)
		} finally {
			writing.release()
		}
	}

	// Account and plugin operations change no project's content. sync.run is itself a sync, and would
	// wait forever on a project it held.
	private val Operation<*, *>.excludesSync: Boolean
		get() = access != Access.Read && scope == OperationScope.Content

	/** Runs [name] on JSON [input], as every front end does. */
	suspend fun dispatch(name: String, input: JsonElement): JsonElement {
		val op = find(name) ?: notFound("No operation named '$name'")
		return dispatch(op, input)
	}

	/**
	 * Runs [name] through its JSON, as a front end would, and decodes the result: for plugins built
	 * on other operations, which then depend only on the public API.
	 */
	suspend inline fun <reified I, reified O> call(name: String, input: I): O =
		OperationJson.decodeFromJsonElement(serializer<O>(), dispatch(name, OperationJson.encodeToJsonElement(serializer<I>(), input)))

	/** [Operation.exitCode] for [output], as [dispatch] returned it for [name]. */
	fun exitCode(name: String, output: JsonElement): Int {
		val op = find(name) ?: notFound("No operation named '$name'")
		return exitCode(op, output)
	}

	private fun <O> exitCode(op: Operation<*, O>, output: JsonElement): Int =
		op.exitCode(OperationJson.decodeFromJsonElement(op.output, output))

	private suspend fun <I, O> dispatch(op: Operation<I, O>, input: JsonElement): JsonElement {
		val decoded = try {
			OperationJson.decodeFromJsonElement(op.input, input)
		} catch (e: IllegalArgumentException) {
			// SerializationException is one, and so is a value type's own parse failure, such as a bad date.
			invalidInput(e.message ?: "Invalid input for '${op.name}'")
		}
		return OperationJson.encodeToJsonElement(op.output, run(op, decoded))
	}

	companion object {
		private val NAME = Regex("[a-z0-9][a-z0-9_-]*(\\.[a-z0-9][a-z0-9_-]*)*")

		/** Dotted lowercase words: letters, digits, `-` and `_`. */
		fun isValidName(name: String): Boolean = NAME.matches(name)
	}
}
