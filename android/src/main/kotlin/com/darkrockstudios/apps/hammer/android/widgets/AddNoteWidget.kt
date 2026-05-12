package com.darkrockstudios.apps.hammer.android.widgets

import android.content.Context
import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import io.github.aakira.napier.Napier

class AddNoteWidget : GlanceAppWidget() {

	override suspend fun provideGlance(context: Context, id: GlanceId) {
		val glanceAppWidgetManager = GlanceAppWidgetManager(context)
		provideContent {
			val widgetId = remember { glanceAppWidgetManager.getAppWidgetId(id) }
			val data by context.widgetConfigDataStore.data.collectAsState(initial = null)
			val projectName = remember(data) { data?.getWidgetConfig(widgetId) }
			val accentHex = remember(data) { data?.getWidgetAccent(widgetId) }

			AddNoteWidgetContent(
				projectName = projectName,
				accentHex = accentHex,
				onClick = getAddNoteActionCallback(projectName),
			)
		}
	}

	private fun getAddNoteActionCallback(projectName: String?): Action {
		return actionRunCallback<AddNoteClickAction>(
			actionParametersOf(AddNoteActionParameterKey to (projectName ?: ""))
		)
	}
}

@androidx.compose.runtime.Composable
private fun AddNoteWidgetContent(
	projectName: String?,
	accentHex: String?,
	onClick: Action,
) {
	val accent = remember(accentHex) { parseAccent(accentHex) }
	val num = remember(projectName) { stableProjectNumber(projectName) }
	val tag = remember(projectName) { projectTag(projectName) }

	Box(
		modifier = GlanceModifier
			.fillMaxSize()
			.appWidgetBackground()
			.cornerRadius(20.dp)
			.background(widgetColor { it.surfaceContainerLow })
			.clickable(onClick)
	) {
		Row(modifier = GlanceModifier.fillMaxSize()) {
			Box(
				modifier = GlanceModifier
					.width(3.dp)
					.fillMaxHeight()
					.background(singleWidgetColor(accent))
			) {}

			Column(
				modifier = GlanceModifier
					.defaultWeight()
					.fillMaxHeight()
					.padding(horizontal = 8.dp, vertical = 6.dp),
			) {
				Row(
					modifier = GlanceModifier.fillMaxWidth(),
					verticalAlignment = Alignment.Vertical.Top,
				) {
					Text(
						text = "§ $num",
						style = monoMicroStyle(widgetColor { it.onSurfaceVariant }),
					)
					Box(modifier = GlanceModifier.defaultWeight()) {}
					Text(
						text = tag,
						style = monoMicroStyle(widgetColor { it.onSurfaceMuted }),
					)
				}

				Box(
					modifier = GlanceModifier.defaultWeight().fillMaxWidth(),
					contentAlignment = Alignment.Center,
				) {
					Text(
						text = "＋",
						style = TextStyle(
							color = widgetColor { it.onSurface },
							fontSize = 34.sp,
							fontWeight = FontWeight.Normal,
						),
					)
				}

				Row(
					modifier = GlanceModifier.fillMaxWidth(),
					verticalAlignment = Alignment.Vertical.Bottom,
				) {
					Text(
						text = "NOTE",
						style = monoLabelStyle(widgetColor { it.onSurface }),
					)
					Box(modifier = GlanceModifier.defaultWeight()) {}
					Text(
						text = "↵",
						style = monoLabelStyle(
							color = widgetColor { it.onSurfaceDim },
							size = 9.sp,
						),
					)
				}
			}
		}
	}
}

private const val ACTION_KEY_PROJECT_NAME = "project_name"
private val AddNoteActionParameterKey = ActionParameters.Key<String>(ACTION_KEY_PROJECT_NAME)

class AddNoteWidgetReceiver : GlanceAppWidgetReceiver() {
	override val glanceAppWidget: GlanceAppWidget = AddNoteWidget()
}

class AddNoteClickAction : ActionCallback {
	override suspend fun onAction(
		context: Context,
		glanceId: GlanceId,
		parameters: ActionParameters
	) {
		val projectName = parameters[AddNoteActionParameterKey]

		Napier.d { "Add Note widget tapped for project: `$projectName`" }

		val intent = Intent(context, AddNoteActivity::class.java)
			.setFlags(FLAG_ACTIVITY_NEW_TASK)
			.putExtra(AddNoteActivity.EXTRA_PROJECT_NAME, projectName)
		context.startActivity(intent)
	}
}
