package com.darkrockstudios.build

// Use legacy java.text date formatting to avoid Kotlin/Gradle embedded version or Android API constraints
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.concurrent.CountDownLatch
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

fun writeSemvar(oldSemVar: String, newSemVar: SemVar, versionFile: File) {
	val versions = versionFile.readText()
	val updated = versions.replace("app = \"$oldSemVar\"", "app = \"$newSemVar\"")
	versionFile.writeText(updated)
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

fun configureRelease(currentSemVarStr: String): ReleaseInfo? {
	var result: ReleaseInfo? = null

	val curSemVar = parseSemVar(currentSemVarStr)

	val windowClosedSignal = CountDownLatch(1)
	var newSemVar = curSemVar.incrementForRelease(SemVar.ReleaseType.MINOR)
	val selectedPlatforms: MutableSet<Platform> = Platform.ALL.toMutableSet()

	System.setProperty("java.awt.headless", "false")
	SwingUtilities.invokeAndWait {
		val frame = JFrame("Prepare Release")
		frame.defaultCloseOperation = JFrame.DISPOSE_ON_CLOSE
		frame.setSize(600, 700)

		val panel = JPanel()
		val boxLayout = BoxLayout(panel, BoxLayout.Y_AXIS)
		panel.layout = boxLayout

		panel.add(
			JLabel("Current Version: $curSemVar").apply {
				setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10))
			}
		)

		panel.add(JLabel("What type of release is this?:"))

		val optionMajor = JRadioButton("Major")
		val optionMinor = JRadioButton("Minor")
		val optionPatch = JRadioButton("Patch")
		val group = ButtonGroup()
		group.add(optionMajor)
		group.add(optionMinor)
		group.add(optionPatch)

		optionMinor.isSelected = true

		val releaseOptions = JPanel()
		val radiobuttonLayout = BoxLayout(releaseOptions, BoxLayout.X_AXIS)
		releaseOptions.layout = radiobuttonLayout

		releaseOptions.add(optionMajor)
		releaseOptions.add(optionMinor)
		releaseOptions.add(optionPatch)

		panel.add(releaseOptions)

		panel.add(JLabel("New Version:"))

		val newVersionLabel = JLabel(newSemVar.toString()).apply {
			setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10))
		}
		panel.add(newVersionLabel)

		// Platform checkboxes — all on by default = today's "ship to every store" behavior.
		// Uncheck any to produce a single-store (or subset) release; the Tag preview updates live.
		panel.add(JLabel("Publish to:"))
		val platformsPanel = JPanel().apply {
			layout = BoxLayout(this, BoxLayout.Y_AXIS)
			setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10))
		}
		val tagLabel = JLabel().apply {
			setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10))
		}
		val commitButton = JButton("Commit Changes")

		fun refreshTagPreview() {
			val anySelected = selectedPlatforms.isNotEmpty()
			commitButton.isEnabled = anySelected
			tagLabel.text = if (anySelected) {
				"Tag: v$newSemVar${tagSuffix(selectedPlatforms)}"
			} else {
				"Tag: (select at least one platform)"
			}
		}

		// Two rows of three so longer labels (Mac App Store, iOS App Store) breathe.
		val platformList = Platform.values().toList()
		platformList.chunked(3).forEach { row ->
			val rowPanel = JPanel().apply { layout = BoxLayout(this, BoxLayout.X_AXIS) }
			row.forEach { platform ->
				val cb = JCheckBox(platform.displayName, true)
				cb.addActionListener {
					if (cb.isSelected) selectedPlatforms.add(platform)
					else selectedPlatforms.remove(platform)
					refreshTagPreview()
				}
				rowPanel.add(cb)
			}
			platformsPanel.add(rowPanel)
		}
		panel.add(platformsPanel)
		panel.add(tagLabel)

		optionMajor.addActionListener { _ ->
			newSemVar = curSemVar.incrementForRelease(SemVar.ReleaseType.MAJOR)
			newVersionLabel.text = newSemVar.toString()
			refreshTagPreview()
		}
		optionMinor.addActionListener { _ ->
			newSemVar = curSemVar.incrementForRelease(SemVar.ReleaseType.MINOR)
			newVersionLabel.text = newSemVar.toString()
			refreshTagPreview()
		}
		optionPatch.addActionListener { _ ->
			newSemVar = curSemVar.incrementForRelease(SemVar.ReleaseType.PATCH)
			newVersionLabel.text = newSemVar.toString()
			refreshTagPreview()
		}

		refreshTagPreview()

		val characterCount = JLabel("Characters: 0")

		panel.add(JLabel("Change Log:"))
		val changeLog = JTextArea().apply {
			setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10))
			lineWrap = true
			wrapStyleWord = true
		}

		changeLog.document.addDocumentListener(OnChangeListener { _ ->
			characterCount.text = "Characters: ${changeLog.document.length}"
		})

		panel.add(changeLog)
		panel.add(characterCount)
		panel.add(commitButton)

		commitButton.addActionListener {
			result = ReleaseInfo(
				semVar = newSemVar,
				changeLog = changeLog.text,
				platforms = selectedPlatforms.toSet(),
			)

			// Handle button click.
			frame.dispose()
		}

		frame.add(panel)
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
