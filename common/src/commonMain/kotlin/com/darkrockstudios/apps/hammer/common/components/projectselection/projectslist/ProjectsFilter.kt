package com.darkrockstudios.apps.hammer.common.components.projectselection.projectslist

import com.darkrockstudios.apps.hammer.common.components.projectselection.ProjectData
import com.darkrockstudios.apps.hammer.common.data.search.ParsedQuery
import com.darkrockstudios.apps.hammer.common.data.search.matchesAllTags

/**
 * Filters the projects list by a parsed query: `#tag` needles are AND-combined case-insensitive
 * substring matches against the project's tag set (same semantics as Global Search), free text is
 * a case-insensitive substring match on the project name. An empty query passes everything.
 *
 * Unlike Global Search there is no minimum query length — this is a pure in-memory filter over an
 * already-loaded list, and gating short queries would make the field feel unresponsive.
 */
fun filterProjects(projects: List<ProjectData>, parsed: ParsedQuery): List<ProjectData> =
	projects.filter { project ->
		project.storedData.tags.matchesAllTags(parsed.tags) &&
			(parsed.text.isEmpty() || project.definition.name.contains(
				parsed.text,
				ignoreCase = true
			))
	}
