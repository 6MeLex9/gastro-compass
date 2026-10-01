package com.gastrocare.compass.domain.engine

import com.gastrocare.compass.domain.model.FoodCategory
import com.gastrocare.compass.domain.model.Nutrition
import com.gastrocare.compass.domain.model.RecipeIngredient
import com.gastrocare.compass.domain.model.RecipeResult
import kotlin.math.roundToInt

/**
 * Калькулятор блюда: собирает КБЖУ из ингредиентов и пересчитывает на 100 г готового продукта.
 *
 * Отдельно решает «больную» тему этикеток и рецептов: значения часто даны на 100 г **сухого**
 * продукта, а едим мы готовый. При варке крупы масса растёт (впитывается вода), при жарке мяса —
 * падает (испаряется вода и вытапливается жир). Общая энергия блюда при этом почти не меняется,
 * поэтому калорийность на 100 г готового продукта считается от общей энергии и итоговой массы.
 */
class RecipeCalculator {

    /** Коэффициент выхода готового продукта из сырого (по массе). */
    fun yieldFactor(category: FoodCategory): Double = when (category) {
        FoodCategory.GRAINS -> 2.5      // крупы и макароны впитывают воду
        FoodCategory.LEGUMES -> 2.4     // бобовые после замачивания и варки
        FoodCategory.MEAT -> 0.7        // ужарка мяса и птицы
        FoodCategory.FISH -> 0.8
        FoodCategory.VEGETABLES -> 0.9  // овощи теряют воду
        FoodCategory.EGGS -> 0.95
        else -> 1.0
    }

    /**
     * @param cookedWeightG фактический вес готового блюда (если пользователь взвесил) —
     *        он точнее расчётного коэффициента
     * @param portionCount на сколько порций делить блюдо
     */
    fun compute(
        ingredients: List<RecipeIngredient>,
        cookedWeightG: Double? = null,
        portionCount: Int = 1
    ): RecipeResult {
        val notes = mutableListOf<String>()

        val total = ingredients.fold(Nutrition.ZERO) { acc, ing ->
            acc + ing.food.nutritionFor(ing.grams)
        }
        val rawWeight = ingredients.sumOf { it.grams }

        // Расчётный выход: взвешенное среднее коэффициентов по массе ингредиентов
        val estimatedWeight = if (ingredients.isEmpty()) rawWeight else {
            ingredients.sumOf { it.grams * yieldFactor(it.food.category) }
        }
        val effectiveYield = if (rawWeight > 0) estimatedWeight / rawWeight else 1.0

        val cookedWeight = when {
            cookedWeightG != null && cookedWeightG > 0 -> {
                notes += "Использован фактически взвешенный вес готового блюда: ${cookedWeightG.roundToInt()} г."
                cookedWeightG
            }

            else -> {
                notes += "Вес готового блюда рассчитан по типовым потерям (коэффициент " +
                    "${String.format(java.util.Locale.US, "%.2f", effectiveYield)}). " +
                    "Взвесьте готовое блюдо — точность заметно вырастет."
                estimatedWeight
            }
        }

        if (cookedWeight <= 0) {
            return RecipeResult(total, rawWeight, rawWeight, 1.0, total.per100Fallback(rawWeight), portionCount, notes)
        }

        notes += "Общая энергия блюда — ${total.calories.roundToInt()} ккал: при варке и жарке она не исчезает, " +
            "меняется только масса. Поэтому калорийность на 100 г готового продукта — " +
            "${(total.calories / cookedWeight * 100).roundToInt()} ккал."

        if (effectiveYield > 1.15) {
            notes += "Масса выросла за счёт воды: для круп, макарон и бобовых вводите вес сухого продукта, " +
                "а калорийность смотрите по готовому блюду."
        }
        if (effectiveYield < 0.9) {
            notes += "Часть массы ушла с водой и вытопленным жиром. Если жарили с маслом, добавьте его в состав " +
                "ингредиентов — иначе калорийность занижается."
        }

        val per100 = Nutrition(
            calories = total.calories / cookedWeight * 100,
            protein = total.protein / cookedWeight * 100,
            fat = total.fat / cookedWeight * 100,
            carbs = total.carbs / cookedWeight * 100,
            saturatedFat = total.saturatedFat / cookedWeight * 100,
            sugar = total.sugar / cookedWeight * 100,
            fiber = total.fiber / cookedWeight * 100,
            salt = total.salt / cookedWeight * 100,
            caffeineMg = total.caffeineMg / cookedWeight * 100
        )

        return RecipeResult(
            totalNutrition = total,
            rawWeightG = rawWeight,
            cookedWeightG = cookedWeight,
            yieldFactor = effectiveYield,
            per100Cooked = per100,
            portionCount = portionCount.coerceAtLeast(1),
            notes = notes
        )
    }

    private fun Nutrition.per100Fallback(weight: Double): Nutrition =
        if (weight <= 0) this else scaled(100.0 / weight)

    /** Быстрый пересчёт «сухой продукт → готовый» для этикеток вроде «на 100 г сухой смеси». */
    fun dryToCooked(dryPer100: Nutrition, dryGrams: Double, cookedWeightG: Double): Nutrition {
        val total = dryPer100.scaled(dryGrams)
        if (cookedWeightG <= 0) return dryPer100
        return Nutrition(
            calories = total.calories / cookedWeightG * 100,
            protein = total.protein / cookedWeightG * 100,
            fat = total.fat / cookedWeightG * 100,
            carbs = total.carbs / cookedWeightG * 100,
            saturatedFat = total.saturatedFat / cookedWeightG * 100,
            sugar = total.sugar / cookedWeightG * 100,
            fiber = total.fiber / cookedWeightG * 100,
            salt = total.salt / cookedWeightG * 100,
            caffeineMg = total.caffeineMg / cookedWeightG * 100
        )
    }

    /** Жидкость: перевод миллилитров в граммы по плотности (молоко 1,03; масло 0,92; мёд 1,4). */
    fun mlToGrams(ml: Double, density: Double = 1.0): Double = ml * density
}
