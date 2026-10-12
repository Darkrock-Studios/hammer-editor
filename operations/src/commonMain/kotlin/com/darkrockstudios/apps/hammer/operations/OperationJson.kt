package com.darkrockstudios.apps.hammer.operations

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlin.io.encoding.Base64

/** How every front end encodes operation input and output. Unknown input fields are an error, not ignored. */
val OperationJson = Json {
	encodeDefaults = true
}

/** Binary content (exports, images) as a base64 string. */
object Base64Bytes : KSerializer<ByteArray> {
	const val SERIAL_NAME = "com.darkrockstudios.apps.hammer.operations.Base64Bytes"

	override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor(SERIAL_NAME, PrimitiveKind.STRING)

	override fun serialize(encoder: Encoder, value: ByteArray) = encoder.encodeString(Base64.encode(value))

	override fun deserialize(decoder: Decoder): ByteArray = try {
		Base64.decode(decoder.decodeString())
	} catch (e: IllegalArgumentException) {
		throw SerializationException("Invalid base64: ${e.message}")
	}
}
