package com.gastrocare.compass.domain.model

/** На что приведены значения на этикетке. */
enum class LabelBasis(val title: String, val short: String) {
    PER_100("На 100 г / 100 мл", "на 100 г"),
    PER_PORTION("На одну порцию", "на порцию"),
    PER_PACKAGE("На всю упаковку", "на упаковку"),
    UNKNOWN("Не указано", "не указано")
}

/**
 * Способы получить КБЖУ с этикетки — от самого простого к самому «умному».
 * [implemented] показывает, что уже работает в этой сборке.
 */
enum class LabelInputMethod(
    val title: String,
    val description: String,
    val accuracy: String,
    val implemented: Boolean
) {
    MANUAL_TABLE(
        title = "1. Ручной ввод по таблице",
        description = "Четыре поля для белков, жиров, углеводов и калорий плюс переключатель «на 100 г / на порцию». " +
            "Приложение само проверит цифры по формуле 4/9/4 и пересчитает на 100 г.",
        accuracy = "Точность 100% — данные вводит человек",
        implemented = true
    ),
    TEXT_PARSE(
        title = "2. Вставка текста этикетки",
        description = "Скопируйте состав и таблицу пищевой ценности (с сайта магазина, из PDF или с экрана) — " +
            "парсер сам найдёт белки, жиры, углеводы, ккал и кДж, поймёт «на 100 г» или «на порцию (30 г)», " +
            "распознает «следы», «в т.ч. насыщенные» и «сахара», переведёт кДж в ккал.",
        accuracy = "Высокая, если текст этикетки читаемый",
        implemented = true
    ),
    PHOTO_OCR(
        title = "3. Фото этикетки (OCR on-device)",
        description = "Съёмка таблицы пищевой ценности, распознавание текста прямо на телефоне " +
            "(ML Kit Text Recognition v2, работает без интернета), автоповорот и кадрирование области таблицы, " +
            "передача текста в тот же парсер, что и в способе 2.",
        accuracy = "90–97% на чётких фото, требует хорошего света",
        implemented = false
    ),
    BARCODE(
        title = "4. Штрихкод",
        description = "Сканирование EAN-13/UPC камерой, поиск в локальной базе (Open Food Facts, дамп ~3 млн позиций) " +
            "и подстановка КБЖУ. Если товара нет — предлагается сфотографировать этикетку и сохранить продукт локально.",
        accuracy = "Зависит от базы; для российских марок покрытие неполное",
        implemented = false
    ),
    VOICE(
        title = "5. Голосовой ввод",
        description = "«Полтора стакана кефира два с половиной процента» — распознавание речи, разбор количества " +
            "и единиц (стакан, ложка, кусок, горсть), перевод в граммы и подстановка КБЖУ из справочника.",
        accuracy = "Средняя: единицы измерения требуют подтверждения",
        implemented = false
    ),
    RECIPE(
        title = "6. Калькулятор блюда и этикетки «сухой/готовый»",
        description = "Складываете рецепт из ингредиентов, задаёте вес готового блюда — приложение считает КБЖУ " +
            "на 100 г готового продукта с учётом ужарки и уварки (крупы +150%, мясо −30% массы). " +
            "Это же решает проблему этикеток «на 100 г сухого продукта».",
        accuracy = "Высокая при честном взвешивании",
        implemented = true
    ),
    PACKAGE_CALC(
        title = "7. Пересчёт «упаковка → порция»",
        description = "Этикетка дана на 100 г, а вы съели половину пачки 180 г — приложение считает по массе нетто " +
            "и по доле упаковки. Отдельно решается случай «на 100 мл» для напитков (перевод мл↔г по плотности).",
        accuracy = "Высокая",
        implemented = true
    ),
    SMART_RISK(
        title = "8. Этикетка → сразу риск для ЖКТ",
        description = "После распознавания КБЖУ приложение анализирует состав: кислотные регуляторы (E330, E260, E338), " +
            "кофеин, какао, мяту, томаты, лук, лактозу, глютен, газирование — и сразу показывает риск для диагноза, " +
            "а не только калории.",
        accuracy = "Экспертная эвристика, не заменяет врача",
        implemented = true
    );

    companion object {
        val available: List<LabelInputMethod> get() = entries.filter { it.implemented }
        val planned: List<LabelInputMethod> get() = entries.filter { !it.implemented }
    }
}

/** Замечание к распознанным данным этикетки. */
data class LabelIssue(
    val severity: Severity,
    val title: String,
    val detail: String
)

/**
 * Результат разбора этикетки.
 *
 * @param per100 пищевая ценность, приведённая к 100 г — это то, что хранится в справочнике
 * @param asPrinted значения ровно так, как напечатано на этикетке
 * @param basisAmountGrams основа, к которой приведены значения (100, 30, 250 …)
 */
data class ParsedLabel(
    val success: Boolean,
    val rawText: String,
    val basis: LabelBasis,
    val basisAmountGrams: Double?,
    val servingSizeG: Double?,
    val packageSizeG: Double?,
    val asPrinted: Nutrition?,
    val per100: Nutrition?,
    val declaredKcal: Double?,
    val declaredKj: Double?,
    val detectedFields: List<String>,
    val issues: List<LabelIssue>,
    val confidence: Int,
    /** Факторы риска, выведенные из состава продукта. */
    val detectedTriggers: List<TriggerFactor> = emptyList(),
    val notes: List<String> = emptyList()
) {
    val hasErrors: Boolean get() = issues.any { it.severity == Severity.ERROR }
}

/** Итог расчёта блюда по рецепту. */
data class RecipeResult(
    val totalNutrition: Nutrition,
    val rawWeightG: Double,
    val cookedWeightG: Double,
    val yieldFactor: Double,
    val per100Cooked: Nutrition,
    val portionCount: Int,
    val notes: List<String>
) {
    fun perPortion(): Nutrition = per100Cooked.scaled(cookedWeightG / portionCount)
}

/** Ингредиент рецепта. */
data class RecipeIngredient(
    val food: FoodItem,
    val grams: Double
)
