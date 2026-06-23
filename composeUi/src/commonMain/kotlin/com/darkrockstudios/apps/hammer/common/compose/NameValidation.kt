package com.darkrockstudios.apps.hammer.common.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.data.isFailure
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ValidationFailedException

/**
 * What a name will be used for on disk, which decides how strictly it is validated. A [Project]
 * name becomes a filesystem directory verbatim; a [SceneItem] name is stored wrapped as
 * `order~name~id`, so leading dots and Windows reserved words are harmless for it.
 */
internal enum class NameKind(val usedAsRawFilename: Boolean) {
	Project(usedAsRawFilename = true),
	SceneItem(usedAsRawFilename = false),
}

/** Outcome of validating a name typed into a form field. */
internal class NameValidation(
	val isValid: Boolean,
	val errorMessage: String?,
) {
	/** The error to surface in a [FormField] — shown only once the field is non-empty and invalid. */
	fun fieldError(name: String): String? =
		if (name.isNotEmpty() && !isValid) errorMessage else null
}

/**
 * Validates a project or scene/group [name] and resolves its localized error message, so every
 * naming dialog shares one validation path instead of re-deriving it — and so the scene-versus-project
 * strictness lives in [NameKind] rather than being re-specified at each call site.
 */
@Composable
internal fun rememberNameValidation(name: String, kind: NameKind): NameValidation {
	val strRes = rememberStrRes()
	val result = remember(name, kind) {
		ProjectsRepository.validateFileName(name.trim().ifEmpty { null }, kind.usedAsRawFilename)
	}
	val errorMessage by produceState<String?>(null, result) {
		value = if (isFailure(result)) {
			when (val exception = result.exception) {
				is ValidationFailedException -> strRes.get(exception.errorMessage)
				else -> result.displayMessage?.text(strRes)
			}
		} else null
	}
	return NameValidation(isValid = result.isSuccess, errorMessage = errorMessage)
}
