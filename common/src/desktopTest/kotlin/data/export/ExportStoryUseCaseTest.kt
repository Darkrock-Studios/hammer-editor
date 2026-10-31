package data.export

import PROJECT_1_NAME
import com.darkrockstudios.apps.hammer.base.http.projectdata.ProjectData
import com.darkrockstudios.apps.hammer.common.data.export.ExportStoryUseCase
import com.darkrockstudios.apps.hammer.common.data.export.BuiltInExportFormat
import com.darkrockstudios.apps.hammer.common.data.export.ExportFileName
import com.darkrockstudios.apps.hammer.common.data.export.ExportImage
import com.darkrockstudios.apps.hammer.common.data.export.ExportInput
import com.darkrockstudios.apps.hammer.common.data.export.ExporterSource
import com.darkrockstudios.apps.hammer.common.data.export.exportFileName
import com.darkrockstudios.apps.hammer.common.data.export.StoryExporter
import com.darkrockstudios.apps.hammer.common.data.export.StoryExporterRegistry
import com.darkrockstudios.apps.hammer.common.data.ExportOptions
import com.darkrockstudios.apps.hammer.common.data.ManuscriptContact
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsStore
import com.darkrockstudios.apps.hammer.common.data.imagesrepository.ImagesRepository
import com.darkrockstudios.apps.hammer.common.data.imagesrepository.image.ImageDef
import com.darkrockstudios.apps.hammer.common.data.projectdata.ProjectDataDatasource
import com.darkrockstudios.apps.hammer.common.data.projectdata.StoredProjectData
import com.darkrockstudios.apps.hammer.common.data.projectplugins.ProjectPluginsDatasource
import com.darkrockstudios.apps.hammer.common.data.projectplugins.ProjectPluginsRepository
import com.darkrockstudios.apps.hammer.common.dependencyinjection.createTomlSerializer
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import com.darkrockstudios.apps.hammer.common.util.DeviceLocaleResolver
import com.darkrockstudios.apps.hammer.common.util.Locale
import com.darkrockstudios.apps.hammer.common.util.StrRes
import integration.BaseIntegrationTest
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.jetbrains.compose.resources.StringResource
import org.junit.jupiter.api.BeforeEach
import okio.BufferedSink
import okio.fakefilesystem.FakeFileSystem
import org.junit.jupiter.api.Test
import utils.inMemoryGlobalSettingsStore
import utils.unzip
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ExportStoryUseCaseTest : BaseIntegrationTest() {

	private lateinit var projectDataDatasource: ProjectDataDatasource
	private lateinit var storedProjectData: StoredProjectData
	private lateinit var localeResolver: DeviceLocaleResolver
	private lateinit var strRes: StrRes
	private lateinit var projectPlugins: ProjectPluginsRepository
	private lateinit var imagesRepository: ImagesRepository
	private lateinit var globalSettingsStore: GlobalSettingsStore
	private var projectDataLoads = 0

	@BeforeEach
	override fun setup() {
		super.setup()
		configureProject(PROJECT_1_NAME)
		storedProjectData = StoredProjectData(data = ProjectData())
		projectDataLoads = 0
		projectDataDatasource = mockk {
			coEvery { load() } answers {
				projectDataLoads++
				storedProjectData
			}
		}
		localeResolver = mockk {
			every { getCurrentLocale() } returns Locale.forLanguageTag("en-US")
		}
		strRes = mockk(relaxed = true)
		projectPlugins = ProjectPluginsRepository(ProjectPluginsDatasource(FakeFileSystem(), createTomlSerializer()))
		imagesRepository = mockk {
			coEvery { ensureImagesLoaded() } returns emptyList()
			every { loadImageBytes(any()) } returns null
		}
		globalSettingsStore = inMemoryGlobalSettingsStore()
	}

	private suspend fun initRepo() {
		sceneEditorRepository.initializeSceneEditor()
	}

	private fun useCase(
		contributed: List<StoryExporter> = emptyList(),
		sources: List<ExporterSource> = emptyList(),
	) = ExportStoryUseCase(
		sceneEditorRepository = sceneEditorService,
		exporters = StoryExporterRegistry(contributed, sources),
		projectPlugins = projectPlugins,
		projectDataDatasource = projectDataDatasource,
		imagesRepository = imagesRepository,
		fileSystem = ffs,
		localeResolver = localeResolver,
		strRes = strRes,
		globalSettingsStore = globalSettingsStore,
	)

	@Test
	fun `markdown export renders project title, chapters, and group children`() = runTest {
		initRepo()

		val exportPath = useCase().execute(
			exportDir = projectPath,
			options = ExportOptions(format = BuiltInExportFormat.MARKDOWN, treatTopLevelAsChapters = true),
		)

		assertTrue(exportPath.path.endsWith(".zip"), "Should produce a .zip file, got $exportPath")
		val text = markdownExportText(exportPath)
		assertEquals(EXPECTED_PROJECT_1_MARKDOWN.trim(), text.trim())
	}

	@Test
	fun `a contributed format renders through its own exporter, with scene boundaries`() = runTest {
		initRepo()
		val loadsBefore = projectDataLoads
		val exporter = object : StoryExporter {
			override val formatId = "fake.txt"
			override val fileExtension = "txt"
			override val mimeType = "text/plain"
			override val needsProjectData = false

			override fun render(sink: BufferedSink, input: ExportInput) {
				sink.writeUtf8(input.chapters.joinToString("|") { "${it.name}:${it.scenes.size}" })
			}
		}

		val exportPath = useCase(listOf(exporter)).execute(
			exportDir = projectPath,
			options = ExportOptions(format = "fake.txt"),
		)

		assertTrue(exportPath.path.endsWith(".txt"), "Should use the exporter's extension, got $exportPath")
		val text = ffs.read(exportPath.toOkioPath()) { readUtf8() }
		assertEquals("Scene ID 1:1|Chapter ID 2:3|Scene ID 6:1|Scene ID 7:1", text)
		assertEquals(loadsBefore, projectDataLoads)
	}

	@Test
	fun `an unknown format is rejected`() = runTest {
		initRepo()

		assertFailsWith<IllegalArgumentException> {
			useCase().execute(exportDir = projectPath, options = ExportOptions(format = "missing"))
		}
	}

	@Test
	fun `a format from a plugin the project turned off is rejected`() = runTest {
		initRepo()
		val exporter = object : StoryExporter {
			override val formatId = "fake.txt"
			override val fileExtension = "txt"
			override val mimeType = "text/plain"
			override fun render(sink: BufferedSink, input: ExportInput) = Unit
		}
		val plugin = object : ExporterSource {
			override val changes = flowOf(Unit)
			override fun exporters(disabledPlugins: Set<String>): List<StoryExporter> =
				if ("fake" in disabledPlugins) emptyList() else listOf(exporter)
		}
		projectPlugins.setPluginEnabled(projectDef.id, "fake", enabled = false)

		assertFailsWith<IllegalArgumentException> {
			useCase(sources = listOf(plugin)).execute(exportDir = projectPath, options = ExportOptions(format = "fake.txt"))
		}
	}

	@Test
	fun `a dotted project name stays in the base name`() {
		assertEquals(ExportFileName("Vol. 2", "tar.gz"), exportFileName("Vol. 2", "tar.gz"))
		assertEquals("Vol. 2.tar.gz", exportFileName("Vol. 2", "tar.gz").fileName)
	}

	@Test
	fun `export includes unsaved scene edits`() = runTest {
		initRepo()
		val tempBuffer = sceneDatasource.getSceneBufferDirectory().toOkioPath() / "1.md"
		ffs.write(tempBuffer) { writeUtf8("Unsaved edit of scene id 1") }
		sceneContentRepository.initialize()

		val exportPath = useCase().execute(
			exportDir = projectPath,
			options = ExportOptions(format = BuiltInExportFormat.MARKDOWN, treatTopLevelAsChapters = true),
		)

		val text = markdownExportText(exportPath)
		assertTrue("Unsaved edit of scene id 1" in text, "Export should carry the unsaved buffer, got: $text")
		assertTrue("Content of scene id 1" !in text, "Export should not carry the stale on-disk scene, got: $text")
		assertTrue("Content of scene id 3" in text, "Scenes without a buffer still come from disk, got: $text")
	}

	/** Records the input it is given. */
	private class CapturingExporter(
		override val embedsImages: Boolean,
		override val embedsCover: Boolean = false,
		override val needsProjectData: Boolean = true,
	) : StoryExporter {
		override val formatId = "fake.capture"
		override val fileExtension = "txt"
		override val mimeType = "text/plain"
		var input: ExportInput? = null

		override fun render(sink: BufferedSink, input: ExportInput) {
			this.input = input
		}
	}

	private suspend fun writeSceneBuffer(sceneId: Int, markdown: String) {
		val buffer = sceneDatasource.getSceneBufferDirectory().toOkioPath() / "$sceneId.md"
		ffs.write(buffer) { writeUtf8(markdown) }
		sceneContentRepository.initialize()
	}

	@Test
	fun `an exporter that embeds images and the cover gets the referenced images and the cover`() = runTest {
		initRepo()
		writeSceneBuffer(1, "Text.\n\n![Cat](hammer://image/1)\n\n![Gone](hammer://image/2)\n\n![Lost](hammer://image/7)")
		storedProjectData = StoredProjectData(data = ProjectData(coverImageId = 5))
		val unreferenced = ImageDef(id = 9, name = "Unused", fileExtension = "png")
		imagesRepository = mockk {
			coEvery { ensureImagesLoaded() } returns listOf(
				ImageDef(id = 1, name = "Cat", fileExtension = "png"),
				ImageDef(id = 2, name = "Gone", fileExtension = "jpg"),
				ImageDef(id = 5, name = "Cover", fileExtension = "webp"),
				unreferenced,
			)
			every { loadImageBytes(match { it.id == 1 }) } returns byteArrayOf(1)
			every { loadImageBytes(match { it.id == 2 }) } returns null
			every { loadImageBytes(match { it.id == 5 }) } returns byteArrayOf(5)
			every { loadImageBytes(unreferenced) } returns byteArrayOf(9)
		}
		val exporter = CapturingExporter(embedsImages = true, embedsCover = true)

		useCase(listOf(exporter)).render(ExportOptions(format = exporter.formatId))

		val images: Map<Int, ExportImage> = checkNotNull(exporter.input).images
		assertEquals(setOf(1, 5), images.keys, "Only referenced images with readable files should load")
		assertEquals("Cat", images.getValue(1).name)
		assertEquals("png", images.getValue(1).fileExtension)
		assertContentEquals(byteArrayOf(1), images.getValue(1).bytes)
		assertContentEquals(byteArrayOf(5), images.getValue(5).bytes)
		verify(exactly = 0) { imagesRepository.loadImageBytes(unreferenced) }
		assertEquals(
			mapOf(1 to "Cat", 2 to "Gone", 5 to "Cover"),
			exporter.input?.imageNames,
			"Every referenced image that exists should be named, readable or not",
		)
	}

	@Test
	fun `an exporter that embeds images but not the cover never reads the cover`() = runTest {
		initRepo()
		writeSceneBuffer(1, "![image](hammer://image/1)")
		storedProjectData = StoredProjectData(data = ProjectData(coverImageId = 5))
		val cover = ImageDef(id = 5, name = "Cover", fileExtension = "png")
		imagesRepository = mockk {
			coEvery { ensureImagesLoaded() } returns listOf(
				ImageDef(id = 1, name = "Cat", fileExtension = "png"),
				cover,
			)
			every { loadImageBytes(match { it.id == 1 }) } returns byteArrayOf(1)
		}
		val exporter = CapturingExporter(embedsImages = true)

		useCase(listOf(exporter)).render(ExportOptions(format = exporter.formatId))

		assertEquals(setOf(1), checkNotNull(exporter.input).images.keys)
		assertEquals(mapOf(1 to "Cat"), exporter.input?.imageNames)
		verify(exactly = 0) { imagesRepository.loadImageBytes(cover) }
	}

	@Test
	fun `an exporter that embeds the cover without project data never learns of the cover`() = runTest {
		initRepo()
		writeSceneBuffer(1, "![image](hammer://image/1)")
		storedProjectData = StoredProjectData(data = ProjectData(coverImageId = 5))
		val cover = ImageDef(id = 5, name = "Cover", fileExtension = "png")
		imagesRepository = mockk {
			coEvery { ensureImagesLoaded() } returns listOf(
				ImageDef(id = 1, name = "Cat", fileExtension = "png"),
				cover,
			)
			every { loadImageBytes(match { it.id == 1 }) } returns byteArrayOf(1)
		}
		val exporter = CapturingExporter(embedsImages = true, embedsCover = true, needsProjectData = false)

		useCase(listOf(exporter)).render(ExportOptions(format = exporter.formatId))

		assertEquals(setOf(1), checkNotNull(exporter.input).images.keys)
		assertEquals(0, projectDataLoads)
		verify(exactly = 0) { imagesRepository.loadImageBytes(cover) }
	}

	@Test
	fun `an exporter that does not embed images gets their names but never reads their files`() = runTest {
		initRepo()
		writeSceneBuffer(1, "![image](hammer://image/1)")
		storedProjectData = StoredProjectData(data = ProjectData(coverImageId = 1))
		imagesRepository = mockk {
			coEvery { ensureImagesLoaded() } returns listOf(ImageDef(id = 1, name = "Cat", fileExtension = "png"))
		}
		val exporter = CapturingExporter(embedsImages = false)

		useCase(listOf(exporter)).render(ExportOptions(format = exporter.formatId))

		assertEquals(emptyMap(), checkNotNull(exporter.input).images)
		assertEquals(mapOf(1 to "Cat"), exporter.input?.imageNames)
		verify(exactly = 0) { imagesRepository.loadImageBytes(any()) }
	}

	@Test
	fun `a story without image references reads no images`() = runTest {
		initRepo()
		val exporter = CapturingExporter(embedsImages = true)

		useCase(listOf(exporter)).render(ExportOptions(format = exporter.formatId))

		assertEquals(emptyMap(), checkNotNull(exporter.input).images)
		assertEquals(emptyMap(), checkNotNull(exporter.input).imageNames)
		coVerify(exactly = 0) { imagesRepository.ensureImagesLoaded() }
	}

	@Test
	fun `epub export produces a valid zip file with PK magic bytes`() = runTest {
		initRepo()
		storedProjectData = StoredProjectData(data = ProjectData(authorName = "Test Author"))

		val exportPath = useCase().execute(
			exportDir = projectPath,
			options = ExportOptions(format = BuiltInExportFormat.EPUB, treatTopLevelAsChapters = true),
		)

		assertTrue(exportPath.path.endsWith(".epub"), "Should produce a .epub file, got $exportPath")
		val bytes = ffs.read(exportPath.toOkioPath()) { readByteArray() }
		assertTrue(bytes.size > 100, "EPUB output should be more than a stub, got ${bytes.size} bytes")
		// EPUB is a ZIP container; first two bytes are the local file header signature "PK".
		assertEquals('P'.code.toByte(), bytes[0])
		assertEquals('K'.code.toByte(), bytes[1])
	}

	@Test
	fun `pdf export produces a valid pdf file with the PDF header`() = runTest {
		initRepo()
		storedProjectData = StoredProjectData(data = ProjectData(authorName = "Test Author"))

		val exportPath = useCase().execute(
			exportDir = projectPath,
			options = ExportOptions(format = BuiltInExportFormat.PDF, treatTopLevelAsChapters = true),
		)

		assertTrue(exportPath.path.endsWith(".pdf"), "Should produce a .pdf file, got $exportPath")
		val bytes = ffs.read(exportPath.toOkioPath()) { readByteArray() }
		assertTrue(bytes.size > 100, "PDF output should be more than a stub, got ${bytes.size} bytes")
		// Every PDF starts with the "%PDF" magic header.
		assertEquals('%'.code.toByte(), bytes[0])
		assertEquals('P'.code.toByte(), bytes[1])
		assertEquals('D'.code.toByte(), bytes[2])
		assertEquals('F'.code.toByte(), bytes[3])
	}

	@Test
	fun `pdf export draws the images the story shows`() = runTest {
		initRepo()
		writeSceneBuffer(1, "Text.\n\n![image](hammer://image/1)")
		val png = encoded("png", width = 8, height = 4)
		imagesRepository = mockk {
			coEvery { ensureImagesLoaded() } returns listOf(ImageDef(id = 1, name = "Cat", fileExtension = "png"))
			every { loadImageBytes(match { it.id == 1 }) } returns png
		}

		val pdf = useCase().render(ExportOptions(format = BuiltInExportFormat.PDF)).readByteArray()

		verify(exactly = 1) { imagesRepository.loadImageBytes(any()) }
		assertTrue("/Subtype /Image" in pdf.toString(Charsets.ISO_8859_1), "The image should be embedded")
	}

	@Test
	fun `pdf docx and rtf exports read the cover for their cover page`() = runTest {
		initRepo()
		storedProjectData = StoredProjectData(data = ProjectData(coverImageId = 5))
		val cover = ImageDef(id = 5, name = "Cover", fileExtension = "png")
		imagesRepository = mockk {
			coEvery { ensureImagesLoaded() } returns listOf(cover)
			every { loadImageBytes(cover) } returns encoded("png")
		}

		for (format in listOf(BuiltInExportFormat.PDF, BuiltInExportFormat.DOCX, BuiltInExportFormat.RTF)) {
			useCase().render(ExportOptions(format = format))
		}

		verify(exactly = 3) { imagesRepository.loadImageBytes(cover) }
	}

	@Test
	fun `markdown export packages the cover and the images the story shows`() = runTest {
		initRepo()
		writeSceneBuffer(1, "![image](hammer://image/1)")
		storedProjectData = StoredProjectData(data = ProjectData(coverImageId = 5))
		val cat = ImageDef(id = 1, name = "Cat", fileExtension = "jpg")
		val cover = ImageDef(id = 5, name = "Cover", fileExtension = "png")
		val catBytes = encoded("jpg")
		val coverBytes = encoded("png")
		imagesRepository = mockk {
			coEvery { ensureImagesLoaded() } returns listOf(cat, cover)
			every { loadImageBytes(cat) } returns catBytes
			every { loadImageBytes(cover) } returns coverBytes
		}

		val entries = unzip(useCase().render(ExportOptions(format = BuiltInExportFormat.MARKDOWN)).readByteArray())

		assertEquals(listOf(markdownDocumentName(), "images/5~Cover.png", "images/1~Cat.jpg"), entries.keys.toList())
		assertContentEquals(coverBytes, entries["images/5~Cover.png"])
		assertContentEquals(catBytes, entries["images/1~Cat.jpg"])
	}

	@Test
	fun `docx export produces a valid zip file with PK magic bytes`() = runTest {
		initRepo()
		storedProjectData = StoredProjectData(data = ProjectData(authorName = "Test Author"))

		val exportPath = useCase().execute(
			exportDir = projectPath,
			options = ExportOptions(format = BuiltInExportFormat.DOCX, treatTopLevelAsChapters = true),
		)

		assertTrue(
			exportPath.path.endsWith(".docx"),
			"Should produce a .docx file, got $exportPath"
		)
		val bytes = ffs.read(exportPath.toOkioPath()) { readByteArray() }
		assertTrue(
			bytes.size > 100,
			"DOCX output should be more than a stub, got ${bytes.size} bytes"
		)
		// DOCX is a ZIP container; first two bytes are the local file header signature "PK".
		assertEquals('P'.code.toByte(), bytes[0])
		assertEquals('K'.code.toByte(), bytes[1])
	}

	@Test
	fun `rtf export produces a valid rtf file with the RTF header`() = runTest {
		initRepo()
		storedProjectData = StoredProjectData(data = ProjectData(authorName = "Test Author"))

		val exportPath = useCase().execute(
			exportDir = projectPath,
			options = ExportOptions(format = BuiltInExportFormat.RTF, treatTopLevelAsChapters = true),
		)

		assertTrue(exportPath.path.endsWith(".rtf"), "Should produce a .rtf file, got $exportPath")
		val text = ffs.read(exportPath.toOkioPath()) { readByteArray() }.decodeToString()
		assertTrue(text.length > 100, "RTF output should be more than a stub, got ${text.length} chars")
		// Every RTF document starts with the "{\rtf1" signature.
		assertTrue(text.startsWith("{\\rtf1"), "RTF should start with the {\\rtf1 signature")
	}

	@Test
	fun `exported document text comes from the localization system`() = runTest {
		initRepo()
		storedProjectData = StoredProjectData(data = ProjectData(authorName = "Ada"))
		strRes = object : StrRes {
			override suspend fun get(str: StringResource): String = "TOC_SENTINEL"
			override suspend fun get(str: StringResource, vararg args: Any): String =
				"BYLINE_SENTINEL ${args.joinToString()}"
		}

		val exportPath = useCase().execute(
			exportDir = projectPath,
			options = ExportOptions(format = BuiltInExportFormat.RTF, treatTopLevelAsChapters = true),
		)

		val text = ffs.read(exportPath.toOkioPath()) { readByteArray() }.decodeToString()
		assertTrue("TOC_SENTINEL" in text, "Contents title should be resolved via StrRes, got: $text")
		assertTrue(
			"BYLINE_SENTINEL Ada" in text,
			"Author byline should be resolved via StrRes with the author name, got: $text",
		)
	}

	@Test
	fun `manuscript word count and end marker come from the localization system`() = runTest {
		initRepo()
		strRes = object : StrRes {
			override suspend fun get(str: StringResource): String = "<${str.key}>"
			override suspend fun get(str: StringResource, vararg args: Any): String =
				"<${str.key}> ${args.joinToString()}"
		}
		val exporter = CapturingExporter(embedsImages = false)

		useCase(listOf(exporter)).render(ExportOptions(format = exporter.formatId))

		val strings = checkNotNull(exporter.input).strings
		assertEquals("<project_home_export_word_count> 12,500", strings.wordCount("12,500"))
		assertEquals("<project_home_export_end_marker>", strings.endMarker)
	}

	@Test
	fun `the saved manuscript contact reaches the exporter`() = runTest {
		initRepo()
		val contact = ManuscriptContact(
			legalName = "Ada King",
			address = "12 St James's Square\nLondon",
			phone = "555-0100",
			email = "ada@example.com",
		)
		globalSettingsStore.updateSettings { it.copy(manuscriptContact = contact) }
		val exporter = CapturingExporter(embedsImages = false)

		useCase(listOf(exporter)).render(ExportOptions(format = exporter.formatId))

		assertEquals(contact, checkNotNull(exporter.input).contact)
	}

	@Test
	fun `docx export still produces a valid file when treatTopLevelAsChapters is false`() =
		runTest {
			initRepo()

			val exportPath = useCase().execute(
				exportDir = projectPath,
				options = ExportOptions(
					format = BuiltInExportFormat.DOCX,
					treatTopLevelAsChapters = false
				),
			)

			val bytes = ffs.read(exportPath.toOkioPath()) { readByteArray() }
			assertTrue(bytes.size > 100, "Single-chapter DOCX should still produce a real file")
		}

	@Test
	fun `pdf export still produces a valid file when treatTopLevelAsChapters is false`() = runTest {
		initRepo()

		val exportPath = useCase().execute(
			exportDir = projectPath,
			options = ExportOptions(format = BuiltInExportFormat.PDF, treatTopLevelAsChapters = false),
		)

		val bytes = ffs.read(exportPath.toOkioPath()) { readByteArray() }
		assertTrue(bytes.size > 100, "Single-chapter PDF should still produce a real file")
	}

	@Test
	fun `epub export uses the project language when set`() = runTest {
		initRepo()
		storedProjectData = StoredProjectData(data = ProjectData(language = "fr"))

		val exportPath = useCase().execute(
			exportDir = projectPath,
			options = ExportOptions(format = BuiltInExportFormat.EPUB, treatTopLevelAsChapters = true),
		)

		val bytes = ffs.read(exportPath.toOkioPath()) { readByteArray() }
		assertTrue(
			zipEntryText(bytes, ".opf").contains(">fr<"),
			"EPUB metadata should carry the project language, not the device locale",
		)
	}

	@Test
	fun `epub export falls back to the device locale when the project language is unset`() = runTest {
		initRepo()
		storedProjectData = StoredProjectData(data = ProjectData())

		val exportPath = useCase().execute(
			exportDir = projectPath,
			options = ExportOptions(format = BuiltInExportFormat.EPUB, treatTopLevelAsChapters = true),
		)

		val bytes = ffs.read(exportPath.toOkioPath()) { readByteArray() }
		assertTrue(
			zipEntryText(bytes, ".opf").contains(">en<"),
			"EPUB metadata should fall back to the device locale's language",
		)
	}

	/** The name of the `.md` inside a Markdown export, which is named after the project as the zip is. */
	private fun markdownDocumentName(): String = exportFileName(sceneEditorService.projectDef.name, "md").fileName

	private fun markdownExportText(exportPath: HPath): String =
		zipEntryText(ffs.read(exportPath.toOkioPath()) { readByteArray() }, markdownDocumentName())

	private fun zipEntryText(bytes: ByteArray, nameSuffix: String): String =
		unzip(bytes).entries.firstOrNull { it.key.endsWith(nameSuffix) }?.value?.decodeToString()
			?: error("No zip entry ending with $nameSuffix")

	@Test
	fun `epub export still produces a valid file when treatTopLevelAsChapters is false`() = runTest {
		initRepo()

		val exportPath = useCase().execute(
			exportDir = projectPath,
			options = ExportOptions(format = BuiltInExportFormat.EPUB, treatTopLevelAsChapters = false),
		)

		val bytes = ffs.read(exportPath.toOkioPath()) { readByteArray() }
		assertTrue(bytes.size > 100, "Single-chapter EPUB should still produce a real file")
	}

	@Test
	fun `scene filter keeps only selected scenes and renumbers chapters`() = runTest {
		initRepo()

		val exportPath = useCase().execute(
			exportDir = projectPath,
			options = ExportOptions(
				format = BuiltInExportFormat.MARKDOWN,
				treatTopLevelAsChapters = true,
				sceneIds = setOf(1, 4),
			),
		)

		val text = markdownExportText(exportPath)
		assertEquals(EXPECTED_FILTERED_MARKDOWN.trim(), text.trim())
	}

	@Test
	fun `markdown chapter headings are numbered on request`() = runTest {
		initRepo()

		val exportPath = useCase().execute(
			exportDir = projectPath,
			options = ExportOptions(
				format = BuiltInExportFormat.MARKDOWN,
				treatTopLevelAsChapters = true,
				numberChapters = true,
			),
		)

		val text = markdownExportText(exportPath)
		assertTrue("## 1. Scene ID 1" in text, text)
		assertTrue("## 2. Chapter ID 2" in text, text)
	}

	@Test
	fun `scene breaks survive when blank lines are skipped`() = runTest {
		initRepo()
		storedProjectData = StoredProjectData(data = ProjectData(authorName = "Test Author"))

		val exportPath = useCase().execute(
			exportDir = projectPath,
			options = ExportOptions(
				format = BuiltInExportFormat.RTF,
				keepBlankLines = false,
				sceneIds = setOf(3, 4),
			),
		)

		val rtf = ffs.read(exportPath.toOkioPath()) { readByteArray() }.decodeToString()
		// \fi360 opens a body paragraph: the two scenes and the break between them.
		assertEquals(3, Regex("""\\fi360""").findAll(rtf).count(), rtf)
	}

	@Test
	fun `scene filter drops chapters with no selected scenes`() = runTest {
		initRepo()

		val exportPath = useCase().execute(
			exportDir = projectPath,
			options = ExportOptions(
				format = BuiltInExportFormat.MARKDOWN,
				treatTopLevelAsChapters = true,
				sceneIds = setOf(6),
			),
		)

		val text = markdownExportText(exportPath)
		val expected = """
			# Test Project 1


			## Scene ID 6

			Content of scene id 6
		""".trimIndent()
		assertEquals(expected, text.trim())
	}

	@Test
	fun `scene filter applies when top-level scenes are not chapters`() = runTest {
		initRepo()

		val exportPath = useCase().execute(
			exportDir = projectPath,
			options = ExportOptions(
				format = BuiltInExportFormat.MARKDOWN,
				treatTopLevelAsChapters = false,
				sceneIds = setOf(3, 6),
			),
		)

		val text = markdownExportText(exportPath)
		assertEquals(
			"# Test Project 1\n\nContent of scene id 3\n\n\nContent of scene id 6",
			text.trim(),
		)
	}

	@Test
	fun `scene ids missing from the tree are silently ignored`() = runTest {
		initRepo()

		val exportPath = useCase().execute(
			exportDir = projectPath,
			options = ExportOptions(
				format = BuiltInExportFormat.MARKDOWN,
				treatTopLevelAsChapters = true,
				sceneIds = setOf(1, 4, 999),
			),
		)

		val text = markdownExportText(exportPath)
		assertEquals(EXPECTED_FILTERED_MARKDOWN.trim(), text.trim())
	}

	@Test
	fun `an empty scene filter fails closed instead of exporting the whole story`() = runTest {
		initRepo()

		val exportPath = useCase().execute(
			exportDir = projectPath,
			options = ExportOptions(
				format = BuiltInExportFormat.MARKDOWN,
				treatTopLevelAsChapters = true,
				sceneIds = emptySet(),
			),
		)

		val text = markdownExportText(exportPath)
		assertEquals("# Test Project 1", text.trim())
	}

	@Test
	fun `epub export honors the scene filter`() = runTest {
		initRepo()
		storedProjectData = StoredProjectData(data = ProjectData(authorName = "Test Author"))

		val exportPath = useCase().execute(
			exportDir = projectPath,
			options = ExportOptions(
				format = BuiltInExportFormat.EPUB,
				treatTopLevelAsChapters = true,
				sceneIds = setOf(4),
			),
		)

		val bytes = ffs.read(exportPath.toOkioPath()) { readByteArray() }
		assertEquals('P'.code.toByte(), bytes[0])
		assertEquals('K'.code.toByte(), bytes[1])
		val chapterText = zipEntryText(bytes, "ch1.xhtml")
		assertTrue(
			"Content of scene id 4" in chapterText,
			"Filtered EPUB chapter should contain the selected scene, got: $chapterText",
		)
		assertTrue(
			"Content of scene id 3" !in chapterText,
			"Filtered EPUB chapter should not contain unselected scenes, got: $chapterText",
		)
	}

	companion object {
		private val EXPECTED_FILTERED_MARKDOWN = """
			# Test Project 1


			## Scene ID 1

			Content of scene id 1

			## Chapter ID 2

			Content of scene id 4
		""".trimIndent()

		private val EXPECTED_PROJECT_1_MARKDOWN = """
			# Test Project 1


			## Scene ID 1

			Content of scene id 1

			## Chapter ID 2

			Content of scene id 3

			Content of scene id 4

			Content of scene id 5

			## Scene ID 6

			Content of scene id 6

			## Scene ID 7

			Content of scene id 7
		""".trimIndent()
	}
}
