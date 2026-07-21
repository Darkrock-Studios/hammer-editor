package components.projectsync

import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.base.http.ApiSceneType
import com.darkrockstudios.apps.hammer.base.http.projectdata.ProjectData
import com.darkrockstudios.apps.hammer.common.components.projectsync.ProjectSynchronization
import com.darkrockstudios.apps.hammer.common.components.projectsync.ProjectSynchronizationComponent
import com.darkrockstudios.apps.hammer.common.data.ClientResult
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.data.drafts.DraftDef
import com.darkrockstudios.apps.hammer.common.data.drafts.SceneDraftRepository
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EncyclopediaService
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettings
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsStore
import com.darkrockstudios.apps.hammer.common.data.notesrepository.NoteError
import com.darkrockstudios.apps.hammer.common.data.notesrepository.NotesRepository
import com.darkrockstudios.apps.hammer.common.data.notesrepository.note.NoteContainer
import com.darkrockstudios.apps.hammer.common.data.notesrepository.note.NoteContent
import com.darkrockstudios.apps.hammer.common.data.projectdata.ProjectDataConflict
import com.darkrockstudios.apps.hammer.common.data.projectdata.ProjectDataConflictBroker
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneEditorService
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.scenemetadata.SceneMetadata
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.ClientProjectSynchronizer
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.SyncLogLevel
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.SyncLogMessage
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineEvent
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineEventError
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineRepository
import com.darkrockstudios.apps.hammer.common.dependencyinjection.ProjectDefScope
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import utils.ComponentTest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class ProjectSynchronizationComponentTest : ComponentTest() {

	private lateinit var globalSettingsStore: GlobalSettingsStore
	private lateinit var sceneEditor: SceneEditorService
	private lateinit var encyclopediaService: EncyclopediaService
	private lateinit var notesRepository: NotesRepository
	private lateinit var timeLineRepository: TimeLineRepository
	private lateinit var sceneDraftRepository: SceneDraftRepository
	private lateinit var synchronizer: ClientProjectSynchronizer
	private lateinit var conflictBroker: ProjectDataConflictBroker

	private var dismissed = false
	private var reauthorized = false

	private class SyncCallbacks(
		val onProgress: suspend (Float, SyncLogMessage?) -> Unit,
		val onLog: suspend (SyncLogMessage?) -> Unit,
		val onConflict: suspend (ApiProjectEntity) -> Unit,
		val onComplete: suspend () -> Unit,
		val onUnauthorized: suspend () -> Unit,
	)

	@BeforeEach
	override fun setup() {
		super.setup()

		dismissed = false
		reauthorized = false

		globalSettingsStore = mockk()
		every { globalSettingsStore.globalSettings } returns GlobalSettings(projectsDirectory = "/projects")

		sceneEditor = mockk()
		encyclopediaService = mockk()
		notesRepository = mockk()
		timeLineRepository = mockk()
		sceneDraftRepository = mockk()
		synchronizer = mockk()
		conflictBroker = ProjectDataConflictBroker(projectDef)

		setupComponentKoin(module {
			single { globalSettingsStore }
			scope<ProjectDefScope> {
				scoped { sceneEditor }
				scoped { encyclopediaService }
				scoped { notesRepository }
				scoped { timeLineRepository }
				scoped { sceneDraftRepository }
				scoped { synchronizer }
				scoped { conflictBroker }
			}
		})
	}

	private fun newComponent() = ProjectSynchronizationComponent(
		componentContext = context,
		projectDef = projectDef,
		dismissSync = { dismissed = true },
		reauthorize = { reauthorized = true },
	)

	/** Stub [synchronizer]'s sync() to run [body] against the component's callbacks and return its result. */
	private fun stubSync(body: suspend (SyncCallbacks) -> Boolean) {
		coEvery {
			synchronizer.sync(any(), any(), any(), any(), any(), any())
		} coAnswers {
			body(
				SyncCallbacks(
					onProgress = arg(0),
					onLog = arg(1),
					onConflict = arg(2),
					onComplete = arg(3),
					onUnauthorized = arg(5),
				)
			)
		}
	}

	@Test
	fun `Successful sync reports progress and auto-closes the dialog`() = runTest(mainTestDispatcher) {
		stubSync { callbacks ->
			callbacks.onProgress(0.5f, null)
			callbacks.onComplete()
			true
		}

		val comp = newComponent()
		context.resume()

		var completedWith: Boolean? = null
		comp.syncProject { success -> completedWith = success }
		advanceUntilIdle()

		assertEquals(true, completedWith)
		assertTrue(dismissed, "Successful sync should auto-close the dialog by default")
		// endSync() resets the state after auto-close
		assertFalse(comp.state.value.isSyncing)
		assertFalse(comp.state.value.failed)
		assertTrue(comp.state.value.syncLog.isEmpty())
	}

	@Test
	fun `Successful sync keeps dialog open when auto-close is disabled`() = runTest(mainTestDispatcher) {
		every { globalSettingsStore.globalSettings } returns GlobalSettings(
			projectsDirectory = "/projects",
			autoCloseSyncDialog = false,
		)
		stubSync { callbacks ->
			callbacks.onComplete()
			true
		}

		val comp = newComponent()
		context.resume()

		comp.syncProject { }
		advanceUntilIdle()

		assertFalse(dismissed)
		assertFalse(comp.state.value.isSyncing)
		assertEquals(1f, comp.state.value.syncProgress)
		assertFalse(comp.state.value.failed)
	}

	@Test
	fun `Failed sync marks failure and shows the log`() = runTest(mainTestDispatcher) {
		stubSync { false }

		val comp = newComponent()
		context.resume()

		var completedWith: Boolean? = null
		comp.syncProject { success -> completedWith = success }
		advanceUntilIdle()

		assertEquals(false, completedWith)
		assertFalse(dismissed)
		assertTrue(comp.state.value.failed)
		assertTrue(comp.state.value.showLog)
	}

	@Test
	fun `Sync log accumulates messages`() = runTest(mainTestDispatcher) {
		stubSync { callbacks ->
			callbacks.onProgress(0.25f, SyncLogMessage("first", SyncLogLevel.INFO, projectDef.name, Instant.DISTANT_PAST))
			callbacks.onLog(SyncLogMessage("second", SyncLogLevel.WARN, projectDef.name, Instant.DISTANT_PAST))
			callbacks.onComplete()
			true
		}
		every { globalSettingsStore.globalSettings } returns GlobalSettings(
			projectsDirectory = "/projects",
			autoCloseSyncDialog = false,
		)

		val comp = newComponent()
		context.resume()

		comp.syncProject { }
		advanceUntilIdle()

		val messages = comp.state.value.syncLog.map { it.message }
		assertTrue(messages.contains("first"))
		assertTrue(messages.contains("second"))
	}

	@Test
	fun `Cancel sync stops syncing and logs a warning`() = runTest(mainTestDispatcher) {
		stubSync { callbacks ->
			callbacks.onProgress(0.1f, null)
			awaitCancellation()
		}

		val comp = newComponent()
		context.resume()

		comp.syncProject { }
		advanceUntilIdle()
		assertTrue(comp.state.value.isSyncing)

		comp.cancelSync()
		advanceUntilIdle()

		assertFalse(comp.state.value.isSyncing)
		assertTrue(comp.state.value.syncLog.any { it.level == SyncLogLevel.WARN })
	}

	@Test
	fun `Note conflict surfaces server and local entities`() = runTest(mainTestDispatcher) {
		val serverNote = ApiProjectEntity.NoteEntity(
			id = 5,
			content = "server content",
			created = Instant.DISTANT_PAST,
		)
		val localNote = NoteContent(
			id = 5,
			created = Instant.DISTANT_FUTURE,
			content = "local content",
			tags = setOf("tag"),
		)
		coEvery { notesRepository.getNoteById(5) } returns NoteContainer(localNote)

		stubSync { callbacks ->
			callbacks.onConflict(serverNote)
			awaitCancellation()
		}

		val comp = newComponent()
		context.resume()
		comp.syncProject { }
		advanceUntilIdle()

		val conflict = comp.state.value.entityConflict
		assertIs<ProjectSynchronization.EntityConflict.NoteConflict>(conflict)
		assertEquals(serverNote, conflict.serverEntity)
		assertEquals("local content", conflict.clientEntity.content)
		assertEquals(setOf("tag"), conflict.clientEntity.tags)
		assertNotNull(comp.state.value.conflictTitle)
	}

	@Test
	fun `Timeline conflict surfaces server and local entities`() = runTest(mainTestDispatcher) {
		val serverEvent = ApiProjectEntity.TimelineEventEntity(
			id = 3,
			order = 1,
			date = "1066",
			content = "server event",
		)
		coEvery { timeLineRepository.getTimelineEvent(3) } returns TimeLineEvent(
			id = 3,
			order = 2,
			date = "1067",
			content = "local event",
		)

		stubSync { callbacks ->
			callbacks.onConflict(serverEvent)
			awaitCancellation()
		}

		val comp = newComponent()
		context.resume()
		comp.syncProject { }
		advanceUntilIdle()

		val conflict = comp.state.value.entityConflict
		assertIs<ProjectSynchronization.EntityConflict.TimelineEventConflict>(conflict)
		assertEquals("local event", conflict.clientEntity.content)
		assertEquals("1067", conflict.clientEntity.date)
	}

	@Test
	fun `Scene conflict builds local entity from scene content and metadata`() = runTest(mainTestDispatcher) {
		val serverScene = ApiProjectEntity.SceneEntity(
			id = 7,
			sceneType = ApiSceneType.Scene,
			order = 0,
			name = "Server Scene",
			path = listOf(0),
			content = "server words",
		)
		val localScene = SceneItem(
			projectDef = projectDef,
			type = SceneItem.Type.Scene,
			id = 7,
			name = "Local Scene",
			order = 4,
		)
		every { sceneEditor.getSceneItemFromIdIncludingArchived(7) } returns localScene
		coEvery { sceneEditor.loadSceneMetadata(7) } returns SceneMetadata(
			outline = "the outline",
			notes = "the notes",
		)
		every { sceneEditor.getPathSegments(localScene) } returns listOf(0, 2)
		every { sceneEditor.loadSceneMarkdownRaw(localScene) } returns "local words"

		stubSync { callbacks ->
			callbacks.onConflict(serverScene)
			awaitCancellation()
		}

		val comp = newComponent()
		context.resume()
		comp.syncProject { }
		advanceUntilIdle()

		val conflict = comp.state.value.entityConflict
		assertIs<ProjectSynchronization.EntityConflict.SceneConflict>(conflict)
		val client = conflict.clientEntity
		assertEquals("Local Scene", client.name)
		assertEquals("local words", client.content)
		assertEquals("the outline", client.outline)
		assertEquals("the notes", client.notes)
		assertEquals(listOf(0, 2), client.path)
		assertEquals(4, client.order)
	}

	@Test
	fun `Scene draft conflict loads local draft content`() = runTest(mainTestDispatcher) {
		val serverDraft = ApiProjectEntity.SceneDraftEntity(
			id = 11,
			sceneId = 7,
			created = Instant.DISTANT_PAST,
			name = "Server Draft",
			content = "server draft",
		)
		val localDef = DraftDef(
			id = 11,
			sceneId = 7,
			draftTimestamp = Instant.DISTANT_FUTURE,
			draftName = "Local Draft",
		)
		every { sceneDraftRepository.getDraftDef(11) } returns localDef
		every { sceneDraftRepository.loadDraftContent(localDef) } returns "local draft"

		stubSync { callbacks ->
			callbacks.onConflict(serverDraft)
			awaitCancellation()
		}

		val comp = newComponent()
		context.resume()
		comp.syncProject { }
		advanceUntilIdle()

		val conflict = comp.state.value.entityConflict
		assertIs<ProjectSynchronization.EntityConflict.SceneDraftConflict>(conflict)
		assertEquals("Local Draft", conflict.clientEntity.name)
		assertEquals("local draft", conflict.clientEntity.content)
	}

	@Test
	fun `Resolving a valid note conflict forwards it and clears the conflict`() = runTest(mainTestDispatcher) {
		every { notesRepository.validateNote(any(), any()) } returns NoteError.NONE
		every { synchronizer.resolveConflict(any()) } just Runs
		setupNoteConflict()

		val comp = newComponent()
		context.resume()
		comp.syncProject { }
		advanceUntilIdle()
		assertNotNull(comp.state.value.entityConflict)

		val resolved = ApiProjectEntity.NoteEntity(
			id = 5,
			content = "merged content",
			created = Instant.DISTANT_PAST,
		)
		val error = comp.resolveConflict(resolved)
		advanceUntilIdle()

		assertNull(error)
		verify(exactly = 1) { synchronizer.resolveConflict(resolved) }
		assertNull(comp.state.value.entityConflict)
		assertNull(comp.state.value.conflictTitle)
	}

	@Test
	fun `Resolving an invalid note returns a merge error and keeps the conflict`() = runTest(mainTestDispatcher) {
		every { notesRepository.validateNote(any(), any()) } returns NoteError.EMPTY
		setupNoteConflict()

		val comp = newComponent()
		context.resume()
		comp.syncProject { }
		advanceUntilIdle()

		val resolved = ApiProjectEntity.NoteEntity(
			id = 5,
			content = "",
			created = Instant.DISTANT_PAST,
		)
		val error = comp.resolveConflict(resolved)
		advanceUntilIdle()

		assertIs<ProjectSynchronization.EntityMergeError.NoteMergeError>(error)
		verify(exactly = 0) { synchronizer.resolveConflict(any()) }
		assertNotNull(comp.state.value.entityConflict)
	}

	@Test
	fun `Resolving a scene with an invalid name returns a merge error`() = runTest(mainTestDispatcher) {
		every { sceneEditor.validateSceneName(any()) } returns ClientResult.failure("bad name")

		val comp = newComponent()
		context.resume()

		val resolved = ApiProjectEntity.SceneEntity(
			id = 7,
			sceneType = ApiSceneType.Scene,
			order = 0,
			name = "",
			path = listOf(0),
		)
		val error = comp.resolveConflict(resolved)

		assertIs<ProjectSynchronization.EntityMergeError.SceneMergeError>(error)
	}

	@Test
	fun `Resolving a timeline event with invalid tags returns a merge error`() = runTest(mainTestDispatcher) {
		every { timeLineRepository.validateTags(any()) } returns TimeLineEventError.TAG_TOO_LONG

		val comp = newComponent()
		context.resume()

		val resolved = ApiProjectEntity.TimelineEventEntity(
			id = 3,
			order = 0,
			date = null,
			content = "content",
			tags = setOf("much too long"),
		)
		val error = comp.resolveConflict(resolved)

		assertIs<ProjectSynchronization.EntityMergeError.TimelineEventMergeError>(error)
	}

	@Test
	fun `Project data conflict surfaces in state and resolution flows back to the broker`() = runTest(mainTestDispatcher) {
		val local = ProjectData(authorName = "Local Author")
		val server = ProjectData(authorName = "Server Author")

		val comp = newComponent()
		context.resume()
		advanceUntilIdle()

		conflictBroker.reportConflict(ProjectDataConflict(local, server, "hash123"))
		advanceUntilIdle()

		val conflict = comp.state.value.projectDataConflict
		assertNotNull(conflict)
		assertEquals(local, conflict.local)
		assertEquals(server, conflict.server)
		assertEquals("hash123", conflict.serverHash)
		assertNotNull(comp.state.value.conflictTitle)

		comp.resolveProjectDataConflict(server)
		advanceUntilIdle()

		assertNull(comp.state.value.projectDataConflict)
		assertNull(comp.state.value.conflictTitle)
		assertEquals(server, conflictBroker.awaitResolution())
	}

	@Test
	fun `Unauthorized marks failure and requests reauthorization`() = runTest(mainTestDispatcher) {
		val comp = newComponent()
		context.resume()

		comp.onUnauthorized()
		advanceUntilIdle()

		assertTrue(reauthorized)
		assertTrue(comp.state.value.failed)
		assertTrue(comp.state.value.showLog)
		assertFalse(comp.state.value.isSyncing)
		assertTrue(comp.state.value.syncLog.any { it.level == SyncLogLevel.WARN })
	}

	@Test
	fun `End sync resets state and dismisses the dialog`() = runTest(mainTestDispatcher) {
		stubSync { callbacks ->
			callbacks.onProgress(0.5f, SyncLogMessage("hi", SyncLogLevel.INFO, projectDef.name, Instant.DISTANT_PAST))
			false
		}

		val comp = newComponent()
		context.resume()
		comp.syncProject { }
		advanceUntilIdle()

		comp.endSync()
		advanceUntilIdle()

		assertTrue(dismissed)
		val state = comp.state.value
		assertFalse(state.isSyncing)
		assertEquals(0f, state.syncProgress)
		assertTrue(state.syncLog.isEmpty())
		assertNull(state.entityConflict)
		assertNull(state.projectDataConflict)
	}

	@Test
	fun `Show log toggles state`() = runTest(mainTestDispatcher) {
		val comp = newComponent()
		context.resume()

		comp.showLog(true)
		assertTrue(comp.state.value.showLog)

		comp.showLog(false)
		assertFalse(comp.state.value.showLog)
	}

	private fun setupNoteConflict() {
		coEvery { notesRepository.getNoteById(5) } returns NoteContainer(
			NoteContent(id = 5, created = Instant.DISTANT_PAST, content = "local content")
		)
		stubSync { callbacks ->
			callbacks.onConflict(
				ApiProjectEntity.NoteEntity(id = 5, content = "server content", created = Instant.DISTANT_PAST)
			)
			awaitCancellation()
		}
	}
}
