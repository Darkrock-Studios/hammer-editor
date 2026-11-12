package com.darkrockstudios.apps.hammer.projects

import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.project.InvalidSyncIdException
import com.darkrockstudios.apps.hammer.project.NotMemberException
import com.darkrockstudios.apps.hammer.project.NotOwnerException
import com.darkrockstudios.apps.hammer.project.ProjectMemberRepository
import com.darkrockstudios.apps.hammer.utilities.SResult
import com.darkrockstudios.apps.hammer.utilities.ServerResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

class DeleteProjectUseCaseTest {

	private val projectsRepository: ProjectsRepository = mockk()
	private val projectMemberRepository: ProjectMemberRepository = mockk()
	private val deleteProject = DeleteProjectUseCase(projectsRepository, projectMemberRepository)

	private val userId = 3L
	private val syncId = "sync-id"
	private val projectId = ProjectId("2b1f3c4d-5e6f-4a1b-8c2d-3e4f5a6b7c8d")

	@Test
	fun `the owner's delete is the repository's result, with no leave`() = runTest {
		val deleted = SResult.success()
		coEvery { projectsRepository.deleteProject(userId, syncId, projectId) } returns deleted

		assertSame(deleted, deleteProject(userId, syncId, projectId))
		coVerify(exactly = 0) { projectMemberRepository.leaveProject(any(), any()) }
	}

	@Test
	fun `a co-author's delete is a leave`() = runTest {
		coEvery { projectsRepository.deleteProject(userId, syncId, projectId) } returns
			SResult.failure(NotOwnerException(projectId))
		coEvery { projectMemberRepository.leaveProject(userId, projectId) } returns true

		assertTrue(deleteProject(userId, syncId, projectId).isSuccess)
	}

	@Test
	fun `a stranger's delete is not_member`() = runTest {
		coEvery { projectsRepository.deleteProject(userId, syncId, projectId) } returns
			SResult.failure(NotOwnerException(projectId))
		coEvery { projectMemberRepository.leaveProject(userId, projectId) } returns false

		val result = deleteProject(userId, syncId, projectId)

		assertIs<NotMemberException>((result as ServerResult.Failure).exception)
	}

	@Test
	fun `any other failure passes through without a leave`() = runTest {
		val refused = SResult.failure<Unit>(InvalidSyncIdException())
		coEvery { projectsRepository.deleteProject(userId, syncId, projectId) } returns refused

		assertSame(refused, deleteProject(userId, syncId, projectId))
		coVerify(exactly = 0) { projectMemberRepository.leaveProject(any(), any()) }
	}
}
