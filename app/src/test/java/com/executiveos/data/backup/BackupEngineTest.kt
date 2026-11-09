package com.executiveos.data.backup

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.executiveos.core.database.AppDatabase
import com.executiveos.core.database.DefaultDataSeedCallback
import com.executiveos.data.repository.OfflineLearningRepository
import com.executiveos.data.repository.OfflinePlanningRepository
import com.executiveos.data.repository.OfflineProjectRepository
import com.executiveos.data.repository.OfflineReviewRepository
import com.executiveos.data.repository.OfflineTaskRepository
import com.executiveos.domain.backup.BackupCheck
import com.executiveos.domain.backup.ExportFormat
import com.executiveos.domain.planning.BlockType
import com.executiveos.domain.planning.TimeBlock
import com.executiveos.domain.reviews.ReviewQuestion
import com.executiveos.domain.reviews.ReviewType
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackupEngineTest {
    private val clock = Clock.fixed(Instant.parse("2026-11-11T15:00:00Z"), ZoneOffset.UTC)
    private val today = LocalDate.of(2026, 11, 11)
    private val password = "una contraseña larga"
    private lateinit var database: AppDatabase
    private lateinit var engine: BackupEngine
    private lateinit var tasks: OfflineTaskRepository
    private lateinit var projects: OfflineProjectRepository
    private lateinit var planning: OfflinePlanningRepository
    private lateinit var reviews: OfflineReviewRepository
    private lateinit var learning: OfflineLearningRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .addCallback(DefaultDataSeedCallback(clock))
            .build()
        engine = BackupEngine(context, database, clock)
        tasks = OfflineTaskRepository(database, clock)
        projects = OfflineProjectRepository(database, clock)
        planning = OfflinePlanningRepository(database, clock)
        reviews = OfflineReviewRepository(database, clock)
        learning = OfflineLearningRepository(database, clock)
    }

    @After
    fun tearDown() = database.close()

    private suspend fun seed() {
        val project = projects.create("Implantar ERP")
        val task = tasks.create("Informe confidencial", projectId = project)
        tasks.save(tasks.observeTask(task).first()!!.copy(targetDate = today))
        planning.save(
            TimeBlock(
                id = "b1",
                type = BlockType.DEEP_WORK,
                title = "Redacción",
                taskId = task,
                startAt = Instant.parse("2026-11-11T09:00:00Z"),
                endAt = Instant.parse("2026-11-11T10:00:00Z"),
            ),
        )
        reviews.saveAnswers(ReviewType.DAILY, today, mapOf(ReviewQuestion.TOP_PRIORITIES to "Comité"))
        learning.addSession(learning.createGoal("Basilea", "Regulación"), today, 45, null, "85/100")
    }

    private suspend fun titles() = tasks.observeTaskList().first().map { it.task.title }.sorted()

    private suspend fun plainJson() = engine.create(ExportFormat.PLAIN_JSON, null).decodeToString()

    private fun snapshot(json: ByteArray) = BackupCodec.decode(json).tables.mapValues { (_, rows) -> rows.toSet() }

    @Test
    fun aProtectedBackupHidesTheContentAndRestoresEverything() = runBlocking {
        seed()
        val before = engine.create(ExportFormat.PLAIN_JSON, null)
        val backup = engine.create(ExportFormat.PROTECTED_JSON, password)
        assertFalse(backup.toString(Charsets.ISO_8859_1).contains("Informe confidencial"))

        tasks.create("Creada después")
        engine.restore(backup, password)

        assertEquals(listOf("Informe confidencial"), titles())
        assertEquals(snapshot(before), snapshot(engine.create(ExportFormat.PLAIN_JSON, null)))
    }

    @Test
    fun theCheckSummarizesTheBackupWithoutChangingAnything() = runBlocking {
        seed()
        val backup = engine.create(ExportFormat.PROTECTED_JSON, password)
        tasks.create("Creada después")

        val check = engine.check(backup, password) as BackupCheck.Valid

        assertEquals(today, check.summary.createdOn)
        assertEquals(1, check.summary.tasks)
        assertEquals(1, check.summary.projects)
        assertEquals(1, check.summary.timeBlocks)
        assertEquals(1, check.summary.reviews)
        assertEquals(1, check.summary.learningGoals)
        assertEquals(2, titles().size)
    }

    @Test
    fun aProtectedBackupAsksForItsPasswordAndRejectsAWrongOne() = runBlocking {
        val backup = engine.create(ExportFormat.PROTECTED_JSON, password)

        assertEquals(BackupCheck.NeedsPassword, engine.check(backup, null))
        assertEquals(BackupCheck.WrongPasswordOrDamaged, engine.check(backup, "otra contraseña"))
    }

    @Test
    fun aTamperedProtectedBackupIsRejected() = runBlocking {
        val backup = engine.create(ExportFormat.PROTECTED_JSON, password)
        backup[backup.size - 1] = (backup.last() + 1).toByte()

        assertEquals(BackupCheck.WrongPasswordOrDamaged, engine.check(backup, password))
    }

    @Test
    fun anEmptyDatabaseCanBeBackedUpAndRestored() = runBlocking {
        val backup = engine.create(ExportFormat.PLAIN_JSON, null)

        assertTrue(engine.check(backup, null) is BackupCheck.Valid)
        engine.restore(backup, null)

        assertTrue(titles().isEmpty())
    }

    @Test
    fun filesThatAreNotBackupsAreInvalid() = runBlocking {
        assertEquals(BackupCheck.Invalid, engine.check("no soy una copia".toByteArray(), null))
        assertEquals(BackupCheck.Invalid, engine.check(ByteArray(0), null))
        assertEquals(BackupCheck.Invalid, engine.check("{\"format\":\"otro\"}".toByteArray(), null))
        val truncated = plainJson().dropLast(40).toByteArray()
        assertEquals(BackupCheck.Invalid, engine.check(truncated, null))
    }

    @Test
    fun aBackupFromANewerVersionIsNotRestored() = runBlocking {
        val document = BackupCodec.decode(engine.create(ExportFormat.PLAIN_JSON, null))
        val newer = BackupCodec.encode(document.copy(schemaVersion = AppDatabase.SCHEMA_VERSION + 1))

        assertEquals(BackupCheck.NewerVersion, engine.check(newer, null))
    }

    @Test
    fun aBackupFromAnOlderSchemaIsRestoredAndLeavesNewTablesEmpty() = runBlocking {
        seed()
        val document = BackupCodec.decode(engine.create(ExportFormat.PLAIN_JSON, null))
        val older = BackupCodec.encode(
            document.copy(
                schemaVersion = AppDatabase.SCHEMA_VERSION - 1,
                tables = document.tables - "reviews" - "learning_sessions" - "learning_goals",
            ),
        )

        engine.restore(older, null)

        assertEquals(listOf("Informe confidencial"), titles())
        assertTrue(reviews.observeHistory().first().isEmpty())
    }

    private fun assertRejectedAndUntouched(corrupt: String) = runBlocking {
        val before = titles()
        val bytes = corrupt.toByteArray()

        assertEquals(BackupCheck.Invalid, engine.check(bytes, null))
        assertThrows(Exception::class.java) { runBlocking { engine.restore(bytes, null) } }
        assertEquals(before, titles())
    }

    @Test
    fun anUnknownStatusIsRejectedBeforeTouchingTheData() = runBlocking {
        seed()
        assertRejectedAndUntouched(plainJson().replace("\"status\":\"PENDING\"", "\"status\":\"NO_EXISTE\""))
    }

    @Test
    fun aBrokenDateIsRejected() = runBlocking {
        seed()
        assertRejectedAndUntouched(plainJson().replace("\"target_date\":\"$today\"", "\"target_date\":\"ayer\""))
    }

    @Test
    fun aValueOfTheWrongTypeIsRejected() = runBlocking {
        seed()
        assertRejectedAndUntouched(plainJson().replace("\"minutes\":45", "\"minutes\":\"cuarenta\""))
    }

    @Test
    fun aBrokenReferenceIsRejected() = runBlocking {
        seed()
        val project = projects.observeProjects().first().single().project.id
        assertRejectedAndUntouched(plainJson().replace("\"project_id\":\"$project\"", "\"project_id\":\"nadie\""))
    }

    @Test
    fun forgedTableAndColumnNamesCannotInjectSql() = runBlocking {
        seed()
        val json = plainJson()
        assertRejectedAndUntouched(json.replace("\"tables\":{", "\"tables\":{\"tasks`; DROP TABLE tasks; --\":[],"))
        assertRejectedAndUntouched(json.replace("\"title\":\"Informe confidencial\"", "\"title`; DROP TABLE tasks; --\":\"x\""))
    }

    @Test
    fun aBackupWithoutEssentialTablesIsRejected() = runBlocking {
        seed()
        val document = BackupCodec.decode(engine.create(ExportFormat.PLAIN_JSON, null))

        assertRejectedAndUntouched(
            BackupCodec.encode(document.copy(tables = document.tables - "tasks")).decodeToString(),
        )
    }

    @Test
    fun aCsvExportHasOneFilePerTable() = runBlocking {
        seed()

        val names = java.util.zip.ZipInputStream(engine.create(ExportFormat.CSV_ZIP, null).inputStream()).use { zip ->
            generateSequence { zip.nextEntry }.map { it.name }.toList()
        }

        assertTrue("tasks.csv" in names)
        assertTrue("learning_sessions.csv" in names)
    }
}
