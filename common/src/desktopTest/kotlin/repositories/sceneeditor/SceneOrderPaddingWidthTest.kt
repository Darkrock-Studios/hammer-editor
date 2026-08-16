package repositories.sceneeditor

import com.darkrockstudios.apps.hammer.common.data.InsertPosition
import com.darkrockstudios.apps.hammer.common.data.MoveRequest
import com.darkrockstudios.apps.hammer.common.data.SceneContent
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneDatasource
import com.darkrockstudios.apps.hammer.common.fileio.okio.toHPath
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.random.Random
import kotlin.test.assertEquals

/**
 * Scene content is read and written through a path built from the tree, so a name the tree
 * derives differently than the one on disk silently retargets every content operation: reads
 * land on nothing and come back empty, writes create a rival file.
 *
 * Order numbers are a contiguous 0-based sequence, so the zero-padded order field must be as
 * wide as the highest order, not as wide as the child count. Those two disagree at every power
 * of ten, which left a directory of exactly ten children unreadable.
 */
class SceneOrderPaddingWidthTest : SceneRepositoryTestBase() {

	/** Scenes whose computed content path points at a file that is not there. */
	private fun unreadableScenes(): List<String> = repo.getSceneTree()
		.filter { !it.value.isRootScene }
		.filter { !ffs.exists(repo.resolveSceneContentPath(it.value).toOkioPath()) }
		.map { "id=${it.value.id} '${it.value.name}'" }

	private fun computedPathsMissingOnDisk(): List<String> = repo.getSceneTree()
		.filter { !it.value.isRootScene }
		.filter { !ffs.exists(repo.getSceneFilePath(it.value.id).toOkioPath()) }
		.map { "id=${it.value.id} '${it.value.name}'" }

	@Test
	fun `every scene stays readable at each child count across a padding boundary`() = runBlocking {
		// Project 1's Group 2 ships with 3 children; grow it one at a time well past ten.
		val group = repo.getSceneItemFromId(2) ?: error("group 2 missing")

		val unreadableAtCount = mutableMapOf<Int, List<String>>()
		repeat(10) { i ->
			repo.createScene(group, "Added $i")
			val childCount = 4 + i
			val unreadable = computedPathsMissingOnDisk()
			if (unreadable.isNotEmpty()) unreadableAtCount[childCount] = unreadable
		}

		assertEquals(emptyMap(), unreadableAtCount, "child counts whose scenes became unreadable")
	}

	@Test
	fun `a group of exactly ten children keeps its content`() = runBlocking {
		val group = repo.getSceneItemFromId(2) ?: error("group 2 missing")
		repeat(7) { repo.createScene(group, "Added $it") }

		// Ten children is the count where the order width and the child width disagree.
		val groupDir = sceneDatasource.resolveScenePathFromFilesystem(2)!!.toOkioPath()
		assertEquals(10, ffs.list(groupDir).size, "group should hold exactly ten children")

		repo.getSceneTree()
			.filter { it.value.type == SceneItem.Type.Scene }
			.forEach { node ->
				val path = sceneDatasource.resolveScenePathFromFilesystem(node.value.id)!!
				ffs.write(path.toOkioPath()) { writeUtf8("content of ${node.value.id}") }
			}

		val readBack = repo.getSceneTree()
			.filter { it.value.type == SceneItem.Type.Scene }
			.associate { it.value.id to repo.loadSceneMarkdownRaw(it.value) }

		val empty = readBack.filterValues { it.isBlank() }.keys
		assertEquals(emptySet(), empty, "scenes that read back empty")
	}

	@Test
	fun `saving into a group of ten children does not create a rival file`() = runBlocking {
		val group = repo.getSceneItemFromId(2) ?: error("group 2 missing")
		repeat(7) { repo.createScene(group, "Added $it") }

		val groupDir = sceneDatasource.resolveScenePathFromFilesystem(2)!!.toOkioPath()
		val before = ffs.list(groupDir).size

		val scene = repo.getSceneItemFromId(3) ?: error("scene 3 missing")
		repo.storeSceneMarkdownRaw(SceneContent(scene, "newly written prose"))

		assertEquals(before, ffs.list(groupDir).size, "a save must overwrite, never add a file")
		assertEquals("newly written prose", repo.loadSceneMarkdownRaw(scene))
	}

	@Test
	fun `rationalizeTree and a reload resolve a duplicate id to the same file`() = runBlocking {
		val group = repo.getSceneItemFromId(2) ?: error("group 2 missing")
		repeat(7) { repo.createScene(group, "Added $it") }

		val groupDir = sceneDatasource.resolveScenePathFromFilesystem(2)!!.toOkioPath()
		val realPath = sceneDatasource.resolveScenePathFromFilesystem(3)!!.toOkioPath()
		ffs.write(realPath) { writeUtf8("the copy the tree was loaded from") }

		// A second file claiming scene 3, of the shape a mis-padded save used to leave behind.
		ffs.write(groupDir.div("0${realPath.name}")) { writeUtf8("a rival copy") }

		// Both the sync pass and a fresh load must settle on the name-first path. The reload goes
		// through a new datasource, the way relaunching the app rebuilds the index from disk.
		val reloaded = SceneDatasource(projectDef, ffs)
		val reloadResolved = reloaded.resolveScenePathFromFilesystem(3)!!.toOkioPath().name
		val fromDir = reloaded.getGroupChildPathsById(groupDir.toHPath())[3]!!
			.toOkioPath().name

		assertEquals(reloadResolved, fromDir, "every id lookup must agree on which file owns the id")
	}

	@Test
	fun `computed paths survive random editing sessions`() = runBlocking {
		val rng = Random(20260815)

		repeat(400) {
			val tree = repo.getSceneTree()
			val all = tree.filter { !it.value.isRootScene }
			val groups = tree.filter { it.value.type == SceneItem.Type.Group || it.value.isRootScene }
			val scenes = all.filter { it.value.type == SceneItem.Type.Scene }

			when (rng.nextInt(10)) {
				in 0..3 -> {
					val target = groups.random(rng)
					repo.createScene(if (target.value.isRootScene) null else target.value, "S$it")
				}

				4 -> {
					val target = groups.random(rng)
					repo.createGroup(if (target.value.isRootScene) null else target.value, "G$it")
				}

				in 5..6 -> {
					if (scenes.isNotEmpty()) repo.deleteScene(scenes.random(rng).value)
				}

				else -> {
					if (all.size >= 2) {
						val mover = all.random(rng)
						val candidates = all.filter { target ->
							target.value.id != mover.value.id &&
								!tree.isAncestorOf(mover.index, target.index)
						}
						if (candidates.isNotEmpty()) {
							val target = candidates.random(rng)
							repo.moveScene(
								MoveRequest(
									id = mover.value.id,
									toPosition = InsertPosition(
										coords = tree.getCoordinatesFor(target),
										before = rng.nextBoolean(),
									),
								)
							)
						}
					}
				}
			}

			assertEquals(emptyList(), unreadableScenes(), "unreadable after step $it")
		}
	}
}
