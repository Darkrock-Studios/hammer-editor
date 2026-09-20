package datamigrator

import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.drafts.SceneDraftsDatasource
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EncyclopediaDatasource
import com.darkrockstudios.apps.hammer.common.data.migrator.Migration2_3
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneDatasource
import com.darkrockstudios.apps.hammer.common.fileio.okio.toHPath
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import okio.Path
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import utils.BaseTest
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class Migration2_3Test : BaseTest() {

	private lateinit var ffs: FakeFileSystem
	private lateinit var projDef: ProjectDef

	@BeforeEach
	override fun setup() {
		super.setup()
		ffs = FakeFileSystem()
		val projectPath = "/projects".toPath().div("Test Project")
		ffs.createDirectories(projectPath)
		projDef = ProjectDef(name = "Test Project", path = projectPath.toHPath())
	}

	private fun encyclopediaTypeDir(type: String): Path {
		val dir = projDef.path.toOkioPath()
			.div(EncyclopediaDatasource.ENCYCLOPEDIA_DIRECTORY)
			.div(type)
		ffs.createDirectories(dir)
		return dir
	}

	private fun sceneDraftsDir(sceneId: Int): Path {
		val dir = SceneDatasource.getSceneDirectory(projDef, ffs).toOkioPath()
			.div(SceneDraftsDatasource.DRAFTS_DIR)
			.div(sceneId.toString())
		ffs.createDirectories(dir)
		return dir
	}

	@Test
	fun `renames encyclopedia entry files from hyphen to tilde`() {
		val dir = encyclopediaTypeDir("person")
		ffs.write(dir.div("person-1-Alice.toml")) { writeUtf8("a") }
		ffs.write(dir.div("person-2-Bob.toml")) { writeUtf8("b") }

		Migration2_3(ffs).migrate(projDef)

		assertTrue(ffs.exists(dir.div("person~1~Alice.toml")))
		assertTrue(ffs.exists(dir.div("person~2~Bob.toml")))
		assertFalse(ffs.exists(dir.div("person-1-Alice.toml")))
	}

	@Test
	fun `renames encyclopedia entry images`() {
		val dir = encyclopediaTypeDir("place")
		ffs.write(dir.div("place-7-image.jpg")) { writeUtf8("img") }

		Migration2_3(ffs).migrate(projDef)

		assertTrue(ffs.exists(dir.div("place~7~image.jpg")))
		assertFalse(ffs.exists(dir.div("place-7-image.jpg")))
	}

	@Test
	fun `renames draft files from hyphen to tilde`() {
		val dir = sceneDraftsDir(1)
		ffs.write(dir.div("1-9-First Draft-1729285670.md")) { writeUtf8("a") }

		Migration2_3(ffs).migrate(projDef)

		assertTrue(ffs.exists(dir.div("1~9~First Draft~1729285670.md")))
		assertFalse(ffs.exists(dir.div("1-9-First Draft-1729285670.md")))
	}

	@Test
	fun `canonicalises a legacy name the old rules let through`() {
		// The old rules allowed a trailing space; the datasources encode it away when they build a
		// filename, so the migrated file has to be encoded too or nothing will ever find it again.
		val entryDir = encyclopediaTypeDir("person")
		ffs.write(entryDir.div("person-1-Alice .toml")) { writeUtf8("a") }
		val draftDir = sceneDraftsDir(1)
		ffs.write(draftDir.div("1-9-My Draft -1729285670.md")) { writeUtf8("b") }

		Migration2_3(ffs).migrate(projDef)

		assertTrue(ffs.exists(entryDir.div("person~1~Alice.toml")))
		assertTrue(ffs.exists(draftDir.div("1~9~My Draft~1729285670.md")))
	}

	@Test
	fun `skips a legacy name that encodes to nothing`() {
		val draftDir = sceneDraftsDir(1)
		val allSpaces = draftDir.div("1-9-   -1729285670.md")
		ffs.write(allSpaces) { writeUtf8("b") }

		Migration2_3(ffs).migrate(projDef)

		assertTrue(ffs.exists(allSpaces), "must be left alone rather than renamed to `1~9~~...`")
	}

	@Test
	fun `leaves already migrated files untouched`() {
		val entryDir = encyclopediaTypeDir("person")
		ffs.write(entryDir.div("person~1~Mr. Finch.toml")) { writeUtf8("a") }
		val draftDir = sceneDraftsDir(1)
		ffs.write(draftDir.div("1~9~Act 2, Take 3~1729285670.md")) { writeUtf8("b") }

		Migration2_3(ffs).migrate(projDef)

		assertTrue(ffs.exists(entryDir.div("person~1~Mr. Finch.toml")))
		assertTrue(ffs.exists(draftDir.div("1~9~Act 2, Take 3~1729285670.md")))
	}

	@Test
	fun `skips a rename when the target already exists`() {
		val dir = encyclopediaTypeDir("person")
		ffs.write(dir.div("person-1-Alice.toml")) { writeUtf8("legacy") }
		ffs.write(dir.div("person~1~Alice.toml")) { writeUtf8("current") }

		Migration2_3(ffs).migrate(projDef)

		assertTrue(ffs.exists(dir.div("person-1-Alice.toml")), "legacy file must be left alone")
		assertTrue(ffs.exists(dir.div("person~1~Alice.toml")))
	}

	@Test
	fun `a project with neither directory migrates cleanly`() {
		Migration2_3(ffs).migrate(projDef)
	}
}
