package com.darkrockstudios.apps.hammer.common.parcelize

import android.os.Parcel
import com.arkivanov.essenty.parcelable.Parceler
import dev.icerock.moko.resources.StringResource
import kotlinx.datetime.Instant

internal actual object InstantParceler : Parceler<Instant> {
	override fun create(parcel: Parcel): Instant =
		Instant.fromEpochSeconds(parcel.readLong())

	override fun Instant.write(parcel: Parcel, flags: Int) {
		parcel.writeLong(epochSeconds)
	}
}

internal actual object StringResourceParceler : Parceler<StringResource?> {
	override fun create(parcel: Parcel): StringResource = StringResource(parcel.readInt())

	override fun StringResource?.write(parcel: Parcel, flags: Int) {
		if (this != null) {
			parcel.writeInt(parcel.readInt())
		}
	}
}