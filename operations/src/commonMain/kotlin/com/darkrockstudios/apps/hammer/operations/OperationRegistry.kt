package com.darkrockstudios.apps.hammer.operations

import kotlinx.serialization.json.JsonElement

/** Every operation in this process, core and plugin. The one place front ends dispatch through. */
class OperationRegistry(operations: List<Operation<*, *>>, projects: ProjectResolver) {

	val operations: List<Operation<*, *>> = operations.sortedBy { it.name }

	private val byName: Map<String, Operation<*, *>> = this.operations.associateBy { it.name }

	private val context = OperationContext(projects, this)

	init {
		operations.forEach { op ->
			require(NAME.matches(op.name)) { "Invalid operation name '${op.name}'" }
			require(!(op.agentVisible && op.access == Access.Destructive)) {
				"Destructive operation '${op.name}' cannot be agent-visible"
			}
		}
		val duplicates = operations.groupBy { it.name }.filterValues { it.size > 1 }.keys
		require(duplicates.isEmpty()) { "Duplicate operation names: $duplicates" }
	}

	fun find(name: String): Operation<*, *>? = byName[name]

	suspend fun <I, O> run(operation: Operation<I, O>, input: I): O = operation.run(context, input)

	/** Runs [name] on JSON [input], as every front end does. */
	suspend fun dispatch(name: String, input: JsonElement): JsonElement {
		val op = find(name) ?: notFound("No operation named '$name'")
		return dispatch(op, input)
	}

	private suspend fun <I, O> dispatch(op: Operation<I, O>, input: JsonElement): JsonElement {
		val decoded = try {
			OperationJson.decodeFromJsonElement(op.input, input)
		} catch (e: IllegalArgumentException) {
			// SerializationException is one, and so is a value type's own parse failure, such as a bad date.
			invalidInput(e.message ?: "Invalid input for '${op.name}'")
		}
		return OperationJson.encodeToJsonElement(op.output, run(op, decoded))
	}

	private companion object {
		val NAME = Regex("[a-z0-9][a-z0-9_-]*(\\.[a-z0-9][a-z0-9_-]*)*")
	}
}
