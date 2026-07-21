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

fun writeChangelogMarkdown(releaseInfo: ReleaseInfo, changelogFile: File) {
	val currentChangelog = changelogFile.readText()
	val withoutHeader = currentChangelog.substring(currentChangelog.indexOf('\n') + 1)

	val headerDate = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-M-d"))
	val newEntry = "## [${releaseInfo.semVar}] - $headerDate\n\n" + releaseInfo.changeLog + "\n\n"

	val newChangeLog = "# Changelog\n\n" + newEntry + withoutHeader

	changelogFile.writeText(newChangeLog)
	println("CHANGELOG.md written")
}

data class ReleaseInfo(
	val semVar: SemVar,
	val changeLog: String,
	val platforms: Set<Platform>,
) {
	init {
		// Fail fast at construction so we never get halfway through prepareForRelease
		// (version bump, develop commit, develop→release merge) and then explode when
		// computing `tag`.
		require(platforms.isNotEmpty()) { "Release must target at least one platform" }
	}

	/** The git tag for this release: `vX.Y.Z` for full, `vX.Y.Z+token+token` for partial. */
	val tag: String get() = "v$semVar${tagSuffix(platforms)}"
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
		frame.setSize(620, 900)

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
		val truncateColor = Color(0xE0, 0x9B, 0x2B)
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

		// Editor and store preview are declared up here so the refresh below can close
		// over them; they are laid out in the Changelog section further down.
		val changeLog = JTextArea().apply {
			lineWrap = true
			wrapStyleWord = true
			rows = 12
		}
		val playPreview = JTextArea().apply {
			isEditable = false
			lineWrap = true
			wrapStyleWord = true
			rows = 6
			font = Font(Font.MONOSPACED, Font.PLAIN, 12)
		}
		val characterCount = JLabel().apply {
			font = font.deriveFont(font.size - 1f)
			foreground = warningColor
		}

		/** The platforms the current scope selection targets, empty if the selection is incomplete. */
		fun currentPlatforms(): Set<Platform> = when (scope) {
			ReleaseScope.ALL -> Platform.ALL
			ReleaseScope.TARGETED -> selectedPlatforms.toSet()
			ReleaseScope.SERVER_ONLY -> setOf(Platform.SERVER)
		}

		fun refresh() {
			val platforms = currentPlatforms()
			// Without a full selection there is no tag yet, so the notes preview falls
			// back to the bare version tag.
			val tag =
				if (platforms.isEmpty()) "v$newSemVar" else "v$newSemVar${tagSuffix(platforms)}"

			if (platforms.isNotEmpty()) {
				tagLabel.text = tag
				tagLabel.font = tagFontBig
				tagLabel.foreground = normalColor
			} else {
				tagLabel.text = "(select at least one store)"
				tagLabel.font = tagFontWarn
				tagLabel.foreground = warningColor
			}

			// Empty notes would publish a "What's new" that describes nothing, which
			// App Store review rejects — after the tag has already been pushed.
			commitButton.isEnabled = platforms.isNotEmpty() && changeLog.text.isNotBlank()

			val url = releaseNotesUrl(tag)
			val needed = storeNotesLength(changeLog.text, url)
			playPreview.text = formatStoreNotes(changeLog.text, PLAY_STORE_LIMIT, url)
			playPreview.caretPosition = 0
			characterCount.text = if (needed > PLAY_STORE_LIMIT) {
				"Characters: ${changeLog.document.length}  ·  Play: $needed/$PLAY_STORE_LIMIT — will truncate"
			} else {
				"Characters: ${changeLog.document.length}  ·  Play: $needed/$PLAY_STORE_LIMIT"
			}
			characterCount.foreground =
				if (needed > PLAY_STORE_LIMIT) truncateColor else warningColor
		}
		changeLog.document.addDocumentListener(OnChangeListener { refresh() })

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

		// ============= Section: Changelog =============
		val changelogSection = section("Changelog")
		val changeLogScroll = JScrollPane(changeLog).apply {
			alignmentX = Component.LEFT_ALIGNMENT
		}
		val counterRow = JPanel(FlowLayout(FlowLayout.RIGHT, 0, 4)).apply {
			alignmentX = Component.LEFT_ALIGNMENT
			add(characterCount)
		}
		val editorPane = JPanel(BorderLayout()).apply {
			add(changeLogScroll, BorderLayout.CENTER)
			add(counterRow, BorderLayout.SOUTH)
		}
		val previewPane = JScrollPane(playPreview).apply {
			border = BorderFactory.createTitledBorder("Google Play preview")
		}

		val splitPane = JSplitPane(JSplitPane.VERTICAL_SPLIT, editorPane, previewPane).apply {
			resizeWeight = 0.6
			border = null
			alignmentX = Component.LEFT_ALIGNMENT
		}
		changelogSection.add(splitPane)

		// ============= Release-type change listeners (after newVersionLabel exists) =============
		// A patch carries the same notes as the release it patches, so pre-fill the
		// changelog from the last release. Only clear it again on Major/Minor if the
		// user hasn't edited the auto-filled text.
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
				platforms = currentPlatforms(),
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
