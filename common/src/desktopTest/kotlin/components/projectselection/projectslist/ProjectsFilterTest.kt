package components.projectselection.projectslist

import com.darkrockstudios.apps.hammer.common.components.projectselection.ProjectData
import com.darkrockstudios.apps.hammer.common.components.projectselection.projectslist.filterProjects
import com.darkrockstudios.apps.hammer.common.components.storyeditor.metadata.Info
import com.darkrockstudios.apps.hammer.common.components.storyeditor.metadata.ProjectMetadata
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.search.parseQuery
import com.darkrockstudios.apps.hammer.common.fileio.okio.toHPath
import okio.Path.Companion.toPath
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.time.Instant
import com.darkrockstudios.apps.hammer.base.http.projectdata.ProjectData as StoredData

class ProjectsFilterTest {

	private fun project(name: String, tags: Set<String> = emptySet()) = ProjectData(
		definition = ProjectDef(name = name, path = "/projects/$name".toPath().toHPath()),
		metadata = ProjectMetadata(Info(created = Instant.fromEpochMilliseconds(0))),
		storedData = StoredData(tags = tags),
	)

	private val alice = project("Alice In Wonderland", setOf("fantasy", "classic"))
	private val hello = project("Hello World", setOf("draft"))
	private val untagged = project("Untagged Opus")
	private val all = listOf(alice, hello, untagged)

	private fun filter(query: String) = filterProjects(all, parseQuery(query))

	@Test
	fun `empty query returns everything`() {
		assertEquals(all, filter(""))
	}

	@Test
	fun `free text matches project name case-insensitively`() {
		assertEquals(listOf(alice), filter("alice"))
		assertEquals(listOf(hello), filter("WORLD"))
	}

	@Test
	fun `single tag filters by tag substring`() {
		assertEquals(listOf(alice), filter("#fan"))
	}

	@Test
	fun `multiple tags are AND-combined`() {
		assertEquals(listOf(alice), filter("#fantasy #classic"))
		assertEquals(emptyList(), filter("#fantasy #draft"))
	}

	@Test
	fun `text and tag combine`() {
		assertEquals(listOf(alice), filter("alice #fantasy"))
		assertEquals(emptyList(), filter("hello #fantasy"))
	}

	@Test
	fun `stray hash filters nothing`() {
		assertEquals(all, filter("#"))
	}

	@Test
	fun `no match returns empty`() {
		assertEquals(emptyList(), filter("zebra"))
	}
}
