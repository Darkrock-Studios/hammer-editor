package com.darkrockstudios.apps.hammer.base.timeline

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * A point on a story calendar, as the filled rungs of its unit chain, e.g. `{year=97, day=210}`.
 * Which keys are allowed and in what ranges depends on the calendar; see [StoryCalendar.validate].
 */
@Serializable(with = StoryDateSerializer::class)
data class StoryDate(val rungs: Map<String, Long>) {
	init {
		require(rungs.isNotEmpty()) { "A StoryDate needs at least one rung" }
	}

	operator fun get(unit: String): Long? = rungs[unit]
}

/** Serialises as the bare map so JSON is `{"year":97,"day":210}` and TOML an inline table. */
object StoryDateSerializer : KSerializer<StoryDate> {
	private val delegate = MapSerializer(String.serializer(), Long.serializer())

	override val descriptor: SerialDescriptor = delegate.descriptor

	override fun serialize(encoder: Encoder, value: StoryDate) {
		encoder.encodeSerializableValue(delegate, value.rungs)
	}

	override fun deserialize(decoder: Decoder): StoryDate =
		StoryDate(decoder.decodeSerializableValue(delegate))
}
