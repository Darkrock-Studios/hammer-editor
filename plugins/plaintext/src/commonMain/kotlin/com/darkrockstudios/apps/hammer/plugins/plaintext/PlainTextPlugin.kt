package com.darkrockstudios.apps.hammer.plugins.plaintext

import com.darkrockstudios.apps.hammer.common.data.export.StoryExporter
import com.darkrockstudios.apps.hammer.operations.plugin.ClientPlugin
import com.darkrockstudios.apps.hammer.operations.plugin.PluginRegistry
import com.darkrockstudios.apps.hammer.operations.plugin.SettingDeclaration
import org.koin.mp.KoinPlatform.getKoin

/** Exports a story as plain text for pasting into submission forms. */
object PlainTextPlugin : ClientPlugin {
	const val ID = "plaintext"

	override val id = ID

	override fun settings(): List<SettingDeclaration> = plainTextSettings

	override fun exporters(): List<StoryExporter> = listOf(
		PlainTextExporter {
			getKoin().get<PluginRegistry>().settings(ID)?.decode(PlainTextSettings.serializer()) ?: PlainTextSettings()
		}
	)
}
