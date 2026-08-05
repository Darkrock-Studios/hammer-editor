package com.darkrockstudios.apps.hammer.common.projectselection

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink

/**
 * Makes links in the changelog clickable while leaving everything else verbatim. Deliberately
 * not a markdown renderer: changelog entries lead with `[New]` / `[Fix]` tags, which a real
 * parser mangles. Only `[label](url)` and bare http(s) urls are touched.
 */
fun linkifyChangelog(text: String, linkStyle: SpanStyle): AnnotatedString {
	val styles = TextLinkStyles(style = linkStyle)
	return buildAnnotatedString {
		var index = 0
		while (index < text.length) {
			val match = LINK.find(text, index)
			if (match == null) {
				append(text.substring(index))
				break
			}

			append(text.substring(index, match.range.first))

			val label = match.groupValues[1]
			val markdownUrl = match.groupValues[2]
			if (markdownUrl.isNotEmpty()) {
				withLink(LinkAnnotation.Url(markdownUrl, styles)) { append(label) }
				index = match.range.last + 1
			} else {
				val url = trimBareUrl(match.value)
				withLink(LinkAnnotation.Url(url, styles)) { append(url) }
				index = match.range.first + url.length
			}
		}
	}
}

/**
 * Drops sentence punctuation that butted up against a bare url. A closing paren only counts
 * as punctuation when it has no opener inside the url, so `…/Foo_(Bar)` survives while
 * `(see https://example.com)` doesn't swallow the wrapping paren.
 */
private fun trimBareUrl(matched: String): String {
	var url = matched
	while (url.isNotEmpty()) {
		val last = url.last()
		val isTrailing = when (last) {
			')' -> url.count { it == ')' } > url.count { it == '(' }
			else -> last in SENTENCE_PUNCTUATION
		}
		if (!isTrailing) break
		url = url.dropLast(1)
	}
	return url
}

/**
 * The url of a markdown link may itself contain parentheses, so one nested balanced pair is
 * allowed before the `)` that closes the link — otherwise `…/Rich_Text_Format_(RTF)` truncates.
 */
private val LINK =
	Regex("""\[([^]\n]*)]\((https?://(?:[^()\s]|\([^()\s]*\))*)\)|https?://\S+""")

private const val SENTENCE_PUNCTUATION = ".,;:!?"
