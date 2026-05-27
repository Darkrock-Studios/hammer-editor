package com.darkrockstudios.apps.hammer.common.components.projecthome

import okio.BufferedSink

fun writeStoryAsMarkdown(
	sink: BufferedSink,
	projectName: String,
	chapters: List<StoryChapter>,
	treatTopLevelAsChapters: Boolean,
) {
	sink.writeUtf8("# $projectName\n\n")

	chapters.forEachIndexed { index, chapter ->
		if (treatTopLevelAsChapters) {
			sink.writeUtf8("\n## ${index + 1}. ${chapter.name}\n\n")
		} else if (index > 0) {
			sink.writeUtf8("\n\n")
		}
		sink.writeUtf8(chapter.markdown)
		sink.writeUtf8("\n")
	}
}
