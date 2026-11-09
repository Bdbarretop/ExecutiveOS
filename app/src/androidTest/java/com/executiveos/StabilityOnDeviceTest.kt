package com.executiveos

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.executiveos.core.database.AppDatabase
import com.executiveos.core.navigation.NavigationTags
import com.executiveos.core.navigation.TopLevelDestination
import com.executiveos.data.backup.BackupEngine
import com.executiveos.domain.backup.BackupCheck
import com.executiveos.domain.backup.BackupRepository
import com.executiveos.domain.backup.ExportFormat
import com.executiveos.domain.repository.TaskRepository
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Comprueba en el teléfono lo que depende del dispositivo: volumen, archivos y cifrado reales. */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class StabilityOnDeviceTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Inject lateinit var database: AppDatabase

    @Inject lateinit var taskRepository: TaskRepository

    @Inject lateinit var backupRepository: BackupRepository

    @Inject lateinit var backupEngine: BackupEngine

    @Before
    fun inject() = hiltRule.inject()

    private fun openAndMeasure(destination: TopLevelDestination, expected: String, label: String): Long {
        val start = System.nanoTime()
        composeRule.onNodeWithTag(NavigationTags.topLevel(destination)).performClick()
        composeRule.waitUntil(TIMEOUT_MILLIS) {
            composeRule.onAllNodes(hasText(expected, substring = true))
                .fetchSemanticsNodes().isNotEmpty()
        }
        val millis = (System.nanoTime() - start) / NANOS_PER_MILLI
        Log.i(PERFORMANCE_TAG, "$label=$millis")
        return millis
    }

    @Test
    fun aThousandTasksStayUsable() {
        val insertStart = System.nanoTime()
        runBlocking {
            database.withTransaction {
                repeat(TASK_COUNT) { index -> taskRepository.create("Carga ${index.toString().padStart(4, '0')}") }
            }
        }
        Log.i(PERFORMANCE_TAG, "insert_ms=${(System.nanoTime() - insertStart) / NANOS_PER_MILLI}")

        val tasksMillis = openAndMeasure(TopLevelDestination.TASKS, "Carga ", "tasks_list_ms")
        val homeMillis = openAndMeasure(TopLevelDestination.HOME, "Carga ", "home_ms")

        assertTrue("Tareas tardó $tasksMillis ms", tasksMillis < MAX_SCREEN_MILLIS)
        assertTrue("Inicio tardó $homeMillis ms", homeMillis < MAX_SCREEN_MILLIS)
    }

    /** Usa el motor para restaurar: el repositorio reprogramaría los avisos reales del teléfono. */
    @Test
    fun aProtectedBackupRoundTripsThroughARealFile() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        taskRepository.create("Informe confidencial")
        val file = File(context.cacheDir, "copia-de-prueba.eosbackup")
        val uri = Uri.fromFile(file).toString()
        try {
            val start = System.nanoTime()
            backupRepository.export(ExportFormat.PROTECTED_JSON, uri, PASSWORD)
            Log.i(PERFORMANCE_TAG, "encrypt_ms=${(System.nanoTime() - start) / NANOS_PER_MILLI}")
            assertFalse(file.readText(Charsets.ISO_8859_1).contains("Informe confidencial"))

            assertEquals(BackupCheck.NeedsPassword, backupRepository.check(uri, null))
            assertEquals(BackupCheck.WrongPasswordOrDamaged, backupRepository.check(uri, "otra contraseña"))
            val check = backupRepository.check(uri, PASSWORD) as BackupCheck.Valid
            assertEquals(1, check.summary.tasks)

            taskRepository.create("Creada después de la copia")
            backupEngine.restore(file.readBytes(), PASSWORD)

            val titles = taskRepository.observeTaskList().first().map { it.task.title }
            assertEquals(listOf("Informe confidencial"), titles)
        } finally {
            file.delete()
        }
    }

    private companion object {
        const val TASK_COUNT = 1_000
        const val TIMEOUT_MILLIS = 30_000L
        const val MAX_SCREEN_MILLIS = 5_000L
        const val NANOS_PER_MILLI = 1_000_000L
        const val PERFORMANCE_TAG = "EosPerf"
        const val PASSWORD = "una contraseña larga"
    }
}
