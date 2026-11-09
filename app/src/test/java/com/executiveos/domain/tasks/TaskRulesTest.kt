package com.executiveos.domain.tasks

import com.executiveos.domain.model.TaskStatus
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskRulesTest {
    @Test
    fun openTasksCanMoveBetweenWorkingStates() {
        assertTrue(TaskStatusRules.canTransition(TaskStatus.PENDING, TaskStatus.IN_PROGRESS))
        assertTrue(TaskStatusRules.canTransition(TaskStatus.BLOCKED, TaskStatus.WAITING))
        assertTrue(TaskStatusRules.canTransition(TaskStatus.IN_PROGRESS, TaskStatus.COMPLETED))
    }

    @Test
    fun finishedTasksCanOnlyBeReopenedOrArchived() {
        assertTrue(TaskStatusRules.canTransition(TaskStatus.COMPLETED, TaskStatus.PENDING))
        assertTrue(TaskStatusRules.canTransition(TaskStatus.CANCELLED, TaskStatus.ARCHIVED))
        assertFalse(TaskStatusRules.canTransition(TaskStatus.COMPLETED, TaskStatus.IN_PROGRESS))
    }

    @Test
    fun nothingReturnsToTheInboxAndSameStateIsNotATransition() {
        assertFalse(TaskStatusRules.canTransition(TaskStatus.PENDING, TaskStatus.INBOX))
        assertFalse(TaskStatusRules.canTransition(TaskStatus.PENDING, TaskStatus.PENDING))
    }

    @Test
    fun archivedTasksCanOnlyBeRestoredToPending() {
        assertTrue(TaskStatusRules.canTransition(TaskStatus.ARCHIVED, TaskStatus.PENDING))
        assertFalse(TaskStatusRules.canTransition(TaskStatus.ARCHIVED, TaskStatus.COMPLETED))
    }

    @Test
    fun aTaskCannotDependOnItself() {
        assertTrue(DependencyRules.wouldCreateCycle("a", "a", emptyMap()))
    }

    @Test
    fun detectsDirectAndIndirectCycles() {
        val graph = mapOf("b" to setOf("c"), "c" to setOf("a"))

        assertTrue(DependencyRules.wouldCreateCycle("a", "b", graph))
    }

    @Test
    fun allowsSharedDependenciesThatFormNoCycle() {
        val graph = mapOf("b" to setOf("d"), "c" to setOf("d"))

        assertFalse(DependencyRules.wouldCreateCycle("a", "b", graph))
        assertFalse(DependencyRules.wouldCreateCycle("a", "c", graph))
    }

    @Test
    fun cycleSearchTerminatesOnExistingCycles() {
        val graph = mapOf("x" to setOf("y"), "y" to setOf("x"))

        assertFalse(DependencyRules.wouldCreateCycle("a", "x", graph))
    }
}
