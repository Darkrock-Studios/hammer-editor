package com.darkrockstudios.apps.hammer.integration.scenarios

import com.darkrockstudios.apps.hammer.common.data.CResult
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsStore
import com.darkrockstudios.apps.hammer.common.data.isSuccess
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.GroupDef
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectTree
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository
import com.darkrockstudios.apps.hammer.common.data.sync.accountsync.ClientAccountSynchronizer
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.SyncLogLevel
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.SyncLogMessage
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import com.darkrockstudios.apps.hammer.common.util.StrRes
import com.darkrockstudios.apps.hammer.integration.RoundTripTestBase
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okio.Path
import okio.Path.Companion.toPath
import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.StringResource
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.koin.core.context.loadKoinModules
import org.koin.core.context.unloadKoinModules
import org.koin.dsl.module
import org.koin.test.get
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Project groups across two installs of one account, through the real account synchronizer and a
 * real server. As in [IdeasTwoInstallSyncTest], an install is a projects root.
 */
class GroupsTwoInstallSyncTest : RoundTripTestBase() {

	private val installA: Path = "/clientA/projects".toPath()
	private val installB: Path = "/clientB/projects".toPath()

	private val settingsStore: GlobalSettingsStore get() = get()
	private val projectsRepository: ProjectsRepository get() = get()

	/** Sync log lines name their string resource, so a run's log reads without Compose Resources. */
	private val keyedStrRes = module {
		single<StrRes> {
			object : StrRes {
				override suspend fun get(str: StringResource): String = str.key
				override suspend fun get(str: StringResource, vararg args: Any): String =
					"${str.key}(${args.joinToString()})"
				override suspend fun get(str: PluralStringResource, quantity: Int, vararg args: Any): String =
					"${str.key}[$quantity](${args.joinToString()})"
			}
		}
	}

	@BeforeEach
	fun loadKeyedStrRes() {
		loadKoinModules(keyedStrRes)
	}

	@AfterEach
	fun unloadKeyedStrRes() {
		unloadKoinModules(keyedStrRes)
	}

	private suspend fun switchInstall(root: Path) {
		fileSystem.createDirectories(root)
		settingsStore.updateSettings { it.copy(projectsDirectory = root.toString()) }
		settingsStore.updateServerSettings(makeServerSettings())
		// ProjectsRepository picks up the new root from a settings collector on the main dispatcher.
		withTimeout(5_000) {
			while (projectsRepository.getProjectsDirectory().toOkioPath() != root) delay(10)
		}
	}

	private var syncCount = 0

	/** Runs account sync on [install]; fails on an unsuccessful sync or any ERROR line. */
	private suspend fun sync(install: Path, label: String): List<SyncLogMessage> {
		switchInstall(install)
		val log = mutableListOf<SyncLogMessage>()
		val ok = get<ClientAccountSynchronizer>().syncProjects(onLog = { log += it }, onUnauthorized = {})
		syncCount++
		println("---- sync #$syncCount $label ----")
		log.forEach { println("  [${it.level}] ${it.message}") }
		assertTrue(ok, "$label: account sync failed")
		val errors = log.filter { it.level == SyncLogLevel.ERROR }
		assertEquals(emptyList(), errors, "$label: sync logged errors")
		return log
	}

	/** Groups end in "/", projects do not; each is its path from the projects root. */
	private fun treeLines(tree: ProjectTree = projectsRepository.getProjectTree()): Set<String> {
		val groups = tree.groups.map { tree.pathNames(it.id).joinToString("/") + "/" }
		val projects = tree.projects.map { project ->
			(tree.pathNames(tree.placement[project.id]) + project.name).joinToString("/")
		}
		return (groups + projects).toSet()
	}

	private suspend fun treeOf(install: Path): Set<String> {
		switchInstall(install)
		return treeLines()
	}

	private fun group(path: String): GroupDef {
		val tree = projectsRepository.getProjectTree()
		val group = tree.groups.firstOrNull { tree.pathNames(it.id).joinToString("/") == path }
		return assertNotNull(group, "no group at '$path' in ${treeLines(tree)}")
	}

	private fun <T> CResult<T>.orFail(): T {
		assertTrue(isSuccess(this), "operation failed: $this")
		return data
	}

	private suspend fun assertSameTree(expected: Set<String>) {
		assertEquals(expected, treeOf(installA), "install A tree")
		assertEquals(expected, treeOf(installB), "install B tree")
	}

	@Test
	@Timeout(value = 120)
	fun `group create, move, rename, delete, and a rename conflict converge across two installs`() = runBlocking {
		// 1. A creates nested groups with a project in the inner one; B pulls the same tree.
		switchInstall(installA)
		val drafts = projectsRepository.createGroup("Drafts", null).orFail()
		val sciFi = projectsRepository.createGroup("SciFi", drafts).orFail()
		assertTrue(isSuccess(projectsRepository.createProject("Starship", seedDefaultLanguage = false, group = sciFi)))
		assertTrue(isSuccess(projectsRepository.createProject("Loose", seedDefaultLanguage = false)))
		sync(installA, "check 1: A pushes new groups")
		sync(installB, "check 1: B pulls")
		assertSameTree(setOf("Drafts/", "Drafts/SciFi/", "Drafts/SciFi/Starship", "Loose"))

		// 2. B moves a project folder into a group with the file manager; A picks the move up.
		switchInstall(installB)
		fileSystem.atomicMove(installB / "Loose", installB / "Drafts" / "Loose")
		sync(installB, "check 2: B pushes folder move")
		sync(installA, "check 2: A pulls")
		assertSameTree(setOf("Drafts/", "Drafts/SciFi/", "Drafts/SciFi/Starship", "Drafts/Loose"))

		// 3. A renames a group; B follows.
		switchInstall(installA)
		assertTrue(isSuccess(projectsRepository.renameGroup(group("Drafts/SciFi"), "Science Fiction")))
		sync(installA, "check 3: A pushes rename")
		sync(installB, "check 3: B pulls")
		assertSameTree(setOf("Drafts/", "Drafts/Science Fiction/", "Drafts/Science Fiction/Starship", "Drafts/Loose"))

		// 4. A deletes a non-empty group; its contents move up one level on B.
		switchInstall(installA)
		assertTrue(isSuccess(projectsRepository.deleteGroup(group("Drafts"))))
		sync(installA, "check 4: A pushes delete")
		sync(installB, "check 4: B pulls")
		assertSameTree(setOf("Science Fiction/", "Science Fiction/Starship", "Loose"))

		// 5. Both rename the same group; A syncs first, so the server holds A's name and B takes it.
		switchInstall(installA)
		assertTrue(isSuccess(projectsRepository.renameGroup(group("Science Fiction"), "Space Opera")))
		switchInstall(installB)
		assertTrue(isSuccess(projectsRepository.renameGroup(group("Science Fiction"), "Hard SF")))
		sync(installA, "check 5: A pushes its rename")
		sync(installB, "check 5: B with a conflicting rename")
		sync(installA, "check 5: A settles")
		assertSameTree(setOf("Space Opera/", "Space Opera/Starship", "Loose"))
	}
}
