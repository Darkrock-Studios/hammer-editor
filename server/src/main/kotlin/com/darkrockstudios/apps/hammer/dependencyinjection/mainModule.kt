package com.darkrockstudios.apps.hammer.dependencyinjection

import com.darkrockstudios.apps.hammer.ServerConfig
import com.darkrockstudios.apps.hammer.StorageMode
import com.darkrockstudios.apps.hammer.account.AccountsComponent
import com.darkrockstudios.apps.hammer.account.AccountsRepository
import com.darkrockstudios.apps.hammer.account.BioService
import com.darkrockstudios.apps.hammer.account.PasswordResetRepository
import com.darkrockstudios.apps.hammer.account.PenNameService
import com.darkrockstudios.apps.hammer.account.PrivacyPolicyRepository
import com.darkrockstudios.apps.hammer.account.TermsOfServiceRepository
import com.darkrockstudios.apps.hammer.account.TokenMaintenanceJob
import com.darkrockstudios.apps.hammer.admin.AdminComponent
import com.darkrockstudios.apps.hammer.admin.ConfigRepository
import com.darkrockstudios.apps.hammer.admin.WhiteListRepository
import com.darkrockstudios.apps.hammer.admin.WhitelistExpiryJob
import com.darkrockstudios.apps.hammer.base.http.createJsonSerializer
import com.darkrockstudios.apps.hammer.base.http.createTokenBase64
import com.darkrockstudios.apps.hammer.database.AccountDao
import com.darkrockstudios.apps.hammer.database.ApiMetricDao
import com.darkrockstudios.apps.hammer.database.AuthTokenDao
import com.darkrockstudios.apps.hammer.database.Database
import com.darkrockstudios.apps.hammer.database.DeletedEntityDao
import com.darkrockstudios.apps.hammer.database.DeletedIdeaDao
import com.darkrockstudios.apps.hammer.database.DeletedProjectDao
import com.darkrockstudios.apps.hammer.database.EmbeddedPostgresDatabase
import com.darkrockstudios.apps.hammer.database.ErrorLogDao
import com.darkrockstudios.apps.hammer.database.LoginAttemptDao
import com.darkrockstudios.apps.hammer.database.PasswordResetTokenDao
import com.darkrockstudios.apps.hammer.database.ProjectAccessDao
import com.darkrockstudios.apps.hammer.database.ProjectDao
import com.darkrockstudios.apps.hammer.database.ProjectDataDao
import com.darkrockstudios.apps.hammer.database.ProjectsDao
import com.darkrockstudios.apps.hammer.database.PublishedStoryReaderDao
import com.darkrockstudios.apps.hammer.database.RemotePostgresDatabase
import com.darkrockstudios.apps.hammer.database.ReviewRequestDao
import com.darkrockstudios.apps.hammer.database.ReviewSceneDao
import com.darkrockstudios.apps.hammer.database.ReviewSuggestionDao
import com.darkrockstudios.apps.hammer.database.ServerConfigDao
import com.darkrockstudios.apps.hammer.database.StoryEntityDao
import com.darkrockstudios.apps.hammer.database.StoryIdeaDao
import com.darkrockstudios.apps.hammer.database.UserActivityDao
import com.darkrockstudios.apps.hammer.database.WhiteListDao
import com.darkrockstudios.apps.hammer.database.WritingActivityDao
import com.darkrockstudios.apps.hammer.email.EmailProvider
import com.darkrockstudios.apps.hammer.email.EmailService
import com.darkrockstudios.apps.hammer.email.MailgunEmailService
import com.darkrockstudios.apps.hammer.email.PostmarkEmailService
import com.darkrockstudios.apps.hammer.email.SendGridEmailService
import com.darkrockstudios.apps.hammer.email.SmtpEmailService
import com.darkrockstudios.apps.hammer.encryption.AesGcmContentEncryptor
import com.darkrockstudios.apps.hammer.encryption.AesGcmKeyProvider
import com.darkrockstudios.apps.hammer.encryption.ContentEncryptor
import com.darkrockstudios.apps.hammer.encryption.ContentEncryptorRegistry
import com.darkrockstudios.apps.hammer.encryption.ContentEncryptors
import com.darkrockstudios.apps.hammer.encryption.EncryptionBootstrap
import com.darkrockstudios.apps.hammer.encryption.EncryptionConvergence
import com.darkrockstudios.apps.hammer.encryption.PlaintextContentEncryptor
import com.darkrockstudios.apps.hammer.encryption.SimpleFileBasedAesGcmKeyProvider
import com.darkrockstudios.apps.hammer.frontend.og.OgImageRenderer
import com.darkrockstudios.apps.hammer.frontend.og.OgImageService
import com.darkrockstudios.apps.hammer.monitoring.ErrorRepository
import com.darkrockstudios.apps.hammer.monitoring.MetricsCollector
import com.darkrockstudios.apps.hammer.monitoring.MetricsRepository
import com.darkrockstudios.apps.hammer.monitoring.MonitoringMaintenanceJob
import com.darkrockstudios.apps.hammer.monitoring.MonitoringState
import com.darkrockstudios.apps.hammer.monitoring.SecurityRepository
import com.darkrockstudios.apps.hammer.monitoring.StoryReaderCollector
import com.darkrockstudios.apps.hammer.monitoring.StoryReaderRepository
import com.darkrockstudios.apps.hammer.monitoring.UserActivityCollector
import com.darkrockstudios.apps.hammer.monitoring.UserActivityRepository
import com.darkrockstudios.apps.hammer.patreon.PatreonApiClient
import com.darkrockstudios.apps.hammer.patreon.PatreonPollingJob
import com.darkrockstudios.apps.hammer.patreon.PatreonSyncService
import com.darkrockstudios.apps.hammer.patreon.PatreonWebhookHandler
import com.darkrockstudios.apps.hammer.project.ProjectEntityDatabaseDatasource
import com.darkrockstudios.apps.hammer.project.ProjectEntityDatasource
import com.darkrockstudios.apps.hammer.project.ProjectEntityRepository
import com.darkrockstudios.apps.hammer.project.ProjectSyncKey
import com.darkrockstudios.apps.hammer.project.ProjectSynchronizationSession
import com.darkrockstudios.apps.hammer.project.ServerProjectDataRepository
import com.darkrockstudios.apps.hammer.project.ServerWritingActivityRepository
import com.darkrockstudios.apps.hammer.project.access.ProjectAccessRepository
import com.darkrockstudios.apps.hammer.project.synchronizers.ServerEncyclopediaSynchronizer
import com.darkrockstudios.apps.hammer.project.synchronizers.ServerNoteSynchronizer
import com.darkrockstudios.apps.hammer.project.synchronizers.ServerSceneDraftSynchronizer
import com.darkrockstudios.apps.hammer.project.synchronizers.ServerSceneSynchronizer
import com.darkrockstudios.apps.hammer.project.synchronizers.ServerTimelineSynchronizer
import com.darkrockstudios.apps.hammer.projects.ProjectsDatabaseDatasource
import com.darkrockstudios.apps.hammer.projects.ProjectsDatasource
import com.darkrockstudios.apps.hammer.projects.ProjectsRepository
import com.darkrockstudios.apps.hammer.projects.ProjectsSynchronizationSession
import com.darkrockstudios.apps.hammer.review.ReviewRepository
import com.darkrockstudios.apps.hammer.scheduling.RecurringTaskRegistry
import com.darkrockstudios.apps.hammer.secret.KeyringCodec
import com.darkrockstudios.apps.hammer.secret.KeyringManager
import com.darkrockstudios.apps.hammer.secret.ServerSecretProvider
import com.darkrockstudios.apps.hammer.secret.buildSecretProvider
import com.darkrockstudios.apps.hammer.story.StoryExportService
import com.darkrockstudios.apps.hammer.story.StoryRenderCache
import com.darkrockstudios.apps.hammer.storyideas.ServerIdeasRepository
import com.darkrockstudios.apps.hammer.syncsessionmanager.SyncSessionManager
import com.darkrockstudios.apps.hammer.utilities.DiskCachePruneJob
import com.darkrockstudios.apps.hammer.utilities.MarkdownService
import com.darkrockstudios.apps.hammer.utilities.ServerSecretManager
import com.darkrockstudios.apps.hammer.utilities.SystemTouchableFileSystem
import com.darkrockstudios.apps.hammer.utilities.TokenHasher
import com.darkrockstudios.apps.hammer.utilities.TouchableFileSystem
import com.darkrockstudios.apps.hammer.utilities.cacheDirectory
import io.ktor.util.logging.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import net.peanuuutz.tomlkt.Toml
import okio.FileSystem
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module
import org.koin.plugin.module.dsl.factory
import org.koin.plugin.module.dsl.single
import java.security.SecureRandom
import kotlin.coroutines.CoroutineContext
import kotlin.io.encoding.Base64
import kotlin.time.Clock

const val DISPATCHER_MAIN = "main-dispatcher"
const val DISPATCHER_DEFAULT = "default-dispatcher"
const val DISPATCHER_IO = "io-dispatcher"

fun mainModule(
	logger: Logger,
) = module {
	single<CoroutineContext>(named(DISPATCHER_MAIN)) { Dispatchers.Unconfined }
	single<CoroutineContext>(named(DISPATCHER_DEFAULT)) { Dispatchers.Default }
	single<CoroutineContext>(named(DISPATCHER_IO)) { Dispatchers.IO }

	single { logger }
	single { com.darkrockstudios.apps.hammer.plugins.LoginRateLimitConfig() }

	single { createJsonSerializer() } bind Json::class
	single { Toml { ignoreUnknownKeys = true } } bind Toml::class
	single { Clock.System } bind Clock::class
	single { createTokenBase64() } bind Base64::class
	single { SecureRandom.getInstanceStrong() } bind SecureRandom::class
	single { FileSystem.SYSTEM } bind FileSystem::class
	single<Database> {
		val cfg = get<ServerConfig>()
		when (cfg.storage.type) {
			StorageMode.EMBEDDED -> EmbeddedPostgresDatabase(cfg.storage.embedded, get())
			StorageMode.REMOTE -> RemotePostgresDatabase(
				cfg.storage.remote
					?: error("storage.type=remote requires storage.remote config block")
			)
		}
	}
	single<AccountDao>()
	single<AuthTokenDao>()
	single<WhiteListDao>()
	single<StoryEntityDao>()
	single<ProjectsDao>()
	single<ProjectDao>()
	single<DeletedProjectDao>()
	single<DeletedEntityDao>()
	single<ServerConfigDao>()
	single<ProjectAccessDao>()
	single<PasswordResetTokenDao>()
	single<ReviewRequestDao>()
	single<ReviewSceneDao>()
	single<ReviewSuggestionDao>()
	single<WritingActivityDao>()
	single<ProjectDataDao>()
	single<StoryIdeaDao>()
	single<DeletedIdeaDao>()
	single<ApiMetricDao>()
	single<ErrorLogDao>()
	single<LoginAttemptDao>()
	single<UserActivityDao>()
	single<PublishedStoryReaderDao>()

	single<AccountsRepository>()
	single<TermsOfServiceRepository>()
	single<PrivacyPolicyRepository>()
	single<ProjectsRepository>()
	single<ProjectEntityRepository>()
	single<ProjectAccessRepository>()
	single<ServerWritingActivityRepository>()
	single<ServerProjectDataRepository>()
	single { ServerIdeasRepository(get(), get(), get(), get(), get(), get(), get()) }
	single<WhiteListRepository>()
	single<ConfigRepository>()
	single<MetricsRepository>()
	single<ErrorRepository>()
	single<SecurityRepository>()
	single<MetricsCollector>()
	single<UserActivityCollector>()
	single<UserActivityRepository>()
	// Explicit ctor: maxPendingKeys has a default, but a constructor reference would
	// make Koin try to inject the Int rather than honor it.
	single { StoryReaderCollector(clock = get()) }
	single<StoryReaderRepository>()
	single { MonitoringState() }
	single { RecurringTaskRegistry() }
	single<MonitoringMaintenanceJob>()
	single<TokenMaintenanceJob>()
	single<WhitelistExpiryJob>()
	single<OgImageRenderer>()
	single { OgImageService(get(), get(), cacheDirectory(get(), "og")) }
	single { StoryRenderCache(get(), cacheDirectory(get(), "story-html")) }
	single<TouchableFileSystem> { SystemTouchableFileSystem() }
	single { DiskCachePruneJob(listOf(get<OgImageService>(), get<StoryRenderCache>()), get()) }
	// Explicit ctor: renderCache defaults to null, which the constructor DSL would honor over injection.
	single { StoryExportService(get(), get(), get()) }
	single<PenNameService>()
	single<BioService>()
	single<PasswordResetRepository>()
	single<ReviewRepository>()

	single<ServerSecretManager>()
	single<MarkdownService>()
	single { KeyringCodec(get(), get()) }
	single<ServerSecretProvider> { buildSecretProvider(get<ServerConfig>().secret, get()) }
	single {
		KeyringManager(get(), get(), get(), KeyringManager.legacySecretPath())
	}
	single<SimpleFileBasedAesGcmKeyProvider>() bind AesGcmKeyProvider::class
	single<PlaintextContentEncryptor>()
	single {
		val keyProvider = get<AesGcmKeyProvider>()
		val random = get<SecureRandom>()
		val aes = get<KeyringManager>().keyringOrNull()?.content?.keys
			?.mapValues { (keyId, contentKey) -> AesGcmContentEncryptor(contentKey, keyId, keyProvider, random) }
			?: emptyMap()
		ContentEncryptors(get<PlaintextContentEncryptor>(), aes)
	}
	single { ContentEncryptorRegistry(get<ContentEncryptors>().all()) }
	single<ContentEncryptor> {
		get<ContentEncryptors>().active(get<ServerConfig>().encryption.effectiveWriteMode(), get())
	}
	single { EncryptionConvergence(get(), get(), get()) }
	single { EncryptionBootstrap(get(), get(), get(), get(), get(), get()) }
	single<TokenHasher>()

	single<EmailService> {
		val serverConfig = get<ServerConfig>()
		when (serverConfig.emailProviderType) {
			EmailProvider.SENDGRID -> SendGridEmailService(get())
			EmailProvider.POSTMARK -> PostmarkEmailService(get())
			EmailProvider.MAILGUN -> MailgunEmailService(get())
			EmailProvider.SMTP -> SmtpEmailService(get())
			null -> SmtpEmailService(get())
		}
	}

	factory<ProjectsDatabaseDatasource>() bind ProjectsDatasource::class
	factory<ProjectEntityDatasource> {
		ProjectEntityDatabaseDatasource(get(), get(), get(), get(), get(), get(), get(), get())
	}

	single<AdminComponent>()
	single<AccountsComponent>()

	single<PatreonApiClient>()
	single<PatreonSyncService>()
	single<PatreonWebhookHandler>()
	single<PatreonPollingJob>()

	single<ServerSceneSynchronizer>()
	single<ServerNoteSynchronizer>()
	single<ServerTimelineSynchronizer>()
	single<ServerEncyclopediaSynchronizer>()
	single<ServerSceneDraftSynchronizer>()

	single<SyncSessionManager<Long, ProjectsSynchronizationSession>>(named(PROJECTS_SYNC_MANAGER)) {
		SyncSessionManager(get(), get())
	}

	single<SyncSessionManager<ProjectSyncKey, ProjectSynchronizationSession>>(
		named(
			PROJECT_SYNC_MANAGER
		)
	) {
		SyncSessionManager(get(), get())
	}
}

const val PROJECTS_SYNC_MANAGER = "projects_sync_manager"
const val PROJECT_SYNC_MANAGER = "project_sync_manager"