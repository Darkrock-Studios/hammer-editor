import androidx.compose.ui.text.AnnotatedString
import com.darkrockstudios.apps.hammer.common.compose.ComposeRichText
import com.darkrockstudios.texteditor.markdown.MarkdownExtension
import io.mockk.every
import io.mockk.mockk
import junit.framework.TestCase
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ComposeRichTextTest {

    @Test
    fun `ComposeRichText With Equal Snapshot`() {
        // Create two ComposeRichText objects with the same snapshot
        val snapshot = AnnotatedString("same snapshot")
        val richText1 = ComposeRichText(snapshot = snapshot)
        val richText2 = ComposeRichText(snapshot = snapshot)

        // Test that they are equal
	    assertTrue(richText1.compare(richText2))
	    assertTrue(richText2.compare(richText1))
	    assertEquals(richText1, richText2)
	    assertEquals(richText1.hashCode(), richText2.hashCode())
    }

    @Test
    fun `ComposeRichText With DifferentSnapshot`() {
        // Create two ComposeRichText objects with different snapshots
        val richText1 = ComposeRichText(snapshot = AnnotatedString("snapshot 1"))
        val richText2 = ComposeRichText(snapshot = AnnotatedString("snapshot 2"))

        // Test that they are not equal
	    TestCase.assertFalse(richText1.compare(richText2))
	    TestCase.assertFalse(richText2.compare(richText1))
	    assertTrue(richText1 != richText2)
    }

	@Test
	fun `ComposeRichText With Equal MarkdownExtension`() {
		// Create two ComposeRichText objects with the same MarkdownExtension
		val state = mockk<MarkdownExtension>()
		every { state.equals(any()) } returns true

		val richText1 = ComposeRichText(state = state)
		val richText2 = ComposeRichText(state = state)

		// Test that they are equal
		assertTrue(richText1.compare(richText2))
		assertTrue(richText2.compare(richText1))
		assertEquals(richText1, richText2)
		assertEquals(richText1.hashCode(), richText2.hashCode())
	}

	@Test
	fun `ComposeRichText With Different MarkdownExtension`() {
		val state1 = mockk<MarkdownExtension>()
		val state2 = mockk<MarkdownExtension>()
		every { state1.equals(any()) } returns false
		every { state2.equals(any()) } returns false

		// Create two ComposeRichText objects with different MarkdownExtension
		val richText1 = ComposeRichText(state = state1)
		val richText2 = ComposeRichText(state = state2)

		// Test that they are not equal
		TestCase.assertFalse(richText1.compare(richText2))
		TestCase.assertFalse(richText2.compare(richText1))
		assertTrue(richText1 != richText2)
	}

	@Test
	fun `stateCompare With Same Snapshot Instance`() {
		// Create two ComposeRichText objects with the same snapshot instance
		val snapshot = AnnotatedString("same snapshot")
		val richText1 = ComposeRichText(snapshot = snapshot)
		val richText2 = ComposeRichText(snapshot = snapshot)

		// Test that stateCompare returns true when snapshots are the same instance
		assertTrue(richText1.stateCompare(richText2))
		assertTrue(richText2.stateCompare(richText1))
	}

	@Test
	fun `stateCompare With Different Snapshot Instances But Same Content`() {
		// Create two ComposeRichText objects with different snapshot instances but same content
		val richText1 = ComposeRichText(snapshot = AnnotatedString("same content"))
		val richText2 = ComposeRichText(snapshot = AnnotatedString("same content"))

		// Test that stateCompare returns false when snapshots are different instances
		// even though they have the same content
		TestCase.assertFalse(richText1.stateCompare(richText2))
		TestCase.assertFalse(richText2.stateCompare(richText1))
	}

	@Test
	fun `stateCompare With Null Snapshots`() {
		// Create ComposeRichText objects with null snapshots
		val state1 = mockk<MarkdownExtension>()
		val state2 = mockk<MarkdownExtension>()

		val richText1 = ComposeRichText(state = state1)
		val richText2 = ComposeRichText(state = state2)

		// Test that stateCompare returns false when snapshots are null
		TestCase.assertFalse(richText1.stateCompare(richText2))
		TestCase.assertFalse(richText2.stateCompare(richText1))
	}

	@Test
	fun `stateCompare With Mixed Null And Non-Null Snapshots`() {
		// Create one ComposeRichText with snapshot and one without
		val snapshot = AnnotatedString("snapshot")
		val state = mockk<MarkdownExtension>()

		val richText1 = ComposeRichText(snapshot = snapshot)
		val richText2 = ComposeRichText(state = state)

		// Test that stateCompare returns false when one snapshot is null
		TestCase.assertFalse(richText1.stateCompare(richText2))
		TestCase.assertFalse(richText2.stateCompare(richText1))
	}
}
