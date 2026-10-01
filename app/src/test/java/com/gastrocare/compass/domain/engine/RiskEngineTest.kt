package com.gastrocare.compass.domain.engine

import com.gastrocare.compass.data.FoodRepository
import com.gastrocare.compass.data.SeedFoods
import com.gastrocare.compass.domain.model.Diagnosis
import com.gastrocare.compass.domain.model.RiskLevel
import com.gastrocare.compass.domain.model.TriggerTag
import com.gastrocare.compass.domain.model.UserProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Проверки «умного» движка риска: запреты, порядок оценок, влияние контекста. */
class RiskEngineTest {

    private val repo = FoodRepository(SeedFoods.items)
    private val engine = RiskEngine(repo)

    private val gerdProfile = UserProfile(
        sex = com.gastrocare.compass.domain.model.Sex.MALE,
        age = 40,
        heightCm = 178,
        weightKg = 86.0,
        diagnoses = setOf(Diagnosis.GERD)
    )

    private fun food(id: String) = repo.byId(id) ?: error("нет продукта $id")

    @Test
    fun `алкоголь при ГЭРБ — полный запрет`() {
        val beer = food("beer")
        val assessment = engine.assess(beer, 500.0, gerdProfile)

        assertTrue("должен сработать жёсткий запрет", assessment.hardBan)
        assertEquals(RiskLevel.AVOID, assessment.level)
        assertTrue(assessment.score >= 95)
        assertNotNull(assessment.hardBanReason)
    }

    @Test
    fun `глютен при целиакии — полный запрет`() {
        val bread = food("bread-white")
        val celiac = UserProfile(diagnoses = setOf(Diagnosis.CELIAC))
        val assessment = engine.assess(bread, 100.0, celiac)

        assertTrue(assessment.hardBan)
        assertEquals(RiskLevel.AVOID, assessment.level)
    }

    @Test
    fun `варёный картофель безопасен, жареный — нет`() {
        val boiled = engine.assess(food("potato-boiled"), 200.0, gerdProfile)
        val fried = engine.assess(food("potato-fried"), 200.0, gerdProfile)

        assertEquals(RiskLevel.SAFE, boiled.level)
        assertTrue("жареный картофель должен быть рискованнее", fried.score > boiled.score + 20)
        assertFalse(fried.reasons.isEmpty())
    }

    @Test
    fun `поздний приём повышает риск`() {
        val coffee = food("coffee-espresso")
        val day = RiskContext(hourOfDay = 13, sleepHour = 23, hoursSinceLastMeal = 1.0)
        val lateNight = RiskContext(hourOfDay = 22, sleepHour = 23, hoursSinceLastMeal = 6.0)

        val dayScore = engine.assess(coffee, 60.0, gerdProfile, day).score
        val nightScore = engine.assess(coffee, 60.0, gerdProfile, lateNight).score

        assertTrue("вечером риск должен быть выше: $nightScore против $dayScore", nightScore > dayScore)
        val lateAssessment = engine.assess(coffee, 60.0, gerdProfile, lateNight)
        assertTrue(lateAssessment.contextWarnings.any { it.contains("сна", ignoreCase = true) || it.contains("натощак", ignoreCase = true) })
    }

    @Test
    fun `большой объём порции добавляет предупреждение`() {
        val rice = food("rice-white")
        val small = engine.assess(rice, 150.0, gerdProfile)
        val large = engine.assess(rice, 600.0, gerdProfile)

        assertTrue("большая порция должна быть рискованнее", large.score > small.score)
        assertTrue(large.reasons.any { it.tag == TriggerTag.VOLUME })
    }

    @Test
    fun `личный триггер усиливает риск`() {
        val withoutTrigger = engine.assess(food("orange"), 150.0, gerdProfile)
        val withTrigger = engine.assess(
            food("orange"),
            150.0,
            gerdProfile.copy(personalTriggers = setOf(TriggerTag.CITRUS))
        )

        assertTrue(withTrigger.score > withoutTrigger.score)
    }

    @Test
    fun `оценка объясняет причины и предлагает замены`() {
        val assessment = engine.assess(food("sausage-boiled"), 100.0, gerdProfile)

        assertTrue(assessment.reasons.isNotEmpty())
        assertTrue(assessment.reasons.first().explanation.length > 20)
        assertTrue("должны быть безопасные замены", assessment.substitutes.isNotEmpty())
        assertTrue(assessment.substitutes.all { it.id != "sausage-boiled" })
    }

    @Test
    fun `рекомендация по порции ограничивает жирные продукты`() {
        val cheese = engine.assess(food("cheese-hard"), 100.0, gerdProfile)
        val rice = engine.assess(food("rice-white"), 100.0, gerdProfile)

        assertTrue(
            "жирный сыр должен иметь меньший безопасный объём, чем рис",
            cheese.portionAdvice.recommendedMaxGrams < rice.portionAdvice.recommendedMaxGrams
        )
        assertTrue(cheese.portionAdvice.note.isNotBlank())
    }

    @Test
    fun `для СРК FODMAP опаснее чем для профилактики`() {
        val beans = food("beans-red")
        val ibs = UserProfile(diagnoses = setOf(Diagnosis.IBS))
        val prevention = UserProfile(diagnoses = setOf(Diagnosis.NO_DIAGNOSIS))

        val ibsScore = engine.assess(beans, 150.0, ibs).score
        val preventionScore = engine.assess(beans, 150.0, prevention).score

        assertTrue("при СРК бобовые должны быть рискованнее: $ibsScore против $preventionScore", ibsScore > preventionScore)
    }

    @Test
    fun `накопленный жир за день усиливает предупреждения`() {
        val food = food("pizza-margherita")
        val clean = RiskContext(hourOfDay = 13, fatSoFarToday = 5.0, fatCapToday = 60.0)
        val fatty = RiskContext(hourOfDay = 13, fatSoFarToday = 70.0, fatCapToday = 60.0)

        val cleanScore = engine.assess(food, 250.0, gerdProfile, clean).score
        val fattyScore = engine.assess(food, 250.0, gerdProfile, fatty).score

        assertTrue(fattyScore >= cleanScore)
        val assessment = engine.assess(food, 250.0, gerdProfile, fatty)
        assertTrue(assessment.contextWarnings.any { it.contains("жир", ignoreCase = true) })
    }

    @Test
    fun `оценка приёма пищи учитывает суммарный объём`() {
        val items = listOf(
            food("borscht") to 300.0,
            food("bread-white") to 100.0,
            food("potato-mashed") to 200.0
        )
        val context = RiskContext(hourOfDay = 19, sleepHour = 23)

        val meal = engine.assessMeal(
            slot = com.gastrocare.compass.domain.model.MealSlot.DINNER,
            items = items,
            profile = gerdProfile,
            context = context
        )

        assertTrue(meal.totalGrams > 400)
        assertNotNull(meal.volumeWarning)
    }

    @Test
    fun `шоколад и мята получают высокий риск при ГЭРБ`() {
        val chocolate = engine.assess(food("chocolate-milk"), 50.0, gerdProfile)
        val mintTea = engine.assess(food("tea-mint"), 200.0, gerdProfile)

        assertTrue(chocolate.level == RiskLevel.RISKY || chocolate.level == RiskLevel.AVOID)
        assertTrue(mintTea.score > 25)
    }

    @Test
    fun `газировка оценивается выше обычной воды`() {
        val sparkling = engine.assess(food("mineral-sparkling"), 330.0, gerdProfile)
        val still = engine.assess(food("mineral-still"), 330.0, gerdProfile)

        assertTrue(sparkling.score > still.score + 20)
        assertEquals(RiskLevel.SAFE, still.level)
    }

    @Test
    fun `щадящие продукты получают положительные отметки`() {
        val assessment = engine.assess(food("oatmeal-water"), 250.0, gerdProfile)

        assertEquals(RiskLevel.SAFE, assessment.level)
        assertTrue("должны быть перечислены плюсы продукта", assessment.benefits.isNotEmpty())
    }
}
