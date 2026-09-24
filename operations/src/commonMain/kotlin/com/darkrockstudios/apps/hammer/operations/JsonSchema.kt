package com.darkrockstudios.apps.hammer.operations

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.PolymorphicKind
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.descriptors.elementDescriptors
import kotlinx.serialization.descriptors.elementNames
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/** Set on a [FromStdin] field's schema: `text`, or `secret` for one never taken as an option. */
const val STDIN_KEY = "x-hammer-stdin"

/** JSON Schema for what [OperationJson] reads and writes for [descriptor]. Recursive types go in `$defs`. */
fun jsonSchema(descriptor: SerialDescriptor): JsonObject {
	val builder = SchemaBuilder()
	val root = builder.schema(descriptor)
	if (builder.defs.isEmpty()) return root
	return JsonObject(root + ("\$defs" to JsonObject(builder.defs)))
}

@OptIn(ExperimentalSerializationApi::class)
private class SchemaBuilder {
	val defs = mutableMapOf<String, JsonObject>()
	private val expanding = mutableSetOf<String>()
	private val recursive = mutableSetOf<String>()

	fun schema(descriptor: SerialDescriptor): JsonObject {
		val schema = nonNullSchema(descriptor)
		return if (descriptor.isNullable) {
			JsonObject(mapOf("anyOf" to JsonArray(listOf(schema, typeOf("null")))))
		} else {
			schema
		}
	}

	private fun nonNullSchema(descriptor: SerialDescriptor): JsonObject {
		if (descriptor.serialName.removeSuffix("?") == Base64Bytes.SERIAL_NAME) {
			return buildJsonObject {
				put("type", "string")
				put("contentEncoding", "base64")
			}
		}
		if (descriptor.isInline) return schema(descriptor.getElementDescriptor(0))

		return when (val kind = descriptor.kind) {
			PrimitiveKind.STRING, PrimitiveKind.CHAR -> typeOf("string")
			PrimitiveKind.BOOLEAN -> typeOf("boolean")
			PrimitiveKind.BYTE, PrimitiveKind.SHORT, PrimitiveKind.INT, PrimitiveKind.LONG -> typeOf("integer")
			PrimitiveKind.FLOAT, PrimitiveKind.DOUBLE -> typeOf("number")
			SerialKind.ENUM -> buildJsonObject {
				put("type", "string")
				putJsonArray("enum") { descriptor.elementNames.forEach { add(JsonPrimitive(it)) } }
			}

			StructureKind.LIST -> buildJsonObject {
				put("type", "array")
				put("items", schema(descriptor.getElementDescriptor(0)))
			}

			StructureKind.MAP -> buildJsonObject {
				put("type", "object")
				put("additionalProperties", schema(descriptor.getElementDescriptor(1)))
			}

			StructureKind.CLASS, StructureKind.OBJECT -> classSchema(descriptor)
			is PolymorphicKind, SerialKind.CONTEXTUAL -> JsonObject(emptyMap())
		}
	}

	private fun classSchema(descriptor: SerialDescriptor): JsonObject {
		val name = descriptor.serialName.removeSuffix("?")
		if (name in expanding) {
			recursive += name
			return ref(name)
		}

		expanding += name
		val schema = buildJsonObject {
			put("type", "object")
			putJsonObject("properties") {
				descriptor.elementNames.zip(descriptor.elementDescriptors.toList()).forEachIndexed { index, (field, element) ->
					val stdin = descriptor.getElementAnnotations(index).filterIsInstance<FromStdin>().firstOrNull()
					val fieldSchema = schema(element)
					put(field, if (stdin == null) fieldSchema else JsonObject(fieldSchema + (STDIN_KEY to JsonPrimitive(stdin.kind))))
				}
			}
			val required = (0 until descriptor.elementsCount)
				.filterNot { descriptor.isElementOptional(it) }
				.map { descriptor.getElementName(it) }
			if (required.isNotEmpty()) putJsonArray("required") { required.forEach { add(JsonPrimitive(it)) } }
			put("additionalProperties", false)
		}
		expanding -= name

		if (name !in recursive) return schema
		defs[name] = schema
		return ref(name)
	}

	private val FromStdin.kind get() = if (secret) "secret" else "text"

	private fun ref(name: String) = JsonObject(mapOf("\$ref" to JsonPrimitive("#/\$defs/$name")))

	private fun typeOf(type: String) = JsonObject(mapOf("type" to JsonPrimitive(type)))
}
