package repositories.duplicateids

import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.duplicateids.DuplicateIdKind
import com.darkrockstudios.apps.hammer.common.data.duplicateids.DuplicateIdSet
import com.darkrockstudios.apps.hammer.common.data.duplicateids.ProjectFileRationalizer
import com.darkrockstudios.apps.hammer.common.dependencyinjection.createTomlSerializer
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import com.darkrockstudios.apps.hammer.common.fileio.okio.toHPath
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import net.peanuuutz.tomlkt.Toml
import okio.Path
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import utils.BaseTest
import kotlin.test.assertNotNull

abstract class RationalizerTestBase : BaseTest() {

	protected lateinit var ffs: FakeFileSystem
	protected lateinit var toml: Toml
	protected lateinit var rationalizer: ProjectFileRationalizer

	protected val projectDef = ProjectDef(
		name = "Test Project",
		path = "/projects/Test Project".toPath().toHPath(),
	)
	protected val projectDir: Path get() = projectDef.path.toOkioPath()

	@BeforeEach
	override fun setup() {
		super.setup()
		setupKoin()
		ffs = FakeFileSystem()
		ffs.createDirectories(projectDir)
		toml = createTomlSerializer()
		rationalizer = ProjectFileRationalizer(projectDef, ffs, toml)
	}

	@AfterEach
	override fun tearDown() {
		super.tearDown()
		ffs.checkNoOpenFiles()
	}

	protected fun write(relative: String, content: String): Path {
		val path = projectDir / relative
		ffs.createDirectories(path.parent!!)
		ffs.write(path) { writeUtf8(content) }
		return path
	}

	protected fun read(relative: String): String = ffs.read(projectDir / relative) { readUtf8() }

	protected fun exists(relative: String): Boolean = ffs.exists(projectDir / relative)

	/** Files under [relative], as paths relative to it. */
	protected fun filesIn(relative: String): List<String> {
		val dir = projectDir / relative
		return ffs.listRecursively(dir)
			.filter { ffs.metadata(it).isRegularFile }
			.map { it.relativeTo(dir).segments.joinToString("/") }
			.sorted()
			.toList()
	}

	protected fun onlySet(kind: DuplicateIdKind): DuplicateIdSet =
		assertNotNull(rationalizer.findDuplicates().singleOrNull { it.kind == kind })

	protected fun DuplicateIdSet.pathOf(relative: String): HPath =
		assertNotNull(copies.firstOrNull { it.relativePath == relative }).path

	protected fun ids(vararg next: Int): suspend () -> Int {
		val remaining = next.toMutableList()
		return { remaining.removeAt(0) }
	}

	protected val noIds: suspend () -> Int = { error("No new id expected") }
}
