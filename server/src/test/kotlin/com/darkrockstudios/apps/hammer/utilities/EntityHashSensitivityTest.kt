package com.darkrockstudios.apps.hammer.utilities

import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.base.http.ApiSceneType
import com.darkrockstudios.apps.hammer.base.http.projectdata.ProjectData
import com.darkrockstudios.apps.hammer.base.http.projectdata.ProjectTheme
import com.darkrockstudios.apps.hammer.base.http.projectdata.WordCountGoal
import com.darkrockstudios.apps.hammer.base.http.synchronizer.ProjectDataHasher
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.StructureKind
import org.junit.jupiter.api.Test
import kotlin.reflect.KClass
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.time.Instant

/**
 * Defends against the bug class where a new field gets added to a synced DTO but the hasher
 * silently keeps ignoring it - so two clients can never converge on the new data, while no
 * test fails.
 *
 * The pattern in [EntityHasherExtTest] - "extension call must equal direct call" - is a
 * tautology trap: when a field is forgotten on both sides simultaneously, the two paths still
 * agree on a *wrong* answer and the test passes. This suite asserts the stronger invariant:
 * **every serialized field of every synced DTO must affect its hash.**
 *
 * It is driven entirely by `@Serializable` descriptors (the same source of truth serialization
 * uses), so the structure can't drift from what actually rides the sync wire. Three holes that
 * a hand-written "differs when X differs" test leaves open are closed here:
 *
 *  1. **New field** - [leafPaths] reads the descriptor, so adding a field expands the declared
 *     set and immediately fails the coverage assertion until a mutation is declared; the
 *     sensitivity loop then fails until the hasher actually folds the field in.
 *  2. **New field on a nested DTO** ([ProjectTheme], [WordCountGoal], `EncyclopediaEntryEntity.Image`)
 *     - [leafPaths] recurses into owned nested classes, so `theme.tertiary` or `image.caption`
 *     is a tracked path, not an invisible sub-field of a single `theme`/`image` element.
 *  3. **New entity type** - [`every ApiProjectEntity Type has a sensitivity spec`] ties the spec
 *     list to the [ApiProjectEntity.Type] registry, so a new subtype can't ship without a spec.
 */
class EntityHashSensitivityTest {

	@Test
	fun `every serialized field affects the hash, recursing into nested DTOs`() {
		allSpecs.forEach(::assertEveryFieldAffectsHash)
	}

	@Test
	fun `every ApiProjectEntity Type has a sensitivity spec`() {
		val declaredTypes = ApiProjectEntity.Type.entries.toSet()
		val coveredTypes = allSpecs.mapNotNull { it.entityType }.toSet()
		assertEquals(
			declaredTypes, coveredTypes,
			"An ApiProjectEntity Type has no hash-sensitivity spec. Add a Spec(...) for the new " +
				"entity subtype to `allSpecs` so its fields are structurally checked against its hasher."
		)
	}

	private fun assertEveryFieldAffectsHash(spec: Spec<*>) {
		// Source of truth: the @Serializable descriptor (flattened through owned nested DTOs).
		// Adding a field anywhere in the tree expands this set, failing the coverage assertion
		// until a mutation is declared - which then fails the sensitivity loop until the hasher
		// is updated. No silent passes.
		val declared = leafPaths(spec.serializer.descriptor) - EXEMPT_FIELDS
		assertEquals(
			declared, spec.mutations.keys,
			"Mutations for ${spec.klass.simpleName} are out of sync with its @Serializable descriptor. " +
				"Add a copy() mutation for each missing path (nested fields use dot paths like " +
				"'theme.primary'), or add the path to EXEMPT_FIELDS if it cannot be meaningfully mutated."
		)

		val baseHash = spec.baseHash
		spec.mutatedHashes().forEach { (path, mutatedHash) ->
			assertNotEquals(
				baseHash, mutatedHash,
				"Hash of ${spec.klass.simpleName} did not change when '$path' changed - the hasher is " +
					"silently dropping this field, so two clients with different values will never " +
					"converge through sync."
			)
		}
	}

	/**
	 * Flattens a descriptor into dot-separated field paths, recursing into owned nested DTOs so
	 * their sub-fields are tracked individually. A nested owned class contributes both its own
	 * path (the presence / whole-object swap, which matters for nullable DTOs) and one path per
	 * leaf beneath it. Collections, enums, primitives and foreign types (e.g. [Instant]) are leaves.
	 */
	private fun leafPaths(descriptor: SerialDescriptor, prefix: String = ""): Set<String> {
		val paths = linkedSetOf<String>()
		for (i in 0 until descriptor.elementsCount) {
			val name = descriptor.getElementName(i)
			val path = if (prefix.isEmpty()) name else "$prefix.$name"
			val child = descriptor.getElementDescriptor(i)
			if (child.kind == StructureKind.CLASS &&
				child.serialName.removeSuffix("?").startsWith(OWNED_PACKAGE)
			) {
				paths += path
				paths += leafPaths(child, path)
			} else {
				paths += path
			}
		}
		return paths
	}

	private class Spec<T : Any>(
		val klass: KClass<T>,
		val serializer: KSerializer<T>,
		/** Non-null for [ApiProjectEntity] subtypes; ties the spec to the Type registry. Null for standalone DTOs. */
		val entityType: ApiProjectEntity.Type?,
		private val base: T,
		private val hashOf: (T) -> String,
		val mutations: Map<String, T>,
	) {
		val baseHash: String get() = hashOf(base)
		fun mutatedHashes(): Map<String, String> = mutations.mapValues { hashOf(it.value) }
	}

	// ---------------------------------------------------------------------------------------
	// Specs - one per synced DTO. `base` populates every field (including nested) with a
	// non-default value so each mutation is a genuine, isolated change.
	// ---------------------------------------------------------------------------------------

	private val sceneSpec = run {
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
			tags = setOf("base-tag"),
			created = Instant.fromEpochMilliseconds(1_000_000),
			lastEdited = Instant.fromEpochMilliseconds(2_000_000),
		)
		Spec(
			klass = ApiProjectEntity.SceneEntity::class,
			serializer = ApiProjectEntity.SceneEntity.serializer(),
			entityType = ApiProjectEntity.Type.SCENE,
			base = base,
			hashOf = { it.hash() },
			mutations = mapOf(
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
				"tags" to base.copy(tags = setOf("important")),
				"created" to base.copy(created = Instant.fromEpochMilliseconds(9_000_000)),
				"lastEdited" to base.copy(lastEdited = Instant.fromEpochMilliseconds(9_000_000)),
			),
		)
	}

	private val noteSpec = run {
		val base = ApiProjectEntity.NoteEntity(
			id = 1,
			content = "base content",
			created = Instant.fromEpochMilliseconds(0),
			tags = setOf("base-tag"),
		)
		Spec(
			klass = ApiProjectEntity.NoteEntity::class,
			serializer = ApiProjectEntity.NoteEntity.serializer(),
			entityType = ApiProjectEntity.Type.NOTE,
			base = base,
			hashOf = { it.hash() },
			mutations = mapOf(
				"id" to base.copy(id = 999),
				"content" to base.copy(content = "different"),
				"created" to base.copy(created = Instant.fromEpochMilliseconds(1_000_000)),
				"tags" to base.copy(tags = setOf("different")),
			),
		)
	}

	private val timelineSpec = run {
		val base = ApiProjectEntity.TimelineEventEntity(
			id = 1,
			order = 0,
			date = "Year 1",
			content = "base content",
			tags = setOf("base-tag"),
		)
		Spec(
			klass = ApiProjectEntity.TimelineEventEntity::class,
			serializer = ApiProjectEntity.TimelineEventEntity.serializer(),
			entityType = ApiProjectEntity.Type.TIMELINE_EVENT,
			base = base,
			hashOf = { it.hash() },
			mutations = mapOf(
				"id" to base.copy(id = 999),
				"order" to base.copy(order = 999),
				"date" to base.copy(date = "Year 2"),
				"content" to base.copy(content = "different"),
				"tags" to base.copy(tags = setOf("different")),
			),
		)
	}

	private val encyclopediaSpec = run {
		val base = ApiProjectEntity.EncyclopediaEntryEntity(
			id = 1,
			name = "base",
			entryType = "person",
			text = "base text",
			tags = setOf("base-tag"),
			// Non-null so the nested image.* sub-fields can be mutated individually.
			image = ApiProjectEntity.EncyclopediaEntryEntity.Image(base64 = "abc", fileExtension = "png"),
			aliases = listOf("Bob"),
		)
		Spec(
			klass = ApiProjectEntity.EncyclopediaEntryEntity::class,
			serializer = ApiProjectEntity.EncyclopediaEntryEntity.serializer(),
			entityType = ApiProjectEntity.Type.ENCYCLOPEDIA_ENTRY,
			base = base,
			hashOf = { it.hash() },
			mutations = mapOf(
				"id" to base.copy(id = 999),
				"name" to base.copy(name = "different"),
				"entryType" to base.copy(entryType = "place"),
				"text" to base.copy(text = "different"),
				"tags" to base.copy(tags = setOf("different")),
				"image" to base.copy(image = null),
				"image.base64" to base.copy(image = base.image!!.copy(base64 = "abcd")),
				"image.fileExtension" to base.copy(image = base.image!!.copy(fileExtension = "jpg")),
				"aliases" to base.copy(aliases = listOf("Bobby")),
				"excludeFromDictionary" to base.copy(excludeFromDictionary = true),
			),
		)
	}

	private val draftSpec = run {
		val base = ApiProjectEntity.SceneDraftEntity(
			id = 1,
			sceneId = 10,
			created = Instant.fromEpochMilliseconds(0),
			name = "base draft",
			content = "base content",
		)
		Spec(
			klass = ApiProjectEntity.SceneDraftEntity::class,
			serializer = ApiProjectEntity.SceneDraftEntity.serializer(),
			entityType = ApiProjectEntity.Type.SCENE_DRAFT,
			base = base,
			hashOf = { it.hash() },
			mutations = mapOf(
				"id" to base.copy(id = 999),
				"sceneId" to base.copy(sceneId = 999),
				"created" to base.copy(created = Instant.fromEpochMilliseconds(1_000_000)),
				"name" to base.copy(name = "different"),
				"content" to base.copy(content = "different"),
			),
		)
	}

	// Synced per-project settings. NOT an ApiProjectEntity - it has its own [ProjectDataHasher],
	// which is exactly why it needs its own structural guard (and recursion over theme/wordCountGoal).
	private val projectDataSpec = run {
		val base = ProjectData(
			authorName = "base author",
			theme = ProjectTheme(primary = "p", secondary = "s"),
			wordCountGoal = WordCountGoal(cadence = WordCountGoal.Cadence.DAY, count = 100),
			tags = setOf("base-tag"),
			language = "en-US",
		)
		Spec(
			klass = ProjectData::class,
			serializer = ProjectData.serializer(),
			entityType = null,
			base = base,
			hashOf = { ProjectDataHasher.hash(it) },
			mutations = mapOf(
				"authorName" to base.copy(authorName = "different"),
				"theme" to base.copy(theme = null),
				"theme.primary" to base.copy(theme = base.theme!!.copy(primary = "different")),
				"theme.secondary" to base.copy(theme = base.theme!!.copy(secondary = "different")),
				"wordCountGoal" to base.copy(wordCountGoal = null),
				"wordCountGoal.cadence" to base.copy(wordCountGoal = base.wordCountGoal!!.copy(cadence = WordCountGoal.Cadence.WEEK)),
				"wordCountGoal.count" to base.copy(wordCountGoal = base.wordCountGoal!!.copy(count = 999)),
				"tags" to base.copy(tags = setOf("different")),
				"language" to base.copy(language = "fr"),
				"encyclopediaDictionary" to base.copy(encyclopediaDictionary = false),
			),
		)
	}

	private val allSpecs: List<Spec<*>> = listOf(
		sceneSpec, noteSpec, timelineSpec, encyclopediaSpec, draftSpec, projectDataSpec,
	)

	companion object {
		// `type` is the polymorphic discriminator on ApiProjectEntity - effectively constant for a
		// given subclass and not meaningfully mutable.
		private val EXEMPT_FIELDS = setOf("type")

		// Recurse into nested classes only when they're our own DTOs; Instant, enums and
		// collections must stay leaves.
		private const val OWNED_PACKAGE = "com.darkrockstudios.apps.hammer"
	}
}
