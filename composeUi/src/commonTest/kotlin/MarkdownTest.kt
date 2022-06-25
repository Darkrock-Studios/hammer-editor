package com.darkrockstudios.apps.hammer.common.data.text

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import kotlin.test.Test
import kotlin.test.assertEquals

class MarkdownTest {
    /*
    @Test
    fun `AnnotatedString to Markdown`() {
        val test = testString()
        val markdown = test.toMarkdown()
        assertEquals(
            "This is a **test** of styled strings that we will apply styles to.",
            markdown,
            "Markdown did not match"
        )
    }
    */

    @Test
    fun `Markdown to AnnotatedString`() {
        val test = "This is a **test** of styled strings that we will apply styles to."
        val annotatedString = test.markdownToAnnotatedString()

        assertEquals(1, annotatedString.spanStyles.size)
        val span = annotatedString.spanStyles.first()
        assertEquals(FontWeight.Bold, span.item.fontWeight)
    }

    private fun testString(): AnnotatedString {
        val plainText = "This is a test of styled strings that we will apply styles to."
        val builder = AnnotatedString.Builder(plainText)

        val start = 10
        val end = 14

        val test = plainText.subSequence(start, end)
        assertEquals("test", test)

        builder.addStyle(boldStyle(), start, end)

        return builder.toAnnotatedString()
    }
}