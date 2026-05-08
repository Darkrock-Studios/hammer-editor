package com.darkrockstudios.apps.hammer.utilities

import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.base.http.ApiSceneType
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.descriptors.elementNames
import kotlinx.serialization.serializer
import org.junit.jupiter.api.Test
import kotlin.reflect.KClass
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.time.Instant

/**
 * Defends against the bug class where a new field gets added to an [ApiProjectEntity]
 * subtype but the hasher silently keeps ignoring it (so two clients can never converge
 * on the new data, while no test fails).
 *
 * The pattern in [EntityHasherExtTest] - "extension call must equal direct call" - was
 * a tautology trap: when a field was forgotten on both sides simultaneously, the test
 * still passed because the two paths agreed with each other on a *wrong* answer. These
 * tests assert the stronger invariant: **every serialized field of the DTO must affect
 * the hash**. They use `@Serializable`'s descriptor (not Kotlin reflection) as the
 * source of truth for "what fields exist," so adding a field forces the test author to
 * declare a mutation for it - omitting one fails the coverage assertion immediately.
 */
@OptIn(InternalSerializationApi::class)
class EntityHashSensitivityTest {

	@Test
	fun `every serialized field of SceneEntity affects the hash`() {
		val base = ApiProjectEntity.SceneEntity(
			id = 1,
			sceneType = ApiSceneType.Scene,
			order = 0,
			name = "base",
			path = listOf(0),
			content = "base content",
			outline = "base outline",
			notes = "base notes",
			archived = false,
			confirmedReferences = emptySet(),
			dismissedReferences = emptySet(),
		)
		val mutations = mapOf(
			"id" to base.copy(id = 999),
			"sceneType" to base.copy(sceneType = ApiSceneType.Group),
			"order" to base.copy(order = 999),
			"name" to base.copy(name = "different"),
			"path" to base.copy(path = listOf(99, 99)),
			"content" to base.copy(content = "different"),
			"outline" to base.copy(outline = "different"),
			"notes" to base.copy(notes = "different"),
			"archived" to base.copy(archived = true),
			"confirmedReferences" to base.copy(confirmedReferences = setOf(7)),
			"dismissedReferences" to base.copy(dismissedReferences = setOf(7)),
		)
		assertEveryFieldAffectsHash(base, mutations, ApiProjectEntity.SceneEntity::class)
	}

	@Test
	fun `every serialized field of EncyclopediaEntryEntity affects the hash`() {
		val base = ApiProjectEntity.EncyclopediaEntryEntity(
			id = 1,
			name = "base",
			entryType = "person",
			text = "base text",
			tags = setOf("base-tag"),
			image = null,
			aliases = emptyList(),
		)
		val mutations = mapOf(
			"id" to base.copy(id = 999),
			"name" to base.copy(name = "different"),
			"entryType" to base.copy(entryType = "place"),
			"text" to base.copy(text = "different"),
			"tags" to base.copy(tags = setOf("different")),
			"image" to base.copy(
				image = ApiProjectEntity.EncyclopediaEntryEntity.Image(
					base64 = "abc",
					fileExtension = "png",
				)
			),
			"aliases" to base.copy(aliases = listOf("Bobby")),
		)
		assertEveryFieldAffectsHash(base, mutations, ApiProjectEntity.EncyclopediaEntryEntity::class)
	}

	@Test
	fun `every serialized field of NoteEntity affects the hash`() {
		val base = ApiProjectEntity.NoteEntity(
			id = 1,
			content = "base content",
			created = Instant.fromEpochMilliseconds(0),
			tags = setOf("base-tag"),
		)
		val mutations = mapOf(
			"id" to base.copy(id = 999),
			"content" to base.copy(content = "different"),
			"created" to base.copy(created = Instant.fromEpochMilliseconds(1_000_000)),
			"tags" to base.copy(tags = setOf("different")),
		)
		assertEveryFieldAffectsHash(base, mutations, ApiProjectEntity.NoteEntity::class)
	}

	@Test
	fun `every serialized field of TimelineEventEntity affects the hash`() {
		val base = ApiProjectEntity.TimelineEventEntity(
			id = 1,
			order = 0,
			date = "Year 1",
			content = "base content",
			tags = setOf("base-tag"),
		)
		val mutations = mapOf(
			"id" to base.copy(id = 999),
			"order" to base.copy(order = 999),
			"date" to base.copy(date = "Year 2"),
			"content" to base.copy(content = "different"),
			"tags" to base.copy(tags = setOf("different")),
		)
		assertEveryFieldAffectsHash(base, mutations, ApiProjectEntity.TimelineEventEntity::class)
	}

	@Test
	fun `every serialized field of SceneDraftEntity affects the hash`() {
		val base = ApiProjectEntity.SceneDraftEntity(
			id = 1,
			sceneId = 10,
			created = Instant.fromEpochMilliseconds(0),
			name = "base draft",
			content = "base content",
		)
		val mutations = mapOf(
			"id" to base.copy(id = 999),
			"sceneId" to base.copy(sceneId = 999),
			"created" to base.copy(created = Instant.fromEpochMilliseconds(1_000_000)),
			"name" to base.copy(name = "different"),
			"content" to base.copy(content = "different"),
		)
		assertEveryFieldAffectsHash(base, mutations, ApiProjectEntity.SceneDraftEntity::class)
	}

	private fun <T : ApiProjectEntity> assertEveryFieldAffectsHash(
		base: T,
		mutations: Map<String, T>,
		entityClass: KClass<T>,
	) {
		// Source of truth: the @Serializable descriptor. Adding a field to the DTO will
		// expand this set, immediately failing the coverage assertion below until the
		// test author declares a mutation - which then fails the sensitivity loop until
		// the hasher is updated. No silent passes.
		val declared = entityClass.serializer().descriptor.elementNames.toSet() - EXEMPT_FIELDS
		val covered = mutations.keys

		assertEquals(
			declared, covered,
			"Mutations list for ${entityClass.simpleName} is out of sync with the @Serializable " +
				"descriptor. Add a copy() mutation for each missing field, or add the field to " +
				"EXEMPT_FIELDS if it cannot meaningfully be mutated."
		)

		val baseHash = base.hash()
		mutations.forEach { (fieldName, mutated) ->
			val mutatedHash = mutated.hash()
			assertNotEquals(
				baseHash, mutatedHash,
				"Hash of ${entityClass.simpleName} did not change when '$fieldName' changed - " +
					"the hasher is silently dropping this field, so two clients with different " +
					"values will never converge through sync."
			)
		}
	}

	companion object {
		// `type` is the polymorphic discriminator on ApiProjectEntity - it's effectively a
		// constant for any instance of a given subclass and cannot be meaningfully mutated.
		private val EXEMPT_FIELDS = setOf("type")
	}
}
