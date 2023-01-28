package com.darkrockstudios.apps.hammer.common.data.drafts

import com.arkivanov.essenty.parcelable.Parcelable
import com.arkivanov.essenty.parcelable.Parcelize
import com.arkivanov.essenty.parcelable.TypeParceler
import com.darkrockstudios.apps.hammer.common.parcelize.InstantParceler
import kotlinx.datetime.Instant

@Parcelize
@TypeParceler<Instant, InstantParceler>()
data class DraftDef(
    val sceneId: Int,
    val draftTimestamp: Instant,
    val draftName: String
) : Parcelable