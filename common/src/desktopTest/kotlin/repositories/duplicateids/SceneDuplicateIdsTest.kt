package repositories.duplicateids

import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.data.duplicateids.DuplicateIdKind
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneDatasource
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SceneDuplicateIdsTest : RationalizerTestBase() {

	private fun mkdir(relative: String) = ffs.createDirectories(projectDir / relative)

	private fun treeIds(): List<Int> {
		val root = SceneItem(projectDef, SceneItem.Type.Root, SceneItem.ROOT_ID, "", 0)
		return SceneDatasource(projectDef, ffs).loadSceneTree(root).map { it.value.id }.filter { it != SceneItem.ROOT_ID }
	}

	@Test
	fun `Identical legacy and current scene files resolve without asking`() = runTest {
		write("scenes/1-Duel-12.md", "En garde")
		write("scenes/1~Duel~12.md", "En garde")

		val resolution = rationalizer.resolveSafeDuplicates()

		assertTrue(resolution.remaining.isEmpty())
		assertEquals(listOf("1~Duel~12.md"), filesIn("scenes"))
	}

	@Test
	fun `Identical scenes in different groups are left for the user`() = runTest {
		write("scenes/0~Part One~3/0~Duel~12.md", "En garde")
		write("scenes/1~Part Two~4/0~Duel~12.md", "En garde")

		val set = rationalizer.resolveSafeDuplicates().remaining.single()

		assertEquals(DuplicateIdKind.Scene, set.kind)
		assertEquals(12, set.id)
		assertEquals(2, set.copies.size)
	}

	@Test
	fun `Two different scenes created offline with one id are both kept`() = runTest {
		write("scenes/0~Prologue~12.md", "Once upon a time")
		write("scenes/1~Duel~12.md", "En garde")
		val set = onlySet(DuplicateIdKind.Scene)

		val dirty = rationalizer.resolve(set, keep = set.copies.map { it.path }.toSet(), claimId = ids(13))

		assertTrue(dirty.isEmpty())
		assertEquals(listOf("0~Prologue~12.md", "1~Duel~13.md"), filesIn("scenes"))
		assertEquals(listOf(12, 13), treeIds())
	}

	@Test
	fun `Three copies across active and archived, keeping the archived one`() = runTest {
		write("scenes/0~Duel~12.md", "Draft one")
		write("scenes/0~Part~3/0~Duel~12.md", "Draft two")
		write("scenes/.archived/Duel-12.md", "The archived take")
		val set = onlySet(DuplicateIdKind.Scene)
		assertEquals(3, set.copies.size)
		assertTrue(set.copies.last().archived)

		val dirty = rationalizer.resolve(set, keep = setOf(set.copies.last().path), claimId = noIds)

		assertEquals(setOf(12), dirty)
		assertEquals(listOf(".archived/Duel~12.md"), filesIn("scenes"))
		assertEquals("The archived take", read("scenes/.archived/Duel~12.md"))
		assertEquals(listOf(3), treeIds())
	}

	@Test
	fun `Legacy and current copies of a group merge their scenes without asking`() = runTest {
		write("scenes/0-Part-3/0-Arrival-10.md", "Arrival")
		write("scenes/0~Part~3/0~Duel~12.md", "En garde")

		val resolution = rationalizer.resolveSafeDuplicates()

		// The legacy group sorts first, so it is the one in use and the other's scenes join it.
		assertTrue(resolution.remaining.isEmpty())
		assertEquals(listOf("0~Part~3/0-Arrival-10.md", "0~Part~3/1~Duel~12.md"), filesIn("scenes"))
		assertEquals(setOf(12), resolution.markDirty)
		assertEquals(listOf(3, 10, 12), treeIds())
	}

	@Test
	fun `Discarding a group moves its scenes into the kept group`() = runTest {
		write("scenes/0~Part One~3/0~Arrival~10.md", "Arrival")
		write("scenes/1~Part One Copy~3/0~Duel~12.md", "En garde")
		write("scenes/1~Part One Copy~3/1~Aftermath~14.md", "After")
		val set = onlySet(DuplicateIdKind.Scene)
		assertTrue(set.copies.all { it.isGroup })

		val dirty = rationalizer.resolve(set, keep = setOf(set.inUse.path), claimId = noIds)

		assertEquals(setOf(12, 14), dirty)
		assertEquals(
			listOf("0~Part One~3/0~Arrival~10.md", "0~Part One~3/1~Duel~12.md", "0~Part One~3/2~Aftermath~14.md"),
			filesIn("scenes"),
		)
		assertTrue(rationalizer.findDuplicates().isEmpty())
	}

	@Test
	fun `A scene merged from a discarded group can still share an id with a kept scene`() = runTest {
		write("scenes/0~Part~3/0~Duel~12.md", "Kept take")
		write("scenes/1~Part Copy~3/0~Duel~12.md", "Other take")
		val groups = rationalizer.findDuplicates().single { it.id == 3 }

		rationalizer.resolve(groups, keep = setOf(groups.inUse.path), claimId = noIds)

		assertEquals(listOf("0~Part~3/0~Duel~12.md", "0~Part~3/1~Duel~12.md"), filesIn("scenes"))
		val scenes = onlySet(DuplicateIdKind.Scene)
		assertEquals(12, scenes.id)
		assertEquals(listOf("Kept take", "Other take"), scenes.copies.map { it.text })
	}

	@Test
	fun `Keeping a group nested inside its duplicate lifts the scenes out instead of into itself`() = runTest {
		write("scenes/0~Part~3/0~Part~3/0~Duel~12.md", "En garde")
		write("scenes/0~Part~3/1~Arrival~10.md", "Arrival")
		val set = onlySet(DuplicateIdKind.Scene)
		val inner = set.copies.single { it.relativePath == "scenes/0~Part~3/0~Part~3" }

		rationalizer.resolve(set, keep = setOf(inner.path), claimId = noIds)

		assertEquals(listOf("1~Part~3/0~Duel~12.md", "2~Arrival~10.md"), filesIn("scenes"))
		assertTrue(rationalizer.findDuplicates().isEmpty())
	}

	@Test
	fun `Scanning an empty project creates no directories`() = runTest {
		rationalizer.resolveSafeDuplicates()

		assertEquals(emptyList(), ffs.list(projectDir))
	}
}
