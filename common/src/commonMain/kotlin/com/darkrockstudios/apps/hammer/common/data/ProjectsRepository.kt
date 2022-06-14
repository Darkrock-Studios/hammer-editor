package com.darkrockstudios.apps.hammer.common.data

import com.darkrockstudios.apps.hammer.common.fileio.HPath
import io.github.aakira.napier.Napier

abstract class ProjectsRepository {

    abstract fun getProjectsDirectory(): HPath
    abstract fun getProjects(projectsDir: HPath = getProjectsDirectory()): List<Project>
    abstract fun createProject(projectName: String): Boolean

    fun validateSceneName(fileName: String?): Boolean {
        return if (fileName != null) {
            val wasValid = fileNameValidations.map {
                it.condition(fileName).also { isValid ->
                    if (!isValid) Napier.w { "$fileName Failed validation: ${it.name}" }
                }
            }.reduce { acc, b -> acc && b }

            Napier.i("$fileName was valid: $wasValid")

            wasValid
        } else {
            false
        }
    }

    private val fileNameValidations = listOf(
        Validator("Was Blank") { it.isNotBlank() },
        Validator("Invalid Characters") { !it.contains("""[\/\<>:"|?*]""") },
    )

    private data class Validator(
        val name: String,
        val condition: (String) -> Boolean,
    )

    companion object {
        const val PROJECTS_DIR = "HammerProjects"
    }
}