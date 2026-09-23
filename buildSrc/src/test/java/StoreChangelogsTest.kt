package com.darkrockstudios.build

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StoreChangelogsTest {

	private val url = releaseNotesUrl("v3.7.0")

	@Test
	fun `A store with no tab of its own takes the shared notes`() {
		val notes = StoreChangelogs(shared = "shared", play = "play", apple = "apple")

		assertEquals("shared", notes.notesFor(Platform.SNAP))
		assertEquals("shared", notes.notesFor(Platform.MS_STORE))
		assertEquals("shared", notes.notesFor(Platform.SERVER))
	}

	// F-Droid and Google Play read the same fastlane file, so they cannot disagree.
	@Test
	fun `F-Droid takes the Google Play notes`() {
		val notes = StoreChangelogs(shared = "shared", play = "play", apple = "apple")

		assertEquals(notes.notesFor(Platform.GOOGLE_PLAY), notes.notesFor(Platform.FDROID))
	}

	@Test
	fun `Both Apple stores take the Apple notes`() {
		val notes = StoreChangelogs(shared = "shared", play = "play", apple = "apple")

		assertEquals("apple", notes.notesFor(Platform.IOS_APP_STORE))
		assertEquals("apple", notes.notesFor(Platform.MAC_APP_STORE))
	}

	@Test
	fun `Stores follow the shared notes until they are given their own`() {
		val notes = StoreChangelogs(shared = "shared")

		Platform.values().forEach { platform ->
			assertEquals("shared", notes.notesFor(platform), "$platform diverged")
		}
	}

	@Test
	fun `Blank is only blank when every sink is`() {
		assertTrue(StoreChangelogs(shared = "").isBlank)
		assertFalse(StoreChangelogs(shared = "", play = "play", apple = "").isBlank)
		assertFalse(StoreChangelogs(shared = "shared").isBlank)
	}

	@Test
	fun `Fitted notes carry no footer`() {
		val changelog = (1..40).joinToString("\n") { "- [New] Feature number $it in this release" }

		val fitted = fitStoreNotes(changelog, storeNotesBudget(PLAY_STORE_LIMIT, url))

		assertFalse(url in fitted, "The footer url leaked into the fitted body")
		assertTrue(fitted.length <= storeNotesBudget(PLAY_STORE_LIMIT, url))
	}

	// The dialog's Trim to fit puts fitted text back in the editor, which is then
	// formatted again on the way to the store. A footer in the fitted body would be
	// published twice and eat the budget a second time.
	@Test
	fun `Trimming then publishing stays within the limit with one footer`() {
		val changelog = (1..40).joinToString("\n") { "- [New] Feature number $it in this release" }

		val trimmed = fitStoreNotes(changelog, storeNotesBudget(PLAY_STORE_LIMIT, url))
		val published = formatStoreNotes(trimmed, PLAY_STORE_LIMIT, url)

		assertTrue(published.length <= PLAY_STORE_LIMIT, "Notes were ${published.length} characters")
		assertEquals(1, published.split(url).size - 1, "The footer was published more than once")
	}

	@Test
	fun `Trimming already fitted notes changes nothing`() {
		val changelog = (1..40).joinToString("\n") { "- [New] Feature number $it in this release" }
		val budget = storeNotesBudget(PLAY_STORE_LIMIT, url)

		val once = fitStoreNotes(changelog, budget)

		assertEquals(once, fitStoreNotes(once, budget))
	}

	@Test
	fun `Publishing is fitting plus the footer`() {
		val changelog = (1..40).joinToString("\n") { "- [New] Feature number $it in this release" }

		assertEquals(
			fitStoreNotes(changelog, storeNotesBudget(PLAY_STORE_LIMIT, url)) + "\n\nFull changelog:\n$url",
			formatStoreNotes(changelog, PLAY_STORE_LIMIT, url),
		)
	}

	@Test
	fun `The Apple budget is the whole limit because it carries no footer`() {
		assertEquals(APPLE_STORE_LIMIT, storeNotesBudget(APPLE_STORE_LIMIT, null))
	}

	@Test
	fun `A server-only release publishes no store notes`() {
		val notes = StoreChangelogs(shared = "shared", play = "play", apple = "apple")

		assertTrue(notes.restrictedTo(setOf(Platform.SERVER)).isBlank)
	}

	@Test
	fun `A release skips the sinks it does not reach`() {
		val notes = StoreChangelogs(shared = "shared", play = "play", apple = "apple")

		val playOnly = notes.restrictedTo(setOf(Platform.GOOGLE_PLAY))

		assertEquals("play", playOnly.play)
		assertEquals("", playOnly.apple)
		assertEquals("", playOnly.shared)
	}

	@Test
	fun `Only a release to every client store reaches Flathub`() {
		val notes = StoreChangelogs(shared = "shared", play = "play", apple = "apple")

		assertEquals("shared", notes.restrictedTo(Platform.CLIENT_STORES).shared)
		assertEquals("", notes.restrictedTo(Platform.CLIENT_STORES - Platform.SNAP).shared)
	}

	@Test
	fun `An F-Droid release still fills the Google Play file they share`() {
		val notes = StoreChangelogs(shared = "shared", play = "play", apple = "apple")

		assertEquals("play", notes.restrictedTo(setOf(Platform.FDROID)).play)
	}

	@Test
	fun `A mac-only release keeps the Apple notes both listings share`() {
		val notes = StoreChangelogs(shared = "shared", play = "play", apple = "apple")

		val macOnly = notes.restrictedTo(setOf(Platform.MAC_APP_STORE))

		assertEquals("apple", macOnly.apple)
		assertEquals("", macOnly.play)
	}

	@Test
	fun `A full release keeps every sink`() {
		val notes = StoreChangelogs(shared = "shared", play = "play", apple = "apple")

		assertEquals(notes, notes.restrictedTo(Platform.ALL))
	}
}
