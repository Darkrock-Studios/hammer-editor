package com.darkrockstudios.apps.hammer.integration

import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.SceneContent
import com.darkrockstudios.apps.hammer.common.data.globalsettings.ServerSettings
import com.darkrockstudios.apps.hammer.common.data.projectmetadata.ProjectMetadataDatasource
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.SyncLogMessage
import com.darkrockstudios.apps.hammer.common.data.sync.accountsync.ProjectSyncOutcome
import com.darkrockstudios.apps.hammer.common.data.sync.accountsync.SyncAccountListener
import com.darkrockstudios.apps.hammer.common.data.sync.accountsync.SyncAccountResult
import com.darkrockstudios.apps.hammer.common.data.sync.accountsync.SyncAccountUseCase
import com.darkrockstudios.apps.hammer.common.components.storyeditor.metadata.Info
import com.darkrockstudios.apps.hammer.e2e.util.E2eTestData
import com.darkrockstudios.apps.hammer.e2e.util.TestAccount
import com.darkrockstudios.apps.hammer.project.ProjectMemberRepository
import com.darkrockstudios.apps.hammer.utilities.isSuccess
import okio.Path.Companion.toPath
import org.junit.jupiter.api.BeforeEach
import org.koin.test.get
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Two accounts on one real server, each running the real client as its own install. Only one
 * install runs at a time: [on] restarts the client as the given device. Membership goes through
 * the server's [ProjectMemberRepository], which the website's invite, accept, remove and leave
 * routes call.
 */
abstract class CoAuthoringTestBase : RoundTripTestBase() {

	protected class Author(val account: TestAccount, val userId: Long, val penName: String)

	protected class Device(val author: Author, val install: ClientInstall)

	protected lateinit var owner: Author
	protected lateinit var coAuthor: Author
	protected lateinit var ownerDevice: Device
	protected lateinit var coAuthorDevice: Device

	@BeforeEach
	fun setUpCoAuthor() {
		val accounts = database().serverDatabase.accountQueries
		owner = Author(account, userId, OWNER_PEN_NAME)
		accounts.updatePenName(OWNER_PEN_NAME, userId)
		ownerDevice = Device(owner, currentInstall)

		val coAuthorAccount = TestAccount(email = "coauthor@test.com", password = "password123!@#")
		E2eTestData.createAccount(coAuthorAccount, database())
		val coAuthorId = accounts.findAccount(coAuthorAccount.email).executeAsOne().id
		accounts.updatePenName(CO_AUTHOR_PEN_NAME, coAuthorId)
		coAuthor = Author(coAuthorAccount, coAuthorId, CO_AUTHOR_PEN_NAME)
		coAuthorDevice = newDevice(coAuthor, "coauthor")
	}

	/** Another install signed in as [author], with its own projects root, install id and token. */
	protected fun newDevice(author: Author, name: String): Device {
		val installId = "$name-install"
		val token = E2eTestData.createAuthToken(
			userId = author.userId,
			installId = installId,
			database = database(),
			tokenHasher = tokenHasher(),
		)
		val settings = ServerSettings(
			ssl = false,
			url = "127.0.0.1:$serverPort",
			email = author.account.email,
			userId = author.userId,
			bearerToken = token.auth,
			refreshToken = token.refresh,
		)
		return Device(author, ClientInstall("/client/$name/projects".toPath(), installId, settings))
	}

	/** Runs [block] as [device], restarting the client first if another install is running. */
	protected suspend fun <T> on(device: Device, block: suspend () -> T): T {
		switchInstall(device.install)
		return block()
	}

	private val members: ProjectMemberRepository get() = get()

	protected suspend fun invite(projectId: ProjectId, invitee: Author = coAuthor) {
		assertTrue(isSuccess(members.createInvite(owner.userId, projectId, invitee.account.email)), "invite refused")
	}

	protected suspend fun accept(projectId: ProjectId, invitee: Author = coAuthor) {
		val invite = assertNotNull(members.receivedInvites(invitee.userId).find { it.projectId == projectId }, "no invite")
		assertTrue(isSuccess(members.acceptInvite(invitee.userId, invite.id)), "accept refused")
	}

	protected suspend fun removeMember(projectId: ProjectId, member: Author = coAuthor) {
		assertTrue(isSuccess(members.removeMember(owner.userId, projectId, member.userId)), "remove refused")
	}

	protected fun serverProjectRowId(projectId: ProjectId): Long? =
		database().serverDatabase.projectQueries.getProjectByUuid(projectId.id).executeAsOneOrNull()?.id

	protected fun isServerMember(projectId: ProjectId, member: Author = coAuthor): Boolean {
		val rowId = serverProjectRowId(projectId) ?: return false
		return database().serverDatabase.projectMemberQueries.isMember(rowId, member.userId).executeAsOne()
	}

	/** The current install's local project named [name], or null. */
	protected fun localProject(name: String): ProjectDef? =
		get<ProjectsRepository>().getProjects().find { it.name == name }

	/** `project.toml`'s `Info`, read from disk. */
	protected fun localInfo(projectDef: ProjectDef): Info =
		assertNotNull(get<ProjectMetadataDatasource>().readMetadata(projectDef), "project.toml unreadable").info

	/** The account sync plus project phase, as the project list's sync button runs it. */
	protected suspend fun fullSync(): SyncAccountResult = get<SyncAccountUseCase>().execute(QuietListener)

	protected suspend fun HeadlessClient.writeScene(id: Int, markdown: String) {
		val scene = assertNotNull(sceneEditor.getSceneItemFromId(id), "scene $id missing")
		sceneEditor.storeSceneMarkdownRaw(SceneContent(scene, markdown))
	}

	protected suspend fun HeadlessClient.readScene(id: Int): String? =
		sceneEditor.getSceneItemFromId(id)?.let { sceneEditor.loadSceneMarkdownRaw(it) }

	protected class SharedProject(val name: String, val id: ProjectId, val sceneIds: List<Int>)

	/**
	 * The owner creates [name] with [sceneCount] scenes and syncs it; the co-author is invited,
	 * accepts, and their first full sync brings the project down. Leaves the co-author's install
	 * running.
	 */
	protected suspend fun shareProject(name: String, sceneCount: Int = 3): SharedProject {
		val shared = on(ownerDevice) {
			val client = newClient(name)
			val ids = (1..sceneCount).map { n ->
				val scene = assertNotNull(client.sceneEditor.createScene(parent = null, sceneName = "Scene $n"))
				client.writeScene(scene.id, "owner wrote scene $n")
				scene.id
			}
			assertTrue(client.syncNoConflict(), "owner's first sync")
			SharedProject(name, client.projectDef.id, ids)
		}
		invite(shared.id)
		accept(shared.id)
		on(coAuthorDevice) {
			assertTrue(fullSync().allSuccess, "co-author's first full sync")
		}
		return shared
	}

	private object QuietListener : SyncAccountListener {
		override suspend fun onLog(message: SyncLogMessage) {}
		override suspend fun onProjectsDiscovered(projects: List<ProjectDef>) {}
		override suspend fun onProjectProgress(projectDef: ProjectDef, progress: Float?) {}
		override suspend fun onProjectOutcome(projectDef: ProjectDef, outcome: ProjectSyncOutcome) {}
		override suspend fun onUnauthorized() {}
	}

	companion object {
		const val OWNER_PEN_NAME = "Owner Pen"
		const val CO_AUTHOR_PEN_NAME = "Coauthor Pen"
	}
}
