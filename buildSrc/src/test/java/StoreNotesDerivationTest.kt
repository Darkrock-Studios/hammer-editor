package com.darkrockstudios.build

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StoreNotesDerivationTest {

	@Test
	fun `The server operators section is dropped whole`() {
		val full = """
			[Fix]
			- Client: Text editor crash

			[Server operators]
			- Whitelist invites can be given an expiry date
			- Configurable disk cache directory
		""".trimIndent()

		val derived = deriveStoreNotes(full)

		assertEquals("[Fix]\n- Client: Text editor crash", derived.notes)
	}

	@Test
	fun `Web and server bullets are dropped but app bullets stay`() {
		val full = """
			[Improve]
			- Web: story reader pages load faster
			- Server: connections are now HTTPS-only
			- Encyclopedia: search by #tag
		""".trimIndent()

		val derived = deriveStoreNotes(full)

		assertEquals("[Improve]\n- Encyclopedia: search by #tag", derived.notes)
	}

	@Test
	fun `A dropped bullet takes its wrapped continuation lines with it`() {
		val full = """
			[Improve]
			- Web: optional Terms of Service and Privacy Policy pages, with a
			two-tier footer
			- Desktop: Ctrl+Q closes the app
		""".trimIndent()

		val derived = deriveStoreNotes(full)

		assertTrue(
			!derived.notes.contains("two-tier footer"),
			"Orphaned a continuation line:\n${derived.notes}",
		)
		assertEquals("[Improve]\n- Desktop: Ctrl+Q closes the app", derived.notes)
	}

	@Test
	fun `A section left with no bullets is dropped along with its header`() {
		val full = """
			[New]
			- Encyclopedia: search by #tag

			[Improve]
			- Web: sitemap and search engine discovery
			- Server: configurable disk cache
		""".trimIndent()

		val derived = deriveStoreNotes(full)

		assertEquals("[New]\n- Encyclopedia: search by #tag", derived.notes)
	}

	@Test
	fun `The combined bracket form is filtered by its audience`() {
		val full = """
			[New/Client] More text editor keyboard shortcuts
			[New/Server] Active Users metric on the monitoring dashboard
			[Fix/Web] Editorial review character escaping
			[Fix/Client] Encyclopedia image selection
		""".trimIndent()

		val derived = deriveStoreNotes(full)

		assertEquals(
			"[New/Client] More text editor keyboard shortcuts\n" +
				"[Fix/Client] Encyclopedia image selection",
			derived.notes,
		)
	}

	@Test
	fun `The dash prefixed bracket form is filtered by its audience`() {
		val full = """
			- [New/Client] More text editor keyboard shortcuts
			- [New/Web] Request editor review
			- [Fix/Web] Editorial review character escaping
			- [Fix/Client] Encyclopedia image selection
		""".trimIndent()

		val derived = deriveStoreNotes(full)

		assertEquals(
			"- [New/Client] More text editor keyboard shortcuts\n" +
				"- [Fix/Client] Encyclopedia image selection",
			derived.notes,
		)
	}

	@Test
	fun `An audience label after a bracket tag is still filtered`() {
		val full = """
			- [New] Server: Improved cryptography key management
			- [New] Re-added translations
		""".trimIndent()

		val derived = deriveStoreNotes(full)

		assertEquals("- [New] Re-added translations", derived.notes)
	}

	@Test
	fun `Bullets whose prose merely mentions a server or the web are kept`() {
		val full = """
			[Improve]
			- Server address: you can now paste a full URL
			- Security hardening: sync auth tokens are encrypted at rest
			- Fixed a crash in the server sync screen: it now closes cleanly
			- Draft compare: paragraph move highlighting
		""".trimIndent()

		val derived = deriveStoreNotes(full)

		assertEquals(full, derived.notes)
	}

	@Test
	fun `An indented sub bullet is dropped with its parent`() {
		val full = """
			[Improve]
			- Web: story reader improvements
			  - Link previews now show a title and image
			- Desktop: Ctrl+Q closes the app
		""".trimIndent()

		val derived = deriveStoreNotes(full)

		assertTrue(
			!derived.notes.contains("Link previews"),
			"Orphaned a sub-bullet from its dropped parent:\n${derived.notes}",
		)
		assertEquals("[Improve]\n- Desktop: Ctrl+Q closes the app", derived.notes)
	}

	@Test
	fun `Everything removed is reported so the release manager can review it`() {
		val full = """
			[New]
			- Encyclopedia: search by #tag
			- Web: faster reader pages

			[Server operators]
			- Configurable disk cache directory
		""".trimIndent()

		val derived = deriveStoreNotes(full)

		assertEquals(
			listOf("- Web: faster reader pages", "[Server operators]", "- Configurable disk cache directory"),
			derived.dropped,
		)
	}

	@Test
	fun `Nothing removed is reported when the whole changelog is app facing`() {
		val derived = deriveStoreNotes("[Fix]\n- Client: Text editor crash")

		assertEquals(emptyList<String>(), derived.dropped)
	}

	@Test
	fun `A changelog with nothing app facing derives to nothing`() {
		val full = """
			[Server operators]
			- Configurable disk cache directory
		""".trimIndent()

		assertEquals("", deriveStoreNotes(full).notes)
	}

	@Test
	fun `An all app changelog is passed through unchanged`() {
		val full = """
			[New]
			- Encyclopedia: search by #tag
			- Desktop: Ctrl+Q closes the app

			[Fix]
			- Client: Text editor crash
		""".trimIndent()

		assertEquals(full, deriveStoreNotes(full).notes)
	}

	@Test
	fun `The v3_7_0 changelog derives to app facing notes only`() {
		val full = """
			[New]
			- Encyclopedia: search by #tag, combined with name search
			- Desktop: Ctrl+Q closes the app from the project selection window
			- Crash reports now include the full stack trace in the exported logs
			(Android, Desktop, and iOS)

			[Improve]
			- Server connections are now HTTPS-only. If you sync with a self-hosted
			server over plain HTTP, it must be put behind HTTPS before upgrading.
			- Web: story reader pages load faster and link previews now show a proper
			title, description, and image when shared
			- Web: sitemap and search engine discovery for public stories

			[Fix]
			- Web: error when saving your bio
			- Client: Text editor crash

			[Server operators]
			- Optional Terms of Service acceptance gate on account creation
			- Whitelist invites can be given an expiry date
		""".trimIndent()

		val notes = deriveStoreNotes(full).notes

		assertTrue(!notes.contains("Web:"), "Kept a web bullet:\n$notes")
		assertTrue(!notes.contains("Server operators"), "Kept the operators section:\n$notes")
		assertTrue(!notes.contains("Whitelist invites"), "Kept an operator bullet:\n$notes")
		assertTrue(notes.contains("Encyclopedia: search by #tag"), notes)
		assertTrue(notes.contains("- Client: Text editor crash"), notes)
		// A user-visible breaking change about their own sync server still concerns
		// the app, so an unprefixed bullet is kept even when it mentions the server.
		assertTrue(notes.contains("HTTPS-only"), notes)
		assertTrue(notes.contains("(Android, Desktop, and iOS)"), "Dropped a continuation:\n$notes")
	}

	@Test
	fun `Blank input derives to nothing`() {
		assertEquals("", deriveStoreNotes("").notes)
		assertEquals("", deriveStoreNotes("  \n\n  ").notes)
	}

	@Test
	fun `CRLF input is normalized`() {
		val full = "[New]\r\n- Encyclopedia: search by #tag\r\n- Web: faster reader"

		val notes = deriveStoreNotes(full).notes

		assertTrue(!notes.contains('\r'), "Kept CRLF")
		assertEquals("[New]\n- Encyclopedia: search by #tag", notes)
	}
}
