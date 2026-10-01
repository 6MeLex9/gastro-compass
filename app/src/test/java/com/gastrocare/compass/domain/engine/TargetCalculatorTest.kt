package com.gastrocare.compass.domain.engine

import com.gastrocare.compass.data.FoodRepository
import com.gastrocare.compass.data.SeedFoods
import com.gastrocare.compass.domain.model.ActivityLevel
import com.gastrocare.compass.domain.model.DayLog
import com.gastrocare.compass.domain.model.Diagnosis
import com.gastrocare.compass.domain.model.DiaryEntry
import com.gastrocare.compass.domain.model.FoodCategory
import com.gastrocare.compass.domain.model.Goal
import com.gastrocare.compass.domain.model.MealSlot
import com.gastrocare.compass.domain.model.Nutrition
import com.gastrocare.compass.domain.model.RecipeIngredient
import com.gastrocare.compass.domain.model.RiskLevel
import com.gastrocare.compass.domain.model.Severity
import com.gastrocare.compass.domain.model.Sex
import com.gastrocare.compass.domain.model.UserProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Тесты расчёта калорий, безопасных границ дефицита и проверок дня. */
class TargetCalculatorTest {

    private val calculator = TargetCalculator()
    private val repo = FoodRepository(SeedFoods.items)

    private fun profile(
        sex: Sex = Sex.FEMALE,
        goal: Goal = Goal.MAINTAIN,
        weight: Double = 70.0,
        height: Int = 168,
        diagnoses: Set<Diagnosis> = setOf(Diagnosis.GERD)
    ) = UserProfile(
        sex = sex,
        age = 35,
        heightCm = height,
        weightKg = weight,
        targetWeightKg = weight - 5,
        activity = ActivityLevel.LIGHT,
        goal = goal,
        diagnoses = diagnoses
    )

    @Test
    fun `расчёт поддержания совпадает с расходом`() {
        val p = profile()
        val targets = calculator.calculate(p)

        assertEquals(targets.tdee, targets.calories, 1.0)
        assertEquals(0.0, targets.deficitOrSurplus, 1.0)
        assertTrue(targets.protein.grams > 60)
        assertTrue(targets.carbs.grams > 100)
    }

    @Test
    fun `дефицит ограничен двадцатью процентами`() {
        val p = profile(goal = Goal.LOSE_WEIGHT, weight = 95.0, height = 170)
        val targets = calculator.calculate(p)

        val maxDeficit = targets.tdee * 0.20
        assertTrue("дефицит ${-targets.deficitOrSurplus} не должен превышать $maxDeficit", -targets.deficitOrSurplus <= maxDeficit + 1.0)
        assertTrue(-targets.deficitOrSurplus <= 500.0 + 1.0)
    }

    @Test
    fun `норма не опускается ниже безопасного минимума`() {
        val p = profile(sex = Sex.FEMALE, goal = Goal.LOSE_WEIGHT, weight = 52.0, height = 155)
        val targets = calculator.calculate(p)

        assertTrue("норма ${targets.calories} должна быть не ниже 1200", targets.calories >= 1200.0)
    }

    @Test
    fun `при ЖКБ и панкреатите дефицит мягче и жир ниже`() {
        val gerd = calculator.calculate(profile(goal = Goal.LOSE_WEIGHT, weight = 95.0))
        val gallstones = calculator.calculate(
            profile(goal = Goal.LOSE_WEIGHT, weight = 95.0, diagnoses = setOf(Diagnosis.GALLSTONES))
        )

        assertTrue(gallstones.fat.percentOfCalories <= 22.0)
        assertTrue(gallstones.fat.grams < gerd.fat.grams)
        assertTrue(
            "при ЖКБ дефицит не должен быть агрессивным",
            -gallstones.deficitOrSurplus <= gallstones.tdee * 0.15 + 1.0
        )
    }

    @Test
    fun `при панкреатите лимит жира самый строгий`() {
        val pancreatitis = calculator.calculate(profile(diagnoses = setOf(Diagnosis.PANCREATITIS)))
        assertTrue(pancreatitis.fat.percentOfCalories <= 20.0)
        assertTrue(pancreatitis.mealCount >= 5)
    }

    @Test
    fun `профицит при наборе веса не превышает четыреста ккал`() {
        val p = profile(goal = Goal.GAIN_WEIGHT, weight = 55.0)
        val targets = calculator.calculate(p)

        assertTrue(targets.deficitOrSurplus <= 400.0 + 1.0)
        assertTrue(targets.calories > targets.tdee)
        assertTrue(targets.notes.any { it.contains("калорийной плотности", ignoreCase = true) })
    }

    @Test
    fun `кофеин и соль ограничены при ГЭРБ`() {
        val targets = calculator.calculate(profile(diagnoses = setOf(Diagnosis.GERD)))
        assertEquals(200.0, targets.caffeineCapMg, 0.1)
        assertEquals(5.0, targets.saltCapGrams, 0.1)
    }

    @Test
    fun `последний приём назначается за три часа до сна`() {
        val targets = calculator.calculate(profile().copy(sleepHour = 23))
        assertEquals(20, targets.lastMealHour)

        val lateSleeper = calculator.calculate(profile().copy(sleepHour = 1))
        assertEquals(22, lateSleeper.lastMealHour)
    }

    @Test
    fun `ручная поправка учитывается и ограничивается полом`() {
        val base = calculator.calculate(profile())
        val adjusted = calculator.calculate(profile().copy(calorieAdjustment = -150.0))
        assertEquals(base.calories - 150.0, adjusted.calories, 1.0)

        val clamped = calculator.calculate(profile().copy(calorieAdjustment = -5000.0))
        assertTrue(clamped.calories >= 1200.0)
        assertTrue(clamped.safetyWarnings.any { it.contains("минимум", ignoreCase = true) })
    }

    @Test
    fun `план приёмов распределяет норму полностью`() {
        val p = profile()
        val targets = calculator.calculate(p)
        val plan = calculator.mealPlan(p, targets)

        assertEquals(targets.mealCount, plan.size)
        assertEquals(targets.calories, plan.sumOf { it.calories }, 1.0)
        assertTrue("у каждого приёма должно быть время", plan.all { it.hour in 0..23 })
    }

    @Test
    fun `выбор числа приёмов уважается даже при ГЭРБ`() {
        val p = profile(diagnoses = setOf(Diagnosis.GERD)).copy(mealsPerDay = 4)
        val targets = calculator.calculate(p)

        assertEquals(
            "ранее выбор игнорировался и всегда подставлялось 6 приёмов",
            4, targets.mealCount
        )
        assertTrue(
            "должно быть предупреждение о крупных порциях",
            targets.safetyWarnings.any { it.contains("приёмов пищи", ignoreCase = true) }
        )
        assertTrue(targets.notes.any { it.contains("рекомендуется") })
    }

    @Test
    fun `шесть приёмов при ГЭРБ не вызывают предупреждений`() {
        val p = profile(diagnoses = setOf(Diagnosis.GERD)).copy(mealsPerDay = 6)
        val targets = calculator.calculate(p)

        assertEquals(6, targets.mealCount)
        assertTrue(targets.safetyWarnings.none { it.contains("приёмов пищи", ignoreCase = true) })
    }

    @Test
    fun `личный лимит воды переопределяет автоматический`() {
        val auto = calculator.calculate(profile())
        val manual = calculator.calculate(profile().copy(waterGoalMl = 2500))

        assertEquals(2500.0, manual.waterMl, 0.1)
        assertTrue(auto.waterMl >= 1200.0 && auto.waterMl <= 2500.0)
        assertTrue(manual.notes.any { it.contains("Личный лимит воды") })
    }

    @Test
    fun `время приёмов берётся из профиля`() {
        val p = profile().copy(mealHours = mapOf("BREAKFAST" to 7, "DINNER" to 18))
        val targets = calculator.calculate(p)
        val plan = calculator.mealPlan(p, targets)

        assertEquals(7, plan.first { it.slot == com.gastrocare.compass.domain.model.MealSlot.BREAKFAST }.hour)
        assertEquals(18, plan.first { it.slot == com.gastrocare.compass.domain.model.MealSlot.DINNER }.hour)
    }

    @Test
    fun `новый симптом ком в горле усиливает риск`() {
        val symptom = com.gastrocare.compass.domain.model.Symptom.GLOBE
        assertTrue(symptom.severityWeight > 0.5)
        assertTrue(symptom.title.contains("Ком в горле"))
    }

    @Test
    fun `проверки дня ловят опасно низкую калорийность`() {
        val targets = calculator.calculate(profile())
        val day = DayLog(
            epochDay = 0,
            entries = listOf(
                entry("Овсянка", Nutrition.of(150.0, 5.0, 3.0, 25.0))
            )
        )

        val checks = calculator.dailyChecks(profile(), targets, day)

        assertTrue(checks.any { it.severity == Severity.WARNING && it.title.contains("недобор", ignoreCase = true) })
        assertTrue(checks.any { it.severity == Severity.ERROR })
    }

    @Test
    fun `проверки дня ловят превышение жира`() {
        val targets = calculator.calculate(profile())
        val day = DayLog(
            epochDay = 0,
            entries = listOf(
                entry("Сыр", Nutrition.of(900.0, 40.0, 90.0, 5.0)),
                entry("Маcло", Nutrition.of(400.0, 0.0, 45.0, 0.0))
            )
        )

        val checks = calculator.dailyChecks(profile(), targets, day)

        assertTrue(checks.any { it.title.contains("жир", ignoreCase = true) })
    }

    @Test
    fun `недельная корректировка предлагает снизить калории при остановке веса`() {
        val targets = calculator.calculate(profile(goal = Goal.LOSE_WEIGHT, weight = 90.0))
        val adjustment = calculator.weeklyAdjustment(profile(goal = Goal.LOSE_WEIGHT, weight = 90.0), targets, 0.0)

        assertEquals(-100.0, adjustment.deltaCalories, 0.1)
        assertTrue(adjustment.newCalories < targets.calories)
    }

    @Test
    fun `недельная корректировка тормозит слишком быструю потерю веса`() {
        val p = profile(goal = Goal.LOSE_WEIGHT, weight = 80.0)
        val targets = calculator.calculate(p)
        val adjustment = calculator.weeklyAdjustment(p, targets, -1.2)

        assertEquals(150.0, adjustment.deltaCalories, 0.1)
        assertEquals(Severity.WARNING, adjustment.severity)
        assertTrue(adjustment.message.contains("Быстрая", ignoreCase = true) || adjustment.message.contains("быстрое", ignoreCase = true))
    }

    @Test
    fun `советы по дефициту упоминают объём и жир`() {
        val p = profile(goal = Goal.LOSE_WEIGHT)
        val advice = calculator.safeDeficitAdvice(p, calculator.calculate(p)).joinToString(" ")

        assertTrue(advice.contains("объём", ignoreCase = true))
        assertTrue(advice.contains("жир", ignoreCase = true))
        assertTrue(advice.contains("голод", ignoreCase = true))
    }

    @Test
    fun `советы по набору веса не предлагают увеличивать порции`() {
        val p = profile(goal = Goal.GAIN_WEIGHT)
        val advice = calculator.safeSurplusAdvice(p, calculator.calculate(p)).joinToString(" ")

        assertTrue(advice.contains("порци", ignoreCase = true) || advice.contains("объём", ignoreCase = true))
        assertTrue(advice.contains("калорийность", ignoreCase = true) || advice.contains("калории", ignoreCase = true))
    }

    @Test
    fun `калькулятор блюда сохраняет энергию при уварке крупы`() {
        val calculator = RecipeCalculator()
        val rice = repo.byId("rice-white")!!
        val result = calculator.compute(listOf(RecipeIngredient(rice, 100.0)))

        assertEquals(116.0, result.totalNutrition.calories, 1.0)
        assertTrue("рис должен увеличить массу", result.cookedWeightG > 200.0)
        assertTrue("калорийность на 100 г готового ниже сухого", result.per100Cooked.calories < 116.0)
    }

    @Test
    fun `калькулятор блюда учитывает фактический вес`() {
        val calculator = RecipeCalculator()
        val chicken = repo.byId("chicken-breast-boiled")!!
        val result = calculator.compute(listOf(RecipeIngredient(chicken, 200.0)), cookedWeightG = 150.0)

        assertEquals(150.0, result.cookedWeightG, 0.01)
        assertEquals(137.0 * 2 / 1.5, result.per100Cooked.calories, 1.0)
        assertEquals(274.0, result.totalNutrition.calories, 2.0)
    }

    @Test
    fun `коэффициенты выхода зависят от категории`() {
        val calculator = RecipeCalculator()
        assertEquals(2.5, calculator.yieldFactor(FoodCategory.GRAINS), 0.001)
        assertEquals(0.7, calculator.yieldFactor(FoodCategory.MEAT), 0.001)
        assertEquals(1.0, calculator.yieldFactor(FoodCategory.SWEETS), 0.001)
    }

    @Test
    fun `перевод миллилитров в граммы учитывает плотность`() {
        val calculator = RecipeCalculator()
        assertEquals(257.5, calculator.mlToGrams(250.0, 1.03), 0.01)
    }

    private fun entry(name: String, nutrition: Nutrition) = DiaryEntry(
        foodId = name,
        foodName = name,
        grams = 100.0,
        nutrition = nutrition,
        slot = MealSlot.BREAKFAST,
        timestamp = System.currentTimeMillis(),
        riskScore = 10,
        riskLevel = RiskLevel.SAFE
    )

    @Test
    fun `инсайты формируются для пустого и заполненного дневника`() {
        val engine = InsightEngine()
        val p = profile()
        val targets = calculator.calculate(p)

        val empty = engine.all(p, targets, DayLog(0), emptyList(), emptyList())
        assertTrue("для пустого дневника инсайты о продуктах не нужны", empty.none { it.kind == com.gastrocare.compass.domain.engine.InsightKind.PERSONAL_TRIGGER })

        val days = (0..9).map { day ->
            DayLog(
                epochDay = day.toLong(),
                entries = listOf(entry("Кофе", Nutrition.of(9.0, 0.1, 0.2, 0.7))),
                symptoms = listOf(
                    com.gastrocare.compass.domain.model.SymptomRecord(
                        symptom = com.gastrocare.compass.domain.model.Symptom.HEARTBURN,
                        severity = 2,
                        timestamp = System.currentTimeMillis()
                    )
                )
            )
        }
        val filled = engine.all(p, targets, days.first(), days, emptyList())
        assertTrue(filled.isNotEmpty())
        assertTrue(filled.any { it.severity == Severity.WARNING || it.severity == Severity.ERROR })
        assertNotNull(filled.first().title)
    }
}
