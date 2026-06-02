package com.darkrockstudios.apps.hammer

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/** Matches a config string to an enum constant by serial name, ignoring case. */
abstract class CaseInsensitiveEnumSerializer<T : Enum<T>>(
	serialName: String,
	private val entries: Array<T>,
	private val serialNameOf: (T) -> String,
) : KSerializer<T> {
	override val descriptor: SerialDescriptor =
		PrimitiveSerialDescriptor(serialName, PrimitiveKind.STRING)

	override fun serialize(encoder: Encoder, value: T) = encoder.encodeString(serialNameOf(value))

	override fun deserialize(decoder: Decoder): T {
		val raw = decoder.decodeString()
		return entries.firstOrNull { serialNameOf(it).equals(raw, ignoreCase = true) }
			?: throw SerializationException(
				"'$raw' is not among valid ${descriptor.serialName} values: " +
					entries.joinToString { serialNameOf(it) }
			)
	}
}
