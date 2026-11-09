package com.executiveos.domain.priority

import com.executiveos.domain.model.Task
import com.executiveos.domain.model.effectiveDate
import java.time.LocalDate
import java.time.temporal.ChronoUnit

enum class PriorityLevel { CRITICAL, HIGH, MEDIUM, LOW }

enum class CriticalObligation {
    REGULATORY,
    LEGAL,
    AUDIT,
    MATERIAL_RISK,
    CONTRACTUAL,
    EXECUTIVE_COMMITMENT,
}

enum class PriorityScale { IMPACT, URGENCY, DELAY_RISK }

enum class DateSignal { OVERDUE, DUE_TODAY, DUE_SOON, FUTURE, NONE }

enum class PriorityReason {
    HIGH_IMPACT,
    HIGH_URGENCY,
    SERIOUS_DELAY_RISK,
    OVERDUE,
    DUE_SOON,
    PENDING_DEPENDENCIES,
    CRITICAL_OBLIGATION,
}

/**
 * Pesos en porcentaje y umbrales en centésimas: con enteros, un 4,00 exacto nunca cae en
 * 3,99 por errores de redondeo.
 */
data class PrioritySettings(
    val impactWeight: Int = 40,
    val urgencyWeight: Int = 25,
    val delayRiskWeight: Int = 35,
    val criticalThreshold: Int = 400,
    val highThreshold: Int = 300,
    val mediumThreshold: Int = 200,
) {
    val isValid: Boolean
        get() = listOf(impactWeight, urgencyWeight, delayRiskWeight).all { it in 0..100 } &&
            impactWeight + urgencyWeight + delayRiskWeight == 100 &&
            mediumThreshold in 100 until highThreshold &&
            highThreshold < criticalThreshold &&
            criticalThreshold <= 500

    fun levelFor(score: Int): PriorityLevel = when {
        score >= criticalThreshold -> PriorityLevel.CRITICAL
        score >= highThreshold -> PriorityLevel.HIGH
        score >= mediumThreshold -> PriorityLevel.MEDIUM
        else -> PriorityLevel.LOW
    }
}

data class ScoreContribution(
    val scale: PriorityScale,
    val value: Int,
    val weightPercent: Int,
) {
    val points: Int get() = value * weightPercent
}

data class PriorityAssessment(
    val score: Int?,
    val calculatedLevel: PriorityLevel?,
    val manualLevel: PriorityLevel?,
    val manualReason: String?,
    val obligation: CriticalObligation?,
    val missingScales: List<PriorityScale>,
    val contributions: List<ScoreContribution>,
    val dateSignal: DateSignal,
    val reasons: List<PriorityReason>,
) {
    val effectiveLevel: PriorityLevel? get() = manualLevel ?: calculatedLevel
}

/**
 * Las fechas y las dependencias explican y avisan, pero no alteran la puntuación: una fecha
 * cercana no convierte una tarea en estratégicamente importante.
 */
object PriorityEngine {
    private const val HIGH_SCALE_VALUE = 4
    private const val DUE_SOON_DAYS = 7L

    fun assess(
        task: Task,
        settings: PrioritySettings,
        today: LocalDate,
        openDependencies: Int = 0,
    ): PriorityAssessment {
        val contributions = listOfNotNull(
            task.impact?.let { ScoreContribution(PriorityScale.IMPACT, it, settings.impactWeight) },
            task.urgency?.let { ScoreContribution(PriorityScale.URGENCY, it, settings.urgencyWeight) },
            task.delayRisk?.let {
                ScoreContribution(PriorityScale.DELAY_RISK, it, settings.delayRiskWeight)
            },
        )
        val missing = PriorityScale.entries.filter { scale ->
            contributions.none { it.scale == scale }
        }
        // Un campo vacío nunca se interpreta como un valor: sin las tres escalas no hay nota.
        val score = if (missing.isEmpty()) contributions.sumOf { it.points } else null
        val signal = dateSignal(task, today)
        return PriorityAssessment(
            score = score,
            calculatedLevel = score?.let(settings::levelFor),
            manualLevel = task.manualPriority,
            manualReason = task.manualPriorityReason,
            obligation = task.obligation,
            missingScales = missing,
            contributions = contributions,
            dateSignal = signal,
            reasons = reasons(task, signal, openDependencies),
        )
    }

    fun dateSignal(task: Task, today: LocalDate): DateSignal {
        val date = task.effectiveDate ?: return DateSignal.NONE
        val days = ChronoUnit.DAYS.between(today, date)
        return when {
            days < 0 -> DateSignal.OVERDUE
            days == 0L -> DateSignal.DUE_TODAY
            days <= DUE_SOON_DAYS -> DateSignal.DUE_SOON
            else -> DateSignal.FUTURE
        }
    }

    private fun reasons(
        task: Task,
        signal: DateSignal,
        openDependencies: Int,
    ): List<PriorityReason> = buildList {
        if (task.obligation != null) add(PriorityReason.CRITICAL_OBLIGATION)
        if ((task.impact ?: 0) >= HIGH_SCALE_VALUE) add(PriorityReason.HIGH_IMPACT)
        if ((task.urgency ?: 0) >= HIGH_SCALE_VALUE) add(PriorityReason.HIGH_URGENCY)
        if ((task.delayRisk ?: 0) >= HIGH_SCALE_VALUE) add(PriorityReason.SERIOUS_DELAY_RISK)
        if (signal == DateSignal.OVERDUE) add(PriorityReason.OVERDUE)
        if (signal == DateSignal.DUE_TODAY || signal == DateSignal.DUE_SOON) {
            add(PriorityReason.DUE_SOON)
        }
        if (openDependencies > 0) add(PriorityReason.PENDING_DEPENDENCIES)
    }
}
