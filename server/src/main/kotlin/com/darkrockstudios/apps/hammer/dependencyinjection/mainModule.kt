package com.darkrockstudios.apps.hammer.dependencyinjection

import com.darkrockstudios.apps.hammer.EncryptionMode
import com.darkrockstudios.apps.hammer.SecretProviderType
import com.darkrockstudios.apps.hammer.ServerConfig
import com.darkrockstudios.apps.hammer.StorageMode
import com.darkrockstudios.apps.hammer.account.*
import com.darkrockstudios.apps.hammer.admin.AdminComponent
import com.darkrockstudios.apps.hammer.admin.ConfigRepository
import com.darkrockstudios.apps.hammer.admin.WhiteListRepository
import com.darkrockstudios.apps.hammer.base.http.createJsonSerializer
import com.darkrockstudios.apps.hammer.base.http.createTokenBase64
import com.darkrockstudios.apps.hammer.database.*
import com.darkrockstudios.apps.hammer.email.*
import com.darkrockstudios.apps.hammer.encryption.AesGcmContentEncryptor
import com.darkrockstudios.apps.hammer.encryption.AesGcmKeyProvider
import com.darkrockstudios.apps.hammer.encryption.ContentEncryptor
import com.darkrockstudios.apps.hammer.encryption.ContentEncryptorRegistry
import com.darkrockstudios.apps.hammer.encryption.ContentEncryptors
import com.darkrockstudios.apps.hammer.encryption.PlaintextContentEncryptor
import com.darkrockstudios.apps.hammer.encryption.SimpleFileBasedAesGcmKeyProvider
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
import com.darkrockstudios.apps.hammer.project.*
import com.darkrockstudios.apps.hammer.project.access.ProjectAccessRepository
import com.darkrockstudios.apps.hammer.project.synchronizers.*
import com.darkrockstudios.apps.hammer.projects.ProjectsDatabaseDatasource
import com.darkrockstudios.apps.hammer.projects.ProjectsDatasource
import com.darkrockstudios.apps.hammer.projects.ProjectsRepository
import com.darkrockstudios.apps.hammer.projects.ProjectsSynchronizationSession
import com.darkrockstudios.apps.hammer.review.ReviewRepository
import com.darkrockstudios.apps.hammer.secret.EnvSecretProvider
import com.darkrockstudios.apps.hammer.secret.FileSecretProvider
import com.darkrockstudios.apps.hammer.secret.KeyringCodec
import com.darkrockstudios.apps.hammer.secret.KeyringManager
import com.darkrockstudios.apps.hammer.secret.ServerSecretProvider
import com.darkrockstudios.apps.hammer.story.StoryExportService
import com.darkrockstudios.apps.hammer.syncsessionmanager.SyncSessionManager
import com.darkrockstudios.apps.hammer.utilities.MarkdownService
import com.darkrockstudios.apps.hammer.utilities.ServerSecretManager
import com.darkrockstudios.apps.hammer.utilities.TokenHasher
import io.ktor.util.logging.*
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import net.peanuuutz.tomlkt.Toml
import okio.FileSystem
import okio.Path.Companion.toPath
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module
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

	singleOf(::createJsonSerializer) bind Json::class
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
	singleOf(::AccountDao)
	singleOf(::AuthTokenDao)
	singleOf(::WhiteListDao)
	singleOf(::StoryEntityDao)
	singleOf(::ProjectsDao)
	singleOf(::ProjectDao)
	singleOf(::DeletedProjectDao)
	singleOf(::DeletedEntityDao)
	singleOf(::ServerConfigDao)
	singleOf(::ProjectAccessDao)
	singleOf(::PasswordResetTokenDao)
	singleOf(::ReviewRequestDao)
	singleOf(::ReviewSceneDao)
	singleOf(::ReviewSuggestionDao)
	singleOf(::WritingActivityDao)
	singleOf(::ProjectDataDao)
	singleOf(::ApiMetricDao)
	singleOf(::ErrorLogDao)
	singleOf(::LoginAttemptDao)
	singleOf(::UserActivityDao)
	singleOf(::PublishedStoryReaderDao)

	singleOf(::AccountsRepository)
	singleOf(::ProjectsRepository)
	singleOf(::ProjectEntityRepository)
	singleOf(::ProjectAccessRepository)
	singleOf(::ServerWritingActivityRepository)
	singleOf(::ServerProjectDataRepository)
	singleOf(::WhiteListRepository)
	singleOf(::ConfigRepository)
	singleOf(::MetricsRepository)
	singleOf(::ErrorRepository)
	singleOf(::SecurityRepository)
	singleOf(::MetricsCollector)
	singleOf(::UserActivityCollector)
	singleOf(::UserActivityRepository)
	singleOf(::StoryReaderCollector)
	singleOf(::StoryReaderRepository)
	single { MonitoringState() }
	singleOf(::MonitoringMaintenanceJob)
	singleOf(::StoryExportService)
	singleOf(::PenNameService)
	singleOf(::BioService)
	singleOf(::PasswordResetRepository)
	singleOf(::ReviewRepository)

	singleOf(::ServerSecretManager)
	singleOf(::MarkdownService)
	single { KeyringCodec(get(), get()) }
	single<ServerSecretProvider> {
		val secret = get<ServerConfig>().secret
		when (secret.provider) {
			SecretProviderType.FILE -> FileSecretProvider(
				get(),
				secret.file?.toPath() ?: KeyringManager.defaultKeyringPath(get()),
			)
			SecretProviderType.ENV -> EnvSecretProvider(secret.envVar)
		}
	}
	single {
		KeyringManager(get(), get(), get(), KeyringManager.legacySecretPath(get()))
	}
	singleOf(::SimpleFileBasedAesGcmKeyProvider) bind AesGcmKeyProvider::class
	singleOf(::PlaintextContentEncryptor)
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
		val encryptors = get<ContentEncryptors>()
		when (get<ServerConfig>().encryption.mode) {
			EncryptionMode.NONE -> encryptors.plaintext
			EncryptionMode.AES -> {
				val activeId = get<KeyringManager>().activeContentKeyId()
				encryptors.aesByKeyId[activeId] ?: error("No content encryptor for active key '$activeId'")
			}
		}
	}
	singleOf(::TokenHasher)

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

	factoryOf(::ProjectsDatabaseDatasource) bind ProjectsDatasource::class
	factory<ProjectEntityDatasource> {
		ProjectEntityDatabaseDatasource(get(), get(), get(), get(), get(), get(), get(), get())
	}

	singleOf(::AdminComponent)
	singleOf(::AccountsComponent)

	singleOf(::PatreonApiClient)
	singleOf(::PatreonSyncService)
	singleOf(::PatreonWebhookHandler)
	singleOf(::PatreonPollingJob)

	singleOf(::ServerSceneSynchronizer)
	singleOf(::ServerNoteSynchronizer)
	singleOf(::ServerTimelineSynchronizer)
	singleOf(::ServerEncyclopediaSynchronizer)
	singleOf(::ServerSceneDraftSynchronizer)

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