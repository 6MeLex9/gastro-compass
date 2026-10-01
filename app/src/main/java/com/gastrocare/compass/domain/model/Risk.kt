package com.gastrocare.compass.domain.model

/** Общая шкала важности сообщений — используется и в оценке риска, и в разборе этикетки. */
enum class Severity { INFO, WARNING, ERROR }

/** Уровень риска продукта или приёма пищи для конкретного пользователя. */
enum class RiskLevel(val title: String, val color: Long, val advice: String) {
    SAFE("Безопасно", 0xFF2E7D32, "Можно есть спокойно"),
    CAUTION("Умеренно", 0xFFF9A825, "Можно, но следите за порцией"),
    RISKY("Рискованно", 0xFFEF6C00, "Высокий шанс симптомов — лучше заменить"),
    AVOID("Избегать", 0xFFC62828, "Не рекомендуется при вашем диагнозе");

    companion object {
        fun fromScore(score: Int): RiskLevel = when {
            score >= 75 -> AVOID
            score >= 50 -> RISKY
            score >= 25 -> CAUTION
            else -> SAFE
        }
    }
}

/** Причина, по которой продукт получил риск-баллы. */
data class RiskReason(
    val tag: TriggerTag,
    val title: String,
    val explanation: String,
    val contribution: Double,
    val severity: Severity
)

/** Рекомендация по объёму порции. */
data class PortionAdvice(
    val recommendedMaxGrams: Double,
    val note: String,
    val isCritical: Boolean = false
)

/**
 * Итог оценки блюда/продукта «умным» движком.
 *
 * @param score 0–100, где 100 — максимальный риск обострения
 * @param contextWarnings предупреждения о контексте: время, натощак, жировой фон дня
 */
data class RiskAssessment(
    val score: Int,
    val level: RiskLevel,
    val reasons: List<RiskReason>,
    val benefits: List<String> = emptyList(),
    val contextWarnings: List<String> = emptyList(),
    val portionAdvice: PortionAdvice,
    val substitutes: List<FoodItem> = emptyList(),
    val hardBan: Boolean = false,
    val hardBanReason: String? = null,
    val summary: String = ""
) {
    val topReason: RiskReason? get() = reasons.maxByOrNull { it.contribution }
}

/** Оценка целого приёма пищи (несколько записей сразу). */
data class MealAssessment(
    val slot: MealSlot,
    val totalGrams: Double,
    val score: Int,
    val level: RiskLevel,
    val volumeWarning: String?,
    val fatLoadWarning: String?,
    val timingWarning: String?
)
