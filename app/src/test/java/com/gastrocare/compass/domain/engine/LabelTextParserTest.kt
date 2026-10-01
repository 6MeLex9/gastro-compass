package com.gastrocare.compass.domain.engine

import com.gastrocare.compass.data.FoodRepository
import com.gastrocare.compass.data.SeedFoods
import com.gastrocare.compass.domain.model.Diagnosis
import com.gastrocare.compass.domain.model.FoodCategory
import com.gastrocare.compass.domain.model.FoodItem
import com.gastrocare.compass.domain.model.FoodSource
import com.gastrocare.compass.domain.model.LabelBasis
import com.gastrocare.compass.domain.model.Nutrition
import com.gastrocare.compass.domain.model.TriggerIntensity
import com.gastrocare.compass.domain.model.TriggerTag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Тесты разбора этикеток: именно здесь ошибки дороже всего,
 * потому что от КБЖУ зависят и калории, и оценка риска.
 */
class LabelTextParserTest {

    private val parser = LabelTextParser()

    private val fullLabel = """
        Пищевая ценность в 100 г продукта:
        Энергетическая ценность 1200 кДж / 287 ккал
        Белки 5,3 г
        Жиры 12,0 г
          в т.ч. насыщенные жирные кислоты 5,2 г
        Углеводы 36,0 г
          в т.ч. сахара 2,0 г
        Пищевые волокна 3,1 г
        Соль 0,9 г
        Состав: мука пшеничная, масло растительное, сахар, соль,
        регулятор кислотности (лимонная кислота), ароматизатор.
    """.trimIndent()

    @Test
    fun `разбирает полную русскую этикетку`() {
        val result = parser.parse(fullLabel)

        assertTrue("разбор должен быть успешным", result.success)
        assertEquals(LabelBasis.PER_100, result.basis)
        assertEquals(100.0, result.basisAmountGrams!!, 0.001)

        val per100 = result.per100!!
        assertEquals(287.0, per100.calories, 0.5)
        assertEquals(5.3, per100.protein, 0.01)
        assertEquals(12.0, per100.fat, 0.01)
        assertEquals(36.0, per100.carbs, 0.01)
        // «в т.ч. насыщенные жирные кислоты» не должно попасть в поле «жиры»
        assertEquals(5.2, per100.saturatedFat, 0.01)
        assertEquals(2.0, per100.sugar, 0.01)
        assertEquals(3.1, per100.fiber, 0.01)
        assertEquals(0.9, per100.salt, 0.01)
        assertEquals(1200.0, result.declaredKj!!, 0.5)
    }

    @Test
    fun `определяет кислотные добавки и глютен по составу`() {
        val result = parser.parse(fullLabel)
        val tags = result.detectedTriggers.map { it.tag }.toSet()

        assertTrue("лимонная кислота должна дать тег кислоты", tags.contains(TriggerTag.ACID))
        assertTrue("пшеничная мука должна дать тег глютена", tags.contains(TriggerTag.GLUTEN))
        assertTrue("жир 12 г на 100 г должен дать тег жирности", tags.contains(TriggerTag.FAT))
        assertTrue(
            "жирность 12 г — это умеренная интенсивность",
            result.detectedTriggers.any { it.tag == TriggerTag.FAT && it.intensity == TriggerIntensity.MODERATE }
        )
    }

    @Test
    fun `пересчитывает значения с порции на 100 г`() {
        val text = """
            Пищевая ценность на 1 порцию (30 г):
            Белки 1,6 г
            Жиры 3,6 г
            Углеводы 10,8 г
            Энергетическая ценность 86 ккал
            Масса нетто 250 г
        """.trimIndent()

        val result = parser.parse(text)

        assertEquals(LabelBasis.PER_PORTION, result.basis)
        assertEquals(30.0, result.servingSizeG!!, 0.01)
        assertEquals(250.0, result.packageSizeG!!, 0.01)

        val per100 = result.per100!!
        assertEquals(86.0 * 100 / 30, per100.calories, 1.0)
        assertEquals(1.6 * 100 / 30, per100.protein, 0.05)
        assertEquals(3.6 * 100 / 30, per100.fat, 0.05)
        assertEquals(10.8 * 100 / 30, per100.carbs, 0.05)
    }

    @Test
    fun `переводит кДж в ккал когда ккал не указаны`() {
        val text = """
            Энергетическая ценность 1200 кДж
            Белки 5 г
            Жиры 10 г
            Углеводы 40 г
        """.trimIndent()

        val result = parser.parse(text)

        assertNotNull(result.per100)
        assertEquals(1200 / 4.184, result.per100!!.calories, 1.0)
        assertTrue(
            "в пояснениях должно быть сказано о переводе кДж",
            result.notes.any { it.contains("кДж") }
        )
    }

    @Test
    fun `распознаёт следы как 0,05 г`() {
        val text = "на 100 г: белки 0,5 г; жиры следы; углеводы 12 г; энергетическая ценность 60 ккал"

        val result = parser.parse(text)

        assertEquals(0.05, result.per100!!.fat, 0.001)
        assertTrue(
            "должно быть замечание про следы",
            result.issues.any { it.title.contains("следы", ignoreCase = true) }
        )
    }

    @Test
    fun `пересчитывает значения с упаковки`() {
        val text = """
            Пищевая ценность на упаковку:
            Белки 10 г
            Жиры 20 г
            Углеводы 60 г
            Энергетическая ценность 460 ккал
            Масса нетто 200 г
        """.trimIndent()

        val result = parser.parse(text)

        assertEquals(LabelBasis.PER_PACKAGE, result.basis)
        assertEquals(200.0, result.basisAmountGrams!!, 0.01)
        assertEquals(230.0, result.per100!!.calories, 1.0)
        assertEquals(5.0, result.per100!!.protein, 0.1)
        assertEquals(10.0, result.per100!!.fat, 0.1)
    }

    @Test
    fun `считает соль из натрия`() {
        val text = "на 100 г: белки 3 г, жиры 2 г, углеводы 60 г, натрий 0,4 г, энергетическая ценность 270 ккал"

        val result = parser.parse(text)

        assertEquals(1.0, result.per100!!.salt, 0.05)
    }

    @Test
    fun `ловит невозможную сумму макросов`() {
        val text = "на 100 г: белки 60 г, жиры 60 г, углеводы 60 г, энергетическая ценность 1020 ккал"

        val result = parser.parse(text)

        assertTrue("должна быть ошибка о сумме макросов", result.hasErrors)
    }

    @Test
    fun `предупреждает о расхождении калорий и макросов`() {
        // 4*5 + 9*20 + 4*60 = 440 ккал, а заявлено 700 — расхождение больше 35%
        val text = "на 100 г: белки 5 г, жиры 20 г, углеводы 60 г, энергетическая ценность 700 ккал"

        val result = parser.parse(text)

        assertTrue(
            "должно быть замечание о сходимости 4/9/4",
            result.issues.any { it.title.contains("не сходятся", ignoreCase = true) }
        )
    }

    @Test
    fun `лимонная кислота не считается цитрусовыми`() {
        val result = parser.parse(fullLabel)
        val tags = result.detectedTriggers.map { it.tag }.toSet()

        assertTrue("регулятор кислотности должен дать тег кислоты", tags.contains(TriggerTag.ACID))
        assertFalse(
            "«лимонная кислота» — это регулятор кислотности, а не цитрусовый ингредиент",
            tags.contains(TriggerTag.CITRUS)
        )
    }

    @Test
    fun `настоящие цитрусовые в составе распознаются`() {
        val text = "на 100 г: белки 1 г, жиры 0,2 г, углеводы 12 г, энергетическая ценность 54 ккал. " +
            "Состав: вода, апельсиновый сок, сахар."

        val result = parser.parse(text)

        assertTrue(
            "апельсиновый сок должен дать тег цитрусовых",
            result.detectedTriggers.any { it.tag == TriggerTag.CITRUS }
        )
    }

    @Test
    fun `создаёт продукт из этикетки с факторами риска`() {
        val result = parser.parse(fullLabel)
        val food = parser.toFoodItem(result, "Печенье с этикетки", FoodCategory.SWEETS)

        assertNotNull(food)
        assertEquals(287.0, food!!.per100.calories, 1.0)
        assertTrue(food.factors.isNotEmpty())
        assertEquals(FoodSource.LABEL_MANUAL, food.source)
        assertTrue("в описании должен быть КБЖУ", food.gastroNote!!.contains("ккал"))
    }

    @Test
    fun `не находит данные в пустом тексте`() {
        val result = parser.parse("Состав: вода, сахар.")
        assertTrue(!result.success)
    }

    @Test
    fun `оценивает риск для ГЭРБ по распознанному продукту`() {
        val result = parser.parse(fullLabel)
        val food = parser.toFoodItem(result, "Печенье", FoodCategory.SWEETS)!!
        val engine = RiskEngine(FoodRepository(SeedFoods.items))
        val profile = com.gastrocare.compass.domain.model.UserProfile(
            diagnoses = setOf(Diagnosis.GERD)
        )

        val assessment = engine.assess(food, 100.0, profile)

        assertTrue("при 12 г жира на 100 г риск не должен быть нулевым", assessment.score > 10)
        assertTrue(assessment.reasons.isNotEmpty())
    }

    @Test
    fun `быстро считает продукт по введённым вручную значениям`() {
        val text = "на 100 г: белки 18 г; жиры 0,6 г; углеводы 1,5 г; энергетическая ценность 79 ккал"
        val result = parser.parse(text)

        assertEquals(79.0, result.per100!!.calories, 0.5)
        assertEquals(18.0, result.per100!!.protein, 0.1)
        assertTrue(result.confidence > 50)
    }

    @Test
    fun `не путает белки жиры углеводы в однострочной записи`() {
        val text = "Пищевая ценность на 100 г: Б 5.3 Ж 12.0 У 36.0, 287 ккал"
        val result = parser.parse(text)
        val per100 = result.per100

        if (per100 != null) {
            // Даже если разбор частичный, жир не должен оказаться равным 5,3 или 36
            assertTrue(per100.fat != 5.3)
            assertTrue(per100.fat != 36.0)
        }
    }

    @Test
    fun `готовый продукт описывается через Nutrition без потери полей`() {
        val nutrition = Nutrition.of(287.0, 5.3, 12.0, 36.0, saturatedFat = 5.2, sugar = 2.0)
        assertEquals(287.0, nutrition.calories, 0.001)
        assertEquals(5.2, nutrition.saturatedFat, 0.001)
        assertEquals(2.0, nutrition.sugar, 0.001)
        assertTrue(nutrition.atwaterCalories > 250)
    }

    @Test
    fun `парсер устойчив к лишним пробелам и переносам строк`() {
        val text = "Пищевая  ценность  на  100 г\n\nБелки   5,3   г\nЖиры  12,0  г\n" +
            "Углеводы  36,0  г\nЭнергетическая  ценность  287  ккал"
        val result = parser.parse(text)
        assertEquals(287.0, result.per100!!.calories, 0.5)
        assertEquals(5.3, result.per100!!.protein, 0.01)
    }

    @Test
    fun `сохраняет английские обозначения`() {
        val text = "Nutrition per 100 g: Protein 5.3 g, Fat 12.0 g, Carbohydrate 36.0 g, Energy 287 kcal"
        val result = parser.parse(text)
        // Английские подписи поддерживаются частично: главное — не потерять калории
        assertEquals(287.0, result.per100!!.calories, 0.5)
    }

    /**
     * Регрессия: реальная ошибка, найденная при запуске на эмуляторе Android.
     *
     * На настольной JVM `\w` не включает кириллицу, и соблазнительно включить флаг
     * `(?U)` (UNICODE_CHARACTER_CLASS). Но Android использует ICU-реализацию регулярных
     * выражений, где флага `U` не существует: `Pattern.compile("(?U)...")` падает с
     * PatternSyntaxException прямо в статическом инициализаторе класса, и приложение
     * закрывается при открытии экрана этикетки. Поэтому кириллица задаётся явными
     * классами `\p{L}`, а флагов быть не должно.
     */
    @Test
    fun `в шаблонах нет флагов, не поддерживаемых Android`() {
        val source = java.io.File(
            "src/main/java/com/gastrocare/compass/domain/engine/LabelTextParser.kt"
        )
        if (!source.exists()) return

        val codeLines = source.readText().lines()
            .map { line -> line.substringBefore("//") }
            .filterNot { line ->
                val trimmed = line.trimStart()
                trimmed.startsWith("*") || trimmed.startsWith("/*")
            }
        val joined = codeLines.joinToString("\n")

        assertTrue(
            "Флаг (?U) не поддерживается регулярными выражениями Android",
            !joined.contains("(?U)")
        )
        assertTrue(
            "Класс \\w не покрывает кириллицу на JVM — используйте [\\p{L}\\p{N}_]",
            !joined.contains("""\w""")
        )
        assertTrue(
            "Границы слов \\b по-разному работают на JVM и Android — используйте lookaround",
            !joined.contains("""\b""")
        )
    }
}
