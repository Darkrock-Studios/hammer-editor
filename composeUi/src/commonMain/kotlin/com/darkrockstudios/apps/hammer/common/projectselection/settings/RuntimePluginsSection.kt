package com.darkrockstudios.apps.hammer.common.projectselection.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Output
import androidx.compose.material.icons.outlined.Spellcheck
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.common.components.projectroot.ProjectRoot
import com.darkrockstudios.apps.hammer.common.compose.AnimatedDialog
import com.darkrockstudios.apps.hammer.common.compose.LocalScreenCharacteristic
import com.darkrockstudios.apps.hammer.common.compose.SimpleConfirm
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.WindowWidthSizeClass
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdDropdownMenu
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineButton
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineDialogShell
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineToggleRow
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdMetaText
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdMonoLabel
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdSettingGroup
import com.darkrockstudios.apps.hammer.common.compose.lastOf
import com.darkrockstudios.apps.hammer.common.compose.linkify
import com.darkrockstudios.apps.hammer.common.compose.plugin.PluginSettingsPane
import com.darkrockstudios.apps.hammer.common.compose.rememberIoDispatcher
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.compose.theme.LocalHammerColors
import com.darkrockstudios.apps.hammer.common.dependencyinjection.APP_SCOPE
import com.darkrockstudios.apps.hammer.common.getCacheDirectory
import com.darkrockstudios.apps.hammer.common.projectroot.getDestinationIcon
import com.darkrockstudios.apps.hammer.common.spellcheck.displayName
import com.darkrockstudios.apps.hammer.common.util.Locale
import com.darkrockstudios.apps.hammer.composeui.resources.*
import com.darkrockstudios.apps.hammer.operations.Access
import com.darkrockstudios.apps.hammer.operations.OperationRegistry
import com.darkrockstudios.apps.hammer.operations.OperationScope
import com.darkrockstudios.apps.hammer.operations.plugin.ActionPlace
import com.darkrockstudios.apps.hammer.operations.plugin.SettingDeclaration
import com.darkrockstudios.apps.hammer.plugins.wasmhost.InstalledPlugin
import com.darkrockstudios.apps.hammer.plugins.wasmhost.OperationGrant
import com.darkrockstudios.apps.hammer.plugins.wasmhost.PluginManifest
import com.darkrockstudios.apps.hammer.plugins.wasmhost.PluginPackage
import com.darkrockstudios.apps.hammer.plugins.wasmhost.PluginPackageException
import com.darkrockstudios.apps.hammer.plugins.wasmhost.RuntimePlugins
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.openFilePicker
import io.github.vinceglb.filekit.size
import io.github.vinceglb.filekit.source
import kotlin.random.Random
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.io.buffered
import okio.FileSystem
import okio.IOException
import okio.Path
import okio.Path.Companion.toPath
import org.koin.compose.koinInject
import org.koin.core.qualifier.named
import com.darkrockstudios.apps.hammer.confirm_kind_uninstall
import com.darkrockstudios.apps.hammer.Res as CommonRes

/**
 * Install, enable, disable, and uninstall runtime plugins, and open the settings of an active one
 * that has a pane in [settings]. Every change applies at once.
 */
@Composable
internal fun ColumnScope.RuntimePluginsSection(
	runtimePlugins: RuntimePlugins,
	settings: (pluginId: String) -> PluginSettingsPane?,
	onOpenSettings: (pluginId: String) -> Unit,
	showHeader: Boolean = true,
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

	val plugins = installed
	RuntimePluginsList(
		showHeader = showHeader,
		installed = plugins,
		failure = failure,
		hasSettings = { id -> settings(id) != null },
		onEnabledChange = { id, enabled -> change { runtimePlugins.setEnabled(id, enabled) } },
		onDetails = { details = it },
		onSettings = onOpenSettings,
		onUninstall = { id -> change { runtimePlugins.uninstall(id) } },
		onInstall = {
			scope.launch {
				// Deleted unless the prompt takes it, even when leaving Settings cancels this part way.
				var staged: Path? = null
				try {
					val file = FileKit.openFilePicker(type = FileKitType.File(extensions = listOf(PluginPackage.EXTENSION)))
						?: return@launch
					failure = null
					val path = stagingPath().also { staged = it }
					withContext(ioDispatcher) { stage(fileSystem, file, path) }
					pending = withContext(ioDispatcher) {
						PendingInstall(path, runtimePlugins.inspect(path), fileSystem.metadata(path).size ?: 0)
					}
					staged = null
				} catch (e: CancellationException) {
					throw e
				} catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
					// A malformed package can make the readers throw anything; it is shown, not left to crash the app.
					failure = e.message ?: e::class.simpleName
				} finally {
					staged?.let { path -> withContext(NonCancellable + ioDispatcher) { fileSystem.delete(path, mustExist = false) } }
				}
			}
		},
	)

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

/** The installed plugins as a setting group, then the install button; [installed] is null while it loads. */
@Composable
internal fun ColumnScope.RuntimePluginsList(
	installed: List<InstalledPlugin>?,
	failure: String?,
	hasSettings: (pluginId: String) -> Boolean,
	onEnabledChange: (pluginId: String, enabled: Boolean) -> Unit,
	onDetails: (InstalledPlugin) -> Unit,
	onSettings: (pluginId: String) -> Unit,
	onUninstall: (pluginId: String) -> Unit,
	onInstall: () -> Unit,
	/** Off where a dialog's title already names the list. */
	showHeader: Boolean = true,
) {
	var uninstalling by remember { mutableStateOf<InstalledPlugin?>(null) }

	HdSettingGroup(title = if (showHeader) Res.string.plugin_runtime_header.get() else null) {
		if (installed != null && installed.isEmpty()) {
			Text(Res.string.plugin_runtime_empty.get(), style = MaterialTheme.typography.bodyMedium)
		}
		installed?.forEach { plugin ->
			InstalledPluginRow(
				plugin = plugin,
				onEnabledChange = { enabled -> onEnabledChange(plugin.id, enabled) },
				onDetails = { onDetails(plugin) },
				onSettings = if (hasSettings(plugin.id)) ({ onSettings(plugin.id) }) else null,
				onUninstall = { uninstalling = plugin },
			)
		}
	}

	HdHairlineButton(label = Res.string.plugin_runtime_install.get(), onClick = onInstall)

	failure?.let { message ->
		Text(
			text = Res.string.plugin_install_failed.get(message),
			color = MaterialTheme.colorScheme.error,
			style = MaterialTheme.typography.bodyMedium,
		)
	}

	uninstalling?.let { plugin ->
		SimpleConfirm(
			title = Res.string.plugin_runtime_uninstall_title.get(plugin.manifest?.name ?: plugin.id),
			message = Res.string.plugin_runtime_uninstall_message.get(),
			positiveButton = Res.string.plugin_runtime_uninstall.get(),
			negativeButton = Res.string.plugin_runtime_uninstall_cancel.get(),
			destructive = true,
			kind = CommonRes.string.confirm_kind_uninstall.get(),
			onDismiss = { uninstalling = null },
		) {
			uninstalling = null
			onUninstall(plugin.id)
		}
	}
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

/**
 * [grants] by the [Access] level the install prompt lists them under. A named operation this version of
 * Hammer does not have, which [accessOf] gives null for, is listed after the changes.
 */
internal fun grantsByAccess(grants: List<OperationGrant>, accessOf: (String) -> Access?): Map<Access, List<OperationGrant>> {
	val byAccess = grants.groupBy {
		when (it) {
			is OperationGrant.Named -> accessOf(it.name)
			is OperationGrant.Scoped -> it.access
		}
	}
	return Access.entries.associateWith { byAccess[it].orEmpty() } +
		(Access.Write to byAccess[Access.Write].orEmpty() + byAccess[null].orEmpty())
}

@Composable
private fun OperationGrant.label(): String = when (this) {
	is OperationGrant.Named -> name
	is OperationGrant.Scoped -> when (scope) {
		OperationScope.Content ->
			if (access == Access.Read) Res.string.plugin_install_scope_content_read.get() else Res.string.plugin_install_scope_content_write.get()
		// A package asking to change the account is refused, so only reading it gets this far.
		OperationScope.Account -> Res.string.plugin_install_scope_account_read.get()
		OperationScope.Plugins ->
			if (access == Access.Read) Res.string.plugin_install_scope_plugins_read.get() else Res.string.plugin_install_scope_plugins_write.get()
	}
}

/**
 * Where a picked package is copied, under the cache directory, since a picked file may only be readable
 * while the picker's grant lasts. Each pick gets its own name, so a later pick cannot replace the package
 * a prompt is showing.
 */
private fun stagingPath(): Path =
	getCacheDirectory().toPath() / STAGING_DIRECTORY / "${Random.nextLong().toULong().toString(16)}.${PluginPackage.EXTENSION}"

/** Copies [file] to [path] in chunks, refused once past [PluginPackage.MAX_PACKAGE_BYTES], since a picked file can be anything. */
private fun stage(fileSystem: FileSystem, file: PlatformFile, path: Path) {
	val tooLarge = "The package is larger than ${PluginPackage.MAX_PACKAGE_BYTES} bytes"
	if (file.size() > PluginPackage.MAX_PACKAGE_BYTES) throw PluginPackageException(tooLarge)
	fileSystem.createDirectories(path.parent!!)
	file.source().buffered().use { source ->
		fileSystem.write(path) {
			val buffer = ByteArray(STAGING_CHUNK_BYTES)
			var copied = 0L
			while (true) {
				val count = source.readAtMostTo(buffer)
				if (count == -1) break
				copied += count
				// The size a picker reports may be unknown, so the copy checks too.
				if (copied > PluginPackage.MAX_PACKAGE_BYTES) throw PluginPackageException(tooLarge)
				write(buffer, 0, count)
			}
		}
	}
}

private const val STAGING_DIRECTORY = "plugin-install"
private const val STAGING_CHUNK_BYTES = 64 * 1024

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
	val name = manifest?.name ?: plugin.id
	val settings: @Composable () -> Unit = {
		onSettings?.let { HdHairlineButton(label = Res.string.plugin_settings_open.get(), onClick = it) }
	}
	val compact = LocalScreenCharacteristic.current.windowWidthClass == WindowWidthSizeClass.Compact

	Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
		Row(
			modifier = Modifier.fillMaxWidth(),
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.spacedBy(12.dp),
		) {
			HdHairlineToggleRow(
				checked = plugin.enabled,
				onCheckedChange = onEnabledChange,
				label = name,
				hint = when {
					manifest == null -> Res.string.plugin_runtime_unreadable.get()
					plugin.turnedOffAfterCrash -> Res.string.plugin_runtime_turned_off_after_crash.get()
					else -> Res.string.plugin_runtime_details.get(manifest.version)
				},
				enabled = manifest != null,
				modifier = Modifier.weight(1f),
			)
			// On a phone Settings goes under the toggle, which would otherwise be squeezed to a sliver.
			if (!compact) settings()
			PluginRowMenu(
				name = name,
				onDetails = onDetails.takeIf { manifest != null },
				onUninstall = onUninstall,
			)
		}
		// Indented to the name, past the toggle's checkbox.
		if (compact) Box(Modifier.padding(start = 18.dp + Ui.Padding.M)) { settings() }
	}
}

/** Details and Uninstall, kept off the row so a list of plugins is not a wall of buttons. */
@Composable
private fun PluginRowMenu(name: String, onDetails: (() -> Unit)?, onUninstall: () -> Unit) {
	var expanded by remember { mutableStateOf(false) }
	Box {
		IconButton(onClick = { expanded = true }) {
			Icon(
				imageVector = Icons.Default.MoreVert,
				contentDescription = Res.string.plugin_runtime_more.get(name),
				tint = MaterialTheme.colorScheme.onSurfaceVariant,
			)
		}
		HdDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
			onDetails?.let {
				DropdownMenuItem(
					text = { Text(Res.string.plugin_runtime_details_open.get()) },
					onClick = {
						expanded = false
						it()
					},
				)
			}
			DropdownMenuItem(
				text = { Text(Res.string.plugin_runtime_uninstall.get(), color = MaterialTheme.colorScheme.error) },
				onClick = {
					expanded = false
					onUninstall()
				},
			)
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
	val shown = lastOf(pending)
	AnimatedDialog(visible = pending != null, onCloseRequest = onDismiss) {
		val install = shown ?: return@AnimatedDialog
		PluginInstallCard(
			manifest = install.plugin.manifest,
			size = install.size,
			translations = install.plugin.translations.keys,
			settings = install.plugin.settings,
			replacingVersion = replacing?.manifest?.version,
			accessOf = { operations.find(it)?.access },
			onConfirm = { onConfirm(install) },
			onCancel = { requestDismiss() },
		)
	}
}

/** The install prompt without its dialog window. */
@Composable
internal fun PluginInstallCard(
	manifest: PluginManifest,
	size: Long?,
	translations: Collection<String>,
	settings: List<SettingDeclaration>,
	/** The installed version this replaces, if any. */
	replacingVersion: String?,
	accessOf: (String) -> Access?,
	onConfirm: () -> Unit,
	onCancel: () -> Unit,
) {
	HdHairlineDialogShell(
		title = if (replacingVersion == null) {
			Res.string.plugin_install_title.get(manifest.name)
		} else {
			Res.string.plugin_update_title.get(manifest.name)
		},
		onClose = onCancel,
		closeContentDescription = Res.string.plugin_install_close.get(),
	) {
		PluginDetails(
			manifest = manifest,
			size = size,
			translations = translations,
			permissions = manifest.permissions.operations,
			accessOf = accessOf,
			settings = settings,
			replacingVersion = replacingVersion,
			modifier = Modifier.weight(1f, fill = false),
		)
		Row(
			modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
			horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
		) {
			HdHairlineButton(label = Res.string.plugin_install_cancel.get(), onClick = onCancel)
			HdHairlineButton(
				label = if (replacingVersion == null) {
					Res.string.plugin_install_confirm.get()
				} else {
					Res.string.plugin_update_confirm.get()
				},
				onClick = onConfirm,
				emphasised = true,
			)
		}
	}
}

/**
 * What a plugin is, what it adds, and what it may use, as the install prompt and its details show it.
 * [size] is its package's, and [translations] its translations' language tags. Scrolls on its own, so
 * a long list of permissions cannot push the dialog's buttons off screen.
 */
@Composable
private fun PluginDetails(
	manifest: PluginManifest,
	size: Long?,
	translations: Collection<String>,
	/** What it may use: what its manifest asks for, or for an installed plugin, what the user granted. */
	permissions: List<String>,
	/** The access level of an operation by name, or null when it is unknown. */
	accessOf: (String) -> Access?,
	/** Its declared settings, for their labels; keys stand in without them. */
	settings: List<SettingDeclaration> = emptyList(),
	/** The installed version an install replaces. */
	replacingVersion: String? = null,
	modifier: Modifier = Modifier,
) {
	Column(
		modifier = modifier.verticalScroll(rememberScrollState()),
		verticalArrangement = Arrangement.spacedBy(20.dp),
	) {
		manifest.description?.let { description ->
			val linkStyle = SpanStyle(color = MaterialTheme.colorScheme.primary, textDecoration = TextDecoration.Underline)
			Text(linkify(description, linkStyle), style = MaterialTheme.typography.bodyMedium)
		}
		PluginFacts(manifest, size, translations, replacingVersion)
		if (manifest.actions.isNotEmpty() || manifest.exporters.isNotEmpty() ||
			manifest.commands.isNotEmpty() || manifest.diagnostics.isNotEmpty()
		) {
			DetailsBlock(Res.string.plugin_install_adds.get()) { PluginFeatures(manifest) }
		}
		PluginAccess(manifest, permissions, accessOf, settings)
	}
}

/** A mono heading over a hairline, so a block's title never reads like one of its values. */
@Composable
private fun DetailsBlock(
	title: String,
	trailing: @Composable () -> Unit = {},
	content: @Composable ColumnScope.() -> Unit,
) {
	Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
		Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
			HdMonoLabel(text = title, color = MaterialTheme.colorScheme.onSurface)
			HorizontalDivider(
				modifier = Modifier.weight(1f),
				thickness = Dp.Hairline,
				color = MaterialTheme.colorScheme.outlineVariant,
			)
			trailing()
		}
		content()
	}
}

/** Version, size and languages as one meta line, wrapping as it needs to. */
@Composable
private fun PluginFacts(manifest: PluginManifest, size: Long?, translations: Collection<String>, replacingVersion: String?) {
	val facts = buildList {
		add(
			replacingVersion?.let { Res.string.plugin_facts_update.get(manifest.version, it) }
				?: Res.string.plugin_facts_version.get(manifest.version),
		)
		size?.let { add(packageSize(it)) }
		if (manifest.limits.memory > PluginManifest.DEFAULT_MEMORY_MIB) {
			add(Res.string.plugin_facts_memory.get(manifest.limits.memory.toString()))
		}
		if (manifest.languages.isNotEmpty()) {
			add(Res.string.plugin_facts_languages.get(languageNames(manifest.languages)))
		}
		if (translations.isNotEmpty()) {
			add(Res.string.plugin_facts_translations.get(languageNames(translations)))
		}
	}
	// A no-break space before each dot keeps it off the start of a wrapped line.
	HdMetaText(text = facts.joinToString("\u00A0· "))
}

@Composable
private fun PluginFeatures(manifest: PluginManifest) {
	Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
		if (manifest.actions.isNotEmpty()) {
			FeatureGroup(Icons.Outlined.TouchApp, Res.string.plugin_install_actions.get()) {
				manifest.actions.forEach { action ->
					Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
						Text(action.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
						ActionPlace.entries.filter { it.id in action.places }.forEach { PlaceIcon(it) }
					}
				}
			}
		}
		if (manifest.exporters.isNotEmpty()) {
			FeatureGroup(Icons.Outlined.Output, Res.string.plugin_install_exports.get()) {
				manifest.exporters.forEach { exporter ->
					Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
						Text(exporter.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
						HdMonoLabel(text = ".${exporter.extension}")
					}
				}
			}
		}
		if (manifest.diagnostics.isNotEmpty()) {
			FeatureGroup(Icons.Outlined.Spellcheck, Res.string.plugin_install_diagnostics.get()) {
				manifest.diagnostics.forEach { Text(it.label, style = MaterialTheme.typography.bodyMedium) }
			}
		}
		if (manifest.commands.isNotEmpty()) {
			FeatureGroup(Icons.Outlined.Terminal, Res.string.plugin_install_commands.get()) {
				manifest.commands.forEach { command ->
					Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
						Text(
							text = "hammer ${command.name}",
							style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
						)
						Text(
							text = command.help,
							style = MaterialTheme.typography.bodySmall,
							color = MaterialTheme.colorScheme.onSurfaceVariant,
						)
					}
				}
			}
		}
	}
}

/** One kind of thing a plugin adds: its icon and name, and the plugin's items of that kind under them. */
@Composable
private fun FeatureGroup(icon: ImageVector, title: String, items: @Composable ColumnScope.() -> Unit) {
	Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
		Icon(
			imageVector = icon,
			contentDescription = null,
			tint = MaterialTheme.colorScheme.onSurfaceVariant,
			modifier = Modifier.size(18.dp),
		)
		Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
			HdMonoLabel(text = title, modifier = Modifier.padding(top = 2.dp))
			items()
		}
	}
}

/** The navigation icon of the screen whose menus show an action, named by its tooltip. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaceIcon(place: ActionPlace) {
	val destination = place.destination()
	val name = destination.text.get()
	TooltipBox(
		positionProvider = TooltipDefaults.rememberTooltipPositionProvider(),
		tooltip = { PlainTooltip { Text(name) } },
		state = rememberTooltipState(),
	) {
		Icon(
			imageVector = getDestinationIcon(destination),
			contentDescription = name,
			tint = MaterialTheme.colorScheme.onSurfaceVariant,
			modifier = Modifier.size(18.dp),
		)
	}
}

private fun ActionPlace.destination(): ProjectRoot.DestinationTypes = when (this) {
	ActionPlace.Project -> ProjectRoot.DestinationTypes.Home
	ActionPlace.Scene -> ProjectRoot.DestinationTypes.Editor
	ActionPlace.Note -> ProjectRoot.DestinationTypes.Notes
	ActionPlace.Entry -> ProjectRoot.DestinationTypes.Encyclopedia
	ActionPlace.Event -> ProjectRoot.DestinationTypes.TimeLine
}

@Composable
private fun PluginAccess(
	manifest: PluginManifest,
	permissions: List<String>,
	accessOf: (String) -> Access?,
	settings: List<SettingDeclaration>,
) {
	var explaining by remember { mutableStateOf(false) }
	DetailsBlock(
		title = Res.string.plugin_install_access.get(),
		trailing = {
			Icon(
				imageVector = Icons.Outlined.Info,
				contentDescription = Res.string.plugin_install_sandbox_info.get(),
				tint = if (explaining) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
				modifier = Modifier
					.clickable { explaining = !explaining }
					.padding(2.dp)
					.size(16.dp),
			)
		},
	) {
		AnimatedVisibility(visible = explaining) {
			Text(
				text = Res.string.plugin_install_sandbox.get(),
				style = MaterialTheme.typography.bodySmall,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
			)
		}

		val grants = permissions.mapNotNull(OperationGrant::parse)
		if (grants.isEmpty()) {
			Text(Res.string.plugin_install_no_access.get(), style = MaterialTheme.typography.bodyMedium)
		}
		val byAccess = grantsByAccess(grants, accessOf)
		val colors = LocalHammerColors.current
		AccessLevel(
			title = Res.string.plugin_install_reads.get(),
			color = MaterialTheme.colorScheme.outlineVariant,
			labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
			grants = byAccess.getValue(Access.Read),
		)
		AccessLevel(
			title = Res.string.plugin_install_writes.get(),
			color = colors.warning,
			grants = byAccess.getValue(Access.Write),
		)
		AccessLevel(
			title = Res.string.plugin_install_prose.get(),
			color = colors.danger,
			grants = byAccess.getValue(Access.Prose),
		)
		AccessLevel(
			title = Res.string.plugin_install_deletes.get(),
			color = colors.danger,
			grants = byAccess.getValue(Access.Destructive),
		)
		val http = manifest.permissions.http
		val userHosts = manifest.permissions.userHosts
		if (http.isNotEmpty() || userHosts != null) {
			AccessFrame(Res.string.plugin_install_http.get(), colors.danger) {
				http.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
				userHosts?.let {
					val label = { key: String -> settings.firstOrNull { it.key == key }?.label ?: key }
					Text(
						text = it.toggle?.let { toggle -> Res.string.plugin_install_user_hosts.get(label(toggle), label(it.setting)) }
							?: Res.string.plugin_install_user_host.get(label(it.setting)),
						style = MaterialTheme.typography.bodyMedium,
					)
				}
			}
		}
		val hammerServer = manifest.permissions.hammerServer
		if (hammerServer.isNotEmpty()) {
			AccessFrame(Res.string.plugin_install_hammer_server.get(), colors.warning) {
				hammerServer.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
			}
		}
	}
}

/**
 * The grants of one [Access] level behind a marginalia rule in [color]. Named operations are grouped
 * by what they act on, `scene` beside `create · rename · move`, so a long list stays scannable. A verb's
 * own words, such as `references set`, are joined by no-break spaces so it never wraps in half.
 */
@Composable
private fun AccessLevel(title: String, color: Color, grants: List<OperationGrant>, labelColor: Color = color) {
	if (grants.isEmpty()) return
	val scoped = grants.filterIsInstance<OperationGrant.Scoped>()
	val named = grants.filterIsInstance<OperationGrant.Named>()
		.groupBy({ it.name.substringBefore('.') }, { it.name.substringAfter('.').replace('.', '\u00A0') })
	AccessFrame(title, color, labelColor) {
		scoped.forEach { Text(it.label(), style = MaterialTheme.typography.bodyMedium) }
		named.forEach { (subject, verbs) ->
			Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
				HdMonoLabel(text = subject, modifier = Modifier.width(SUBJECT_WIDTH).padding(top = 3.dp))
				Text(verbs.joinToString(" · "), style = MaterialTheme.typography.bodyMedium)
			}
		}
	}
}

/** A titled run of access rows behind a marginalia rule in [color]. */
@Composable
private fun AccessFrame(title: String, color: Color, labelColor: Color = color, content: @Composable ColumnScope.() -> Unit) {
	Row(Modifier.height(IntrinsicSize.Min)) {
		Box(Modifier.width(2.dp).fillMaxHeight().background(color))
		Column(
			modifier = Modifier.padding(start = 12.dp),
			verticalArrangement = Arrangement.spacedBy(6.dp),
		) {
			HdMonoLabel(text = title, color = labelColor)
			content()
		}
	}
}

private val SUBJECT_WIDTH = 72.dp

private fun languageNames(tags: Collection<String>): String =
	tags.joinToString { Locale.forLanguageTag(it.replace('_', '-')).displayName() }

@Composable
private fun DetailsDialog(plugin: InstalledPlugin?, operations: OperationRegistry, onDismiss: () -> Unit) {
	val shown = lastOf(plugin)
	AnimatedDialog(visible = plugin != null, onCloseRequest = onDismiss) {
		val installed = shown ?: return@AnimatedDialog
		PluginDetailsCard(
			installed = installed,
			accessOf = { operations.find(it)?.access },
			onClose = { requestDismiss() },
		)
	}
}

/** An installed plugin's details without their dialog window. */
@Composable
internal fun PluginDetailsCard(installed: InstalledPlugin, accessOf: (String) -> Access?, onClose: () -> Unit) {
	val manifest = installed.manifest ?: return
	HdHairlineDialogShell(
		title = manifest.name,
		onClose = onClose,
		closeContentDescription = Res.string.plugin_details_close.get(),
	) {
		// Only what the manifest still asks for applies.
		val permissions = installed.granted.filter { it in manifest.permissions.operations }
		PluginDetails(
			manifest = manifest,
			size = installed.size,
			translations = installed.translations,
			permissions = permissions,
			accessOf = accessOf,
			modifier = Modifier.weight(1f, fill = false),
		)
	}
}
