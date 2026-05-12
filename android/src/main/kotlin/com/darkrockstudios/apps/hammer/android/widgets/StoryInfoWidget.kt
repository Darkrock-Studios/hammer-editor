package com.darkrockstudios.apps.hammer.android.widgets

import android.content.Context
import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.PreviewSizeMode
import androidx.glance.appwidget.SizeMode
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
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontStyle
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.darkrockstudios.apps.hammer.android.ProjectRootActivity
import com.darkrockstudios.apps.hammer.android.R
import com.darkrockstudios.apps.hammer.base.http.projectdata.WordCountGoal
import com.darkrockstudios.apps.hammer.common.data.projectdata.loadStoredProjectData
import com.darkrockstudios.apps.hammer.common.data.projectstatistics.ProjectStatisticsCacheReader
import com.darkrockstudios.apps.hammer.common.data.projectstatistics.deriveWritingStats
import com.darkrockstudios.apps.hammer.common.data.projectstatistics.parseDailyWordTotals
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import net.peanuuutz.tomlkt.Toml
import okio.FileSystem
import org.koin.java.KoinJavaComponent.getKoin

private val SIZE_STRIP = DpSize(200.dp, 80.dp)    // 3×1 / 4×1
private val SIZE_SMALL = DpSize(140.dp, 140.dp)   // 2×2
private val SIZE_MEDIUM = DpSize(200.dp, 140.dp)  // 3×2 / 4×2
private val SIZE_FULL = DpSize(200.dp, 220.dp)    // 3×3 and larger

class StoryInfoWidget : GlanceAppWidget() {

	override val sizeMode: SizeMode = SizeMode.Responsive(
		setOf(SIZE_STRIP, SIZE_SMALL, SIZE_MEDIUM, SIZE_FULL),
	)

	override val previewSizeMode: PreviewSizeMode =
		SizeMode.Responsive(setOf(SIZE_STRIP, SIZE_SMALL, SIZE_MEDIUM, SIZE_FULL))

	override suspend fun provideGlance(context: Context, id: GlanceId) {
		val widgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
		provideContent {
			val data by context.widgetConfigDataStore.data.collectAsState(initial = null)
			val projectName = remember(data) { data?.getWidgetConfig(widgetId).orEmpty() }
			val accentHex = remember(data) { data?.getWidgetAccent(widgetId) }
			val info by produceState<StoryInfo?>(initialValue = null, projectName, accentHex) {
				value = if (projectName.isBlank()) {
					null
				} else {
					withContext(Dispatchers.IO) { loadStoryInfo(projectName, accentHex) }
				}
			}
			StoryInfoContent(projectName, info)
		}
	}

	override suspend fun providePreview(context: Context, widgetCategory: Int) {
		provideContent {
			StoryInfoContent(projectName = SAMPLE_STORY.name, info = SAMPLE_STORY)
		}
	}
}

private val SAMPLE_STORY = StoryInfo(
	name = "Apophis",
	accentHex = "#5A66B5",
	authorName = "M. Reyes",
	totalWords = 81402,
	goal = WordCountGoal(WordCountGoal.Cadence.DAY, 1500),
	wordsToday = 1240,
	currentStreak = 21,
	weekWords = 5740,
	sparkline = listOf(800, 1100, 600, 0, 920, 1080, 1240),
)

class StoryInfoWidgetReceiver : GlanceAppWidgetReceiver() {
	override val glanceAppWidget: GlanceAppWidget = StoryInfoWidget()
}

@Immutable
private data class StoryInfo(
	val name: String,
	val accentHex: String?,
	val authorName: String?,
	val totalWords: Int,
	val goal: WordCountGoal?,
	val wordsToday: Int,
	val currentStreak: Int,
	val weekWords: Int,
	val sparkline: List<Int>,
)

private suspend fun loadStoryInfo(name: String, accentHex: String?): StoryInfo? {
	val koin = getKoin()
	val projects = koin.get<ProjectsRepository>()
	val statsReader = koin.get<ProjectStatisticsCacheReader>()
	val fs = koin.get<FileSystem>()
	val toml = koin.get<Toml>()

	val def = runCatching { projects.getProjectDefinition(name) }.getOrNull() ?: return null
	val stats = statsReader.loadStatistics(def)
	val storedData = runCatching { loadStoredProjectData(def, fs, toml).data }.getOrNull()

	val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
	val dateMap = parseDailyWordTotals(stats?.dailyWordTotals.orEmpty())
	val derived = deriveWritingStats(dateMap, today)
	val last7 = (0 until 7).map { i ->
		val day = today.minus(6 - i, DateTimeUnit.DAY)
		dateMap[day] ?: 0
	}

	return StoryInfo(
		name = def.name,
		accentHex = accentHex ?: storedData?.theme?.primary,
		authorName = storedData?.authorName?.takeIf { it.isNotBlank() },
		totalWords = stats?.totalWords ?: 0,
		goal = stats?.wordCountGoal ?: storedData?.wordCountGoal,
		wordsToday = derived.wordsToday,
		currentStreak = derived.currentStreak,
		weekWords = last7.sum(),
		sparkline = last7,
	)
}

@Composable
private fun StoryInfoContent(projectName: String, info: StoryInfo?) {
	val size = LocalSize.current
	if (projectName.isBlank()) {
		UnconfiguredCard()
		return
	}
	if (info == null) {
		LoadingCard()
		return
	}

	val isStrip = size.height < 110.dp
	if (isStrip) {
		StoryInfoStripUi(info)
	} else {
		StoryInfoFullUi(info, size.width, size.height)
	}
}

@Composable
private fun UnconfiguredCard() {
	val ctx = LocalContext.current
	Box(
		modifier = GlanceModifier
			.fillMaxSize()
			.appWidgetBackground()
			.cornerRadius(20.dp)
			.background(widgetColor { it.surfaceContainerLow })
			.clickable(actionStartActivity<StoryInfoWidgetConfigActivity>()),
		contentAlignment = Alignment.Center,
	) {
		Text(
			text = ctx.getString(R.string.story_info_widget_unconfigured),
			style = monoMicroStyle(widgetColor { it.onSurfaceMuted }),
		)
	}
}

@Composable
private fun LoadingCard() {
	val ctx = LocalContext.current
	Box(
		modifier = GlanceModifier
			.fillMaxSize()
			.appWidgetBackground()
			.cornerRadius(20.dp)
			.background(widgetColor { it.surfaceContainerLow }),
		contentAlignment = Alignment.Center,
	) {
		Text(
			text = ctx.getString(R.string.loading),
			style = monoMicroStyle(widgetColor { it.onSurfaceMuted }),
		)
	}
}

@Composable
private fun StoryInfoFullUi(
	info: StoryInfo,
	widthDp: androidx.compose.ui.unit.Dp,
	heightDp: androidx.compose.ui.unit.Dp,
) {
	val ctx = LocalContext.current
	val tiny = widthDp < 200.dp
	val hasAuthorSpace = heightDp >= 200.dp && widthDp >= 200.dp
	val hasSessions = heightDp >= 200.dp && widthDp >= 200.dp
	val hasActions = heightDp >= 180.dp && widthDp >= 200.dp
	val accent = singleWidgetColor(parseAccent(info.accentHex))
	val num = remember(info.name) { stableProjectNumber(info.name) }

	val goal = info.goal
	val periodValue = goal?.let {
		if (it.cadence == WordCountGoal.Cadence.DAY) info.wordsToday else info.weekWords
	} ?: 0
	val goalPct = if (goal != null && goal.count > 0) periodValue * 100 / goal.count else 0
	val cadenceLabel = goal?.let {
		when (it.cadence) {
			WordCountGoal.Cadence.DAY -> ctx.getString(R.string.story_info_widget_cadence_today)
			WordCountGoal.Cadence.WEEK -> ctx.getString(R.string.story_info_widget_cadence_this_week)
		}
	}

	Box(
		modifier = GlanceModifier
			.fillMaxSize()
			.appWidgetBackground()
			.cornerRadius(20.dp)
			.background(widgetColor { it.surfaceContainerLow })
			.clickable(openStoryAction(info.name)),
	) {
		Column(modifier = GlanceModifier.fillMaxSize()) {
			Box(
				modifier = GlanceModifier
					.fillMaxWidth()
					.height(3.dp)
					.background(accent),
			) {}

			Row(
				modifier = GlanceModifier
					.fillMaxWidth()
					.padding(horizontal = if (tiny) 12.dp else 14.dp, vertical = 6.dp),
				verticalAlignment = Alignment.Vertical.Bottom,
			) {
				Text(
					text = ctx.getString(R.string.story_info_widget_folio_header, num),
					style = monoMicroStyle(widgetColor { it.onSurfaceVariant }),
				)
			}

			Column(
				modifier = GlanceModifier
					.fillMaxWidth()
					.padding(horizontal = if (tiny) 12.dp else 14.dp),
			) {
				Text(
					text = info.name,
					maxLines = 1,
					style = TextStyle(
						color = widgetColor { it.onSurface },
						fontSize = if (tiny) 14.sp else 18.sp,
						fontWeight = FontWeight.Medium,
					),
				)
				if (hasAuthorSpace && info.authorName != null) {
					Text(
						text = ctx.getString(R.string.story_info_widget_byline, info.authorName),
						maxLines = 1,
						style = TextStyle(
							color = widgetColor { it.onSurfaceVariant },
							fontSize = 10.sp,
							fontStyle = FontStyle.Italic,
						),
					)
				}
			}

			Spacer(modifier = GlanceModifier.height(6.dp))

			Column(
				modifier = GlanceModifier
					.fillMaxWidth()
					.padding(horizontal = if (tiny) 12.dp else 14.dp),
			) {
				Row(modifier = GlanceModifier.fillMaxWidth()) {
					Text(
						text = ctx.getString(R.string.story_info_widget_words_label),
						style = monoMicroStyle(widgetColor { it.onSurfaceMuted }),
					)
					Spacer(modifier = GlanceModifier.defaultWeight())
					if (!tiny && goal != null) {
						Text(
							text = ctx.getString(
								R.string.story_info_widget_goal_summary,
								goalPct,
								formatWidgetWords(goal.count),
								cadenceLabel,
							),
							style = monoMicroStyle(widgetColor { it.onSurfaceVariant }),
						)
					}
				}
				Spacer(modifier = GlanceModifier.height(2.dp))
				Text(
					text = formatWidgetWords(info.totalWords),
					style = TextStyle(
						color = widgetColor { it.onSurface },
						fontSize = if (tiny) 26.sp else 32.sp,
						fontWeight = FontWeight.Normal,
					),
				)
				if (goal != null) {
					Spacer(modifier = GlanceModifier.height(8.dp))
					WidgetProgressBar(
						value = periodValue,
						max = goal.count,
						fillColor = accent,
						heightDp = if (tiny) 3 else 4,
					)
					if (!tiny) {
						Spacer(modifier = GlanceModifier.height(4.dp))
						Row(modifier = GlanceModifier.fillMaxWidth()) {
							Text(
								text = if (info.wordsToday > 0) {
									ctx.getString(
										R.string.story_info_widget_today_words,
										info.wordsToday,
									)
								} else {
									ctx.getString(R.string.story_info_widget_no_session_today)
								},
								style = monoMicroStyle(
									widgetColor {
										if (info.wordsToday > 0) it.onSurface else it.onSurfaceDim
									},
								),
							)
							Spacer(modifier = GlanceModifier.defaultWeight())
							if (info.currentStreak > 0) {
								Text(
									text = ctx.getString(
										R.string.story_info_widget_streak,
										info.currentStreak,
									),
									style = monoMicroStyle(widgetColor { it.onSurfaceVariant }),
								)
							}
						}
					}
				}
			}

			if (hasSessions) {
				Spacer(modifier = GlanceModifier.height(10.dp))
				Column(
					modifier = GlanceModifier
						.fillMaxWidth()
						.padding(horizontal = 14.dp),
				) {
					Row(modifier = GlanceModifier.fillMaxWidth()) {
						Text(
							text = ctx.getString(R.string.story_info_widget_sessions_header),
							style = monoMicroStyle(widgetColor { it.onSurfaceMuted }),
						)
						Spacer(modifier = GlanceModifier.defaultWeight())
						Text(
							text = ctx.getString(
								R.string.story_info_widget_week_total,
								formatWidgetWords(info.weekWords),
							),
							style = monoMicroStyle(widgetColor { it.onSurfaceMuted }),
						)
					}
					Spacer(modifier = GlanceModifier.height(4.dp))
					WidgetHairline(color = widgetColor { it.ruleSoft })
					Spacer(modifier = GlanceModifier.height(6.dp))
					WidgetSparkline(
						values = info.sparkline,
						fillColor = accent,
						heightDp = 24,
					)
				}
			}

			Spacer(modifier = GlanceModifier.defaultWeight())

			if (hasActions) {
				WidgetHairline(color = widgetColor { it.rule })
				Row(modifier = GlanceModifier.fillMaxWidth().height(40.dp)) {
					ActionTile(
						label = ctx.getString(R.string.story_info_widget_action_open),
						glyph = "↗",
						onClick = openStoryAction(info.name),
						modifier = GlanceModifier.defaultWeight().fillMaxHeight(),
					)
					Box(
						modifier = GlanceModifier
							.width(1.dp)
							.fillMaxHeight()
							.background(widgetColor { it.ruleSoft }),
					) {}
					ActionTile(
						label = ctx.getString(R.string.story_info_widget_action_note),
						glyph = "＋",
						onClick = addStoryNoteAction(info.name),
						modifier = GlanceModifier.defaultWeight().fillMaxHeight(),
					)
				}
			}
		}
	}
}

@Composable
private fun ActionTile(
	label: String,
	glyph: String,
	onClick: Action,
	modifier: GlanceModifier = GlanceModifier,
) {
	Column(
		modifier = modifier.clickable(onClick),
		verticalAlignment = Alignment.Vertical.CenterVertically,
		horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
	) {
		Text(
			text = glyph,
			style = TextStyle(
				color = widgetColor { it.onSurface },
				fontSize = 14.sp,
				fontWeight = FontWeight.Normal,
			),
		)
		Text(
			text = label,
			style = monoMicroStyle(widgetColor { it.onSurface }),
		)
	}
}

@Composable
private fun StoryInfoStripUi(info: StoryInfo) {
	val ctx = LocalContext.current
	val accent = singleWidgetColor(parseAccent(info.accentHex))
	val num = remember(info.name) { stableProjectNumber(info.name) }
	val hasGoal = info.goal != null

	Box(
		modifier = GlanceModifier
			.fillMaxSize()
			.appWidgetBackground()
			.cornerRadius(20.dp)
			.background(widgetColor { it.surfaceContainerLow })
			.clickable(openStoryAction(info.name)),
	) {
		Row(modifier = GlanceModifier.fillMaxSize()) {
			Box(
				modifier = GlanceModifier
					.width(3.dp)
					.fillMaxHeight()
					.background(accent),
			) {}

			Column(
				modifier = GlanceModifier
					.defaultWeight()
					.fillMaxHeight()
					.padding(horizontal = 10.dp, vertical = 8.dp),
			) {
				Row(
					modifier = GlanceModifier.fillMaxWidth(),
					verticalAlignment = Alignment.Vertical.CenterVertically,
				) {
					Text(
						text = ctx.getString(R.string.story_info_widget_strip_folio, num),
						style = monoMicroStyle(widgetColor { it.onSurfaceVariant }),
					)
					Spacer(modifier = GlanceModifier.width(6.dp))
					Text(
						text = info.name,
						maxLines = 1,
						style = TextStyle(
							color = widgetColor { it.onSurface },
							fontSize = 14.sp,
							fontWeight = FontWeight.Medium,
						),
					)
				}
				Spacer(modifier = GlanceModifier.height(2.dp))
				Row(
					modifier = GlanceModifier.fillMaxWidth(),
					verticalAlignment = Alignment.Vertical.Bottom,
				) {
					Text(
						text = formatWidgetWords(info.totalWords),
						style = TextStyle(
							color = widgetColor { it.onSurface },
							fontSize = 18.sp,
							fontWeight = FontWeight.Normal,
						),
					)
					Spacer(modifier = GlanceModifier.width(6.dp))
					Text(
						text = ctx.getString(R.string.story_info_widget_strip_words_unit),
						style = monoMicroStyle(widgetColor { it.onSurfaceMuted }),
					)
					Spacer(modifier = GlanceModifier.defaultWeight())
					if (info.wordsToday > 0) {
						Text(
							text = ctx.getString(
								R.string.story_info_widget_today_words,
								info.wordsToday,
							),
							style = monoMicroStyle(widgetColor { it.onSurfaceVariant }),
						)
					}
				}
				if (hasGoal) {
					Spacer(modifier = GlanceModifier.height(4.dp))
					val goal = info.goal
					val periodValue = if (goal.cadence == WordCountGoal.Cadence.DAY) {
						info.wordsToday
					} else {
						info.weekWords
					}
					WidgetProgressBar(
						value = periodValue,
						max = goal.count,
						fillColor = accent,
						heightDp = 3,
					)
				}
			}

			Box(
				modifier = GlanceModifier
					.width(1.dp)
					.fillMaxHeight()
					.background(widgetColor { it.ruleSoft }),
			) {}

			Column(
				modifier = GlanceModifier
					.width(56.dp)
					.fillMaxHeight()
					.clickable(openStoryAction(info.name)),
				verticalAlignment = Alignment.Vertical.CenterVertically,
				horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
			) {
				Text(
					text = "↗",
					style = TextStyle(
						color = widgetColor { it.onSurface },
						fontSize = 16.sp,
						fontWeight = FontWeight.Normal,
					),
				)
				Text(
					text = ctx.getString(R.string.story_info_widget_action_open),
					style = monoMicroStyle(widgetColor { it.onSurface }),
				)
			}
		}
	}
}

private const val ACTION_KEY_STORY_PROJECT = "story_project_name"
private val StoryProjectNameKey = ActionParameters.Key<String>(ACTION_KEY_STORY_PROJECT)

private fun openStoryAction(name: String): Action =
	actionRunCallback<OpenStoryClickAction>(
		actionParametersOf(StoryProjectNameKey to name),
	)

private fun addStoryNoteAction(name: String): Action =
	actionRunCallback<AddStoryNoteClickAction>(
		actionParametersOf(StoryProjectNameKey to name),
	)

class OpenStoryClickAction : ActionCallback {
	override suspend fun onAction(
		context: Context,
		glanceId: GlanceId,
		parameters: ActionParameters,
	) {
		val name = parameters[StoryProjectNameKey].orEmpty()
		if (name.isBlank()) return
		val def = getKoin().get<ProjectsRepository>().getProjectDefinition(name)

		Napier.d { "Story Info widget tapped: `$name`" }

		val intent = ProjectRootActivity.createIntent(context, def)
			.setFlags(FLAG_ACTIVITY_NEW_TASK)
		context.startActivity(intent)
	}
}

class AddStoryNoteClickAction : ActionCallback {
	override suspend fun onAction(
		context: Context,
		glanceId: GlanceId,
		parameters: ActionParameters,
	) {
		val name = parameters[StoryProjectNameKey].orEmpty()

		Napier.d { "Story Info widget add-note tapped: `$name`" }

		val intent = Intent(context, AddNoteActivity::class.java)
			.setFlags(FLAG_ACTIVITY_NEW_TASK)
			.putExtra(AddNoteActivity.EXTRA_PROJECT_NAME, name)
		context.startActivity(intent)
	}
}
