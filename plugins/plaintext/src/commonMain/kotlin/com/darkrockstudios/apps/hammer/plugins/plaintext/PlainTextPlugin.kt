package com.darkrockstudios.apps.hammer.plugins.plaintext

import com.darkrockstudios.apps.hammer.common.data.export.StoryExporter
import com.darkrockstudios.apps.hammer.operations.plugin.ClientPlugin
import org.koin.core.module.Module
import org.koin.dsl.module
import org.koin.mp.KoinPlatform.getKoin

/** Exports a story as plain text for pasting into submission forms. */
object PlainTextPlugin : ClientPlugin {
	const val ID = "plaintext"

	override val id = ID

	override fun koinModule(): Module = module {
		single { PlainTextSettingsStore(get()) }
	}

	override fun exporters(): List<StoryExporter> =
		listOf(PlainTextExporter { getKoin().get<PlainTextSettingsStore>().settings.value })
}
