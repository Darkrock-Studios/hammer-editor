package com.darkrockstudios.build

// Use legacy java.text date formatting to avoid Kotlin/Gradle embedded version or Android API constraints
import com.formdev.flatlaf.FlatDarculaLaf
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.GridLayout
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.concurrent.CountDownLatch
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.ButtonGroup
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JRadioButton
import javax.swing.JScrollPane
import javax.swing.JSplitPane
import javax.swing.JTabbedPane
import javax.swing.JTextArea
import javax.swing.SwingUtilities
import javax.swing.UIManager
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

/** Publish scope chosen in the prepare-release dialog. */
private enum class ReleaseScope { ALL, TARGETED, SERVER_ONLY }

fun writeSemvar(oldSemVar: String, newSemVar: SemVar, versionFile: File) {
	val versions = versionFile.readText()
	val updated = versions.replace("app = \"$oldSemVar\"", "app = \"$newSemVar\"")
	versionFile.writeText(updated)
}

/** Body of the most recent `## [version] - date` entry in CHANGELOG.md, or null if none. */
fun extractLatestChangelog(changelogFile: File): String? {
	if (!changelogFile.exists()) return null
	val text = changelogFile.readText()
	val firstHeader = text.indexOf("## [")
	if (firstHeader < 0) return null
	val bodyStart = text.indexOf('\n', firstHeader)
	if (bodyStart < 0) return null
	val nextHeader = text.indexOf("## [", bodyStart)
	val body = if (nextHeader < 0) text.substring(bodyStart) else text.substring(bodyStart, nextHeader)
	return body.trim().ifEmpty { null }
}

/** Prepends this release's entry to CHANGELOG.md and returns the entry text. */
fun writeChangelogMarkdown(releaseInfo: ReleaseInfo, changelogFile: File): String {
	val currentChangelog = changelogFile.readText()
	val withoutHeader = currentChangelog.substring(currentChangelog.indexOf('\n') + 1)

	val headerDate = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-M-d"))
	val entry = "## [${releaseInfo.semVar}] - $headerDate\n\n" + releaseInfo.changeLog + "\n"

	val newChangeLog = "# Changelog\n\n" + entry + "\n" + withoutHeader

	changelogFile.writeText(newChangeLog)
	println("CHANGELOG.md written")

	return entry
}

/** The same entry, baked into the app so the client can show it without calling GitHub. */
fun writeBakedChangelog(entry: String, bakedFile: File) {
	bakedFile.parentFile.mkdirs()
	bakedFile.writeText(entry)
	println("Baked changelog written to ${bakedFile.path}")
}

/** Tag-message trailer that tells CI to publish the release instead of leaving it a draft. */
const val AUTO_PUBLISH_TRAILER = "Auto-Publish: true"

/**
 * @param changeLog The full release notes: CHANGELOG.md and the GitHub release.
 * @param storeNotes The app-only text each store listing carries.
 * @param autoPublish Publish the GitHub release once every CI build job passes.
 */
data class ReleaseInfo(
	val semVar: SemVar,
	val changeLog: String,
	val storeNotes: StoreChangelogs,
	val platforms: Set<Platform>,
	val autoPublish: Boolean = false,
) {
	init {
		// Fail fast at construction so we never get halfway through prepareForRelease
		// (version bump, develop commit, develop→release merge) and then explode when
		// computing `tag`.
		require(platforms.isNotEmpty()) { "Release must target at least one platform" }
	}

	/** The git tag for this release: `vX.Y.Z` for full, `vX.Y.Z+token+token` for partial. */
	val tag: String get() = "v$semVar${tagSuffix(platforms)}"

	// The trailer is the only channel that reaches CI per-release: the tag name already
	// encodes platform scope, and a `+` suffix there would read as a partial release.
	/** The annotated-tag message: the notes, plus the auto-publish trailer when asked for. */
	val tagMessage: String get() = if (autoPublish) "$changeLog\n\n$AUTO_PUBLISH_TRAILER" else changeLog
}

/**
 * Wraps caption text in HTML so a JLabel wraps it. The explicit width is what makes
 * the label report a tall enough preferred size; without it the text clips.
 */
private fun wrapped(text: String): String {
	val escaped = text.replace("&", "&amp;").replace("<", "&lt;")
	return "<html><div style='width: ${HINT_WIDTH}px;'>$escaped</div></html>"
}

private const val HINT_WIDTH = 400

/** A dropped entry, shortened to something scannable in a one-line summary. */
private fun droppedSummary(dropped: List<String>): String {
	val shown = dropped.take(3).joinToString("  ·  ") { entry ->
		val label = entry.removePrefix("-").trim()
		if (label.length > 44) label.take(43).trimEnd() + "…" else label
	}
	val rest = dropped.size - 3
	return if (rest > 0) "$shown  ·  +$rest more" else shown
}

/** A store editor's character count against its limit, and by how much it is over. */
private fun counterText(length: Int, limit: Int): String =
	if (length > limit) "$length/$limit  ·  over by ${length - limit}" else "$length/$limit"

/** Small muted caption above a changelog editor. */
private fun tabHint(text: String): JLabel = JLabel(wrapped(text)).apply {
	font = font.deriveFont(Font.ITALIC, font.size - 1f)
	foreground = UIManager.getColor("Label.disabledForeground") ?: Color.GRAY
	border = BorderFactory.createEmptyBorder(0, 2, 2, 2)
}

/** Tab positions in the changelog tab strip. */
private const val TAB_FULL = 0
private const val TAB_STORE = 1
private const val TAB_PLAY = 2
private const val TAB_APPLE = 3

/**
 * A changelog editor that mirrors [derive] until it is hand-edited, after which it
 * keeps its own text until [resync] is called. Chaining these gives the dialog its
 * default: the full notes are the only thing anyone has to write, and a store tab
 * is touched only when that store needs something different.
 */
private class MirroredEditor(private val derive: () -> String) {
	val editor = JTextArea().apply {
		lineWrap = true
		wrapStyleWord = true
		rows = 12
	}

	var edited = false
		private set

	private var syncing = false

	val text: String get() = editor.text

	fun onChange(listener: () -> Unit) {
		editor.document.addDocumentListener(OnChangeListener {
			if (!syncing) edited = true
			listener()
		})
	}

	/** Pulls the source text in, unless this editor has been hand-edited. */
	fun sync() {
		if (!edited) write(derive())
	}

	/** Discards hand edits and follows the source again. */
	fun resync() {
		edited = false
		sync()
	}

	/** Replaces the text with a hand edit the dialog made, such as trim-to-fit. */
	fun overwrite(value: String) {
		write(value)
		edited = true
	}

	private fun write(value: String) {
		syncing = true
		editor.text = value
		editor.caretPosition = 0
		syncing = false
	}
}

class OnChangeListener(
	val onChange: (e: DocumentEvent?) -> Unit
) : DocumentListener {
	override fun insertUpdate(e: DocumentEvent?) = onChange(e)
	override fun removeUpdate(e: DocumentEvent?) = onChange(e)
	override fun changedUpdate(e: DocumentEvent?) = onChange(e)
}

fun configureRelease(currentSemVarStr: String, lastReleaseChangelog: String? = null): ReleaseInfo? {
	var result: ReleaseInfo? = null
	val curSemVar = parseSemVar(currentSemVarStr)
	val windowClosedSignal = CountDownLatch(1)
	var newSemVar = curSemVar.incrementForRelease(SemVar.ReleaseType.MINOR)
	var scope = ReleaseScope.ALL
	val selectedPlatforms: MutableSet<Platform> = mutableSetOf()

	System.setProperty("java.awt.headless", "false")
	FlatDarculaLaf.setup()

	SwingUtilities.invokeAndWait {
		val frame = JFrame()
		frame.defaultCloseOperation = JFrame.DISPOSE_ON_CLOSE
		// The fixed sections take the height they need; this leaves the changelog editor ~250px.
		frame.setSize(620, 1080)

		fun refreshTitle() {
			frame.title = "Prepare Release — $curSemVar → $newSemVar"
		}
		refreshTitle()

		// --- Section helper: titled border + inner padding, left-aligned for BoxLayout.Y_AXIS parents ---
		fun section(title: String): JPanel = JPanel().apply {
			layout = BoxLayout(this, BoxLayout.Y_AXIS)
			val tb = BorderFactory.createTitledBorder(title)
			tb.titleFont = tb.titleFont?.deriveFont(Font.BOLD)
			border = BorderFactory.createCompoundBorder(
				tb,
				BorderFactory.createEmptyBorder(8, 12, 8, 12),
			)
			alignmentX = Component.LEFT_ALIGNMENT
		}

		// --- Tag preview label + commit button declared early so refresh can close over them ---
		val warningColor: Color = UIManager.getColor("Label.disabledForeground") ?: Color.GRAY
		val normalColor: Color = UIManager.getColor("Label.foreground") ?: Color.LIGHT_GRAY
		val editedColor = Color(0xE0, 0x9B, 0x2B)
		val errorColor = Color(0xD0, 0x4A, 0x4A)
		val tagFontBig = Font(Font.MONOSPACED, Font.BOLD, 18)
		val tagFontWarn = Font(Font.MONOSPACED, Font.ITALIC, 14)

		val tagLabel = JLabel().apply {
			font = tagFontBig
			border = BorderFactory.createEmptyBorder(8, 4, 8, 4)
			alignmentX = Component.LEFT_ALIGNMENT
		}
		val commitButton = JButton("Commit Changes").apply {
			font = font.deriveFont(Font.BOLD, 14f)
		}

		// Editors, previews and tabs are declared up here so the refresh below can
		// close over them; the Changelog section further down only places them.
		val changeLog = JTextArea().apply {
			lineWrap = true
			wrapStyleWord = true
			rows = 12
		}

		// Each store editor mirrors its source until hand-edited: the shared notes
		// follow a filtered copy of the full changelog, and each store follows the
		// shared notes. Stores diverge only when someone makes them.
		val storeNotes = MirroredEditor { deriveStoreNotes(changeLog.text).notes }
		val playNotes = MirroredEditor { storeNotes.text }
		val appleNotes = MirroredEditor { storeNotes.text }

		val playPreview = JTextArea().apply {
			isEditable = false
			lineWrap = true
			wrapStyleWord = true
			rows = 6
			font = Font(Font.MONOSPACED, Font.PLAIN, 12)
		}
		fun counterLabel() = JLabel().apply {
			font = font.deriveFont(font.size - 1f)
			foreground = warningColor
		}
		val playCount = counterLabel()
		val appleCount = counterLabel()

		// What the filter took out, and whether a store still follows its source, so
		// nothing leaves a listing unnoticed.
		val storeStatus = tabHint("")
		val playStatus = tabHint("")
		val appleStatus = tabHint("")

		fun smallButton(label: String) = JButton(label).apply {
			font = font.deriveFont(font.size - 1f)
		}
		val storeResync = smallButton("Re-sync from full notes")
		val playResync = smallButton("Re-sync from store notes")
		val appleResync = smallButton("Re-sync from store notes")
		val trimButton = smallButton("Trim to fit")

		fun tabHeader(hint: String, status: JLabel, buttons: List<JButton>): JPanel = JPanel().apply {
			layout = BoxLayout(this, BoxLayout.Y_AXIS)
			add(tabHint(hint).apply { alignmentX = Component.LEFT_ALIGNMENT })
			add(status.apply { alignmentX = Component.LEFT_ALIGNMENT })
			add(
				JPanel(FlowLayout(FlowLayout.LEFT, 6, 2)).apply {
					alignmentX = Component.LEFT_ALIGNMENT
					buttons.forEach { add(it) }
				},
			)
		}

		fun editorTab(header: JComponent, center: JComponent, counter: JLabel?): JPanel =
			JPanel(BorderLayout(0, 4)).apply {
				border = BorderFactory.createEmptyBorder(8, 0, 0, 0)
				add(header, BorderLayout.NORTH)
				add(center, BorderLayout.CENTER)
				if (counter != null) {
					add(
						JPanel(FlowLayout(FlowLayout.RIGHT, 0, 4)).apply { add(counter) },
						BorderLayout.SOUTH,
					)
				}
			}

		val fullTab = editorTab(
			tabHint("Goes to CHANGELOG.md and the GitHub release. Write everything here."),
			JScrollPane(changeLog),
			null,
		)
		val storeTab = editorTab(
			tabHeader(
				"The app-only base every listing starts from. Entries tagged for the web or server are filtered out, because stores reject notes about anything but the app. Google Play and Apple follow this text on their own tabs; Flathub publishes it as it stands, on releases to every store.",
				storeStatus,
				listOf(storeResync),
			),
			JScrollPane(storeNotes.editor),
			null,
		)
		val playTab = editorTab(
			tabHeader(
				"Goes to Google Play and F-Droid, which read the same file. 500 characters, including the link to the full notes on GitHub.",
				playStatus,
				listOf(playResync, trimButton),
			),
			JSplitPane(
				JSplitPane.VERTICAL_SPLIT,
				JScrollPane(playNotes.editor),
				JScrollPane(playPreview).apply {
					border = BorderFactory.createTitledBorder("Google Play preview")
				},
			).apply {
				resizeWeight = 0.6
				border = null
			},
			playCount,
		)
		val appleTab = editorTab(
			tabHeader(
				"Goes to the iOS and Mac App Stores. 4000 characters, and no link: App Store review rejects notes pointing at the GitHub releases page.",
				appleStatus,
				listOf(appleResync),
			),
			JScrollPane(appleNotes.editor),
			appleCount,
		)

		val changelogTabs = JTabbedPane().apply {
			addTab("Full", fullTab)
			addTab("App stores", storeTab)
			addTab("Google Play", playTab)
			addTab("Apple", appleTab)
		}

		/** The platforms the current scope selection targets, empty if the selection is incomplete. */
		fun currentPlatforms(): Set<Platform> = when (scope) {
			ReleaseScope.ALL -> Platform.ALL
			ReleaseScope.TARGETED -> selectedPlatforms.toSet()
			ReleaseScope.SERVER_ONLY -> setOf(Platform.SERVER)
		}

		// Without a full selection there is no tag yet, so the notes preview falls back
		// to the bare version tag.
		fun currentTag(): String {
			val platforms = currentPlatforms()
			return if (platforms.isEmpty()) "v$newSemVar" else "v$newSemVar${tagSuffix(platforms)}"
		}

		fun followStatus(edited: Boolean) =
			if (edited) "Hand-edited, so it no longer follows the store notes."
			else "Following the store notes."

		fun refresh() {
			val platforms = currentPlatforms()
			val tag = currentTag()

			if (platforms.isNotEmpty()) {
				tagLabel.text = tag
				tagLabel.font = tagFontBig
				tagLabel.foreground = normalColor
			} else {
				tagLabel.text = "(select at least one store)"
				tagLabel.font = tagFontWarn
				tagLabel.foreground = warningColor
			}

			// A sink only matters when the release reaches it, and a store outside the
			// scope has its tab disabled so nobody writes notes that go nowhere.
			// The shared tab stays enabled for any store release because the store tabs
			// follow it, but it is only required when it is published itself.
			val needsShared = reachesFlathub(platforms)
			val needsPlay = reachesPlay(platforms)
			val needsApple = reachesApple(platforms)

			changelogTabs.setEnabledAt(TAB_STORE, reachesAnyStore(platforms))
			changelogTabs.setEnabledAt(TAB_PLAY, needsPlay)
			changelogTabs.setEnabledAt(TAB_APPLE, needsApple)
			if (!changelogTabs.isEnabledAt(changelogTabs.selectedIndex)) {
				changelogTabs.selectedIndex = TAB_FULL
			}

			val dropped = deriveStoreNotes(changeLog.text).dropped
			storeStatus.text = wrapped(
				when {
					storeNotes.edited -> "Hand-edited, so it no longer follows the full notes."
					dropped.isEmpty() -> "Nothing was removed."
					else -> "Removed ${dropped.size}:  " + droppedSummary(dropped)
				}
			)
			storeStatus.foreground = if (storeNotes.edited) editedColor else warningColor
			storeResync.isEnabled = storeNotes.edited

			playStatus.text = wrapped(followStatus(playNotes.edited))
			playStatus.foreground = if (playNotes.edited) editedColor else warningColor
			playResync.isEnabled = playNotes.edited

			appleStatus.text = wrapped(followStatus(appleNotes.edited))
			appleStatus.foreground = if (appleNotes.edited) editedColor else warningColor
			appleResync.isEnabled = appleNotes.edited

			// Over-limit blocks the commit instead of truncating quietly: the Play tab
			// exists so the 500 characters are chosen, not cut off at a bullet boundary.
			val url = releaseNotesUrl(tag)
			val playLength = storeNotesLength(playNotes.text, url)
			val playOver = playLength > PLAY_STORE_LIMIT
			playPreview.text = formatStoreNotes(playNotes.text, PLAY_STORE_LIMIT, url)
			playPreview.caretPosition = 0
			playCount.text = counterText(playLength, PLAY_STORE_LIMIT)
			playCount.foreground = if (playOver) errorColor else warningColor
			trimButton.isEnabled = playOver

			val appleLength = storeNotesLength(appleNotes.text, null)
			val appleOver = appleLength > APPLE_STORE_LIMIT
			appleCount.text = counterText(appleLength, APPLE_STORE_LIMIT)
			appleCount.foreground = if (appleOver) errorColor else warningColor

			// Empty notes would publish a "What's new" that describes nothing, which App
			// Store review rejects, after the tag has already been pushed.
			commitButton.isEnabled = platforms.isNotEmpty() &&
				changeLog.text.isNotBlank() &&
				(!needsShared || storeNotes.text.isNotBlank()) &&
				(!needsPlay || (playNotes.text.isNotBlank() && !playOver)) &&
				(!needsApple || (appleNotes.text.isNotBlank() && !appleOver))
		}

		changeLog.document.addDocumentListener(OnChangeListener { storeNotes.sync(); refresh() })
		storeNotes.onChange { playNotes.sync(); appleNotes.sync(); refresh() }
		playNotes.onChange { refresh() }
		appleNotes.onChange { refresh() }

		storeResync.addActionListener { storeNotes.resync(); refresh() }
		playResync.addActionListener { playNotes.resync(); refresh() }
		appleResync.addActionListener { appleNotes.resync(); refresh() }
		// Trim puts the fitted text in the editor instead of letting the write path cut
		// it later, so what the store gets is text someone looked at.
		trimButton.addActionListener {
			val budget = storeNotesBudget(PLAY_STORE_LIMIT, releaseNotesUrl(currentTag()))
			playNotes.overwrite(fitStoreNotes(playNotes.text, budget))
			refresh()
		}

		// --- Label-value row helper for the Version section ---
		fun labelPair(label: String, value: JComponent): JPanel = JPanel().apply {
			layout = FlowLayout(FlowLayout.LEFT, 12, 0)
			alignmentX = Component.LEFT_ALIGNMENT
			add(JLabel(label).apply { preferredSize = Dimension(70, preferredSize.height) })
			add(value)
		}

		// ============= Section: Version =============
		val versionSection = section("Version")

		versionSection.add(labelPair(
			"Current",
			JLabel(curSemVar.toString()).apply { font = Font(Font.MONOSPACED, Font.PLAIN, 14) },
		))

		val optionMajor = JRadioButton("Major")
		val optionMinor = JRadioButton("Minor").apply { isSelected = true }
		val optionPatch = JRadioButton("Patch")
		ButtonGroup().apply { add(optionMajor); add(optionMinor); add(optionPatch) }
		val typeRow = JPanel(FlowLayout(FlowLayout.LEFT, 12, 0)).apply {
			add(optionMajor); add(optionMinor); add(optionPatch)
		}
		versionSection.add(labelPair("Type", typeRow))

		val newVersionLabel = JLabel(newSemVar.toString()).apply {
			font = Font(Font.MONOSPACED, Font.BOLD, 14)
		}
		versionSection.add(labelPair("New", newVersionLabel))

		// ============= Section: Publish scope =============
		val scopeSection = section("Publish scope")

		val scopeAll = JRadioButton("All").apply { isSelected = true }
		val scopeTargeted = JRadioButton("Targeted")
		val scopeServer = JRadioButton("Server only")
		ButtonGroup().apply { add(scopeAll); add(scopeTargeted); add(scopeServer) }
		val scopeRow = JPanel(FlowLayout(FlowLayout.LEFT, 12, 0)).apply {
			alignmentX = Component.LEFT_ALIGNMENT
			add(scopeAll); add(scopeTargeted); add(scopeServer)
		}
		scopeSection.add(scopeRow)
		scopeSection.add(Box.createRigidArea(Dimension(0, 6)))

		// Per-store checkboxes — built once, enabled only in Targeted mode. 2 rows × 3
		// cols keeps longer labels (Mac App Store, iOS App Store) from crowding. The
		// server is a whole scope of its own (Server only), so it isn't a checkbox here.
		val clientStores = Platform.values().filter { it in Platform.CLIENT_STORES }
		val checkboxesByPlatform: Map<Platform, JCheckBox> = clientStores.associateWith { platform ->
			JCheckBox(platform.displayName).apply {
				isEnabled = false  // All mode is the default → checkboxes start disabled
				addActionListener {
					if (isSelected) selectedPlatforms.add(platform)
					else selectedPlatforms.remove(platform)
					refresh()
				}
			}
		}
		val checkboxGrid = JPanel(GridLayout(2, 3, 12, 4)).apply {
			alignmentX = Component.LEFT_ALIGNMENT
			clientStores.forEach { add(checkboxesByPlatform[it]) }
		}
		scopeSection.add(checkboxGrid)

		fun setMode(newScope: ReleaseScope) {
			scope = newScope
			selectedPlatforms.clear()
			checkboxesByPlatform.values.forEach { cb ->
				cb.isSelected = false
				cb.isEnabled = newScope == ReleaseScope.TARGETED
			}
			refresh()
		}
		scopeAll.addActionListener { setMode(ReleaseScope.ALL) }
		scopeTargeted.addActionListener { setMode(ReleaseScope.TARGETED) }
		scopeServer.addActionListener { setMode(ReleaseScope.SERVER_ONLY) }

		// ============= Section: Will push tag =============
		val tagSection = section("Will push tag").apply { add(tagLabel) }

		// ============= Section: After the build =============
		val autoPublishBox = JCheckBox("Auto-publish the GitHub release").apply {
			alignmentX = Component.LEFT_ALIGNMENT
		}
		val autoPublishSection = section("After the build").apply {
			add(autoPublishBox)
			add(
				tabHint("Off leaves a draft. Publishing starts the store workflow.")
					.apply { alignmentX = Component.LEFT_ALIGNMENT },
			)
		}

		// ============= Section: Changelog =============
		val changelogSection = section("Changelog")

		changelogSection.add(changelogTabs)

		// ============= Release-type change listeners (after newVersionLabel exists) =============
		// A patch carries the same notes as the release it patches, so pre-fill the
		// changelog from the last release. Only clear it again on Major/Minor if the
		// user hasn't edited the auto-filled text.
		// Hand-written store notes survive: they are the user's text, and losing them
		// to a radio button is unrecoverable. The store tab flags that they no longer
		// track the full notes and offers a re-sync.
		fun clearAutofill() {
			if (lastReleaseChangelog != null && changeLog.text == lastReleaseChangelog) {
				changeLog.text = ""
			}
		}
		optionMajor.addActionListener {
			newSemVar = curSemVar.incrementForRelease(SemVar.ReleaseType.MAJOR)
			newVersionLabel.text = newSemVar.toString()
			clearAutofill()
			refreshTitle(); refresh()
		}
		optionMinor.addActionListener {
			newSemVar = curSemVar.incrementForRelease(SemVar.ReleaseType.MINOR)
			newVersionLabel.text = newSemVar.toString()
			clearAutofill()
			refreshTitle(); refresh()
		}
		optionPatch.addActionListener {
			newSemVar = curSemVar.incrementForRelease(SemVar.ReleaseType.PATCH)
			newVersionLabel.text = newSemVar.toString()
			if (lastReleaseChangelog != null && changeLog.text.isBlank()) {
				changeLog.text = lastReleaseChangelog
			}
			refreshTitle(); refresh()
		}

		// ============= Commit handler =============
		commitButton.addActionListener {
			result = ReleaseInfo(
				semVar = newSemVar,
				changeLog = changeLog.text,
				// Mirroring fills every editor whatever the scope, so the sinks this
				// release does not reach are dropped here rather than published.
				storeNotes = StoreChangelogs(
					shared = storeNotes.text,
					play = playNotes.text,
					apple = appleNotes.text,
				).restrictedTo(currentPlatforms()),
				platforms = currentPlatforms(),
				autoPublish = autoPublishBox.isSelected,
			)
			frame.dispose()
		}

		val buttonBar = JPanel(FlowLayout(FlowLayout.RIGHT, 0, 8)).apply {
			alignmentX = Component.LEFT_ALIGNMENT
			add(commitButton)
		}

		// --- Root container: BorderLayout — fixed sections in NORTH, changelog
		// in CENTER (absorbs leftover height), button bar in SOUTH.
		val topStack = JPanel().apply {
			layout = BoxLayout(this, BoxLayout.Y_AXIS)
			add(versionSection)
			add(Box.createRigidArea(Dimension(0, 8)))
			add(scopeSection)
			add(Box.createRigidArea(Dimension(0, 8)))
			add(tagSection)
			add(Box.createRigidArea(Dimension(0, 8)))
			add(autoPublishSection)
			add(Box.createRigidArea(Dimension(0, 8)))
		}
		val root = JPanel(BorderLayout()).apply {
			border = BorderFactory.createEmptyBorder(16, 20, 16, 20)
			add(topStack, BorderLayout.NORTH)
			add(changelogSection, BorderLayout.CENTER)
			add(buttonBar, BorderLayout.SOUTH)
		}

		refresh()  // initial state

		frame.add(root)
		frame.addWindowListener(object : WindowAdapter() {
			override fun windowClosed(e: WindowEvent) {
				windowClosedSignal.countDown()
			}
		})

		frame.isVisible = true
	}
	windowClosedSignal.await()

	return result
}
