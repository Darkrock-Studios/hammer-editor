package com.darkrockstudios.apps.hammer.operations.core

import com.darkrockstudios.apps.hammer.operations.Access
import com.darkrockstudios.apps.hammer.operations.NoInput
import com.darkrockstudios.apps.hammer.operations.Operation
import com.darkrockstudios.apps.hammer.operations.OperationScope
import com.darkrockstudios.apps.hammer.operations.invalidInput
import com.darkrockstudios.apps.hammer.operations.notFound
import com.darkrockstudios.apps.hammer.operations.operation
import com.darkrockstudios.apps.hammer.operations.plugin.PluginInstaller
import com.darkrockstudios.apps.hammer.operations.plugin.PluginSummary
import kotlinx.serialization.Serializable
import okio.Path.Companion.toPath
import org.koin.mp.KoinPlatform

// Installing grants a plugin its manifest's permissions with no prompt, so both changes are Destructive:
// the CLI asks for --confirm, and no plugin gets them through a scope.
internal fun pluginOperations(): List<Operation<*, *>> = listOf(
	operation<NoInput, PluginList>(
		name = "plugin.list",
		description = "The installed runtime plugins, and what each may use.",
		access = Access.Read,
		scope = OperationScope.Plugins,
	) {
		PluginList(installer().summaries())
	},
	operation<PluginInstallInput, PluginSummary>(
		name = "plugin.install",
		description = "Install, or replace, a runtime plugin from a .hammerplugin file, granting it every permission its " +
			"manifest asks for, which the reply lists. Takes effect at once in a running app; otherwise, the next time it starts.",
		access = Access.Destructive,
		scope = OperationScope.Plugins,
	) { input ->
		if (!input.path.toPath().isAbsolute) invalidInput("The path must be absolute: ${input.path}")
		installer().installFrom(input.path)
	},
	operation<PluginIdInput, PluginRemoved>(
		name = "plugin.uninstall",
		description = "Uninstall a runtime plugin, forgetting its cache and secrets. Its other settings are kept for a reinstall.",
		access = Access.Destructive,
		scope = OperationScope.Plugins,
	) { input ->
		if (!installer().remove(input.id)) notFound("No plugin '${input.id}' is installed")
		PluginRemoved(input.id)
	},
)

private fun installer(): PluginInstaller =
	KoinPlatform.getKoin().getOrNull() ?: error("Runtime plugins are not available here")

@Serializable
data class PluginInstallInput(
	/** An absolute path to a `.hammerplugin` package. */
	val path: String,
)

@Serializable
data class PluginIdInput(val id: String)

@Serializable
data class PluginList(val plugins: List<PluginSummary>)

@Serializable
data class PluginRemoved(val id: String)
