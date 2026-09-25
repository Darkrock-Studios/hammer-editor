package com.darkrockstudios.apps.hammer.common.compose.plugin.style

import com.darkrockstudios.apps.hammer.common.compose.plugin.PluginUi
import com.darkrockstudios.apps.hammer.common.compose.plugin.ProjectAction
import com.darkrockstudios.apps.hammer.composeui.resources.*
import com.darkrockstudios.apps.hammer.plugins.style.StylePlugin
import com.darkrockstudios.apps.hammer.plugins.style.StyleReportNote

object StylePluginUi : PluginUi {
	override val id = StylePlugin.ID
	override val name = Res.string.style_plugin_name

	override fun projectActions(): List<ProjectAction> = listOf(
		ProjectAction(
			label = Res.string.style_action_report,
			done = Res.string.style_action_report_done,
		) { project, operations -> StyleReportNote.save(operations, project) },
	)
}
