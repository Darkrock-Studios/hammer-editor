import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.common.compose.LocalScreenCharacteristic
import com.darkrockstudios.apps.hammer.common.compose.ScreenCharacteristics
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.WindowHeightSizeClass
import com.darkrockstudios.apps.hammer.common.compose.WindowWidthSizeClass
import com.darkrockstudios.apps.hammer.common.compose.rememberRootSnackbarHostState
import com.darkrockstudios.apps.hammer.common.preview.KoinApplicationPreview
import com.darkrockstudios.apps.hammer.common.preview.projectselection.fakeProjectsList
import com.darkrockstudios.apps.hammer.common.projectselection.CreateGroupButtonTestTag
import com.darkrockstudios.apps.hammer.common.projectselection.CreateProjectButtonTestTag
import com.darkrockstudios.apps.hammer.common.projectselection.Masthead
import com.darkrockstudios.apps.hammer.common.projectselection.ProjectListUi
import com.darkrockstudios.apps.hammer.common.projectselection.ProjectListWideMinWidth
import com.darkrockstudios.apps.hammer.common.projectselection.ProjectsSortMode
import org.junit.Rule
import org.junit.Test
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val PANE_TAG = "test-pane"
private const val SEARCH_TOGGLE_TAG = "projects-list-search-toggle"

/** The desktop window that was reported clipping (about 808dp), beside the collapsed nav rail. */
private val ReportedPaneWidth: Dp = 808.dp - 72.dp

/** A 600dp window, Medium width, beside the expanded nav rail. */
private val ExpandedRailPaneWidth: Dp = 600.dp - 176.dp

private val ReferencePaneWidth: Dp = 1600.dp

/** Every locale the app ships strings for. */
private val ShippedLocales = listOf("en", "de", "es", "fr", "it", "pt-BR", "tr", "uk")

/** The wide masthead, which carries the create buttons, and the pane width that selects it. */
class ProjectListMastheadTest : BaseTest() {

	@get:Rule
	val compose = createComposeRule()

	private var paneWidth by mutableStateOf(ReferencePaneWidth)
	private var locale by mutableStateOf(Locale.ENGLISH)
	private var sortMode by mutableStateOf(ProjectsSortMode.LastAccessedDesc)

	private fun showMasthead() {
		compose.setContent {
			// Keyed so string resources resolve again after the default locale changes.
			key(locale) {
				KoinApplicationPreview {
					Box(modifier = Modifier.testTag(PANE_TAG).width(paneWidth)) {
						Masthead(
							entryCount = 128,
							showEntryCount = true,
							isServerSynced = true,
							onSync = {},
							onCreate = {},
							onCreateGroup = {},
							showCreate = true,
							horizontalPadding = Ui.Padding.XXL,
							sortMode = sortMode,
							onSortChange = {},
							showSort = true,
							searchActive = false,
							onToggleSearch = {},
						)
					}
				}
			}
		}
	}

	private fun bounds(tag: String): Rect = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot

	private fun assertButtonsUnclipped(width: Dp) {
		paneWidth = ReferencePaneWidth
		compose.waitForIdle()
		val fullCreate = bounds(CreateProjectButtonTestTag).width
		val fullGroup = bounds(CreateGroupButtonTestTag).width

		paneWidth = width
		compose.waitForIdle()
		val pane = bounds(PANE_TAG)
		val create = bounds(CreateProjectButtonTestTag)
		val group = bounds(CreateGroupButtonTestTag)

		assertEquals(fullCreate, create.width, 0.5f, "Create Project is squeezed at $width")
		assertEquals(fullGroup, group.width, 0.5f, "New Group is squeezed at $width")
		assertTrue(create.right <= pane.right + 0.5f, "Create Project runs past the pane at $width")
		assertTrue(group.right <= create.left, "New Group overlaps Create Project at $width")
	}

	@Test
	fun `Create buttons keep their full width at the reported window size`() {
		showMasthead()
		assertButtonsUnclipped(ReportedPaneWidth)
	}

	@Test
	fun `Create buttons keep their full width in the narrowest wide layout`() {
		showMasthead()
		assertButtonsUnclipped(ProjectListWideMinWidth)
	}

	@Test
	fun `The wide masthead fits the wide layout threshold in every locale and sort mode`() {
		val original = Locale.getDefault()
		try {
			showMasthead()
			for (tag in ShippedLocales) {
				for (mode in ProjectsSortMode.entries) {
					Locale.setDefault(Locale.forLanguageTag(tag))
					locale = Locale.forLanguageTag(tag)
					sortMode = mode
					compose.waitForIdle()

					val controls = with(compose.density) {
						(bounds(CreateProjectButtonTestTag).right - bounds(SEARCH_TOGGLE_TAG).left).toDp()
					}
					// Both side paddings, plus the gap before the shrinkable title slot.
					val required = controls + Ui.Padding.XXL * 2 + Ui.Padding.L
					assertTrue(
						required <= ProjectListWideMinWidth,
						"$tag sorted by $mode needs $required, over the $ProjectListWideMinWidth threshold",
					)
				}
			}
		} finally {
			Locale.setDefault(original)
		}
	}

	@Test
	fun `Create Project is not squeezed when the nav rail is expanded`() {
		compose.setContent {
			KoinApplicationPreview {
				CompositionLocalProvider(
					LocalScreenCharacteristic provides ScreenCharacteristics(
						isWide = false,
						windowWidthClass = WindowWidthSizeClass.Medium,
						windowHeightClass = WindowHeightSizeClass.Medium,
						needsExplicitClose = false,
					),
				) {
					Box(modifier = Modifier.testTag(PANE_TAG).width(ExpandedRailPaneWidth).height(700.dp)) {
						ProjectListUi(
							component = fakeProjectsList(emptyList(), isServerSynced = true),
							rootSnackbar = rememberRootSnackbarHostState(),
						)
					}
				}
			}
		}

		val pane = bounds(PANE_TAG)
		val create = bounds(CreateProjectButtonTestTag)
		val fullCreateWidth = with(compose.density) { 140.dp.toPx() }
		assertTrue(create.width >= fullCreateWidth - 0.5f, "Create Project is squeezed to ${create.width}px")
		assertTrue(create.right <= pane.right + 0.5f, "Create Project runs past the pane")
	}
}
