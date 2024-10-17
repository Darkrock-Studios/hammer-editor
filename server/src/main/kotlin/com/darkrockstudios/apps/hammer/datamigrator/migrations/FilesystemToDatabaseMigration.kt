package com.darkrockstudios.apps.hammer.datamigrator.migrations

import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.base.http.createTokenBase64
import com.darkrockstudios.apps.hammer.database.AccountDao
import com.darkrockstudios.apps.hammer.database.DeletedEntityDao
import com.darkrockstudios.apps.hammer.database.DeletedProjectDao
import com.darkrockstudios.apps.hammer.database.ProjectDao
import com.darkrockstudios.apps.hammer.database.ProjectsDao
import com.darkrockstudios.apps.hammer.database.SqliteDatabase
import com.darkrockstudios.apps.hammer.database.StoryEntityDao
import com.darkrockstudios.apps.hammer.dependencyinjection.mainModule
import com.darkrockstudios.apps.hammer.encryption.AesGcmContentEncryptor
import com.darkrockstudios.apps.hammer.encryption.SimpleFileBasedAesGcmKeyProvider
import com.darkrockstudios.apps.hammer.project.ProjectEntityDatabaseDatasource
import com.darkrockstudios.apps.hammer.project.ProjectEntityFilesystemDatasource
import com.darkrockstudios.apps.hammer.projects.ProjectsDatabaseDatasource
import com.darkrockstudios.apps.hammer.projects.ProjectsFileSystemDatasource
import com.darkrockstudios.apps.hammer.projects.ProjectsSyncData
import com.darkrockstudios.apps.hammer.utilities.SecureTokenGenerator
import com.darkrockstudios.apps.hammer.utilities.isSuccess
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okio.FileSystem
import org.koin.core.context.startKoin
import org.slf4j.LoggerFactory
import java.security.SecureRandom


class FilesystemToDatabaseMigration : DataMigration {

	override suspend fun migrate() {

		val fileSystem = FileSystem.SYSTEM
		val json = Json {
			prettyPrint = true
			prettyPrintIndent = "\t"
			encodeDefaults = true
			coerceInputValues = true
		}

		val db = SqliteDatabase(fileSystem)
		db.initialize()
		//ServerDatabase.Schema.migrate(db.getDriver(), 1, 2)

		val rootFilesDir = ProjectsFileSystemDatasource.getRootDirectory(fileSystem)
		if (fileSystem.exists(rootFilesDir).not()) {
			println("No need to run FS data migration")
			return
		}

		println("Running FS data migration...")

		val koinApp = startKoin {
			modules(mainModule(LoggerFactory.getLogger(FilesystemToDatabaseMigration::class.java)))
		}

		val secureRandom = SecureRandom()
		val base64 = createTokenBase64()
		val cipherSaltGenerator = SecureTokenGenerator(16, base64)

		val projectsFsDatasource = ProjectsFileSystemDatasource(fileSystem, json)
		val projectFsDatasource = ProjectEntityFilesystemDatasource(fileSystem, json)

		val simpleAesKeyProvider =
			SimpleFileBasedAesGcmKeyProvider(fileSystem, base64, secureRandom)
		val contentEncryptor = AesGcmContentEncryptor(simpleAesKeyProvider, secureRandom)

		val accountDao = AccountDao(db)
		val projectsDao = ProjectsDao(db)
		val projectDao = ProjectDao(db, Clock.System)
		val deletedProjectDao = DeletedProjectDao(db)
		val storyEntityDao = StoryEntityDao(db)
		val deletedEntityDao = DeletedEntityDao(db)

		val projectsDbDatasource = ProjectsDatabaseDatasource(projectDao, projectsDao)
		val projectDbDatasource =
			ProjectEntityDatabaseDatasource(
				projectDao,
				accountDao,
				deletedProjectDao,
				storyEntityDao,
				deletedEntityDao,
				contentEncryptor,
				json
			)

		accountDao.getAllAccounts().forEach { account ->

			// Create a new cipher secret for the account as it didn't exist before
			val accountCipherSecret = cipherSaltGenerator.generateToken()
			db.serverDatabase.accountQueries.migration_setCipherSecret(
				id = account.id,
				cipherSecret = accountCipherSecret
			)

			projectsDbDatasource.createUserData(account.id)

			loadOldSyncData(account.id, projectsFsDatasource, fileSystem, json).let { data ->
				// This is going to lose previously deleted projects, nothing we can do
				val newData = ProjectsSyncData(data.lastSync, emptySet())
				projectsDbDatasource.saveSyncData(account.id, newData)
			}

			val projects = projectsFsDatasource.getProjects(account.id)
			projects.forEach { project ->
				val projectData = projectDbDatasource.createProject(
					userId = account.id,
					projectName = project.name
				)

				projectFsDatasource.getEntityDefs(account.id, projectData).forEach { entityDef ->
					val serializer: KSerializer<ApiProjectEntity> =
						getSerializerForType(entityDef.type)
					projectFsDatasource.loadEntity(
						account.id,
						projectData,
						entityDef.id,
						entityDef.type,
						serializer,
					).let { entityData ->
						if (isSuccess(entityData)) {
							val entity = entityData.data
							projectDbDatasource.storeEntity(
								account.id,
								projectData,
								entity,
								entityDef.type,
								serializer
							)
						} else {
							error("Failed to load entity: $entityData")
						}
					}
				}
			}
		}

		db.close()
		koinApp.close()

		fileSystem.atomicMove(rootFilesDir, rootFilesDir.parent!! / "userdata_migration_backup")

		println("FS data migration complete.")
	}

	private fun loadOldSyncData(
		userId: Long,
		projectsFsDatasource: ProjectsFileSystemDatasource,
		fileSystem: FileSystem,
		json: Json
	): OldProjectsSyncData {
		val path = projectsFsDatasource.getSyncDataPath(userId)
		return fileSystem.read(path) {
			val syncDataJson = readUtf8()
			json.decodeFromString(syncDataJson)
		}
	}
}

@Serializable
private class OldProjectsSyncData(
	val lastSync: Instant = Instant.DISTANT_PAST,
	val deletedProjects: Set<String>, // This is just ignored during migration
)

fun getSerializerForType(type: ApiProjectEntity.Type): KSerializer<ApiProjectEntity> {
	return when (type) {
		ApiProjectEntity.Type.NOTE -> ApiProjectEntity.NoteEntity.serializer() as KSerializer<ApiProjectEntity>
		ApiProjectEntity.Type.SCENE -> ApiProjectEntity.SceneEntity.serializer() as KSerializer<ApiProjectEntity>
		ApiProjectEntity.Type.TIMELINE_EVENT -> ApiProjectEntity.TimelineEventEntity.serializer() as KSerializer<ApiProjectEntity>
		ApiProjectEntity.Type.ENCYCLOPEDIA_ENTRY -> ApiProjectEntity.EncyclopediaEntryEntity.serializer() as KSerializer<ApiProjectEntity>
		ApiProjectEntity.Type.SCENE_DRAFT -> ApiProjectEntity.SceneDraftEntity.serializer() as KSerializer<ApiProjectEntity>
	}
}