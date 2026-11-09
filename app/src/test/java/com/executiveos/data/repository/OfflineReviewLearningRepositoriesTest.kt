package com.executiveos.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.executiveos.core.database.AppDatabase
import com.executiveos.core.database.DefaultDataSeedCallback
import com.executiveos.domain.learning.LearningGoalStatus
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OfflineReviewLearningRepositoriesTest {
    private val clock = Clock.fixed(Instant.parse("2026-11-11T15:00:00Z"), ZoneOffset.UTC)

    /** Miércoles. */
    private val today = LocalDate.of(2026, 11, 11)
    private lateinit var database: AppDatabase
    private lateinit var reviews: OfflineReviewRepository
    private lateinit var learning: OfflineLearningRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .addCallback(DefaultDataSeedCallback(clock))
            .build()
        reviews = OfflineReviewRepository(database, clock)
        learning = OfflineLearningRepository(database, clock)
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun answersAreKeptAndBlankOnesAreNotStored() = runBlocking {
        reviews.saveAnswers(
            ReviewType.DAILY,
            today,
            mapOf(ReviewQuestion.TOP_PRIORITIES to "Comité", ReviewQuestion.CAN_DELEGATE to "  "),
        )

        val review = reviews.observeReview(ReviewType.DAILY, today).first()!!

        assertEquals(mapOf(ReviewQuestion.TOP_PRIORITIES to "Comité"), review.answers)
        assertEquals(today, review.periodDate)
    }

    @Test
    fun savingTwiceInThePeriodEditsTheSameReview() = runBlocking {
        reviews.saveAnswers(ReviewType.DAILY, today, mapOf(ReviewQuestion.TOP_PRIORITIES to "Uno"))
        reviews.saveAnswers(ReviewType.DAILY, today, mapOf(ReviewQuestion.TOP_PRIORITIES to "Dos"))

        val history = reviews.observeHistory().first()

        assertEquals("Dos", history.single().answers[ReviewQuestion.TOP_PRIORITIES])
    }

    @Test
    fun theMorningReviewAndTheClosingAreDifferentReviews() = runBlocking {
        reviews.saveAnswers(ReviewType.DAILY, today, mapOf(ReviewQuestion.TOP_PRIORITIES to "Plan"))
        reviews.saveAnswers(ReviewType.CLOSING, today, mapOf(ReviewQuestion.FINISHED to "Hecho"))

        assertEquals(2, reviews.observeHistory().first().size)
    }

    @Test
    fun anyDayOfTheWeekOpensTheSameWeeklyReview() = runBlocking {
        val notes = mapOf(ReviewQuestion.WEEKLY_NOTES to "Buena semana")
        reviews.saveAnswers(ReviewType.WEEKLY, today, notes)

        val fromFriday = reviews.observeReview(ReviewType.WEEKLY, today.plusDays(2)).first()!!

        assertEquals(LocalDate.of(2026, 11, 9), fromFriday.periodDate)
        assertEquals(notes, fromFriday.answers)
    }

    @Test
    fun aQuestionOfAnotherReviewIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                reviews.saveAnswers(ReviewType.DAILY, today, mapOf(ReviewQuestion.FINISHED to "x"))
            }
        }
    }

    @Test
    fun anUnknownStoredQuestionDoesNotBreakTheReview() = runBlocking {
        database.openHelper.writableDatabase.execSQL(
            "INSERT INTO reviews (id, type, period_date, answers, updated_at) VALUES " +
                "('r', 'DAILY', '2026-11-11', '{\"RETIRADA\":\"x\",\"PROTECT_TIME\":\"Mañana\"}', 0)",
        )

        val review = reviews.observeReview(ReviewType.DAILY, today).first()!!

        assertEquals(mapOf(ReviewQuestion.PROTECT_TIME to "Mañana"), review.answers)
    }

    @Test
    fun aGoalKeepsItsDataAndCreationDate() = runBlocking {
        val id = learning.createGoal(" Basilea III ", "Regulación")
        val created = learning.observeGoals().first().single()
        assertEquals("Basilea III", created.title)
        assertEquals(id, created.id)

        learning.saveGoal(
            created.copy(course = "CFA", masteryLevel = 3, status = LearningGoalStatus.PAUSED),
        )

        val saved = learning.observeGoals().first().single()
        assertEquals("CFA", saved.course)
        assertEquals(3, saved.masteryLevel)
        assertEquals(LearningGoalStatus.PAUSED, saved.status)
        assertEquals(created.createdAt, saved.createdAt)
    }

    @Test
    fun aGoalWithoutTitleOrTopicIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { learning.createGoal(" ", "Regulación") }
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { learning.createGoal("Basilea", " ") }
        }
    }

    @Test
    fun sessionsAreStoredWithoutEmptyTextsAndCanBeDeleted() = runBlocking {
        val goal = learning.createGoal("Basilea", "Regulación")
        learning.addSession(goal, today, 45, "  ", "Examen: 85/100")

        val session = learning.observeSessions().first().single()
        assertNull(session.notes)
        assertEquals("Examen: 85/100", session.evaluation)

        learning.deleteSession(session.id)
        assertTrue(learning.observeSessions().first().isEmpty())
    }

    @Test
    fun aSessionNeedsPositiveMinutesAndAnExistingGoal() = runBlocking {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { learning.addSession(learning.createGoal("A", "B"), today, 0, null, null) }
        }
        assertThrows(Exception::class.java) {
            runBlocking { learning.addSession("inexistente", today, 30, null, null) }
        }
        assertTrue(learning.observeSessions().first().isEmpty())
    }
}
