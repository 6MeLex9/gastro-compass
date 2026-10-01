package com.gastrocare.compass.domain.model

/** Пищевая ценность. Все значения — на 100 г (для напитков — на 100 мл) продукта. */
data class Nutrition(
    val calories: Double,
    val protein: Double,
    val fat: Double,
    val carbs: Double,
    val saturatedFat: Double = 0.0,
    val sugar: Double = 0.0,
    val fiber: Double = 0.0,
    val salt: Double = 0.0,
    val caffeineMg: Double = 0.0
) {
    val energyKj: Double get() = calories * 4.184

    /** Энергия, рассчитанная по макросам (4/9/4). Нужна для валидации этикеток. */
    val atwaterCalories: Double get() = protein * 4.0 + fat * 9.0 + carbs * 4.0

    fun scaled(grams: Double): Nutrition {
        val k = grams / 100.0
        return Nutrition(
            calories = calories * k,
            protein = protein * k,
            fat = fat * k,
            carbs = carbs * k,
            saturatedFat = saturatedFat * k,
            sugar = sugar * k,
            fiber = fiber * k,
            salt = salt * k,
            caffeineMg = caffeineMg * k
        )
    }

    operator fun plus(other: Nutrition) = Nutrition(
        calories = calories + other.calories,
        protein = protein + other.protein,
        fat = fat + other.fat,
        carbs = carbs + other.carbs,
        saturatedFat = saturatedFat + other.saturatedFat,
        sugar = sugar + other.sugar,
        fiber = fiber + other.fiber,
        salt = salt + other.salt,
        caffeineMg = caffeineMg + other.caffeineMg
    )

    companion object {
        val ZERO = Nutrition(0.0, 0.0, 0.0, 0.0)
        val PER_100_ML = Nutrition(0.0, 0.0, 0.0, 0.0)

        private fun d(v: Double?) = v ?: 0.0

        /** Создание с безопасными значениями по умолчанию. */
        fun of(
            calories: Double,
            protein: Double,
            fat: Double,
            carbs: Double,
            saturatedFat: Double? = null,
            sugar: Double? = null,
            fiber: Double? = null,
            salt: Double? = null,
            caffeineMg: Double? = null
        ) = Nutrition(calories, protein, fat, carbs, d(saturatedFat), d(sugar), d(fiber), d(salt), d(caffeineMg))
    }

    /** Доля калорий из жира, % — ключевой показатель при ГЭРБ и болезнях желчного. */
    val fatEnergyPercent: Double
        get() = if (calories <= 0.0) 0.0 else fat * 9.0 / calories * 100.0

    /** Доля калорий из белка, %. */
    val proteinEnergyPercent: Double
        get() = if (calories <= 0.0) 0.0 else protein * 4.0 / calories * 100.0
}

/** Откуда взялся продукт — влияет на отображение и доверие к КБЖУ. */
enum class FoodSource(val title: String) {
    BUILT_IN("Справочник"),
    BARCODE("Штрихкод"),
    LABEL_OCR("Фото этикетки"),
    LABEL_MANUAL("Ввод с этикетки"),
    RECIPE("Своё блюдо"),
    VOICE("Голосовой ввод"),
    MANUAL("Введено вручную")
}

/**
 * Продукт или блюдо в справочнике.
 *
 * @param per100 пищевая ценность на 100 г (напитки — на 100 мл)
 * @param typicalPortionG типичная порция в граммах, используется для быстрого добавления
 * @param factors факторы риска с интенсивностью
 * @param isDrink напиток: учёт объёма и пересчёт мл↔г
 */
data class FoodItem(
    val id: String,
    val name: String,
    val category: FoodCategory,
    val per100: Nutrition,
    val typicalPortionG: Int = 100,
    val factors: List<TriggerFactor> = emptyList(),
    val isDrink: Boolean = false,
    val densityGPerMl: Double = 1.0,
    val gastroNote: String? = null,
    /** id продуктов-замен с меньшим риском. */
    val substitutes: List<String> = emptyList(),
    val barcode: String? = null,
    val source: FoodSource = FoodSource.BUILT_IN,
    /** Пометка «можно и полезно» — база спокойных продуктов для подсказок. */
    val isGentle: Boolean = false
) {
    val tags: Set<TriggerTag> get() = factors.map { it.tag }.toSet()

    fun factor(tag: TriggerTag): TriggerFactor? = factors.firstOrNull { it.tag == tag }

    fun has(tag: TriggerTag): Boolean = factors.any { it.tag == tag }

    fun nutritionFor(grams: Double): Nutrition = per100.scaled(grams)

    /** Вес порции для напитков: объём в мл → граммы. */
    fun gramsFromMl(ml: Double): Double = ml * densityGPerMl
}
