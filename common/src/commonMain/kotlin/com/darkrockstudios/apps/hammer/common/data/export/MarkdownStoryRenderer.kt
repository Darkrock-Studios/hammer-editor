package com.darkrockstudios.apps.hammer.common.data.export

internal fun storyMarkdown(input: ExportInput): String = buildString {
	append("# ${input.projectName}\n\n")

	input.chapters.forEachIndexed { index, chapter ->
		if (input.treatTopLevelAsChapters) {
			append("\n## ${chapterTitle(index, chapter, input.options.numberChapters)}\n\n")
		} else if (index > 0) {
			append("\n\n")
		}
		append(chapter.markdown)
		append("\n")
	}
}
