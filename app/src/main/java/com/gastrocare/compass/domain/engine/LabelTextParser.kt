package com.gastrocare.compass.domain.engine

import com.gastrocare.compass.domain.model.FoodCategory
import com.gastrocare.compass.domain.model.FoodItem
import com.gastrocare.compass.domain.model.FoodSource
import com.gastrocare.compass.domain.model.LabelBasis
import com.gastrocare.compass.domain.model.LabelIssue
import com.gastrocare.compass.domain.model.Nutrition
import com.gastrocare.compass.domain.model.ParsedLabel
import com.gastrocare.compass.domain.model.Severity
import com.gastrocare.compass.domain.model.TriggerFactor
import com.gastrocare.compass.domain.model.TriggerIntensity
import com.gastrocare.compass.domain.model.TriggerTag
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Разбор этикетки: превращает текст таблицы пищевой ценности в КБЖУ на 100 г,
 * проверяет цифры и сразу выводит факторы риска для ЖКТ.
 *
 * Поддерживает:
 *  * русские и частично английские обозначения («Белки», «Protein», «кДж», «kcal»);
 *  * основу «на 100 г / на 100 мл / на порцию (30 г) / на упаковку»;
 *  * «в т.ч. насыщенные жирные кислоты», «в т.ч. сахара», «пищевые волокна», «соль», «натрий»;
 *  * «следы», «менее 0,1», «<0,1»;
 *  * перевод кДж → ккал и проверку сходимости по формуле 4/9/4;
 *  * поиск в составе кислотных регуляторов, кофеина, какао, мяты, лактозы, глютена и газа.
 */
class LabelTextParser {

    // ---------------------------------------------------------------- публичный API

    /**
     * @param servingSizeG известный размер порции (если пользователь его указал)
     * @param packageSizeG известная масса нетто упаковки
     */
    fun parse(
        rawText: String,
        servingSizeG: Double? = null,
        packageSizeG: Double? = null
    ): ParsedLabel {
        val normalized = normalize(rawText)
        val basisInfo = detectBasis(normalized)
        val serving = servingSizeG ?: basisInfo.servingSize
        val packageSize = packageSizeG ?: basisInfo.packageSize

        val issues = mutableListOf<LabelIssue>()
        val notes = mutableListOf<String>()

        // Работаем по фрагменту с таблицей, чтобы не путать цифры из состава и Е-номеров с КБЖУ
        val segment = tableSegment(normalized)
        val masked = maskPhrases(segment)
        val consumed = mutableListOf<IntRange>()

        val energy = extractEnergy(masked, consumed)
        val protein = extractField(masked, consumed, PAT_PROTEIN)
        val fat = extractField(masked, consumed, PAT_FAT)
        val carbs = extractField(masked, consumed, PAT_CARBS)
        val satFat = extractField(masked, consumed, PAT_SAT_FAT)
        val sugar = extractField(masked, consumed, PAT_SUGAR)
        val fiber = extractField(masked, consumed, PAT_FIBER)
        val saltDirect = extractField(masked, consumed, PAT_SALT)
        val sodium = extractField(masked, consumed, PAT_SODIUM)
        val caffeine = extractField(masked, consumed, PAT_CAFFEINE)

        var kcal = energy.kcal
        val kj = energy.kj
        if (kcal == null && kj != null) {
            kcal = kj / 4.184
            notes += "На этикетке указана только энергия в кДж — перевели в ккал (${kcal.roundToInt()} ккал), " +
                "разделив на 4,184."
        }

        val detected = mutableListOf<String>()
        if (energy.kcal != null || kj != null) detected += "энергетическая ценность"
        if (protein.value != null) detected += "белки"
        if (fat.value != null) detected += "жиры"
        if (carbs.value != null) detected += "углеводы"
        if (satFat.value != null) detected += "насыщенные жиры"
        if (sugar.value != null) detected += "сахара"
        if (fiber.value != null) detected += "пищевые волокна"
        if (saltDirect.value != null || sodium.value != null) detected += "соль"
        if (caffeine.value != null) detected += "кофеин"

        // Соль из натрия: 1 г натрия ≈ 2,5 г соли
        val salt = saltDirect.value ?: sodium.value?.let {
            notes += "Соль рассчитана из натрия: ${fmt(it)} г Na × 2,5 = ${fmt(it * 2.5)} г соли."
            it * 2.5
        }

        val traces = mutableListOf<String>()
        listOf(
            "белки" to protein, "жиры" to fat, "углеводы" to carbs,
            "насыщенные жиры" to satFat, "сахара" to sugar
        ).forEach { (name, ex) -> if (ex.trace) traces += name }
        if (traces.isNotEmpty()) {
            issues += LabelIssue(
                Severity.INFO,
                "Обнаружены «следы»",
                "Для полей (${traces.joinToString(", ")}) указано «следы» или «менее …». " +
                    "Принято значение 0,05 г на 100 г — на итог влияет незначительно."
            )
        }

        val asPrinted = if (protein.value != null || fat.value != null || carbs.value != null || kcal != null) {
            Nutrition.of(
                calories = kcal ?: 0.0,
                protein = protein.value ?: 0.0,
                fat = fat.value ?: 0.0,
                carbs = carbs.value ?: 0.0,
                saturatedFat = satFat.value,
                sugar = sugar.value,
                fiber = fiber.value,
                salt = salt,
                caffeineMg = caffeine.value
            )
        } else null

        // ---- основа и пересчёт на 100 г
        var basisAmount: Double? = when (basisInfo.basis) {
            LabelBasis.PER_100 -> 100.0
            LabelBasis.PER_PORTION -> serving
            LabelBasis.PER_PACKAGE -> packageSize
            LabelBasis.UNKNOWN -> null
        }

        if (basisInfo.basis == LabelBasis.UNKNOWN) {
            issues += LabelIssue(
                Severity.WARNING,
                "Не указано, на что приведены значения",
                "В тексте нет пометки «на 100 г», «на порцию» или «на упаковку». " +
                    "Укажите это вручную — иначе КБЖУ на 100 г может отличаться в разы."
            )
        }
        if (basisInfo.basis == LabelBasis.PER_PORTION && serving == null) {
            issues += LabelIssue(
                Severity.WARNING,
                "Не найден вес порции",
                "На этикетке значения даны на порцию, но её масса не указана. Введите вес порции в граммах — " +
                    "иначе пересчёт на 100 г невозможен."
            )
        }

        var per100: Nutrition? = null
        if (asPrinted != null && basisAmount != null && basisAmount > 0) {
            val k = 100.0 / basisAmount
            per100 = Nutrition(
                calories = asPrinted.calories * k,
                protein = asPrinted.protein * k,
                fat = asPrinted.fat * k,
                carbs = asPrinted.carbs * k,
                saturatedFat = asPrinted.saturatedFat * k,
                sugar = asPrinted.sugar * k,
                fiber = asPrinted.fiber * k,
                salt = asPrinted.salt * k,
                caffeineMg = asPrinted.caffeineMg * k
            )
            if (basisAmount != 100.0) {
                notes += "Значения пересчитаны с ${fmt(basisAmount)} г на 100 г (коэффициент ${fmt(k)})."
            }
        } else if (asPrinted != null) {
            // Не смогли пересчитать — оставляем как есть, но предупреждаем
            per100 = asPrinted
            basisAmount = 100.0
            issues += LabelIssue(
                Severity.WARNING,
                "Пересчёт не выполнен",
                "Значения сохранены «как напечатано». Уточните основу, чтобы КБЖУ на 100 г было корректным."
            )
        }

        // ---- проверки корректности
        val printedKcal = energy.kcal
        val declaredKjKcalDeviation = if (printedKcal != null && kj != null && printedKcal > 0) {
            abs(printedKcal - kj / 4.184) / printedKcal * 100.0
        } else null
        if (declaredKjKcalDeviation != null && declaredKjKcalDeviation > 8.0) {
            issues += LabelIssue(
                Severity.WARNING,
                "кДж и ккал не совпадают",
                "По кДж получается ${((kj ?: 0.0) / 4.184).roundToInt()} ккал, а на этикетке указано " +
                    "${printedKcal?.roundToInt() ?: 0} ккал (расхождение ${declaredKjKcalDeviation.roundToInt()}%). " +
                    "Проверьте цифры."
            )
        }

        if (per100 != null) {
            issues += validatePer100(per100, detected)
        }

        // Эвристика: значения похожи на «на порцию», хотя заявлены на 100 г
        if (per100 != null && basisInfo.basis == LabelBasis.PER_100 && per100.calories > 0) {
            val atwater = per100.atwaterCalories
            if (atwater > 0 && per100.calories < atwater * 0.55) {
                issues += LabelIssue(
                    Severity.WARNING,
                    "Похоже, это значения на порцию, а не на 100 г",
                    "Заявленная калорийность (${per100.calories.roundToInt()} ккал) значительно ниже расчёта " +
                        "по макросам (${atwater.roundToInt()} ккал). Проверьте основу пересчёта."
                )
            }
        }

        val triggers = detectTriggers(normalized, per100)

        val confidence = computeConfidence(detected, basisInfo.basis, per100, issues)
        val success = per100 != null && detected.count { it in MACRO_NAMES } >= 2

        return ParsedLabel(
            success = success,
            rawText = rawText,
            basis = basisInfo.basis,
            basisAmountGrams = basisAmount,
            servingSizeG = serving,
            packageSizeG = packageSize,
            asPrinted = asPrinted,
            per100 = per100,
            declaredKcal = energy.kcal,
            declaredKj = kj,
            detectedFields = detected,
            issues = issues,
            confidence = confidence,
            detectedTriggers = triggers,
            notes = notes
        )
    }

    /**
     * Создаёт продукт для справочника из разобранной этикетки.
     * Продукт сразу участвует в оценке риска, как обычный продукт из базы.
     */
    fun toFoodItem(
        parsed: ParsedLabel,
        name: String,
        category: FoodCategory = FoodCategory.OTHER,
        isDrink: Boolean = false,
        densityGPerMl: Double = 1.0,
        typicalPortionG: Int = if (isDrink) 250 else 100
    ): FoodItem? {
        val per100 = parsed.per100 ?: return null
        return FoodItem(
            id = "label-" + name.lowercase().replace(" ", "-").take(40) + "-" + System.currentTimeMillis(),
            name = name.ifBlank { "Продукт с этикетки" },
            category = category,
            per100 = per100,
            typicalPortionG = typicalPortionG,
            factors = parsed.detectedTriggers,
            isDrink = isDrink,
            densityGPerMl = densityGPerMl,
            gastroNote = buildGastroNote(parsed),
            source = FoodSource.LABEL_MANUAL
        )
    }

    // ---------------------------------------------------------------- извлечение полей

    private data class Extraction(val value: Double?, val trace: Boolean = false, val found: Boolean = false)

    private data class Energy(val kcal: Double?, val kj: Double?)

    private data class BasisInfo(
        val basis: LabelBasis,
        val servingSize: Double?,
        val packageSize: Double?
    )

    private fun extractEnergy(text: String, consumed: MutableList<IntRange>): Energy {
        var kcal: Double? = null
        var kj: Double? = null

        PAT_KCAL.find(text)?.let {
            kcal = it.groupValues[1].toNumberOrNull()
            consumed += it.range
        }
        PAT_KJ.find(text)?.let {
            kj = it.groupValues[1].toNumberOrNull()
            consumed += it.range
        }

        // «Энергетическая ценность 287» без единицы измерения
        if (kcal == null && kj == null) {
            PAT_ENERGY_BARE.find(text)?.let {
                kcal = it.groupValues[1].toNumberOrNull()
                consumed += it.range
            }
        }
        // «Энергетическая ценность, ккал 287» — число после единицы
        if (kcal == null) {
            PAT_KCAL_AFTER.find(text)?.let {
                kcal = it.groupValues[1].toNumberOrNull()
                consumed += it.range
            }
        }
        return Energy(kcal, kj)
    }

    private fun extractField(
        text: String,
        consumed: MutableList<IntRange>,
        pattern: Regex
    ): Extraction {
        val match = pattern.find(text) ?: return Extraction(null)
        val after = match.range.last + 1

        // Границы поиска значения: следующий ключ таблицы либо второе окончание строки
        val nextKeyword = PAT_ANY_KEYWORD.find(text, after)?.range?.first
        val limit = minOf(
            nextKeyword ?: text.length,
            nthNewline(text, after, 2) ?: text.length,
            after + 90,
            text.length
        )
        if (limit <= after) return Extraction(null, found = true)

        val slice = text.substring(after, limit)
        if (PAT_TRACE.containsMatchIn(slice)) {
            return Extraction(0.05, trace = true, found = true)
        }
        for (m in PAT_NUMBER.findAll(slice)) {
            val abs = after + m.range.first
            if (consumed.any { abs in it }) continue
            val value = m.value.toNumberOrNull() ?: continue
            consumed += abs until (abs + m.value.length)
            return Extraction(value, found = true)
        }
        return Extraction(null, found = true)
    }

    // ---------------------------------------------------------------- основа этикетки

    private fun detectBasis(text: String): BasisInfo {
        var serving: Double? = null
        var packageSize: Double? = null

        PAT_SERVING.find(text)?.let { serving = it.groupValues[1].toNumberOrNull() }
        if (serving == null) {
            PAT_SERVING_AFTER.find(text)?.let { serving = it.groupValues[1].toNumberOrNull() }
        }
        PAT_NETTO.find(text)?.let {
            val value = it.groupValues[1].toNumberOrNull()
            val unit = it.groupValues[2]
            packageSize = when (unit) {
                "кг", "kg" -> value?.times(1000.0)
                "л", "l" -> value?.times(1000.0)
                else -> value
            }
        }

        val basis = when {
            PAT_PER_PACKAGE.containsMatchIn(text) -> LabelBasis.PER_PACKAGE
            PAT_PER_PORTION.containsMatchIn(text) -> LabelBasis.PER_PORTION
            PAT_PER_100.containsMatchIn(text) -> LabelBasis.PER_100
            else -> LabelBasis.UNKNOWN
        }
        return BasisInfo(basis, serving, packageSize)
    }

    private fun tableSegment(text: String): String {
        val start = PAT_TABLE_START.find(text)?.range?.first ?: return text
        val end = PAT_TABLE_END.find(text, start)?.range?.first ?: text.length
        return if (end > start) text.substring(start, end) else text.substring(start)
    }

    private fun ingredientsSegment(text: String): String {
        val start = PAT_INGREDIENTS.find(text)?.range?.first ?: return text
        return text.substring(start, minOf(text.length, start + 700))
    }

    // ---------------------------------------------------------------- нормализация и маскировка

    /**
     * Нормализация с сохранением длины строки: это важно, потому что позиции совпадений
     * используются для отслеживания уже «израсходованных» чисел.
     */
    private fun normalize(raw: String): String {
        val sb = StringBuilder(raw.length)
        for (ch in raw) {
            sb.append(
                when (ch) {
                    '\u00A0', '\u202F', '\u2009', '\t' -> ' '
                    ',' -> '.'
                    else -> ch
                }
            )
        }
        return sb.toString().lowercase()
    }

    /** Заменяет «мешающие» фразы на ▓, сохраняя длину: «жирные кислоты» не должно попадать в «жиры». */
    private fun maskPhrases(text: String): String {
        var result = text
        result = PAT_MONO_POLY.replace(result) { "▓".repeat(it.value.length) }
        result = PAT_FATTY_ACIDS.replace(result) { "▓".repeat(it.value.length) }
        result = PAT_BASIS_PHRASE.replace(result) { "▓".repeat(it.value.length) }
        return result
    }

    private fun nthNewline(text: String, from: Int, n: Int): Int? {
        var index = from
        var count = 0
        while (index < text.length) {
            if (text[index] == '\n' || text[index] == ';') {
                count++
                if (count >= n) return index
            }
            index++
        }
        return null
    }

    // ---------------------------------------------------------------- проверки

    private fun validatePer100(per100: Nutrition, detected: List<String>): List<LabelIssue> {
        val issues = mutableListOf<LabelIssue>()

        val massSum = per100.protein + per100.fat + per100.carbs
        if (massSum > 100.5) {
            issues += LabelIssue(
                Severity.ERROR,
                "Сумма макросов больше 100 г",
                "Белки + жиры + углеводы = ${fmt(massSum)} г на 100 г продукта. Такого быть не может — " +
                    "проверьте, не перепутаны ли значения и основа (на 100 г или на порцию)."
            )
        }
        if (per100.fat > 100.0 || per100.protein > 100.0 || per100.carbs > 100.0) {
            issues += LabelIssue(
                Severity.ERROR,
                "Невозможное значение",
                "Ни один макронутриент не может превышать 100 г на 100 г продукта."
            )
        }
        if (per100.calories > 900.0) {
            issues += LabelIssue(
                Severity.ERROR,
                "Слишком высокая калорийность",
                "Более 900 ккал на 100 г — это возможно только для чистого жира или масла. Проверьте ввод."
            )
        }
        if (per100.calories <= 0.0 && massSum > 0.0) {
            issues += LabelIssue(
                Severity.ERROR,
                "Не найдена калорийность",
                "Указаны макросы, но энергетическая ценность отсутствует или равна нулю."
            )
        }

        // Сходимость по формуле 4/9/4 (+2 ккал на клетчатку, как принято в маркировке ЕС)
        if (per100.calories > 0 && massSum > 0) {
            val simple = per100.protein * 4 + per100.fat * 9 + per100.carbs * 4
            val withFiber = simple + per100.fiber * 2
            val best = if (abs(withFiber - per100.calories) < abs(simple - per100.calories)) withFiber else simple
            val deviation = abs(best - per100.calories) / per100.calories * 100.0

            when {
                deviation > 35.0 -> issues += LabelIssue(
                    Severity.ERROR,
                    "Калории не сходятся с макросами",
                    "По формуле 4/9/4 получается ${best.roundToInt()} ккал, на этикетке — " +
                        "${per100.calories.roundToInt()} ккал (расхождение ${deviation.roundToInt()}%). " +
                        "Проверьте цифры и основу пересчёта."
                )

                deviation > 20.0 -> issues += LabelIssue(
                    Severity.WARNING,
                    "Заметное расхождение с формулой 4/9/4",
                    "Расчёт по макросам даёт ${best.roundToInt()} ккал против ${per100.calories.roundToInt()} ккал " +
                        "на этикетке (${deviation.roundToInt()}%). Возможно, часть углеводов — это полиолы " +
                        "или клетчатка, либо есть опечатка."
                )

                else -> issues += LabelIssue(
                    Severity.INFO,
                    "Цифры сходятся",
                    "Калорийность совпадает с расчётом по белкам, жирам и углеводам (расхождение " +
                        "${deviation.roundToInt()}%). Данные можно использовать."
                )
            }
        }

        if (!detected.contains("энергетическая ценность")) {
            issues += LabelIssue(
                Severity.WARNING,
                "Калорийность не распознана",
                "На этикетке не найдено значение энергии. Введите калории вручную или укажите кДж."
            )
        }
        return issues
    }

    private fun computeConfidence(
        detected: List<String>,
        basis: LabelBasis,
        per100: Nutrition?,
        issues: List<LabelIssue>
    ): Int {
        if (per100 == null) return 0
        var score = 30
        score += detected.count { it in MACRO_NAMES } * 12
        if (detected.contains("энергетическая ценность")) score += 16
        if (basis != LabelBasis.UNKNOWN) score += 14
        if (issues.any { it.severity == Severity.ERROR }) score -= 25
        if (issues.any { it.severity == Severity.WARNING }) score -= 10
        return score.coerceIn(0, 100)
    }

    // ---------------------------------------------------------------- состав → факторы риска

    /** Анализ состава и КБЖУ: что именно в этом продукте опасно при заболеваниях ЖКТ. */
    private fun detectTriggers(text: String, per100: Nutrition?): List<TriggerFactor> {
        val factors = mutableListOf<TriggerFactor>()
        val ingredients = ingredientsSegment(text)

        per100?.let { n ->
            when {
                n.fat >= 17.5 -> factors += TriggerFactor(
                    TriggerTag.FAT, TriggerIntensity.STRONG,
                    "Жира ${fmt(n.fat)} г на 100 г — очень высокая жирность. Такой продукт надолго задерживается в желудке."
                )

                n.fat >= 10.0 -> factors += TriggerFactor(
                    TriggerTag.FAT, TriggerIntensity.MODERATE,
                    "Жира ${fmt(n.fat)} г на 100 г — заметная жирность, следите за размером порции."
                )

                n.fat >= 5.0 -> factors += TriggerFactor(
                    TriggerTag.FAT, TriggerIntensity.MILD,
                    "Жира ${fmt(n.fat)} г на 100 г — умеренно, но при обострении лучше ограничить."
                )
            }
            if (n.saturatedFat >= 5.0) {
                factors += TriggerFactor(TriggerTag.FAT, TriggerIntensity.MODERATE, "Насыщенных жиров ${fmt(n.saturatedFat)} г на 100 г.")
            }
            when {
                n.salt >= 1.5 -> factors += TriggerFactor(TriggerTag.SALT, TriggerIntensity.STRONG, "Соли ${fmt(n.salt)} г на 100 г — очень много.")
                n.salt >= 0.7 -> factors += TriggerFactor(TriggerTag.SALT, TriggerIntensity.MILD, "Соли ${fmt(n.salt)} г на 100 г.")
            }
            when {
                n.sugar >= 20.0 -> factors += TriggerFactor(TriggerTag.SUGAR, TriggerIntensity.STRONG, "Сахаров ${fmt(n.sugar)} г на 100 г — провоцирует брожение и вздутие.")
                n.sugar >= 10.0 -> factors += TriggerFactor(TriggerTag.SUGAR, TriggerIntensity.MODERATE, "Сахаров ${fmt(n.sugar)} г на 100 г.")
                n.sugar >= 5.0 -> factors += TriggerFactor(TriggerTag.SUGAR, TriggerIntensity.MILD, "Сахаров ${fmt(n.sugar)} г на 100 г.")
            }
            if (n.caffeineMg >= 20.0) {
                factors += TriggerFactor(TriggerTag.CAFFEINE, TriggerIntensity.MODERATE, "Кофеина ${n.caffeineMg.roundToInt()} мг на 100 г: расслабляет сфинктер пищевода.")
            }
            if (n.fiber >= 6.0) {
                factors += TriggerFactor(TriggerTag.COARSE_FIBER, TriggerIntensity.MILD, "Много клетчатки (${fmt(n.fiber)} г) — при обострении может раздражать слизистую.")
            }
            if (n.calories >= 400.0) {
                factors += TriggerFactor(TriggerTag.VOLUME, TriggerIntensity.MILD, "Высокая калорийность на 100 г — легко превысить размер порции.")
            }
        }

        fun has(pattern: Regex) = pattern.containsMatchIn(ingredients)

        // «Лимонная кислота» как регулятор кислотности — это не цитрусовый ингредиент.
        // Иначе продукт получал бы лишний тег CITRUS, а у пользователя с личным триггером
        // «цитрусовые» такой продукт сразу попадал бы в красную зону.
        val ingredientsWithoutAcids = PAT_ACID_ADDITIVES.replace(ingredients) { " " }

        if (has(PAT_ACID_ADDITIVES)) {
            factors += TriggerFactor(
                TriggerTag.ACID, TriggerIntensity.MODERATE,
                "В составе кислотные регуляторы (лимонная, уксусная, яблочная, фосфорная кислота или их Е-номера) — " +
                    "добавляют кислоту и провоцируют жжение."
            )
        }
        if (PAT_CITRUS_ING.containsMatchIn(ingredientsWithoutAcids)) {
            factors += TriggerFactor(TriggerTag.CITRUS, TriggerIntensity.MODERATE, "В составе цитрусовые компоненты.")
        }
        if (has(PAT_TOMATO_ING)) {
            factors += TriggerFactor(TriggerTag.TOMATO, TriggerIntensity.MODERATE, "В составе томаты или томатная паста.")
        }
        if (has(PAT_SPICY_ING)) {
            factors += TriggerFactor(TriggerTag.SPICY, TriggerIntensity.MODERATE, "В составе острые специи (перец, чили, паприка).")
        }
        if (has(PAT_CARBONATED)) {
            factors += TriggerFactor(TriggerTag.CARBONATED, TriggerIntensity.MODERATE, "Продукт газирован: диоксид углерода повышает давление в желудке.")
        }
        if (has(PAT_ALCOHOL_ING)) {
            factors += TriggerFactor(TriggerTag.ALCOHOL, TriggerIntensity.STRONG, "В составе алкоголь — недопустим при ГЭРБ, язве и панкреатите.")
        }
        if (has(PAT_COCOA)) {
            factors += TriggerFactor(TriggerTag.CHOCOLATE, TriggerIntensity.MODERATE, "В составе какао — классический триггер рефлюкса.")
        }
        if (has(PAT_MINT)) {
            factors += TriggerFactor(TriggerTag.MINT, TriggerIntensity.MODERATE, "В составе мята или ментол — расслабляет сфинктер пищевода.")
        }
        if (has(PAT_ONION_GARLIC)) {
            factors += TriggerFactor(TriggerTag.ONION_GARLIC, TriggerIntensity.MILD, "В составе лук или чеснок — частые триггеры отрыжки и вздутия.")
        }
        if (has(PAT_LACTOSE)) {
            factors += TriggerFactor(TriggerTag.LACTOSE, TriggerIntensity.MILD, "В составе молочные компоненты (лактоза).")
        }
        if (has(PAT_GLUTEN)) {
            factors += TriggerFactor(TriggerTag.GLUTEN, TriggerIntensity.MILD, "В составе пшеница, рожь, ячмень или овёс (глютен).")
        }
        if (has(PAT_FODMAP_ING)) {
            factors += TriggerFactor(TriggerTag.FODMAP, TriggerIntensity.MILD, "В составе фруктоза, сиропы или инулин — высокий FODMAP.")
        }
        if (has(PAT_FRIED_ING) || text.contains("жарен")) {
            factors += TriggerFactor(TriggerTag.FRIED, TriggerIntensity.MODERATE, "Продукт жареный или во фритюре.")
        }
        if (has(PAT_SMOKED_ING)) {
            factors += TriggerFactor(TriggerTag.SMOKED, TriggerIntensity.MODERATE, "Продукт копчёный или содержит коптильные ароматизаторы.")
        }

        return factors.distinctBy { it.tag to it.intensity }
    }

    private fun buildGastroNote(parsed: ParsedLabel): String {
        val per100 = parsed.per100 ?: return "Данные этикетки распознаны частично."
        val parts = mutableListOf<String>()
        val energyShare = per100.fatEnergyPercent
        parts += "На 100 г: ${per100.calories.roundToInt()} ккал, Б ${fmt(per100.protein)} / Ж ${fmt(per100.fat)} / " +
            "У ${fmt(per100.carbs)}"
        if (energyShare > 30.0) {
            parts += "доля калорий из жира ${energyShare.roundToInt()}% — выше безопасного коридора 25–30%"
        } else if (per100.fat > 0) {
            parts += "доля калорий из жира ${energyShare.roundToInt()}% — в пределах безопасного коридора"
        }
        if (parsed.basis != LabelBasis.PER_100) {
            parts += "значения приведены к 100 г из расчёта «${parsed.basis.short}»"
        }
        if (parsed.detectedTriggers.isNotEmpty()) {
            parts += "выявлены факторы риска: " + parsed.detectedTriggers.joinToString(", ") { it.tag.title.lowercase() }
        }
        return parts.joinToString("; ").replaceFirstChar { it.uppercase() } + "."
    }

    // ---------------------------------------------------------------- утилиты

    private fun String.toNumberOrNull(): Double? =
        replace(',', '.').trim().toDoubleOrNull()

    private fun fmt(v: Double): String = String.format(java.util.Locale.US, "%.1f", v)

    companion object {
        /**
         * Переносимые Unicode-классы вместо `\w` и `\b`.
         *
         * Почему не `\w` с флагом `(?U)`: на настольной JVM `\w` = только `[a-zA-Z0-9_]`,
         * и его расширяет флаг `(?U)` (UNICODE_CHARACTER_CLASS). Но Android использует
         * ICU-реализацию регулярных выражений, где флага `U` не существует —
         * `Pattern.compile("(?U)...")` падает с `PatternSyntaxException`, и приложение
         * закрывается. Поэтому кириллица задаётся явными классами `\p{L}`, которые
         * одинаково работают и на JVM, и на Android.
         */
        private const val W = "[\\p{L}\\p{N}_]"          // «словесный» символ
        private const val NB = "(?![\\p{L}\\p{N}])"      // граница после слова (аналог \b)
        private const val NB_L = "(?<![\\p{L}])"         // граница перед словом
        private const val NB_BOTH = "(?<![\\p{L}\\p{N}])"

        private fun rx(pattern: String) = Regex(pattern)

        private val PAT_NUMBER = rx("""\d+(?:\.\d+)?""")

        private val PAT_KCAL = rx("""(\d+(?:\.\d+)?)\s*(?:ккал|kcal|кал)$NB""")
        private val PAT_KJ = rx("""(\d+(?:\.\d+)?)\s*(?:кдж|kj)$NB""")
        private val PAT_ENERGY_BARE = rx("""(?:энергетическая\s+ценность|калорийность|энергия)[^\d\n]{0,25}(\d+(?:\.\d+)?)""")
        private val PAT_KCAL_AFTER = rx("""(?:ккал|kcal)\D{0,6}(\d+(?:\.\d+)?)""")

        // Поддерживаются и полные подписи, и сокращения вида «Б 5,3 Ж 12 У 36»
        private val PAT_PROTEIN = rx("""белк$W*|протеин$W*|$NB_L""" + """б\s*[:.\-]?\s*(?=\d)""")
        private val PAT_FAT = rx("""жир$W*|$NB_L""" + """ж\s*[:.\-]?\s*(?=\d)""")
        private val PAT_CARBS = rx("""углевод$W*|$NB_L""" + """у\s*[:.\-]?\s*(?=\d)""")
        private val PAT_SAT_FAT = rx("""насыщенн$W*|$NB_L""" + """нж\s*[:.\-]?""")
        private val PAT_SUGAR = rx("""сахар$W*""")
        private val PAT_FIBER = rx("""пищевые\s+волокна|волокн$W*|клетчатк$W*""")
        private val PAT_SALT = rx("""соль|$NB_L""" + """соль\s*[:.\-]""")
        private val PAT_SODIUM = rx("""натрий|$NB_L""" + """na\s*[:.\-]""")
        private val PAT_CAFFEINE = rx("""кофеин$W*""")

        private val PAT_ANY_KEYWORD = rx(
            """белк$W*|протеин$W*|жир$W*|углевод$W*|насыщенн$W*|сахар$W*|волокн$W*|клетчатк$W*|""" +
                """соль|натрий|кофеин$W*|энерг$W*|калорийн$W*|порц$W*|в\s*т\.?\s*ч"""
        )

        private val PAT_TRACE = rx("""след$W*|trace|<|менее""")

        private val PAT_SERVING = rx("""порц$W*[^\d\n]{0,15}(\d+(?:\.\d+)?)\s*(?:г|g|мл|ml)""")
        private val PAT_SERVING_AFTER = rx("""(\d+(?:\.\d+)?)\s*(?:г|g|мл|ml)\s*\)?\s*(?:порц$W*|саше|пакетик$W*)""")
        private val PAT_NETTO = rx("""(?:масса\s+нетто|нетто|объ[её]м|масса)\s*[:\-]?\s*(\d+(?:\.\d+)?)\s*(кг|kg|г|g|мл|ml|л|l)$NB""")
        private val PAT_PER_PACKAGE = rx("""на\s+(?:всю\s+)?упаковк$W*|в\s+упаковке|на\s+банку|за\s+упаковку""")
        private val PAT_PER_PORTION = rx("""порц$W*|саше|пакетик$W*|стакан$W*|в\s*1\s*шт""")
        private val PAT_PER_100 = rx("""(?:в|на)\s*100\s*(?:г|g|мл|ml)$NB|$NB_BOTH""" + """100\s*(?:г|g|мл|ml)$NB""")
        private val PAT_BASIS_PHRASE = rx(
            """(?:в|на)\s*100(?:\s*(?:г|g|мл|ml))?|$NB_BOTH""" + """100\s*(?:г|g|мл|ml)$NB|$NB_BOTH""" + """100g$NB|$NB_BOTH""" + """100ml$NB"""
        )

        private val PAT_TABLE_START = rx("""пищевая\s+ценность|энергетическая\s+ценность|калорийность|на\s+100\s*(?:г|мл)|в\s+100\s*(?:г|мл)""")
        private val PAT_TABLE_END = rx("""состав|ингредиент$W*|срок\s+годности|условия\s+хранения|изготовитель|производитель|противопоказан$W*|хранение""")
        private val PAT_INGREDIENTS = rx("""состав|ингредиент$W*""")

        private val PAT_MONO_POLY = rx("""(?:моно|поли)ненасыщенн$W*""")
        private val PAT_FATTY_ACIDS = rx("""жирн$W*\s+кислот$W*""")

        private val PAT_ACID_ADDITIVES = rx(
            """лимонн$W*\s+кислот$W*|уксус$W*|яблочн$W*\s+кислот$W*|фосфорн$W*\s+кислот$W*|""" +
                """молочн$W*\s+кислот$W*|e\s*330|e\s*260|e\s*296|e\s*338|e\s*270|e\s*334|е\s*330|е\s*260"""
        )
        private val PAT_CITRUS_ING = rx("""лимон$W*|апельсин$W*|цитрус$W*|грейпфрут$W*|мандарин$W*|лайм$NB""")
        private val PAT_TOMATO_ING = rx("""томат$W*|паста\s+томатн$W*|кетчуп""")
        private val PAT_SPICY_ING = rx("""перец\s+(?:ч[её]рн|красн|острый|чили)|капсаицин|паприк$W*|чили|халапеньо|имбир$W*""")
        private val PAT_CARBONATED = rx("""газирован$W*|диоксид\s+углерода|углекислый\s+газ|e\s*290|е\s*290|карбониз$W*|seltzer""")
        private val PAT_ALCOHOL_ING = rx("""алкогол$W*|этанол|спирт$W*|вино$NB|пиво$NB|сидр""")
        private val PAT_COCOA = rx("""какао$W*|шоколад$W*""")
        private val PAT_MINT = rx("""мят$W*|ментол$W*""")
        private val PAT_ONION_GARLIC = rx("""лук$NB|луков$W*|чеснок$W*|экстракт\s+чеснока""")
        private val PAT_LACTOSE = rx("""молок$W*|лактоз$W*|сыворотк$W*|сливк$W*|сливочн$W*\s+масл$W*|творог$W*|сыр$NB""")
        private val PAT_GLUTEN = rx("""пшениц$W*|рож$W*|ячмен$W*|ов[её]с$W*|глютен$W*|солод$W*|мука\s+пшеничн$W*""")
        private val PAT_FODMAP_ING = rx("""фруктоз$W*|сироп$W*|инулин|сорбит$W*|ксилит$W*|мальтит$W*|концентрат\s+сока\s+яблок$W*""")
        private val PAT_FRIED_ING = rx("""фритюр$W*|жарен$W*|обжарен$W*""")
        private val PAT_SMOKED_ING = rx("""копч[её]н$W*|коптильн$W*|дым$W*\s+ароматизатор$W*""")

        private val MACRO_NAMES = setOf("белки", "жиры", "углеводы")
    }
}
