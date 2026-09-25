package com.darkrockstudios.apps.hammer.plugins.style

import com.darkrockstudios.apps.hammer.common.data.notesrepository.NotesRepository
import com.darkrockstudios.apps.hammer.operations.OperationRegistry
import com.darkrockstudios.apps.hammer.operations.core.Note
import com.darkrockstudios.apps.hammer.operations.core.NoteCreateInput
import kotlin.math.roundToInt

/** The style report as a note: the whole story first, then a line per scene, for as many as fit. */
object StyleReportNote {
	const val TAG = "style-report"

	/** Runs the report on [project] and saves it as a note, through operations like any caller. */
	suspend fun save(operations: OperationRegistry, project: String): Note {
		val report = operations.call<StyleReportInput, StyleReport>(StylePlugin.REPORT, StyleReportInput(project))
		return operations.call<NoteCreateInput, Note>("note.create", NoteCreateInput(project, format(report), listOf(TAG)))
	}

	fun format(report: StyleReport, maxLength: Int = NotesRepository.MAX_NOTE_SIZE): String {
		val head = buildString {
			appendLine("Style report")
			appendLine()
			appendLine("Whole story: ${summary(report.total)}")
			report.total.topAdverbs.takeIf { it.isNotEmpty() }?.let { appendLine("Adverbs: ${it.list()}") }
			report.total.repeatedWords.takeIf { it.isNotEmpty() }?.let { appendLine("Repeated words: ${it.list()}") }
			report.total.repeatedPhrases.takeIf { it.isNotEmpty() }?.let { appendLine("Repeated phrases: ${it.list()}") }
			if (report.scenes.isNotEmpty()) {
				appendLine()
				appendLine("Scenes")
			}
		}

		val lines = report.scenes.map { scene ->
			val repeats = scene.figures.repeatedWords.take(SCENE_REPEATS).takeIf { it.isNotEmpty() }?.let { " Repeats: ${it.list()}." }.orEmpty()
			"${scene.name}: ${summary(scene.figures)}$repeats"
		}
		// Only the lists can make the head this long; cut rather than have note.create refuse it.
		if (head.length > maxLength) return head.take(maxLength).trimEnd()
		val body = StringBuilder(head)
		for ((index, line) in lines.withIndex()) {
			val remaining = lines.size - index
			val omission = omitted(remaining)
			if (body.length + line.length + 1 + omission.length > maxLength) {
				body.append(omission)
				break
			}
			body.appendLine(line)
		}
		return body.toString().trimEnd()
	}

	private fun summary(figures: StyleFigures): String = buildString {
		append("${figures.words} words in ${figures.sentences} sentences.")
		if (figures.readingEase != null) append(" Reading ease ${figures.readingEase}, grade ${figures.gradeLevel}.")
		append(" ${figures.adverbsPerThousandWords} adverbs per 1,000 words.")
		append(" ${(figures.dialogueRatio * PERCENT).roundToInt()}% dialogue.")
	}

	private fun List<Count>.list() = joinToString { "${it.text} (${it.count})" }

	private fun omitted(scenes: Int) =
		"…and $scenes more ${if (scenes == 1) "scene" else "scenes"}. Run style.report to see them all."

	private const val SCENE_REPEATS = 5
	private const val PERCENT = 100
}
