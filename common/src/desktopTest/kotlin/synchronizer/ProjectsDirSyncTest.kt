package synchronizer

import com.darkrockstudios.apps.hammer.base.GroupId
import com.darkrockstudios.apps.hammer.base.http.ApiGroupDefinition
import com.darkrockstudios.apps.hammer.base.http.HttpResponseError
import com.darkrockstudios.apps.hammer.base.http.UpsertGroupResponse
import com.darkrockstudios.apps.hammer.base.http.createJsonSerializer
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettings
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsStore
import com.darkrockstudios.apps.hammer.common.data.groupmetadata.GroupMetadataDatasource
import com.darkrockstudios.apps.hammer.common.data.projectmetadata.ProjectMetadataDatasource
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository
import com.darkrockstudios.apps.hammer.common.data.sync.accountsync.AccountSyncDatasource
import com.darkrockstudios.apps.hammer.common.dependencyinjection.createTomlSerializer
import com.darkrockstudios.apps.hammer.common.server.HttpFailureException
import com.darkrockstudios.apps.hammer.common.server.ServerProjectsApi
import com.darkrockstudios.apps.hammer.common.util.DeviceLocaleResolver
import com.darkrockstudios.apps.hammer.common.util.Locale
import createGroupDirectory
import createNestedProjectDirectory
import createRootDirectory
import getProjectsDirectory
import io.ktor.http.HttpStatusCode
import io.mockk.coEvery
import io.mockk.coJustAwait
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.SharedFlow
import okio.Path
import okio.fakefilesystem.FakeFileSystem
import org.junit.jupiter.api.BeforeEach
import testGroupId
import utils.BaseTest
import utils.RecordingStrRes
import utils.projectsDirectoryOf

/**
 * Account sync against a real [ProjectsRepository] on a fake file system, with only the server
 * mocked: the group and placement phases read the tree back after every change they make.
 */
abstract class ProjectsDirSyncTest : BaseTest() {
	protected lateinit var ffs: FakeFileSystem
	protected lateinit var globalSettingsStore: GlobalSettingsStore
	protected lateinit var repo: ProjectsRepository
	protected lateinit var serverProjectsApi: ServerProjectsApi
	protected lateinit var strRes: RecordingStrRes
	protected lateinit var accountSyncDatasource: AccountSyncDatasource
	protected val json = createJsonSerializer()
	protected val toml = createTomlSerializer()
	protected val root: Path get() = getProjectsDirectory()

	@BeforeEach
	override fun setup() {
		super.setup()
		ffs = FakeFileSystem()
		createRootDirectory(ffs)
		ffs.createDirectories(root)
		setupKoin()

		globalSettingsStore = mockk(relaxed = true)
		every { globalSettingsStore.globalSettings } returns GlobalSettings(projectsDirectory = root.toString())
		coEvery { globalSettingsStore.globalSettingsUpdates } coAnswers {
			val flow = mockk<SharedFlow<GlobalSettings>>()
			coJustAwait { flow.collect(any()) }
			flow
		}
		val locale = mockk<DeviceLocaleResolver>()
		every { locale.getCurrentLocale() } returns Locale.forLanguage("en", "US")

		repo = ProjectsRepository(
			ffs,
			globalSettingsStore,
			ProjectMetadataDatasource(ffs, toml),
			GroupMetadataDatasource(ffs, toml),
			toml,
			json,
			locale,
		)
		serverProjectsApi = mockk()
		strRes = RecordingStrRes()
		accountSyncDatasource = AccountSyncDatasource(ffs, json, projectsDirectoryOf(globalSettingsStore))
	}

	protected fun group(name: String, parent: Path = root, id: GroupId = testGroupId(name)): Path =
		createGroupDirectory(ffs, name, parent, id)

	protected fun project(name: String, parent: Path = root): ProjectDef = createNestedProjectDirectory(ffs, name, parent)

	protected fun tree() = repo.getProjectTree()

	protected fun groupNamed(name: String) = tree().groups.single { it.name == name }

	/** The server accepts every upsert under the id it was sent. */
	protected fun acceptUpserts() {
		coEvery { serverProjectsApi.upsertGroup(any(), any()) } answers {
			Result.success(UpsertGroupResponse(firstArg<ApiGroupDefinition>().uuid, alreadyExisted = false))
		}
	}

	protected fun conflict(errorCode: String) = HttpFailureException(
		statusCode = HttpStatusCode.Conflict,
		error = HttpResponseError(error = "Conflict", displayMessage = "nope", errorCode = errorCode),
	)

	protected fun serverError() = HttpFailureException(
		statusCode = HttpStatusCode.InternalServerError,
		error = HttpResponseError(error = "Error", displayMessage = "nope"),
	)
}
