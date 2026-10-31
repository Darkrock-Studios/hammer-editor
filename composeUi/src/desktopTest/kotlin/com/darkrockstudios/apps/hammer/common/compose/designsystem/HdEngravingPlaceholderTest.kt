package com.darkrockstudios.apps.hammer.common.compose.designsystem

import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test

class HdEngravingPlaceholderTest {

	@get:Rule
	val compose = createComposeRule()

	@Test
	fun `the default placeholder carries the engraving prefix`() {
		compose.setContent {
			HdEngravingPlaceholder(label = "NO IMAGE")
		}

		compose.onNodeWithText("[ENGRAVING · NO IMAGE]").assertExists()
	}

	@Test
	fun `the compact placeholder shows only the bracketed label`() {
		compose.setContent {
			HdEngravingPlaceholder(
				label = "NO COVER",
				compact = true,
				modifier = Modifier.width(120.dp).aspectRatio(2f / 3f),
			)
		}

		compose.onNodeWithText("[NO COVER]").assertExists()
		compose.onAllNodesWithText("ENGRAVING", substring = true).assertCountEquals(0)
	}
}
