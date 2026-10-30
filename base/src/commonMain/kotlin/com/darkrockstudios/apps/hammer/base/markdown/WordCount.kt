package com.darkrockstudios.apps.hammer.base.markdown

/**
 * Counts the words in [text] by Unicode default word boundaries ([wordSpans]): a word holds at
 * least one letter or digit, so standalone Markdown markers (`---`, `#`, `-`, `>`) are not words.
 * Single pass: a whole manuscript is one string, not a word list.
 */
fun countWords(text: String): Int = wordSpans(text).count()
