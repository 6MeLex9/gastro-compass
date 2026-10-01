package com.gastrocare.compass.domain.model

import java.util.UUID

/**
 * Приёмы пищи с типовым временем.
 *
 * Слоты намеренно разделены (второй завтрак и полдник — разные), чтобы у каждого приёма
 * было своё настраиваемое время и понятная подпись в плане на день.
 */
enum class MealSlot(val title: String, val short: String, val defaultHour: Int) {
    BREAKFAST("Завтрак", "Завтрак", 8),
    SECOND_BREAKFAST("Второй завтрак", "2-й завтрак", 11),
    LUNCH("Обед", "Обед", 13),
    SNACK("Полдник", "Полдник", 16),
    DINNER("Ужин", "Ужин", 19),
    LATE_SNACK("Перед сном", "Перед сном", 21);

    companion object {
        fun forHour(hour: Int): MealSlot = when {
            hour < 10 -> BREAKFAST
            hour < 12 -> SECOND_BREAKFAST
            hour < 15 -> LUNCH
            hour < 18 -> SNACK
            hour < 21 -> DINNER
            else -> LATE_SNACK
        }
    }
}

/** Запись дневника питания. Пищевая ценность сохраняется снимком — история не «плывёт» при правках справочника. */
data class DiaryEntry(
    val id: String = UUID.randomUUID().toString(),
    val foodId: String,
    val foodName: String,
    val grams: Double,
    val nutrition: Nutrition,
    val slot: MealSlot,
    val timestamp: Long,
    val riskScore: Int,
    val riskLevel: RiskLevel,
    val factors: List<TriggerTag> = emptyList(),
    val note: String? = null
) {
    val calories: Double get() = nutrition.calories
}

/** Запись о симптоме: время, выраженность 1–3, связь с приёмами пищи. */
data class SymptomRecord(
    val id: String = UUID.randomUUID().toString(),
    val symptom: Symptom,
    val severity: Int,
    val timestamp: Long,
    val note: String? = null
)

/** Один день дневника. */data class DayLog(
    val epochDay: Long,
    val entries: List<DiaryEntry> = emptyList(),
    val symptoms: List<SymptomRecord> = emptyList()
) {
    val totals: Nutrition get() = entries.fold(Nutrition.ZERO) { acc, e -> acc + e.nutrition }

    val averageRisk: Int
        get() = if (entries.isEmpty()) 0 else entries.sumOf { it.riskScore } / entries.size

    val maxRisk: Int get() = entries.maxOfOrNull { it.riskScore } ?: 0

    fun entriesFor(slot: MealSlot): List<DiaryEntry> = entries.filter { it.slot == slot }

    /** Последний приём пищи по времени — для правила «за 3 часа до сна». */
    val lastMealHour: Int?
        get() {
            val last = entries.maxByOrNull { it.timestamp } ?: return null
            val cal = java.util.Calendar.getInstance()
            cal.timeInMillis = last.timestamp
            return cal.get(java.util.Calendar.HOUR_OF_DAY)
        }

    /** Минуты от полуночи для последнего приёма — точнее при сравнении с временем сна. */
    val lastMealMinuteOfDay: Int?
        get() {
            val last = entries.maxByOrNull { it.timestamp } ?: return null
            val cal = java.util.Calendar.getInstance()
            cal.timeInMillis = last.timestamp
            return cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE)
        }
}

/** Взвешивание: нужно для корректировки калорий и оценки темпа. */
data class WeightRecord(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long,
    val kg: Double
)
