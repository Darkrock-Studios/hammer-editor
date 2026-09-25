package com.darkrockstudios.apps.hammer.common.projectselection.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.common.compose.AnimatedDialog
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineButton
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineDialogShell
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineToggleRow
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdMonoLabel
import com.darkrockstudios.apps.hammer.common.compose.rememberIoDispatcher
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.dependencyinjection.APP_SCOPE
import com.darkrockstudios.apps.hammer.common.getCacheDirectory
import com.darkrockstudios.apps.hammer.composeui.resources.*
import com.darkrockstudios.apps.hammer.operations.Access
import com.darkrockstudios.apps.hammer.operations.OperationRegistry
import com.darkrockstudios.apps.hammer.operations.OperationScope
import com.darkrockstudios.apps.hammer.plugins.wasmhost.InstalledPlugin
import com.darkrockstudios.apps.hammer.plugins.wasmhost.OperationGrant
import com.darkrockstudios.apps.hammer.plugins.wasmhost.PluginPackage
import com.darkrockstudios.apps.hammer.plugins.wasmhost.PluginPackageException
import com.darkrockstudios.apps.hammer.plugins.wasmhost.RuntimePlugins
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.openFilePicker
import io.github.vinceglb.filekit.readBytes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.IOException
import okio.Path
import okio.Path.Companion.toPath
import org.koin.compose.koinInject
import org.koin.core.qualifier.named
import kotlin.random.Random

/** Install, enable, disable, and uninstall runtime plugins. Every change applies at the next start. */
@Composable
internal fun ColumnScope.RuntimePluginsSection(runtimePlugins: RuntimePlugins) {
	val scope = rememberCoroutineScope()
	// Changes run on the app scope so leaving Settings cannot cancel one half done.
	val appScope = koinInject<CoroutineScope>(named(APP_SCOPE))
	val ioDispatcher = rememberIoDispatcher()
	val fileSystem = koinInject<FileSystem>()
	val operations = koinInject<OperationRegistry>()

	var installed by remember { mutableStateOf<List<InstalledPlugin>?>(null) }
	var pending by remember { mutableStateOf<PendingInstall?>(null) }
	var failure by remember { mutableStateOf<String?>(null) }
	val restartNeeded by runtimePlugins.restartNeeded.collectAsState()

	suspend fun refresh() {
		installed = withContext(ioDispatcher) { runtimePlugins.installed() }
	}
	LaunchedEffect(runtimePlugins) { refresh() }

	fun change(action: () -> Unit) {
		scope.launch {
			appScope.launch(ioDispatcher) { action() }.join()
			refresh()
		}
	}

	fun discard(install: PendingInstall) {
		appScope.launch(ioDispatcher) { fileSystem.delete(install.path, mustExist = false) }
	}

	HdMonoLabel(text = Res.string.plugin_runtime_header.get(), color = MaterialTheme.colorScheme.onSurface)

	val plugins = installed
	if (plugins != null && plugins.isEmpty()) {
		Text(Res.string.plugin_runtime_empty.get(), style = MaterialTheme.typography.bodyMedium)
	}
	plugins?.forEach { plugin ->
		InstalledPluginRow(
			plugin = plugin,
			onEnabledChange = { enabled -> change { runtimePlugins.setEnabled(plugin.id, enabled) } },
			onUninstall = { change { runtimePlugins.uninstall(plugin.id) } },
		)
	}

	HdHairlineButton(
		label = Res.string.plugin_runtime_install.get(),
		onClick = {
			scope.launch {
				val file = FileKit.openFilePicker(type = FileKitType.File(extensions = listOf(PluginPackage.EXTENSION)))
					?: return@launch
				failure = null
				val staged = withContext(ioDispatcher) { stage(fileSystem, file.readBytes()) }
				try {
					pending = withContext(ioDispatcher) { PendingInstall(staged, runtimePlugins.inspect(staged)) }
				} catch (e: PluginPackageException) {
					failure = e.message
					withContext(ioDispatcher) { fileSystem.delete(staged, mustExist = false) }
				} catch (e: IOException) {
					failure = e.message
					withContext(ioDispatcher) { fileSystem.delete(staged, mustExist = false) }
				}
			}
		},
	)

	failure?.let { message ->
		Text(
			text = Res.string.plugin_install_failed.get(message),
			color = MaterialTheme.colorScheme.error,
			style = MaterialTheme.typography.bodyMedium,
		)
	}
	if (restartNeeded) {
		Text(Res.string.plugin_runtime_restart.get(), style = MaterialTheme.typography.bodyMedium)
	}

	InstallDialog(
		pending = pending,
		replacing = pending?.let { install -> plugins?.firstOrNull { it.id == install.plugin.manifest.id } },
		operations = operations,
		onConfirm = { install ->
			pending = null
			scope.launch {
				appScope.launch(ioDispatcher) {
					try {
						runtimePlugins.install(install.path, install.plugin)
					} catch (e: PluginPackageException) {
						failure = e.message
					} catch (e: IOException) {
						failure = e.message
					} finally {
						fileSystem.delete(install.path, mustExist = false)
					}
				}.join()
				refresh()
			}
		},
		onDismiss = {
			pending?.let(::discard)
			pending = null
		},
	)
}

private class PendingInstall(val path: Path, val plugin: PluginPackage)

/** Null for an operation this version of Hammer does not have, which is shown with the changes. */
private fun OperationGrant.access(operations: OperationRegistry): Access? = when (this) {
	is OperationGrant.Named -> operations.find(name)?.access
	is OperationGrant.Scoped -> access
}

@Composable
private fun OperationGrant.label(): String = when (this) {
	is OperationGrant.Named -> name
	is OperationGrant.Scoped -> when (scope) {
		OperationScope.Content ->
			if (access == Access.Read) Res.string.plugin_install_scope_content_read.get() else Res.string.plugin_install_scope_content_write.get()
		OperationScope.Account ->
			if (access == Access.Read) Res.string.plugin_install_scope_account_read.get() else Res.string.plugin_install_scope_account_write.get()
	}
}

/**
 * A copy under the cache directory, since a picked file may only be readable while the picker's grant
 * lasts. Each pick gets its own name, so a later pick cannot replace the package a prompt is showing.
 */
private fun stage(fileSystem: FileSystem, bytes: ByteArray): Path {
	val directory = getCacheDirectory().toPath() / STAGING_DIRECTORY
	fileSystem.createDirectories(directory)
	val path = directory / "${Random.nextLong().toULong().toString(16)}.${PluginPackage.EXTENSION}"
	fileSystem.write(path) { write(bytes) }
	return path
}

private const val STAGING_DIRECTORY = "plugin-install"

@Composable
private fun InstalledPluginRow(
	plugin: InstalledPlugin,
	onEnabledChange: (Boolean) -> Unit,
	onUninstall: () -> Unit,
) {
	Row(
		modifier = Modifier.fillMaxWidth(),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(12.dp),
	) {
		val manifest = plugin.manifest
		HdHairlineToggleRow(
			checked = plugin.enabled,
			onCheckedChange = onEnabledChange,
			label = manifest?.name ?: plugin.id,
			hint = if (manifest != null) {
				Res.string.plugin_runtime_details.get(manifest.version)
			} else {
				Res.string.plugin_runtime_unreadable.get()
			},
			enabled = manifest != null,
			modifier = Modifier.weight(1f),
		)
		var confirming by remember(plugin.id) { mutableStateOf(false) }
		HdHairlineButton(
			label = if (confirming) Res.string.plugin_runtime_uninstall_confirm.get() else Res.string.plugin_runtime_uninstall.get(),
			onClick = { if (confirming) onUninstall() else confirming = true },
			danger = true,
		)
	}
}

@Composable
private fun InstallDialog(
	pending: PendingInstall?,
	replacing: InstalledPlugin?,
	operations: OperationRegistry,
	onConfirm: (PendingInstall) -> Unit,
	onDismiss: () -> Unit,
) {
	// Kept after pending clears, so the dialog still has its content while it animates out.
	var shown by remember { mutableStateOf(pending) }
	if (pending != null) shown = pending
	AnimatedDialog(visible = pending != null, onCloseRequest = onDismiss) {
		val install = shown ?: return@AnimatedDialog
		val manifest = install.plugin.manifest
		HdHairlineDialogShell(
			title = Res.string.plugin_install_title.get(manifest.name),
			onClose = { requestDismiss() },
			closeContentDescription = Res.string.plugin_install_close.get(),
		) {
			Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
				Text(Res.string.plugin_install_version.get(manifest.version), style = MaterialTheme.typography.bodyMedium)
				replacing?.manifest?.let {
					Text(Res.string.plugin_install_replaces.get(it.version), style = MaterialTheme.typography.bodyMedium)
				}
				Text(Res.string.plugin_install_sandbox.get(), style = MaterialTheme.typography.bodyMedium)

				val grants = manifest.permissions.operations.mapNotNull(OperationGrant::parse)
				val byAccess = grants.groupBy { it.access(operations) }
				val reads = byAccess[Access.Read].orEmpty().map { it.label() }
				val changes = (byAccess[Access.Write].orEmpty() + byAccess[null].orEmpty()).map { it.label() }
				val deletes = byAccess[Access.Destructive].orEmpty().map { it.label() }
				if (grants.isEmpty()) {
					Text(Res.string.plugin_install_no_access.get(), style = MaterialTheme.typography.bodyMedium)
				}
				if (reads.isNotEmpty()) {
					Text(Res.string.plugin_install_reads.get(reads.joinToString()), style = MaterialTheme.typography.bodyMedium)
				}
				if (changes.isNotEmpty()) {
					Text(
						text = Res.string.plugin_install_writes.get(changes.joinToString()),
						style = MaterialTheme.typography.bodyMedium,
						color = MaterialTheme.colorScheme.error,
					)
				}
				if (deletes.isNotEmpty()) {
					Text(
						text = Res.string.plugin_install_deletes.get(deletes.joinToString()),
						style = MaterialTheme.typography.bodyMedium,
						color = MaterialTheme.colorScheme.error,
					)
				}
				if (manifest.exporters.isNotEmpty()) {
					Text(
						text = Res.string.plugin_install_exports.get(manifest.exporters.joinToString { it.label }),
						style = MaterialTheme.typography.bodyMedium,
					)
				}
				if (manifest.commands.isNotEmpty()) {
					Text(
						text = Res.string.plugin_install_commands.get(manifest.commands.joinToString { "hammer ${it.name}" }),
						style = MaterialTheme.typography.bodyMedium,
					)
				}

				Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
					HdHairlineButton(label = Res.string.plugin_install_cancel.get(), onClick = { requestDismiss() })
					HdHairlineButton(
						label = Res.string.plugin_install_confirm.get(),
						onClick = { onConfirm(install) },
						emphasised = true,
					)
				}
			}
		}
	}
}
