package com.darkrockstudios.apps.hammer.wear.tile

import android.content.ComponentName
import android.content.Context
import androidx.wear.tiles.TileService
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import com.darkrockstudios.apps.hammer.wear.complication.CaptureComplicationService
import io.github.aakira.napier.Napier

/**
 * The tile and complication are cached by the system and only re-read on their own slow cadence, so
 * anything that changes the pending count has to say so or the watch face keeps showing a stale one.
 */
interface CaptureSurfaceUpdater {
	fun refresh()
}

class WearCaptureSurfaceUpdater(private val context: Context) : CaptureSurfaceUpdater {

	override fun refresh() {
		// Refreshing is only ever cosmetic, so it must never take down the capture that triggered it.
		try {
			TileService.getUpdater(context).requestUpdate(CaptureTileService::class.java)
			ComplicationDataSourceUpdateRequester
				.create(context, ComponentName(context, CaptureComplicationService::class.java))
				.requestUpdateAll()
		} catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
			Napier.w("Could not refresh the watch face surfaces", e)
		}
	}
}
