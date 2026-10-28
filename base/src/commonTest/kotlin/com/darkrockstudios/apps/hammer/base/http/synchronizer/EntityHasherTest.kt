package com.darkrockstudios.apps.hammer.base.http.synchronizer

import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.base.http.ApiSceneType
import korlibs.crypto.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.time.Instant

class EntityHasherTest {
	@Test
	fun hashScene() {
		val hash = sceneHash()
		val hash2 = sceneHash()
		assertEquals(hash, hash2, "Hash should be deterministic")

		val hashDifferentContent = sceneHash(content = "Different Content")
		assertNotEquals(hash, hashDifferentContent, "Hash should change when content changes")
	}

	@Test
	fun hashNote() {
		val instant = Instant.fromEpochMilliseconds(0)
		val hash = EntityHasher.hashNote(
			id = 2,
			created = instant,
			content = "Content",
			tags = emptySet(),
		)

		assertEquals("NKZ2n0XDoHLagRABzkb8Yg", hash)
	}

	@Test
	fun hashTimelineEvent() {
		val hash = EntityHasher.hashTimelineEvent(
			id = 2,
			order = 1,
			content = "Content",
			date = "The Futuer",
			tags = emptySet(),
		)

		assertEquals("SAH6B_pamg_T5MCpWZM6vQ", hash)
	}

	@Test
	fun hashEncyclopediaEntry() {
		val hash = entryHash(images = listOf(7, 3))

		assertEquals("jquhxHz81aFulUhzRdatNQ", hash)
	}

	// These are hashes already stored by servers and clients: an entry without images must keep
	// producing them.
	@Test
	fun `hashEncyclopediaEntry without images keeps its stored hash`() {
		assertEquals("M7sPNEDlP3y88o9a8VE8Lw", entryHash())
		assertEquals(
			"BQIQf-DRJ1heCdp7diwSpg",
			entryHash(aliases = listOf("Debate"), excludeFromDictionary = true),
		)
	}

	@Test
	fun `EncyclopediaEntryEntity hash matches hashEncyclopediaEntry`() {
		val entity = ApiProjectEntity.EncyclopediaEntryEntity(
			id = 2,
			name = "The Great Debate",
			entryType = "person",
			text = "Some great content",
			tags = setOf("tag1", "tag2"),
			images = listOf(7, 3),
		)
		assertEquals(entryHash(images = listOf(7, 3)), entity.hash())
		assertEquals(entryHash(), entity.copy(images = emptyList()).hash())
	}

	@Test
	fun `hashEncyclopediaEntry differs when an image is added`() {
		assertNotEquals(entryHash(images = listOf(7)), entryHash(images = listOf(7, 3)))
		assertNotEquals(entryHash(), entryHash(images = listOf(7)))
	}

	@Test
	fun `hashEncyclopediaEntry image order is significant`() {
		// The first image is the primary one, so reordering is a user-visible change that must sync.
		assertNotEquals(entryHash(images = listOf(7, 3)), entryHash(images = listOf(3, 7)))
	}

	@Test
	fun `hashEncyclopediaEntry images and excludeFromDictionary contribute distinctly`() {
		assertNotEquals(
			entryHash(excludeFromDictionary = true),
			entryHash(images = listOf(1)),
		)
		assertNotEquals(
			entryHash(excludeFromDictionary = true, images = listOf(5)),
			entryHash(images = listOf(1, 5)),
		)
	}

	@Test
	fun hashSceneDraft() {
		val instant = Instant.fromEpochMilliseconds(0)
		val hash = EntityHasher.hashSceneDraft(
			id = 2,
			name = "The Great Debate",
			created = instant,
			content = "Some great content",
			sceneId = 0,
		)

		assertEquals("lGUFzy0jQtYbLHWa998BfA", hash)
	}

	@Test
	fun hashImage() {
		val hash = EntityHasher.hashImage(
			id = 7,
			name = "Castle Map",
			fileExtension = "png",
			bytes = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3),
		)

		assertEquals("1jWFiWJPKpy7Db-32vCaQQ", hash)
	}

	@Test
	fun `hashImage of empty bytes`() {
		val hash = EntityHasher.hashImage(id = 1, name = "", fileExtension = "jpg", bytes = ByteArray(0))
		assertEquals("L8xYoT-YxVjqc30-RvPPFg", hash)
	}

	@Test
	fun `hashImage name and extension do not bleed into each other`() {
		val a = EntityHasher.hashImage(id = 1, name = "ab", fileExtension = "c", bytes = byteArrayOf(1))
		val b = EntityHasher.hashImage(id = 1, name = "a", fileExtension = "bc", bytes = byteArrayOf(1))
		assertNotEquals(a, b)
	}

	@Test
	fun `ImageEntity hash decodes base64 and hashes the raw bytes`() {
		val bytes = byteArrayOf(0x47, 0x49, 0x46, 0x38, -1, -2, -3)
		val entity = ApiProjectEntity.ImageEntity(
			id = 3,
			name = "Portrait",
			fileExtension = "gif",
			base64 = Base64.encode(bytes, url = true),
		)
		assertEquals(
			EntityHasher.hashImage(id = 3, name = "Portrait", fileExtension = "gif", bytes = bytes),
			entity.hash(),
		)
	}

	@Test
	fun hashSceneWithArchived() {
		val hashNotArchived = sceneHash(archived = false)
		val hashArchived = sceneHash(archived = true)

		assertNotEquals(hashNotArchived, hashArchived, "Hash should differ when archived status changes")

		val hashArchived2 = sceneHash(archived = true)
		assertEquals(hashArchived, hashArchived2, "Archived hash should be deterministic")
	}

	@Test
	fun `hashScene differs when confirmedReferences differ`() {
		val base = sceneHash(confirmedReferences = setOf(1, 2))
		val different = sceneHash(confirmedReferences = setOf(1, 3))
		assertNotEquals(base, different, "hashScene must include confirmedReferences in the digest")
	}

	@Test
	fun `hashScene confirmedReferences is order-independent`() {
		// Defends against a Set-iteration-order regression - the impl must sort before hashing
		val a = sceneHash(confirmedReferences = linkedSetOf(1, 2, 3))
		val b = sceneHash(confirmedReferences = linkedSetOf(3, 1, 2))
		assertEquals(a, b, "confirmedReferences must be sorted before hashing for stability")
	}

	@Test
	fun `hashScene differs when dismissedReferences differ`() {
		val base = sceneHash(dismissedReferences = setOf(5))
		val different = sceneHash(dismissedReferences = setOf(5, 6))
		assertNotEquals(base, different, "hashScene must include dismissedReferences in the digest")
	}

	@Test
	fun `hashScene confirmed and dismissed contribute distinctly`() {
		// Defends against a swapped-arg regression where confirmed and dismissed get
		// folded in identically - the same id sets in opposite slots must hash differently.
		val confirmedOne = sceneHash(confirmedReferences = setOf(7), dismissedReferences = emptySet())
		val dismissedOne = sceneHash(confirmedReferences = emptySet(), dismissedReferences = setOf(7))
		assertNotEquals(
			confirmedOne, dismissedOne,
			"confirmedReferences and dismissedReferences must contribute distinctly to the hash"
		)
	}

	@Test
	fun `hashEncyclopediaEntry differs when aliases differ`() {
		val base = EntityHasher.hashEncyclopediaEntry(
			id = 2, name = "Robert", entryType = "person",
			text = "text", tags = emptySet(),
			aliases = listOf("Bob"),
		)
		val different = EntityHasher.hashEncyclopediaEntry(
			id = 2, name = "Robert", entryType = "person",
			text = "text", tags = emptySet(),
			aliases = listOf("Bob", "Bobby"),
		)
		assertNotEquals(base, different, "hashEncyclopediaEntry must include aliases in the digest")
	}

	@Test
	fun `hashEncyclopediaEntry differs when excludeFromDictionary differs`() {
		val included = EntityHasher.hashEncyclopediaEntry(
			id = 2, name = "Robert", entryType = "person",
			text = "text", tags = emptySet(),
			aliases = emptyList(),
			excludeFromDictionary = false,
		)
		val excluded = EntityHasher.hashEncyclopediaEntry(
			id = 2, name = "Robert", entryType = "person",
			text = "text", tags = emptySet(),
			aliases = emptyList(),
			excludeFromDictionary = true,
		)
		assertNotEquals(included, excluded, "hashEncyclopediaEntry must include excludeFromDictionary in the digest")
	}

	@Test
	fun `hashEncyclopediaEntry alias order is significant`() {
		// aliases is a List, not a Set - reordering is a meaningful change that should
		// propagate through sync so two clients with reordered aliases don't silently diverge.
		val a = EntityHasher.hashEncyclopediaEntry(
			id = 2, name = "Robert", entryType = "person",
			text = "text", tags = emptySet(),
			aliases = listOf("Bob", "Bobby"),
		)
		val b = EntityHasher.hashEncyclopediaEntry(
			id = 2, name = "Robert", entryType = "person",
			text = "text", tags = emptySet(),
			aliases = listOf("Bobby", "Bob"),
		)
		assertNotEquals(
			a, b,
			"aliases is ordered (List) - reordering must change the hash so reorderings sync"
		)
	}

	@Test
	fun `hashScene differs when tags differ`() {
		val base = sceneHash(tags = emptySet())
		val different = sceneHash(tags = setOf("important"))
		assertNotEquals(base, different, "hashScene must include tags in the digest")
	}

	@Test
	fun `hashScene tags are order-independent`() {
		// Defends against a Set-iteration-order regression - the impl must sort tags before hashing
		val a = sceneHash(tags = linkedSetOf("alpha", "beta", "gamma"))
		val b = sceneHash(tags = linkedSetOf("gamma", "alpha", "beta"))
		assertEquals(a, b, "tags must be sorted before hashing for stability")
	}

	@Test
	fun `hashScene differs when created differs`() {
		val base = sceneHash(created = Instant.fromEpochSeconds(1_000_000))
		val different = sceneHash(created = Instant.fromEpochSeconds(2_000_000))
		assertNotEquals(base, different, "hashScene must include created in the digest")
	}

	@Test
	fun `hashScene null created differs from epoch zero`() {
		// `null` and "epoch 0" are semantically different states (never set vs deliberately
		// set to the epoch) and must hash to different values.
		val nullCreated = sceneHash(created = null)
		val epochZero = sceneHash(created = Instant.fromEpochSeconds(0))
		assertNotEquals(nullCreated, epochZero, "null created must hash differently from epoch 0")
	}

	@Test
	fun `hashScene differs when lastEdited differs`() {
		val base = sceneHash(lastEdited = Instant.fromEpochSeconds(1_000_000))
		val different = sceneHash(lastEdited = Instant.fromEpochSeconds(2_000_000))
		assertNotEquals(base, different, "hashScene must include lastEdited in the digest")
	}

	@Test
	fun `hashScene null lastEdited differs from epoch zero`() {
		val nullLastEdited = sceneHash(lastEdited = null)
		val epochZero = sceneHash(lastEdited = Instant.fromEpochSeconds(0))
		assertNotEquals(
			nullLastEdited, epochZero,
			"null lastEdited must hash differently from epoch 0",
		)
	}

	private fun entryHash(
		aliases: List<String> = emptyList(),
		excludeFromDictionary: Boolean = false,
		images: List<Int> = emptyList(),
	): String = EntityHasher.hashEncyclopediaEntry(
		id = 2,
		name = "The Great Debate",
		entryType = "person",
		text = "Some great content",
		tags = setOf("tag1", "tag2"),
		aliases = aliases,
		excludeFromDictionary = excludeFromDictionary,
		images = images,
	)

	// Test-only defaults. Production [EntityHasher.hashScene] has none on purpose.
	private fun sceneHash(
		id: Int = 2,
		order: Int = 0,
		path: List<Int> = listOf(0, 1),
		name: String = "Test",
		type: ApiSceneType = ApiSceneType.Scene,
		content: String = "Content",
		outline: String = "outline",
		notes: String = "notes",
		archived: Boolean = false,
		confirmedReferences: Set<Int> = emptySet(),
		dismissedReferences: Set<Int> = emptySet(),
		tags: Set<String> = emptySet(),
		created: Instant? = null,
		lastEdited: Instant? = null,
	): String = EntityHasher.hashScene(
		id = id,
		order = order,
		path = path,
		name = name,
		type = type,
		content = content,
		outline = outline,
		notes = notes,
		archived = archived,
		confirmedReferences = confirmedReferences,
		dismissedReferences = dismissedReferences,
		tags = tags,
		created = created,
		lastEdited = lastEdited,
	)
}
