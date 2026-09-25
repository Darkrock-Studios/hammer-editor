package operations

import com.darkrockstudios.apps.hammer.common.data.closeProjectScope
import com.darkrockstudios.apps.hammer.common.data.openProjectScope
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository
import com.darkrockstudios.apps.hammer.operations.NoInput
import com.darkrockstudios.apps.hammer.operations.OperationException
import com.darkrockstudios.apps.hammer.operations.core.Backup
import com.darkrockstudios.apps.hammer.operations.core.Backups
import com.darkrockstudios.apps.hammer.operations.core.Idea
import com.darkrockstudios.apps.hammer.operations.core.IdeaCreateInput
import com.darkrockstudios.apps.hammer.operations.core.IdeaInput
import com.darkrockstudios.apps.hammer.operations.core.IdeaListInput
import com.darkrockstudios.apps.hammer.operations.core.IdeaUpdateInput
import com.darkrockstudios.apps.hammer.operations.core.Ideas
import com.darkrockstudios.apps.hammer.operations.core.ImportFileFormat
import com.darkrockstudios.apps.hammer.operations.core.ProjectCreateInput
import com.darkrockstudios.apps.hammer.operations.core.ProjectImportInput
import com.darkrockstudios.apps.hammer.operations.core.ProjectImported
import com.darkrockstudios.apps.hammer.operations.core.ProjectInput
import com.darkrockstudios.apps.hammer.operations.core.ProjectList
import com.darkrockstudios.apps.hammer.operations.core.ProjectName
import com.darkrockstudios.apps.hammer.operations.core.ProjectRenameInput
import com.darkrockstudios.apps.hammer.operations.core.SceneTree
import org.junit.jupiter.api.Test
import org.koin.core.component.get
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ProjectWriteOperationsTest : KoinOperationsTest() {

	private suspend fun projectNames() = run<NoInput, ProjectList>("project.list", NoInput).projects.map { it.name }

	private suspend fun invalid(block: suspend () -> Unit) =
		assertEquals(OperationException.Kind.InvalidInput, assertFailsWith<OperationException> { block() }.kind)

	@Test
	fun `projects are created, renamed, and deleted`() = onTestThread {
		run<ProjectCreateInput, ProjectName>("project.create", ProjectCreateInput(" Storm Novel "))
		invalid { run<ProjectCreateInput, ProjectName>("project.create", ProjectCreateInput("Storm Novel")) }

		run<ProjectRenameInput, ProjectName>("project.rename", ProjectRenameInput("Storm Novel", "Calm Novel"))
		assertEquals(listOf("Calm Novel"), projectNames())

		run<ProjectInput, ProjectName>("project.delete", ProjectInput("Calm Novel"))
		assertEquals(emptyList(), projectNames())
	}

	@Test
	fun `an open project is not renamed or deleted`() = onTestThread {
		run<ProjectCreateInput, ProjectName>("project.create", ProjectCreateInput("Storm Novel"))
		val def = get<ProjectsRepository>().findProject("Storm Novel")!!
		val scope = openProjectScope(def)

		invalid { run<ProjectRenameInput, ProjectName>("project.rename", ProjectRenameInput("Storm Novel", "Calm Novel")) }
		invalid { run<ProjectInput, ProjectName>("project.delete", ProjectInput("Storm Novel")) }

		closeProjectScope(scope, def)
		assertEquals(listOf("Storm Novel"), projectNames())
	}

	@Test
	fun `a manuscript is imported into a new project`() = onTestThread {
		val manuscript = "# Opening\n\nThe storm came early.\n\n# Landfall\n\nAlice ran.\n".encodeToByteArray()
		val imported = run<ProjectImportInput, ProjectImported>(
			"project.import",
			ProjectImportInput(name = "Storm Novel", format = ImportFileFormat.Markdown, content = manuscript),
		)

		assertEquals(2, imported.scenes)
		val tree = run<ProjectInput, SceneTree>("scene.tree", ProjectInput("Storm Novel"))
		assertEquals(listOf("Opening", "Landfall"), tree.nodes.map { it.name })
		invalid {
			run<ProjectImportInput, ProjectImported>(
				"project.import",
				ProjectImportInput(name = "Empty", format = ImportFileFormat.Markdown, content = ByteArray(0)),
			)
		}
	}

	@Test
	fun `an imported project takes the file's title, made safe for a name`() = onTestThread {
		val manuscript = "# Book: One\n\n## Opening\n\nThe storm came early.\n".encodeToByteArray()
		val imported = run<ProjectImportInput, ProjectImported>(
			"project.import",
			ProjectImportInput(format = ImportFileFormat.Markdown, content = manuscript),
		)
		assertEquals(imported.name, projectNames().single())
	}

	@Test
	fun `a backup is made`() = onTestThread {
		run<ProjectCreateInput, ProjectName>("project.create", ProjectCreateInput("Storm Novel"))
		val backup = run<ProjectInput, Backup>("backup.create", ProjectInput("Storm Novel"))
		assertEquals(listOf(backup), run<ProjectInput, Backups>("backup.list", ProjectInput("Storm Novel")).backups)
	}

	@Test
	fun `ideas are created, updated, archived, and deleted`() = onTestThread {
		val idea = run<IdeaCreateInput, Idea>("idea.create", IdeaCreateInput("A second lighthouse", title = "Sequel"))
		val updated = run<IdeaUpdateInput, Idea>("idea.update", IdeaUpdateInput(idea.id, "A third lighthouse", title = ""))
		assertEquals("A third lighthouse", updated.content)
		assertNull(updated.title)

		assertNotNull(run<IdeaInput, Idea>("idea.archive", IdeaInput(idea.id)).archived)
		assertNull(run<IdeaInput, Idea>("idea.unarchive", IdeaInput(idea.id)).archived)

		run<IdeaInput, Idea>("idea.delete", IdeaInput(idea.id))
		assertEquals(emptyList(), run<IdeaListInput, Ideas>("idea.list", IdeaListInput()).ideas)
		invalid { run<IdeaCreateInput, Idea>("idea.create", IdeaCreateInput(" ")) }
	}
}
