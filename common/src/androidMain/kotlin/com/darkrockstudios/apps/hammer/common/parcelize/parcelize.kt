package com.darkrockstudios.apps.hammer.common.parcelize

import android.os.Parcel
import com.arkivanov.essenty.parcelable.Parceler
import kotlinx.datetime.Instant

internal actual object InstantParceler : Parceler<Instant> {
	override fun create(parcel: Parcel): Instant =
		Instant.fromEpochSeconds(parcel.readLong())

	override fun Instant.write(parcel: Parcel, flags: Int) {
		parcel.writeLong(epochSeconds)
	}
}