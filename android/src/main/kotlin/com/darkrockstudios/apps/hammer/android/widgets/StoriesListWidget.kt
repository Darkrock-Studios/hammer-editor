package com.darkrockstudios.apps.hammer.android.widgets

import android.content.Context
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.darkrockstudios.apps.hammer.android.ProjectRootActivity
import com.darkrockstudios.apps.hammer.android.ProjectSelectActivity
import com.darkrockstudios.apps.hammer.common.data.projectdata.loadStoredProjectData
import com.darkrockstudios.apps.hammer.common.data.projectmetadata.ProjectMetadataDatasource
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository
import com.darkrockstudios.apps.hammer.common.data.projectstatistics.ProjectStatisticsCacheReader
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.time.Instant
import net.peanuuutz.tomlkt.Toml
import okio.FileSystem
import org.koin.java.KoinJavaComponent.getKoin

class StoriesListWidget : GlanceAppWidget() {

	override suspend fun provideGlance(context: Context, id: GlanceId) {
		val rows = withContext(Dispatchers.IO) { loadRows() }
		provideContent { StoriesListWidgetContent(rows) }
	}
}

@Immutable
private data class StoryRow(
	val name: String,
	val path: String,
	val accentHex: String?,
	val words: Int?,
	val lastAccessed: Instant?,
)

private suspend fun loadRows(): List<StoryRow> {
	val koin = getKoin()
	val projects = koin.get<ProjectsRepository>()
	val stats = koin.get<ProjectStatisticsCacheReader>()
	val meta = koin.get<ProjectMetadataDatasource>()
	val fs = koin.get<FileSystem>()
	val toml = koin.get<Toml>()

	return projects.getProjects().map { def ->
		val lastAccessed = runCatching { meta.loadMetadata(def).info.lastAccessed }.getOrNull()
		val accentHex = runCatching {
			loadStoredProjectData(def, fs, toml).data.theme?.primary
		}.getOrNull()
		val words = stats.loadTotalWords(def)
		StoryRow(
			name = def.name,
			path = def.path.path,
			accentHex = accentHex,
			words = words,
			lastAccessed = lastAccessed,
		)
	}.sortedByDescending { it.lastAccessed }
}

@Composable
private fun StoriesListWidgetContent(rows: List<StoryRow>) {
	Box(
		modifier = GlanceModifier
			.fillMaxSize()
			.appWidgetBackground()
			.cornerRadius(20.dp)
			.background(widgetColor { it.surfaceContainerLow })
	) {
		Column(
			modifier = GlanceModifier
				.fillMaxSize()
				.padding(horizontal = 14.dp, vertical = 8.dp),
		) {
			Row(
				modifier = GlanceModifier
					.fillMaxWidth()
					.clickable(actionStartActivity<ProjectSelectActivity>()),
				verticalAlignment = Alignment.Vertical.Bottom,
			) {
				Text(
					text = "HAMMER · LIBRARY",
					style = monoMicroStyle(widgetColor { it.onSurfaceVariant }),
				)
				Box(modifier = GlanceModifier.defaultWeight()) {}
				Text(
					text = if (rows.isEmpty()) "—" else "${rows.size} STORIES",
					style = monoMicroStyle(widgetColor { it.onSurface }),
				)
			}

			Spacer(modifier = GlanceModifier.height(4.dp))
			Box(
				modifier = GlanceModifier
					.fillMaxWidth()
					.height(1.dp)
					.background(widgetColor { it.rule }),
			) {}

			Box(modifier = GlanceModifier.defaultWeight().fillMaxWidth()) {
				if (rows.isEmpty()) {
					Box(
						modifier = GlanceModifier
							.fillMaxSize()
							.clickable(actionStartActivity<ProjectSelectActivity>()),
						contentAlignment = Alignment.Center,
					) {
						Text(
							text = "No projects yet — tap to open",
							style = monoMicroStyle(widgetColor { it.onSurfaceMuted }),
						)
					}
				} else {
					LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
						items(rows, itemId = { it.path.hashCode().toLong() }) { row ->
							StoryRowUi(row)
						}
					}
				}
			}

			Row(
				modifier = GlanceModifier.fillMaxWidth(),
				verticalAlignment = Alignment.Vertical.Bottom,
			) {
				Text(
					text = "FOL. 00",
					style = monoMicroStyle(widgetColor { it.onSurfaceDim }),
				)
				Box(modifier = GlanceModifier.defaultWeight()) {}
				Text(
					text = "TAP TO OPEN",
					style = monoMicroStyle(widgetColor { it.onSurfaceDim }),
				)
			}
		}
	}
}

@Composable
private fun StoryRowUi(row: StoryRow) {
	val accent = parseAccent(row.accentHex)
	Column(
		modifier = GlanceModifier
			.fillMaxWidth()
			.clickable(openProjectAction(row.name)),
	) {
		Row(
			modifier = GlanceModifier
				.fillMaxWidth()
				.padding(vertical = 8.dp),
			verticalAlignment = Alignment.Vertical.CenterVertically,
		) {
			Box(
				modifier = GlanceModifier
					.width(3.dp)
					.height(28.dp)
					.background(singleWidgetColor(accent)),
			) {}
			Spacer(modifier = GlanceModifier.width(8.dp))
			Text(
				text = stableProjectNumber(row.name),
				style = monoMicroStyle(widgetColor { it.onSurfaceMuted }, size = 11.sp),
			)
			Spacer(modifier = GlanceModifier.width(8.dp))
			Box(modifier = GlanceModifier.defaultWeight()) {
				Text(
					text = row.name,
					style = titleStyle(),
					maxLines = 1,
				)
			}
			Spacer(modifier = GlanceModifier.width(8.dp))
			Text(
				text = formatWords(row.words),
				style = monoMicroStyle(widgetColor { it.onSurfaceMuted }, size = 10.sp),
			)
			Spacer(modifier = GlanceModifier.width(6.dp))
			Text(
				text = "›",
				style = monoLabelStyle(widgetColor { it.onSurfaceDim }, size = 12.sp),
			)
		}
		Box(
			modifier = GlanceModifier
				.fillMaxWidth()
				.height(1.dp)
				.background(widgetColor { it.ruleSoft }),
		) {}
	}
}

private fun titleStyle(): TextStyle = TextStyle(
	color = widgetColor { it.onSurface },
	fontSize = 13.sp,
	fontFamily = FontFamily.Serif,
	fontWeight = FontWeight.Medium,
)

private fun formatWords(words: Int?): String = when {
	words == null -> "— w"
	words >= 1000 -> "%.1fk w".format(words / 1000.0)
	else -> "$words w"
}

private const val ACTION_KEY_OPEN_PROJECT_NAME = "open_project_name"
private val OpenProjectNameKey = ActionParameters.Key<String>(ACTION_KEY_OPEN_PROJECT_NAME)

private fun openProjectAction(projectName: String): Action =
	actionRunCallback<OpenProjectClickAction>(
		actionParametersOf(OpenProjectNameKey to projectName),
	)

class StoriesListWidgetReceiver : GlanceAppWidgetReceiver() {
	override val glanceAppWidget: GlanceAppWidget = StoriesListWidget()
}

class OpenProjectClickAction : ActionCallback {
	override suspend fun onAction(
		context: Context,
		glanceId: GlanceId,
		parameters: ActionParameters,
	) {
		val name = parameters[OpenProjectNameKey].orEmpty()
		if (name.isBlank()) return

		val def = getKoin().get<ProjectsRepository>().getProjectDefinition(name)

		Napier.d { "Stories list widget tapped: `$name`" }

		val intent = ProjectRootActivity.createIntent(context, def)
			.setFlags(FLAG_ACTIVITY_NEW_TASK)
		context.startActivity(intent)
	}
}
