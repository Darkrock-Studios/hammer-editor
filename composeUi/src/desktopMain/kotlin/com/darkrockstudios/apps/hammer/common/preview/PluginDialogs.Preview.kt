package com.darkrockstudios.apps.hammer.common.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.darkrockstudios.apps.hammer.common.projectselection.settings.PluginDetailsCard
import com.darkrockstudios.apps.hammer.common.projectselection.settings.PluginInstallCard
import com.darkrockstudios.apps.hammer.operations.Access
import com.darkrockstudios.apps.hammer.operations.core.coreOperations
import com.darkrockstudios.apps.hammer.plugins.wasmhost.InstalledPlugin
import com.darkrockstudios.apps.hammer.plugins.wasmhost.PluginManifest

/** The install prompt for a plugin that adds an action, an export and a check, replacing an older version. */
@Composable
internal fun PluginInstallScrimPreview() {
	Scrim {
		PluginInstallCard(
			manifest = previewManifest,
			size = 412L * 1024,
			translations = listOf("de", "fr"),
			settings = emptyList(),
			replacingVersion = "1.3.2",
			accessOf = previewAccessOf,
			onConfirm = {},
			onCancel = {},
		)
	}
}

/** The same plugin's details once installed. */
@Composable
internal fun PluginDetailsScrimPreview() {
	Scrim {
		PluginDetailsCard(
			installed = InstalledPlugin(
				id = previewManifest.id,
				manifest = previewManifest,
				enabled = true,
				granted = previewManifest.permissions.operations,
				size = 412L * 1024,
				translations = setOf("de", "fr"),
			),
			accessOf = previewAccessOf,
			onClose = {},
		)
	}
}

@Composable
private fun Scrim(content: @Composable () -> Unit) {
	Box(
		modifier = Modifier
			.fillMaxSize()
			.background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f)),
		contentAlignment = Alignment.Center,
	) {
		content()
	}
}

private val coreAccess: Map<String, Access> by lazy { coreOperations().associate { it.name to it.access } }
private val previewAccessOf: (String) -> Access? = { coreAccess[it] }

private val previewManifest = PluginManifest(
	id = "manuscript-tools",
	name = "Manuscript tools",
	version = "1.4.0",
	api = 1,
	description = "Formats a project as a standard manuscript and flags words you lean on too often.",
	languages = listOf("en"),
	permissions = PluginManifest.Permissions(
		operations = listOf("content:read", "scene.meta.write", "scene.references.set"),
	),
	exporters = listOf(
		PluginManifest.Exporter(
			format = "manuscript-tools.standard",
			extension = "docx",
			mime = "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
			label = "Standard manuscript",
		),
	),
	actions = listOf(
		PluginManifest.Action(name = "frequency", label = "Word frequency", places = listOf("project", "scene")),
	),
	diagnostics = listOf(PluginManifest.Diagnostics(name = "repeats", label = "Repeated words")),
)
