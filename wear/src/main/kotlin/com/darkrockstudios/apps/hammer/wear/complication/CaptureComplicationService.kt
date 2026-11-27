package com.darkrockstudios.apps.hammer.wear.complication

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.MonochromaticImage
import androidx.wear.watchface.complications.data.MonochromaticImageComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import com.darkrockstudios.apps.hammer.wear.CaptureActivity
import com.darkrockstudios.apps.hammer.wear.MainActivity
import com.darkrockstudios.apps.hammer.wear.R
import com.darkrockstudios.apps.hammer.wear.components.capture.Capture
import com.darkrockstudios.apps.hammer.wear.data.CaptureTileState
import com.darkrockstudios.apps.hammer.wear.data.CaptureTileStateUseCase
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/** A watch face shortcut into dictating a note, showing what is still waiting to sync. */
class CaptureComplicationService : SuspendingComplicationDataSourceService(), KoinComponent {

	private val tileState: CaptureTileStateUseCase by inject()

	override fun getPreviewData(type: ComplicationType): ComplicationData? =
		complicationData(type, CaptureTileState(pending = 2))

	override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
		val state = try {
			tileState.load()
		} catch (e: CancellationException) {
			throw e
		} catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
			Napier.e("Failed to read the capture complication's state", e)
			CaptureTileState()
		}
		return complicationData(request.complicationType, state)
	}

	private fun complicationData(type: ComplicationType, state: CaptureTileState): ComplicationData? {
		val description = PlainComplicationText.Builder(getString(R.string.complication_description)).build()
		return when (type) {
			ComplicationType.SHORT_TEXT -> ShortTextComplicationData.Builder(
				text = PlainComplicationText.Builder(shortText(state)).build(),
				contentDescription = description,
			)
				.setMonochromaticImage(icon())
				.setTapAction(tapIntent(state))
				.build()

			ComplicationType.MONOCHROMATIC_IMAGE -> MonochromaticImageComplicationData.Builder(
				monochromaticImage = icon(),
				contentDescription = description,
			)
				.setTapAction(tapIntent(state))
				.build()

			else -> null
		}
	}

	/** The count is the useful number; the label is the fallback when there is nothing waiting. */
	private fun shortText(state: CaptureTileState): String {
		if (!state.signedIn) return getString(R.string.sign_in_button)
		val pending = state.pending
		return if (pending != null && pending > 0) pending.toString() else getString(R.string.tile_note)
	}

	private fun icon() = MonochromaticImage.Builder(
		Icon.createWithResource(this, R.drawable.ic_launcher_monochrome)
	).build()

	/** Capture has nowhere to go without an account, so a signed out tap opens onboarding instead. */
	private fun tapIntent(state: CaptureTileState): PendingIntent = PendingIntent.getActivity(
		this,
		0,
		if (state.signedIn) {
			CaptureActivity.intent(this, Capture.Mode.Note)
		} else {
			Intent(this, MainActivity::class.java)
		},
		PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
	)
}
