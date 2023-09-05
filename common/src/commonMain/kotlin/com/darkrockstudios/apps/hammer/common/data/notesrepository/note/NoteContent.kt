package com.darkrockstudios.apps.hammer.common.data.notesrepository.note

import com.arkivanov.essenty.parcelable.Parcelable
import com.arkivanov.essenty.parcelable.Parcelize
import com.arkivanov.essenty.parcelable.TypeParceler
import com.darkrockstudios.apps.hammer.common.parcelize.InstantParceler
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@TypeParceler<Instant, InstantParceler>()
@Parcelize
@Serializable
data class NoteContent(
	val id: Int,
	val created: Instant,
	val content: String
) : Parcelable