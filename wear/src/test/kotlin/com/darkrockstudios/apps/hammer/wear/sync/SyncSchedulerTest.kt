package com.darkrockstudios.apps.hammer.wear.sync

import androidx.work.WorkManager
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsStore
import com.darkrockstudios.apps.hammer.common.data.globalsettings.ServerSettings
import com.darkrockstudios.apps.hammer.wear.FakeCaptureSurfaceUpdater
import com.darkrockstudios.apps.hammer.wear.SignedInSettings
import com.darkrockstudios.apps.hammer.wear.WearTestBase
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SyncSchedulerTest : WearTestBase() {

	private lateinit var settingsUpdates: MutableSharedFlow<ServerSettings?>
	private lateinit var surfaces: FakeCaptureSurfaceUpdater
	private lateinit var scope: CoroutineScope

	@BeforeEach
	override fun setUp() {
		super.setUp()
		// Replays the current settings to a new collector, like GlobalSettingsStore.
		settingsUpdates = MutableSharedFlow(replay = 1, extraBufferCapacity = 4)
		settingsUpdates.tryEmit(null)
		surfaces = FakeCaptureSurfaceUpdater()
		scope = CoroutineScope(dispatcher)
		val store: GlobalSettingsStore = mockk(relaxed = true)
		every { store.serverSettingsUpdates } returns settingsUpdates
		every { store.serverSettings } returns null
		SyncScheduler(mockk<WorkManager>(relaxed = true), store, surfaces, scope).start()
		scheduler.advanceUntilIdle()
	}

	@AfterEach
	override fun tearDown() {
		scope.cancel()
		super.tearDown()
	}

	@Test
	fun `signing in and out redraws the tile`() {
		settingsUpdates.tryEmit(SignedInSettings)
		scheduler.advanceUntilIdle()
		settingsUpdates.tryEmit(null)
		scheduler.advanceUntilIdle()

		assertEquals(2, surfaces.refreshes)
	}

	@Test
	fun `a token refresh while signed in leaves the tile alone`() {
		settingsUpdates.tryEmit(SignedInSettings)
		scheduler.advanceUntilIdle()
		settingsUpdates.tryEmit(SignedInSettings.copy(bearerToken = "rotated"))
		scheduler.advanceUntilIdle()

		assertEquals(1, surfaces.refreshes)
	}
}
