package repositories.writingactivity

import com.darkrockstudios.apps.hammer.common.data.writingactivity.countAddedWords
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class WordDiffTest {

	@Test
	fun `appending words counts each one`() {
		val added = countAddedWords(
			oldText = "I went home",
			newText = "I went home today and rested",
		)
		assertEquals(3, added)
	}

	@Test
	fun `replacing words counts the new ones`() {
		val added = countAddedWords(
			oldText = "I went home",
			newText = "We returned home",
		)
		// "We" and "returned" are new; "home" carries over.
		assertEquals(2, added)
	}

	@Test
	fun `pure rearrangement is not credited as writing`() {
		// Position is ignored — same vocabulary in any order means no new
		// words were typed.
		val added = countAddedWords(
			oldText = "the quick brown fox",
			newText = "fox brown quick the",
		)
		assertEquals(0, added)
	}

	@Test
	fun `same text records zero`() {
		val added = countAddedWords(
			oldText = "the quick brown fox",
			newText = "the quick brown fox",
		)
		assertEquals(0, added)
	}

	@Test
	fun `deleting words records nothing`() {
		val added = countAddedWords(
			oldText = "the cat sat on the mat",
			newText = "the cat",
		)
		assertEquals(0, added)
	}

	@Test
	fun `whole-paragraph rewrite is credited generously`() {
		val added = countAddedWords(
			oldText = "The cat sat on the mat. It was a fine day.",
			newText = "The dog ran across the floor. Sun shone bright.",
		)
		// "dog", "ran", "across", "floor.", "Sun", "shone", "bright." are all new
		// (the punctuation stays attached to the adjacent word — that's fine for our purposes).
		assertEquals(7, added)
	}

	@Test
	fun `delete-then-add within a save still credits the adds`() {
		// User had 3 words, deleted them all, wrote 5 different ones.
		val added = countAddedWords(
			oldText = "old boring intro",
			newText = "a fresh new opening line",
		)
		assertEquals(5, added)
	}

	@Test
	fun `repeated word counts contribute correctly`() {
		val added = countAddedWords(
			oldText = "I I I",
			newText = "I I I I",
		)
		assertEquals(1, added)
	}

	@Test
	fun `empty old text counts every new word`() {
		val added = countAddedWords(oldText = "", newText = "this is brand new content")
		assertEquals(5, added)
	}

	@Test
	fun `empty new text records zero`() {
		val added = countAddedWords(oldText = "this all gets deleted", newText = "")
		assertEquals(0, added)
	}

	@Test
	fun `whitespace-only changes record zero`() {
		val added = countAddedWords(
			oldText = "hello world",
			newText = "hello   world\n",
		)
		assertEquals(0, added)
	}

	@Test
	fun `case sensitive matching treats different casings as distinct`() {
		val added = countAddedWords(oldText = "hello", newText = "Hello")
		assertEquals(1, added)
	}
}
