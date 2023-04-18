package com.darkrockstudios.apps.hammer.android.widgets

import android.content.Context
import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.*
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import com.darkrockstudios.apps.hammer.android.R
import io.github.aakira.napier.Napier

class AddNoteWidget : GlanceAppWidget() {
	@Composable
	override fun Content() {
		Box(
			modifier = GlanceModifier
				.fillMaxSize()
				.background(
					day = Color.White,
					night = Color.DarkGray
				)
				.appWidgetBackground()
				.cornerRadius(16.dp)
				.padding(8.dp)
				.clickable(
					onClick = actionRunCallback<AddNoteClickAction>()
				)
		) {
			Column {
				Image(
					ImageProvider(resId = R.drawable.ic_add_note),
					contentDescription = "Add Note",
					modifier = GlanceModifier.fillMaxSize().clickable(
						onClick = actionRunCallback<AddNoteClickAction>()
					)
				)
			}
		}
	}
}

class AddNoteWidgetReceiver : GlanceAppWidgetReceiver() {
	override val glanceAppWidget: GlanceAppWidget = AddNoteWidget()
}

class AddNoteClickAction : ActionCallback {
	override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
		Napier.d { "Add Note widget tapped" }

		val intent = Intent(context, AddNoteActivity::class.java)
			.setFlags(FLAG_ACTIVITY_NEW_TASK)
			.putExtra(AddNoteActivity.EXTRA_PROJECT_NAME, "Alice In Wonderland")
		context.startActivity(intent)
	}
}