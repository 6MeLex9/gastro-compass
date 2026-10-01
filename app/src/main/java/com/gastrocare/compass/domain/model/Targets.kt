package com.gastrocare.compass.domain.model

/** Цель по одному макронутриенту. */
data class MacroTarget(
    val grams: Double,
    val percentOfCalories: Double,
    val title: String
)

/**
 * Персональные цели на день, рассчитанные с учётом диагноза.
 *
 * @param deficitOrSurplus разница с поддерживающей калорийностью: отрицательная — дефицит
 */
data class NutritionTargets(
    val bmr: Double,
    val tdee: Double,
    val calories: Double,
    val protein: MacroTarget,
    val fat: MacroTarget,
    val carbs: MacroTarget,
    val deficitOrSurplus: Double,
    val fatCapGrams: Double,
    val saturatedFatCapGrams: Double,
    val sugarCapGrams: Double,
    val saltCapGrams: Double,
    val caffeineCapMg: Double,
    val fiberTargetGrams: Double,
    val mealCount: Int,
    val lastMealHour: Int,
    val waterMl: Double,
    val notes: List<String> = emptyList(),
    val safetyWarnings: List<String> = emptyList()
) {
    /** Безопасная скорость изменения веса, кг в неделю. */
    val weeklyWeightChangeKg: Double get() = deficitOrSurplus * 7.0 / 7700.0
}

/** Проверка безопасности дневного рациона. */
data class SafetyCheck(
    val severity: Severity,
    val title: String,
    val detail: String,
    val recommendation: String
)

/** Итог сравнения фактического рациона с целями. */
data class DailyProgress(
    val targets: NutritionTargets,
    val consumed: Nutrition,
    val entryCount: Int
) {
    val caloriesLeft: Double get() = targets.calories - consumed.calories
    val caloriePercent: Double get() = if (targets.calories <= 0) 0.0 else consumed.calories / targets.calories * 100.0

    fun macroPercent(kind: MacroKind): Double = when (kind) {
        MacroKind.PROTEIN -> consumed.protein / targets.protein.grams * 100.0
        MacroKind.FAT -> consumed.fat / targets.fat.grams * 100.0
        MacroKind.CARBS -> consumed.carbs / targets.carbs.grams * 100.0
    }

    fun consumedOf(kind: MacroKind): Double = when (kind) {
        MacroKind.PROTEIN -> consumed.protein
        MacroKind.FAT -> consumed.fat
        MacroKind.CARBS -> consumed.carbs
    }

    fun targetOf(kind: MacroKind): Double = when (kind) {
        MacroKind.PROTEIN -> targets.protein.grams
        MacroKind.FAT -> targets.fat.grams
        MacroKind.CARBS -> targets.carbs.grams
    }
}

enum class MacroKind(val title: String, val short: String, val color: Long) {
    PROTEIN("Белки", "Б", 0xFF1E88E5),
    FAT("Жиры", "Ж", 0xFFF9A825),
    CARBS("Углеводы", "У", 0xFF43A047)
}
