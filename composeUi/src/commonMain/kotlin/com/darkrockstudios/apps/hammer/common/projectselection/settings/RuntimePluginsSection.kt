package com.darkrockstudios.apps.hammer.common.projectselection.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.common.compose.AnimatedDialog
import com.darkrockstudios.apps.hammer.common.compose.LocalScreenCharacteristic
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineButton
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineDialogShell
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineToggleRow
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdMonoLabel
import com.darkrockstudios.apps.hammer.common.compose.linkify
import com.darkrockstudios.apps.hammer.common.compose.plugin.PluginSettingsPane
import com.darkrockstudios.apps.hammer.common.compose.rememberIoDispatcher
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.dependencyinjection.APP_SCOPE
import com.darkrockstudios.apps.hammer.common.spellcheck.displayName
import com.darkrockstudios.apps.hammer.common.util.Locale
import com.darkrockstudios.apps.hammer.common.getCacheDirectory
import com.darkrockstudios.apps.hammer.composeui.resources.*
import com.darkrockstudios.apps.hammer.operations.Access
import com.darkrockstudios.apps.hammer.operations.OperationRegistry
import com.darkrockstudios.apps.hammer.operations.OperationScope
import com.darkrockstudios.apps.hammer.plugins.wasmhost.InstalledPlugin
import com.darkrockstudios.apps.hammer.plugins.wasmhost.OperationGrant
import com.darkrockstudios.apps.hammer.plugins.wasmhost.PluginManifest
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

/**
 * Install, enable, disable, and uninstall runtime plugins, and open the settings of an active one
 * that has a pane in [settings]. Every change applies at once.
 */
@Composable
internal fun ColumnScope.RuntimePluginsSection(
	runtimePlugins: RuntimePlugins,
	settings: (pluginId: String) -> PluginSettingsPane?,
	onOpenSettings: (pluginId: String) -> Unit,
) {
	val scope = rememberCoroutineScope()
	// Changes run on the app scope so leaving Settings cannot cancel one half done.
	val appScope = koinInject<CoroutineScope>(named(APP_SCOPE))
	val ioDispatcher = rememberIoDispatcher()
	val fileSystem = koinInject<FileSystem>()
	val operations = koinInject<OperationRegistry>()

	var installed by remember { mutableStateOf<List<InstalledPlugin>?>(null) }
	var pending by remember { mutableStateOf<PendingInstall?>(null) }
	var details by remember { mutableStateOf<InstalledPlugin?>(null) }
	var failure by remember { mutableStateOf<String?>(null) }

	suspend fun refresh() {
		installed = withContext(ioDispatcher) { runtimePlugins.installed() }
	}
	LaunchedEffect(runtimePlugins) { refresh() }

	fun change(action: () -> Unit) {
		failure = null
		scope.launch {
			appScope.launch(ioDispatcher) {
				try {
					action()
				} catch (e: PluginPackageException) {
					failure = e.message
				} catch (e: IOException) {
					failure = e.message
				}
			}.join()
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
			onDetails = { details = plugin },
			onSettings = settings(plugin.id)?.let { { onOpenSettings(plugin.id) } },
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
					pending = withContext(ioDispatcher) {
						PendingInstall(staged, runtimePlugins.inspect(staged), fileSystem.metadata(staged).size ?: 0)
					}
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

	DetailsDialog(plugin = details, operations = operations, onDismiss = { details = null })
}

/** [size] is the package's, which is what installing it keeps. */
private class PendingInstall(val path: Path, val plugin: PluginPackage, val size: Long)

private const val BYTES_PER_KB = 1024L
private const val BYTES_PER_MB = 1024L * 1024

@Composable
private fun packageSize(bytes: Long): String {
	val kb = ((bytes + BYTES_PER_KB - 1) / BYTES_PER_KB).coerceAtLeast(1)
	if (kb < BYTES_PER_KB) return Res.string.plugin_install_size_kb.get(kb.toString())
	val tenths = (bytes * 10 + BYTES_PER_MB / 2) / BYTES_PER_MB
	return Res.string.plugin_install_size_mb.get("${tenths / 10}.${tenths % 10}")
}

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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InstalledPluginRow(
	plugin: InstalledPlugin,
	onEnabledChange: (Boolean) -> Unit,
	onDetails: () -> Unit,
	/** Null when the plugin is not active or has no settings. */
	onSettings: (() -> Unit)?,
	onUninstall: () -> Unit,
) {
	val manifest = plugin.manifest
	val toggle: @Composable (Modifier) -> Unit = { modifier ->
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
			modifier = modifier,
		)
	}
	val buttons: @Composable () -> Unit = {
		if (manifest != null) HdHairlineButton(label = Res.string.plugin_runtime_details_open.get(), onClick = onDetails)
		onSettings?.let { HdHairlineButton(label = Res.string.plugin_settings_open.get(), onClick = it) }
		var confirming by remember(plugin.id) { mutableStateOf(false) }
		HdHairlineButton(
			label = if (confirming) Res.string.plugin_runtime_uninstall_confirm.get() else Res.string.plugin_runtime_uninstall.get(),
			onClick = { if (confirming) onUninstall() else confirming = true },
			danger = true,
		)
	}

	// On a phone the buttons go under the toggle, which would otherwise be squeezed to a sliver.
	if (LocalScreenCharacteristic.current.windowWidthClass == WindowWidthSizeClass.Compact) {
		Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
			toggle(Modifier.fillMaxWidth())
			FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
				buttons()
			}
		}
	} else {
		Row(
			modifier = Modifier.fillMaxWidth(),
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.spacedBy(12.dp),
		) {
			toggle(Modifier.weight(1f))
			buttons()
		}
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
				PluginDetails(manifest, install.size, install.plugin.translations.keys, manifest.permissions.operations, operations)
				replacing?.manifest?.let {
					Text(Res.string.plugin_install_replaces.get(it.version), style = MaterialTheme.typography.bodyMedium)
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

/**
 * What a plugin is, what it may use, and what it adds, as the install prompt and its details show it.
 * [size] is its package's, and [translations] its translations' language tags.
 */
@Composable
private fun PluginDetails(
	manifest: PluginManifest,
	size: Long?,
	translations: Collection<String>,
	/** What it may use: what its manifest asks for, or for an installed plugin, what the user granted. */
	permissions: List<String>,
	operations: OperationRegistry,
) {
	Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
		manifest.description?.let { description ->
			val linkStyle = SpanStyle(color = MaterialTheme.colorScheme.primary, textDecoration = TextDecoration.Underline)
			Text(linkify(description, linkStyle), style = MaterialTheme.typography.bodyMedium)
		}
		Text(Res.string.plugin_install_version.get(manifest.version), style = MaterialTheme.typography.bodyMedium)
		size?.let { Text(packageSize(it), style = MaterialTheme.typography.bodyMedium) }
		if (manifest.languages.isNotEmpty()) {
			Text(Res.string.plugin_details_languages.get(languageNames(manifest.languages)), style = MaterialTheme.typography.bodyMedium)
		}
		if (translations.isNotEmpty()) {
			Text(Res.string.plugin_details_translations.get(languageNames(translations)), style = MaterialTheme.typography.bodyMedium)
		}
		Text(Res.string.plugin_install_sandbox.get(), style = MaterialTheme.typography.bodyMedium)

		val grants = permissions.mapNotNull(OperationGrant::parse)
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
		if (manifest.actions.isNotEmpty()) {
			Text(
				text = Res.string.plugin_install_actions.get(manifest.actions.joinToString { it.label }),
				style = MaterialTheme.typography.bodyMedium,
			)
		}
		if (manifest.commands.isNotEmpty()) {
			Text(
				text = Res.string.plugin_install_commands.get(manifest.commands.joinToString { "hammer ${it.name}" }),
				style = MaterialTheme.typography.bodyMedium,
			)
		}
		if (manifest.diagnostics.isNotEmpty()) {
			Text(
				text = Res.string.plugin_install_diagnostics.get(manifest.diagnostics.joinToString { it.label }),
				style = MaterialTheme.typography.bodyMedium,
			)
		}
		if (manifest.limits.memory > PluginManifest.DEFAULT_MEMORY_MIB) {
			Text(
				text = Res.string.plugin_install_memory.get(manifest.limits.memory.toString()),
				style = MaterialTheme.typography.bodyMedium,
			)
		}
	}
}

private fun languageNames(tags: Collection<String>): String =
	tags.joinToString { Locale.forLanguageTag(it.replace('_', '-')).displayName() }

@Composable
private fun DetailsDialog(plugin: InstalledPlugin?, operations: OperationRegistry, onDismiss: () -> Unit) {
	// Kept after plugin clears, so the dialog still has its content while it animates out.
	var shown by remember { mutableStateOf(plugin) }
	if (plugin != null) shown = plugin
	AnimatedDialog(visible = plugin != null, onCloseRequest = onDismiss) {
		val installed = shown ?: return@AnimatedDialog
		val manifest = installed.manifest ?: return@AnimatedDialog
		HdHairlineDialogShell(
			title = manifest.name,
			onClose = { requestDismiss() },
			closeContentDescription = Res.string.plugin_details_close.get(),
		) {
			Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
				// Only what the manifest still asks for applies.
				val permissions = installed.granted.filter { it in manifest.permissions.operations }
				PluginDetails(manifest, installed.size, installed.translations, permissions, operations)
				HdHairlineButton(label = Res.string.plugin_details_done.get(), onClick = { requestDismiss() })
			}
		}
	}
}
