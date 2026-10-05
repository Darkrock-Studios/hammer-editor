package com.darkrockstudios.apps.hammer.common.components.projecthome

import okio.BufferedSink

fun writeStoryAsMarkdown(
	sink: BufferedSink,
	projectName: String,
	chapters: List<StoryChapter>,
	treatTopLevelAsChapters: Boolean,
	numberChapters: Boolean = false,
) {
	sink.writeUtf8("# $projectName\n\n")

	chapters.forEachIndexed { index, chapter ->
		if (treatTopLevelAsChapters) {
			sink.writeUtf8("\n## ${chapterTitle(index, chapter, numberChapters)}\n\n")
		} else if (index > 0) {
			sink.writeUtf8("\n\n")
		}
		sink.writeUtf8(chapter.markdown)
		sink.writeUtf8("\n")
	}
}
