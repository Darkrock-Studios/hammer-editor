package com.darkrockstudios.apps.hammer.integration.scenarios

import com.darkrockstudios.apps.hammer.common.data.SceneContent
import com.darkrockstudios.apps.hammer.common.data.UpdateSource
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.scenemetadata.SceneMetadata
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.scenemetadata.SceneMetadataDatasource
import com.darkrockstudios.apps.hammer.integration.RoundTripTestBase
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A scene uploaded before scenes carried timestamps sits on the server with null `created` and
 * `lastEdited`. A device that downloads it must keep them null: a backfill from its own project
 * date differs from every other device's, and the heal upload it drives rewrites the server's hash
 * under the first device, which then conflicts on each scene it has merely opened.
 */
class LegacySceneTimestampsTwoDeviceTest : RoundTripTestBase() {

	@Test
	@Timeout(value = 120)
	fun `a scene with null timestamps round-trips between two devices without a conflict`() = runBlocking {
		val project = "legacy timestamps A"
		val a = newClient(project)
		val scene = assertNotNull(a.sceneEditor.createScene(parent = null, sceneName = "Old Scene"))
		a.sceneEditor.storeSceneMarkdownRaw(SceneContent(scene, "written before timestamps"))
		// createScene stamps both timestamps; a scene from before they existed has neither.
		val metadataA: SceneMetadataDatasource = a.scope.get()
		metadataA.storeMetadata(SceneMetadata(), scene.id)
		assertTrue(a.syncNoConflict(), "A uploads the scene")
		val uploadedHash = assertNotNull(serverEntityHash(project, scene.id))

		val b = secondDeviceFor(a, "legacy timestamps B")
		val wire = tapWire()
		assertTrue(b.syncNoConflict(), "B downloads the scene")
		assertEquals(emptyList(), wire.entitiesUploaded(), "B pushed a backfilled copy of the scene")
		assertEquals(uploadedHash, serverEntityHash(project, scene.id), "the server's scene changed under A")
		val metadataB: SceneMetadataDatasource = b.scope.get()
		val downloaded = assertNotNull(metadataB.loadMetadata(scene.id))
		assertNull(downloaded.created, "B backfilled created")
		assertNull(downloaded.lastEdited, "B backfilled lastEdited")

		// A opens the scene without editing: the editor reports the text it loaded.
		val sceneA = assertNotNull(a.sceneEditor.getSceneItemFromId(scene.id))
		val loaded = a.sceneEditorService.loadSceneBuffer(sceneA)
		a.sceneEditorService.onContentChanged(SceneContent(sceneA, loaded.content.coerceMarkdown()), UpdateSource.Editor)
		delay(1500)
		a.sceneEditorService.storeAllBuffers()
		wire.reset()
		assertTrue(a.syncNoConflict(), "A resyncs after opening the scene")
		assertEquals(emptyList(), wire.entitiesUploaded(), "opening the scene uploaded it")
		assertNull(assertNotNull(metadataA.loadMetadata(scene.id)).lastEdited, "opening the scene stamped it")
		assertConverged(project, a, b)

		// A real edit stamps the timestamps and carries them to B.
		a.sceneEditorService.onContentChanged(SceneContent(sceneA, "edited once timestamps existed"), UpdateSource.Editor)
		delay(1500)
		assertTrue(a.sceneEditorService.storeSceneBuffer(sceneA))
		assertTrue(a.syncNoConflict(), "A uploads the edit")
		assertTrue(b.syncNoConflict(), "B takes the edit")
		assertNotNull(assertNotNull(metadataB.loadMetadata(scene.id)).created, "A's stamp did not reach B")

		assertConverged(project, a, b)
		assertResyncSilent(a)
		assertResyncSilent(b)
	}
}
