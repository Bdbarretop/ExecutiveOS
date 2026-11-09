package com.executiveos.domain.tasks

import com.executiveos.domain.model.TaskStatus

object TaskStatusRules {
    private val FINISHED = setOf(TaskStatus.COMPLETED, TaskStatus.CANCELLED)

    /** INBOX solo se abandona desde la bandeja; lo terminado únicamente se reabre o archiva. */
    fun canTransition(from: TaskStatus, to: TaskStatus): Boolean = when {
        from == to || to == TaskStatus.INBOX -> false
        from in FINISHED -> to == TaskStatus.PENDING || to == TaskStatus.ARCHIVED
        from == TaskStatus.ARCHIVED -> to == TaskStatus.PENDING
        else -> true
    }
}

object DependencyRules {
    /** Añadir «tarea depende de otra» crea un ciclo si desde la otra se llega a la tarea. */
    fun wouldCreateCycle(
        taskId: String,
        dependsOnId: String,
        dependencies: Map<String, Set<String>>,
    ): Boolean {
        if (taskId == dependsOnId) return true
        val visited = mutableSetOf<String>()
        val pending = ArrayDeque(listOf(dependsOnId))
        while (pending.isNotEmpty()) {
            val current = pending.removeLast()
            if (current == taskId) return true
            if (visited.add(current)) pending.addAll(dependencies[current].orEmpty())
        }
        return false
    }
}
