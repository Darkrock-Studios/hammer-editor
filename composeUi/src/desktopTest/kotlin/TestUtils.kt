import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.text.AnnotatedString

fun SemanticsNodeInteraction.assertTextSatisfies(
	predicate: (AnnotatedString) -> Boolean
): SemanticsNodeInteraction = this.assert(hasTextThatSatisfies(predicate))

fun hasTextThatSatisfies(
	predicate: (AnnotatedString) -> Boolean
): SemanticsMatcher {
	val propertyName = "${SemanticsProperties.Text.name} + ${SemanticsProperties.EditableText.name}"
	return SemanticsMatcher(
		"$propertyName text that satisfies predicate"
	) {
		val isInEditableTextValue = it.config.getOrNull(SemanticsProperties.EditableText)
		val isInTextValue = it.config.getOrNull(SemanticsProperties.Text)
		return@SemanticsMatcher if (isInEditableTextValue != null) {
			predicate(isInEditableTextValue)
		} else if (isInTextValue != null) {
			isInTextValue.any(predicate)
		} else {
			false
		}
	}
}