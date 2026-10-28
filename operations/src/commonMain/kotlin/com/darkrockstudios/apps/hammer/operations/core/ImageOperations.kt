package com.darkrockstudios.apps.hammer.operations.core

import com.darkrockstudios.apps.hammer.base.http.EntityType
import com.darkrockstudios.apps.hammer.base.image.ImageFormat
import com.darkrockstudios.apps.hammer.common.data.imagesrepository.ImageAddOutcome
import com.darkrockstudios.apps.hammer.common.data.imagesrepository.ImageAddResult
import com.darkrockstudios.apps.hammer.common.data.imagesrepository.ImagesService
import com.darkrockstudios.apps.hammer.common.data.imagesrepository.image.ImageDef
import com.darkrockstudios.apps.hammer.common.data.references.ImageReferrer
import com.darkrockstudios.apps.hammer.operations.Access
import com.darkrockstudios.apps.hammer.operations.Base64Bytes
import com.darkrockstudios.apps.hammer.operations.OpenProject
import com.darkrockstudios.apps.hammer.operations.Operation
import com.darkrockstudios.apps.hammer.operations.OperationScope
import com.darkrockstudios.apps.hammer.operations.invalidInput
import com.darkrockstudios.apps.hammer.operations.notFound
import com.darkrockstudios.apps.hammer.operations.operation
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

internal fun imageOperations(): List<Operation<*, *>> = listOf(
	operation<ProjectInput, Images>(
		name = "image.list",
		description = "A project's images, with whether anything in the project uses each one.",
		access = Access.Read,
		scope = OperationScope.Content,
	) { input ->
		projects.withProject(input.project) { project ->
			val images = project.scope.get<ImagesService>()
			val used = images.findUsedImageIds()
			Images(
				images.ensureImagesLoaded()
					.sortedBy { it.id }
					.map { ImageSummary(it.id, it.name, it.fileExtension, inUse = it.id in used) }
			)
		}
	},
	operation<ProjectItemInput, ImageContent>(
		name = "image.get",
		description = "One image's name, extension, and bytes.",
		access = Access.Read,
		scope = OperationScope.Content,
	) { input ->
		projects.withProject(input.project) { project ->
			val def = project.requireImage(input.id)
			val bytes = project.scope.get<ImagesService>().loadImageBytes(def) ?: error("Could not read image ${def.id}")
			ImageContent(def.id, def.name, def.fileExtension, bytes)
		}
	},
	operation<ImageAddInput, ImageSummary>(
		name = "image.add",
		description = "Add an image to a project. $FORMAT_RULES",
		access = Access.Write,
		scope = OperationScope.Content,
	) { input ->
		projects.withProject(input.project) { project ->
			val outcome = project.scope.get<ImagesService>().add(input.name, input.extension, input.content)
			ImageSummary(outcome.requireImage(), inUse = false)
		}
	},
	operation<ImageReplaceInput, ImageSummary>(
		name = "image.replace",
		description = "Replace an image's bytes, keeping its id, so everything that uses it shows the new image. " +
			FORMAT_RULES,
		access = Access.Write,
		scope = OperationScope.Content,
	) { input ->
		projects.withProject(input.project) { project ->
			project.requireImage(input.id)
			val images = project.scope.get<ImagesService>()
			val replaced = images.replace(input.id, input.extension, input.content).requireImage()
			ImageSummary(replaced, inUse = images.isUsed(input.id))
		}
	},
	operation<ImageRenameInput, ImageSummary>(
		name = "image.rename",
		description = "Rename an image.",
		access = Access.Write,
		scope = OperationScope.Content,
	) { input ->
		projects.withProject(input.project) { project ->
			project.requireImage(input.id)
			val images = project.scope.get<ImagesService>()
			val renamed = images.rename(input.id, input.name).requireImage()
			ImageSummary(renamed, inUse = images.isUsed(input.id))
		}
	},
	operation<ProjectItemInput, ImageDeleted>(
		name = "image.delete",
		description = "Delete an image for good, first taking it out of every entry and text that uses it, and the project cover.",
		access = Access.Destructive,
		scope = OperationScope.Content,
	) { input ->
		projects.withProject(input.project) { project ->
			val def = project.requireImage(input.id)
			val result = project.scope.get<ImagesService>().delete(def.id)
			if (!result.deleted) error("Could not delete image ${def.id}")
			ImageDeleted(
				id = def.id,
				name = def.name,
				extension = def.fileExtension,
				removedFrom = result.removedFrom
					.map(ImageUser::of)
					.sortedWith(compareBy({ it.kind.ordinal }, { it.id })),
			)
		}
	},
)

private const val FORMAT_RULES = "The extension is jpg, jpeg, png, webp, gif, or svg, and must match the image."

internal suspend fun OpenProject.requireImage(id: Int): ImageDef {
	val images = scope.get<ImagesService>()
	images.ensureImagesLoaded()
	return images.findImage(id) ?: notFound("No image $id")
}

/** The image an add, replace or rename produced; anything else is reported as the caller's mistake. */
internal fun ImageAddOutcome.requireImage(): ImageDef {
	when (result) {
		ImageAddResult.Ok -> Unit
		ImageAddResult.TooLarge -> invalidInput("Images can be at most ${ImageFormat.MAX_IMAGE_MB} MB")
		ImageAddResult.UnsupportedType -> {
			val formats = ImageFormat.ALLOWED_EXTENSIONS.joinToString()
			invalidInput("Images must be one of $formats, and match their extension")
		}

		ImageAddResult.NameInvalid -> invalidInput("That name cannot name an image")
		ImageAddResult.ReadFailed -> error("Could not write the image")
	}
	return image ?: error("Could not write the image")
}

@Serializable
data class Images(val images: List<ImageSummary>)

@Serializable
data class ImageSummary(
	val id: Int,
	val name: String,
	/** One of [ImageFormat.ALLOWED_EXTENSIONS]. */
	val extension: String,
	/** Whether an entry or a text in the project uses the image. */
	val inUse: Boolean,
) {
	constructor(def: ImageDef, inUse: Boolean) : this(def.id, def.name, def.fileExtension, inUse)
}

@Serializable
class ImageContent(
	val id: Int,
	val name: String,
	val extension: String,
	@Serializable(with = Base64Bytes::class)
	val content: ByteArray,
)

@Serializable
class ImageAddInput(
	val project: String,
	val name: String,
	/** One of [ImageFormat.ALLOWED_EXTENSIONS]. */
	val extension: String,
	@Serializable(with = Base64Bytes::class)
	val content: ByteArray,
)

@Serializable
class ImageReplaceInput(
	val project: String,
	val id: Int,
	/** One of [ImageFormat.ALLOWED_EXTENSIONS]; it may differ from the image's current one. */
	val extension: String,
	@Serializable(with = Base64Bytes::class)
	val content: ByteArray,
)

@Serializable
data class ImageRenameInput(val project: String, val id: Int, val name: String)

@Serializable
data class ImageDeleted(
	val id: Int,
	val name: String,
	val extension: String,
	/** Everything the image was taken out of before it was deleted. */
	val removedFrom: List<ImageUser>,
)

@Serializable
data class ImageUser(
	val kind: ImageUserKind,
	/** Null for [ImageUserKind.Cover], which is not an entity. */
	val id: Int? = null,
) {
	companion object {
		fun of(referrer: ImageReferrer): ImageUser = when (referrer) {
			is ImageReferrer.Entity -> ImageUser(ImageUserKind.of(referrer.entity.type), referrer.entity.id)
			ImageReferrer.ProjectCover -> ImageUser(ImageUserKind.Cover)
		}
	}
}

@Serializable
enum class ImageUserKind {
	@SerialName("scene") Scene,
	@SerialName("note") Note,
	@SerialName("timeline") Timeline,
	@SerialName("entry") Entry,
	@SerialName("draft") Draft,
	@SerialName("image") Image,
	@SerialName("cover") Cover;

	companion object {
		fun of(type: EntityType): ImageUserKind = when (type) {
			EntityType.Scene -> Scene
			EntityType.Note -> Note
			EntityType.TimelineEvent -> Timeline
			EntityType.EncyclopediaEntry -> Entry
			EntityType.SceneDraft -> Draft
			EntityType.Image -> Image
		}
	}
}
