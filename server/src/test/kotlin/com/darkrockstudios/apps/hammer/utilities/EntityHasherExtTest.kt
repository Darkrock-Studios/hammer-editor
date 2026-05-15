package com.darkrockstudios.apps.hammer.utilities

import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.base.http.ApiSceneType
import com.darkrockstudios.apps.hammer.base.http.synchronizer.EntityHasher
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class EntityHasherExtTest {

	@Test
	fun `hashEntity extension must match direct call for all parameter variations`() {
		// This test ensures that ALL parameters are being passed through correctly.
		// If a parameter is added to EntityHasher.hashScene() but not to the extension,
		// this test will fail.

		val testScenes = listOf(
			// Minimal scene - archived
			ApiProjectEntity.SceneEntity(
				id = 1,
				order = 0,
				path = emptyList(),
				name = "Minimal",
				sceneType = ApiSceneType.Scene,
				content = "",
				outline = "",
				notes = "",
				archived = true
			),
			// Minimal scene - not archived
			ApiProjectEntity.SceneEntity(
				id = 2,
				order = 0,
				path = emptyList(),
				name = "Not Archived",
				sceneType = ApiSceneType.Scene,
				content = "",
				outline = "",
				notes = "",
				archived = false
			),
			// Scene with all fields populated
			ApiProjectEntity.SceneEntity(
				id = 3,
				order = 5,
				path = listOf(1, 2, 3, 4),
				name = "Full Scene",
				sceneType = ApiSceneType.Scene,
				content = "Rich content\nwith multiple lines\nand special chars: !@#$%",
				outline = "Detailed outline",
				notes = "Comprehensive notes",
				archived = false
			),
			// Group scene (different sceneType)
			ApiProjectEntity.SceneEntity(
				id = 4,
				order = 10,
				path = listOf(1, 2),
				name = "Group Scene",
				sceneType = ApiSceneType.Group,
				content = "",
				outline = "",
				notes = "",
				archived = false
			),
			// Archived group
			ApiProjectEntity.SceneEntity(
				id = 5,
				order = 15,
				path = emptyList(),
				name = "Archived Group",
				sceneType = ApiSceneType.Group,
				content = "",
				outline = "",
				notes = "",
				archived = true
			),
			// Scene with special characters in fields
			ApiProjectEntity.SceneEntity(
				id = 6,
				order = 999,
				path = listOf(0, 100, 200),
				name = "Special !@#$%^&*() Scene",
				sceneType = ApiSceneType.Scene,
				content = "Unicode: \u00E9\u00E8\u00EA\u00EB",
				outline = "Line1\nLine2\nLine3",
				notes = "Notes with \"quotes\" and 'apostrophes'",
				archived = true
			),
			// Scene with confirmed and dismissed reference sets - guards against the
			// extension forgetting to forward these fields to hashScene.
			ApiProjectEntity.SceneEntity(
				id = 7,
				order = 0,
				path = listOf(0),
				name = "Scene With Refs",
				sceneType = ApiSceneType.Scene,
				content = "",
				outline = "",
				notes = "",
				archived = false,
				confirmedReferences = setOf(11, 12, 13),
				dismissedReferences = setOf(99),
			),
			// Scene with tags - guards against the extension forgetting to forward
			// the tags field to hashScene.
			ApiProjectEntity.SceneEntity(
				id = 8,
				order = 0,
				path = listOf(0),
				name = "Scene With Tags",
				sceneType = ApiSceneType.Scene,
				content = "",
				outline = "",
				notes = "",
				archived = false,
				tags = setOf("important", "draft"),
			),
		)

		testScenes.forEach { scene ->
			// Hash using extension
			val hashFromExtension = scene.hash()

			// Hash using direct call with ALL parameters explicitly passed
			val hashDirect = EntityHasher.hashScene(
				id = scene.id,
				order = scene.order,
				path = scene.path,
				name = scene.name,
				type = scene.sceneType,
				content = scene.content,
				outline = scene.outline,
				notes = scene.notes,
				archived = scene.archived,
				confirmedReferences = scene.confirmedReferences,
				dismissedReferences = scene.dismissedReferences,
				tags = scene.tags,
			)

			// They MUST match
			assertEquals(
				hashDirect,
				hashFromExtension,
				"Extension function hash must match direct call for scene ID ${scene.id} " +
					"(archived=${scene.archived}, type=${scene.sceneType})"
			)
		}
	}

	@Test
	fun `hashEntity extension must match direct call for SceneDraft variations`() {
		val testDrafts = listOf(
			// Minimal draft
			ApiProjectEntity.SceneDraftEntity(
				id = 1,
				sceneId = 10,
				name = "Draft 1",
				created = Instant.fromEpochMilliseconds(0),
				content = ""
			),
			// Draft with content
			ApiProjectEntity.SceneDraftEntity(
				id = 2,
				sceneId = 20,
				name = "Full Draft",
				created = Instant.fromEpochMilliseconds(1000000),
				content = "Draft content\nwith multiple lines\nand special chars: !@#$%"
			),
			// Draft with unicode
			ApiProjectEntity.SceneDraftEntity(
				id = 3,
				sceneId = 30,
				name = "Unicode Draft éèêë",
				created = Instant.fromEpochMilliseconds(9999999999),
				content = "Content with unicode: \u00E9\u00E8\u00EA\u00EB"
			)
		)

		testDrafts.forEach { draft ->
			val hashFromExtension = draft.hash()

			val hashDirect = EntityHasher.hashSceneDraft(
				id = draft.id,
				sceneId = draft.sceneId,
				name = draft.name,
				created = draft.created,
				content = draft.content,
			)

			assertEquals(
				hashDirect,
				hashFromExtension,
				"Extension function hash must match direct call for draft ID ${draft.id}"
			)
		}
	}

	@Test
	fun `hashEntity extension must match direct call for Note variations`() {
		val testNotes = listOf(
			// Minimal note
			ApiProjectEntity.NoteEntity(
				id = 1,
				created = Instant.fromEpochMilliseconds(0),
				content = ""
			),
			// Note with content
			ApiProjectEntity.NoteEntity(
				id = 2,
				created = Instant.fromEpochMilliseconds(123456789),
				content = "Note content\nwith multiple lines"
			),
			// Note with special characters
			ApiProjectEntity.NoteEntity(
				id = 3,
				created = Instant.fromEpochMilliseconds(9876543210),
				content = "Special chars: !@#$%^&*()\nQuotes: \"double\" and 'single'\nUnicode: éèêë"
			),
			// Very long note
			ApiProjectEntity.NoteEntity(
				id = 4,
				created = Instant.fromEpochMilliseconds(1000),
				content = "Lorem ipsum ".repeat(100)
			)
		)

		testNotes.forEach { note ->
			val hashFromExtension = note.hash()

			val hashDirect = EntityHasher.hashNote(
				id = note.id,
				created = note.created,
				content = note.content
			)

			assertEquals(
				hashDirect,
				hashFromExtension,
				"Extension function hash must match direct call for note ID ${note.id}"
			)
		}
	}

	@Test
	fun `hashEntity extension must match direct call for TimelineEvent variations`() {
		val testEvents = listOf(
			// Minimal event
			ApiProjectEntity.TimelineEventEntity(
				id = 1,
				order = 0,
				content = "",
				date = ""
			),
			// Event with content and date
			ApiProjectEntity.TimelineEventEntity(
				id = 2,
				order = 5,
				content = "Event content",
				date = "2024-01-15"
			),
			// Event with complex date string
			ApiProjectEntity.TimelineEventEntity(
				id = 3,
				order = 999,
				content = "Multi-line\nevent\ncontent",
				date = "The Year of the Dragon, 3rd moon"
			),
			// Event with special characters
			ApiProjectEntity.TimelineEventEntity(
				id = 4,
				order = -5,
				content = "Content with unicode: éèêë and symbols: !@#$%",
				date = "Stardate 47634.44"
			)
		)

		testEvents.forEach { event ->
			val hashFromExtension = event.hash()

			val hashDirect = EntityHasher.hashTimelineEvent(
				id = event.id,
				order = event.order,
				content = event.content,
				date = event.date
			)

			assertEquals(
				hashDirect,
				hashFromExtension,
				"Extension function hash must match direct call for timeline event ID ${event.id}"
			)
		}
	}

	@Test
	fun `hashEntity extension must match direct call for EncyclopediaEntry variations`() {
		val testEntries = listOf(
			// Minimal entry - no image
			ApiProjectEntity.EncyclopediaEntryEntity(
				id = 1,
				name = "Entry 1",
				entryType = "person",
				text = "",
				tags = emptySet(),
				image = null
			),
			// Entry with all text fields
			ApiProjectEntity.EncyclopediaEntryEntity(
				id = 2,
				name = "Full Entry",
				entryType = "place",
				text = "Detailed description\nwith multiple lines\nand special chars: !@#$%",
				tags = setOf("tag1", "tag2", "tag3"),
				image = null
			),
			// Entry with image
			ApiProjectEntity.EncyclopediaEntryEntity(
				id = 3,
				name = "Entry with Image",
				entryType = "item",
				text = "Entry with an image",
				tags = setOf("important", "visual"),
				image = ApiProjectEntity.EncyclopediaEntryEntity.Image(
					base64 = "iVBORw0KGgoAAAANSUhEUgAAAAUA",
					fileExtension = "png"
				)
			),
			// Entry with unicode and complex tags
			ApiProjectEntity.EncyclopediaEntryEntity(
				id = 4,
				name = "Unicode Entry éèêë",
				entryType = "concept",
				text = "Text with unicode: \u00E9\u00E8\u00EA\u00EB",
				tags = setOf("unicode", "special-chars", "tag with spaces"),
				image = ApiProjectEntity.EncyclopediaEntryEntity.Image(
					base64 = "base64encodedstringhere==",
					fileExtension = "jpg"
				)
			),
			// Entry with many tags
			ApiProjectEntity.EncyclopediaEntryEntity(
				id = 5,
				name = "Many Tags",
				entryType = "event",
				text = "Entry with many tags",
				tags = setOf("a", "b", "c", "d", "e", "f", "g", "h", "i", "j"),
				image = null
			),
			// Entry with different image extension
			ApiProjectEntity.EncyclopediaEntryEntity(
				id = 6,
				name = "JPEG Image",
				entryType = "person",
				text = "Has a JPEG",
				tags = setOf("image"),
				image = ApiProjectEntity.EncyclopediaEntryEntity.Image(
					base64 = "/9j/4AAQSkZJRgABAQEAYABgAAD/2wBD",
					fileExtension = "jpeg"
				)
			),
			// Entry with aliases - guards against the extension forgetting to forward
			// the aliases field to hashEncyclopediaEntry.
			ApiProjectEntity.EncyclopediaEntryEntity(
				id = 7,
				name = "Robert",
				entryType = "person",
				text = "A character with nicknames",
				tags = setOf("protagonist"),
				image = null,
				aliases = listOf("Bob", "Bobby", "Rob"),
			),
		)

		testEntries.forEach { entry ->
			val hashFromExtension = entry.hash()

			val hashDirect = EntityHasher.hashEncyclopediaEntry(
				id = entry.id,
				name = entry.name,
				entryType = entry.entryType,
				text = entry.text,
				tags = entry.tags,
				image = entry.image,
				aliases = entry.aliases,
			)

			assertEquals(
				hashDirect,
				hashFromExtension,
				"Extension function hash must match direct call for encyclopedia entry ID ${entry.id}"
			)
		}
	}
}
