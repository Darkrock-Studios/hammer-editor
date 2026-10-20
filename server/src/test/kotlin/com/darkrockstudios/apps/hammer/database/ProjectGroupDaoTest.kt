package com.darkrockstudios.apps.hammer.database

import com.darkrockstudios.apps.hammer.base.GroupId
import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.e2e.util.SharedPostgresTestDatabase
import com.darkrockstudios.apps.hammer.utils.BaseTest
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProjectGroupDaoTest : BaseTest() {

	private lateinit var db: SharedPostgresTestDatabase
	private lateinit var dao: ProjectGroupDao
	private lateinit var projectDao: ProjectDao
	private var userId = 0L
	private var otherUserId = 0L

	@BeforeEach
	override fun setup() {
		super.setup()
		db = SharedPostgresTestDatabase()
		db.initialize()
		setupKoin()
		dao = ProjectGroupDao(db)
		projectDao = ProjectDao(db)
		userId = createAccount("groups@example.com")
		otherUserId = createAccount("other@example.com")
	}

	// Synchronous: the DAOs run on a test dispatcher that only advances inside runTest.
	private fun createAccount(email: String): Long {
		val queries = db.serverDatabase.accountQueries
		queries.createAccount(email = email, password_hash = "hash", cipher_secret = "secret", is_admin = false)
		return queries.findAccount(email).executeAsOne().id
	}

	private suspend fun create(name: String, parent: Long? = null, id: GroupId = GroupId.randomUUID()): Long {
		assertTrue(dao.createGroup(userId, id, name, null, parent))
		return assertNotNull(dao.getGroup(userId, id)).rowId
	}

	@Test
	fun `createGroup stores the group and reports its parent`() = runTest {
		val parentId = GroupId.randomUUID()
		val parentRow = create("Trilogy", id = parentId)
		val childId = GroupId.randomUUID()

		assertTrue(dao.createGroup(userId, childId, "Book One", "first", parentRow))

		val child = assertNotNull(dao.getGroup(userId, childId))
		assertEquals("Book One", child.name)
		assertEquals("first", child.description)
		assertEquals(parentRow, child.parentRowId)
		assertEquals(parentId, child.parentUuid)
		assertEquals(setOf(parentId, childId), dao.getGroups(userId).map { it.uuid }.toSet())
	}

	@Test
	fun `createGroup is idempotent on the id`() = runTest {
		val id = GroupId.randomUUID()
		create("Trilogy", id = id)

		assertFalse(dao.createGroup(userId, id, "Trilogy", null, null))
		assertEquals(1, dao.getGroups(userId).size)
	}

	@Test
	fun `createGroup refuses an id another account holds`() = runTest {
		val id = GroupId.randomUUID()
		create("Trilogy", id = id)

		assertFalse(dao.createGroup(otherUserId, id, "Mine", null, null))
		assertNull(dao.getGroup(otherUserId, id))
	}

	@Test
	fun `sibling names are unique case-insensitively`() = runTest {
		val parent = create("Trilogy")
		create("Book", parent = parent)
		create("Top")

		assertFalse(dao.createGroup(userId, GroupId.randomUUID(), "BOOK", null, parent))
		assertFalse(dao.createGroup(userId, GroupId.randomUUID(), "top", null, null))
		assertEquals("Book", dao.findSiblingByName(userId, parent, "bOoK")?.name)
		assertEquals("Top", dao.findSiblingByName(userId, null, "TOP")?.name)
	}

	@Test
	fun `the same name may repeat under different parents and accounts`() = runTest {
		val a = create("A")
		val b = create("B")

		assertTrue(dao.createGroup(userId, GroupId.randomUUID(), "Drafts", null, a))
		assertTrue(dao.createGroup(userId, GroupId.randomUUID(), "Drafts", null, b))
		assertTrue(dao.createGroup(userId, GroupId.randomUUID(), "Drafts", null, null))
		assertTrue(dao.createGroup(otherUserId, GroupId.randomUUID(), "Drafts", null, null))
	}

	@Test
	fun `updateGroup refuses a sibling name collision`() = runTest {
		val parent = create("Trilogy")
		create("One", parent = parent)
		val two = create("Two", parent = parent)

		assertFalse(dao.updateGroup(userId, two, "one", null, parent))
		assertTrue(dao.updateGroup(userId, two, "one", null, null))
	}

	@Test
	fun `deleteGroup moves child groups and projects up to the new parent`() = runTest {
		val grandparent = create("Series")
		val parentId = GroupId.randomUUID()
		val parent = create("Trilogy", parent = grandparent, id = parentId)
		val childId = GroupId.randomUUID()
		create("Book", parent = parent, id = childId)
		val projectId = ProjectId.randomUUID()
		projectDao.createProject(userId, projectId, "Novel")
		projectDao.setProjectGroup(userId, projectId, parent)

		val group = assertNotNull(dao.getGroup(userId, parentId))
		assertTrue(dao.deleteGroup(userId, group, grandparent))

		assertNull(dao.getGroup(userId, parentId))
		assertEquals(grandparent, dao.getGroup(userId, childId)?.parentRowId)
		assertEquals(dao.getGroups(userId).single { it.rowId == grandparent }.uuid, projectDao.getProjectGroupUuid(userId, projectId))
	}

	@Test
	fun `deleteGroup rolls back when a child collides with a sibling at the new parent`() = runTest {
		val parentId = GroupId.randomUUID()
		val parent = create("Trilogy", id = parentId)
		create("Drafts")
		val childId = GroupId.randomUUID()
		create("drafts", parent = parent, id = childId)
		val projectId = ProjectId.randomUUID()
		projectDao.createProject(userId, projectId, "Novel")
		projectDao.setProjectGroup(userId, projectId, parent)

		val group = assertNotNull(dao.getGroup(userId, parentId))
		assertFalse(dao.deleteGroup(userId, group, null))

		assertNotNull(dao.getGroup(userId, parentId))
		assertEquals(parent, dao.getGroup(userId, childId)?.parentRowId)
		assertEquals(parentId, projectDao.getProjectGroupUuid(userId, projectId))
	}

	@Test
	fun `deleteAllForUser leaves other accounts alone`() = runTest {
		create("Mine")
		assertTrue(dao.createGroup(otherUserId, GroupId.randomUUID(), "Theirs", null, null))

		dao.deleteAllForUser(userId)

		assertEquals(emptyList(), dao.getGroups(userId))
		assertEquals(listOf("Theirs"), dao.getGroups(otherUserId).map { it.name })
	}

	@Test
	fun `project placements report only grouped projects`() = runTest {
		val groupId = GroupId.randomUUID()
		val group = create("Trilogy", id = groupId)
		val grouped = ProjectId.randomUUID()
		val loose = ProjectId.randomUUID()
		projectDao.createProject(userId, grouped, "Grouped")
		projectDao.createProject(userId, loose, "Loose")

		assertTrue(projectDao.setProjectGroup(userId, grouped, group))

		assertEquals(mapOf(grouped to groupId), projectDao.getProjectPlacements(userId))
		assertEquals(groupId, projectDao.getProjectGroupUuid(userId, grouped))
		assertNull(projectDao.getProjectGroupUuid(userId, loose))
	}

	@Test
	fun `setProjectGroup to null moves a project to the top level`() = runTest {
		val group = create("Trilogy")
		val projectId = ProjectId.randomUUID()
		projectDao.createProject(userId, projectId, "Novel")
		projectDao.setProjectGroup(userId, projectId, group)

		assertTrue(projectDao.setProjectGroup(userId, projectId, null))

		assertNull(projectDao.getProjectGroupUuid(userId, projectId))
	}

	@Test
	fun `setProjectGroup reports an unknown project`() = runTest {
		assertFalse(projectDao.setProjectGroup(userId, ProjectId.randomUUID(), null))
	}
}
