package com.gastrocare.compass.domain.engine

import com.gastrocare.compass.domain.model.DayLog
import com.gastrocare.compass.domain.model.Diagnosis
import com.gastrocare.compass.domain.model.Goal
import com.gastrocare.compass.domain.model.NutritionTargets
import com.gastrocare.compass.domain.model.RiskLevel
import com.gastrocare.compass.domain.model.Severity
import com.gastrocare.compass.domain.model.Symptom
import com.gastrocare.compass.domain.model.TriggerTag
import com.gastrocare.compass.domain.model.UserProfile
import com.gastrocare.compass.domain.model.WeightRecord
import kotlin.math.abs
import kotlin.math.roundToInt

enum class InsightKind(val title: String) {
    RED_FLAG("Важно для здоровья"),
    PERSONAL_TRIGGER("Ваш личный триггер"),
    TIMING("Режим и время"),
    NUTRIENT_EXCESS("Избыток нутриента"),
    NUTRIENT_DEFICIT("Недостаток нутриента"),
    ADHERENCE("Дисциплина питания"),
    STREAK("Прогресс"),
    SAFE_SWAP("Безопасная замена"),
    PORTION("Размер порции"),
    WEIGHT("Динамика веса"),
    DIAGNOSIS_TIP("Правило при вашем диагнозе")
}

/**
 * Персональные подсказки: движок ищет закономерности в дневнике пользователя,
 * а не выдаёт общие статьи. Это то, что превращает счётчик калорий в помощника.
 */
data class Insight(
    val kind: InsightKind,
    val title: String,
    val body: String,
    val action: String? = null,
    val severity: Severity = Severity.INFO,
    val priority: Int = 50,
    /** Тег, который можно предложить добавить в личные триггеры. */
    val suggestedTrigger: TriggerTag? = null
) {
    /**
     * Стабильный ключ подсказки для функции «Понятно» (скрыть).
     *
     * Числа из заголовка убираются намеренно: иначе «Жир выше лимита на 12 г» и
     * «Жир выше лимита на 30 г» считались бы разными подсказками, и скрытая вчера
     * возвращалась бы сегодня снова.
     */
    val key: String
        get() = kind.name + ":" + title.filterNot { it.isDigit() }
            .replace(Regex("\\s+"), " ")
            .trim()
}

class InsightEngine {

    private val correlationWindowHours = 4.0
    private val minOccurrences = 3

    /** Полный набор подсказок для главного экрана: сначала самое важное. */
    fun all(
        profile: UserProfile,
        targets: NutritionTargets,
        today: DayLog,
        history: List<DayLog>,
        weights: List<WeightRecord>
    ): List<Insight> {
        val list = mutableListOf<Insight>()
        list += redFlagInsights(profile)
        list += todayNutrientInsights(profile, targets, today)
        list += timingInsights(profile, targets, today, history)
        list += correlationInsights(profile, history)
        list += streakInsights(history)
        list += adherenceInsights(profile, targets, history)
        list += weightInsights(profile, weights, targets)
        list += diagnosisTips(profile, targets)
        return list.sortedWith(compareByDescending<Insight> { it.severity.ordinal }.thenByDescending { it.priority })
    }

    // ------------------------------------------------------------------ красные флаги

    fun redFlagInsights(profile: UserProfile): List<Insight> {
        val result = mutableListOf<Insight>()
        if (profile.redFlags.isNotEmpty()) {
            result += Insight(
                kind = InsightKind.RED_FLAG,
                title = "Есть признаки, при которых нужен врач, а не диета",
                body = profile.redFlags.joinToString("\n• ", prefix = "• ") { "${it.title} — ${it.action}" },
                action = "Запишитесь к гастроэнтерологу. Приложение продолжит вести дневник — это поможет врачу.",
                severity = Severity.ERROR,
                priority = 100
            )
        }
        if (profile.diagnoses.contains(Diagnosis.BARRETTS)) {
            result += Insight(
                kind = InsightKind.RED_FLAG,
                title = "Пищевод Барретта требует регулярного наблюдения",
                body = "Диета уменьшает симптомы, но не отменяет эндоскопический контроль. " +
                    "Обсудите с врачом периодичность осмотров и приём препаратов.",
                action = "Не отменяйте назначенные ингибиторы протонной помпы самостоятельно.",
                severity = Severity.WARNING,
                priority = 95
            )
        }
        return result
    }

    // ------------------------------------------------------------------ нутриенты дня

    fun todayNutrientInsights(
        profile: UserProfile,
        targets: NutritionTargets,
        today: DayLog
    ): List<Insight> {
        val result = mutableListOf<Insight>()
        if (today.entries.isEmpty()) return result
        val consumed = today.totals

        if (consumed.fat > targets.fatCapGrams) {
            val over = consumed.fat - targets.fatCapGrams
            result += Insight(
                kind = InsightKind.NUTRIENT_EXCESS,
                title = "Жир выше лимита на ${over.roundToInt()} г",
                body = "Сегодня ${consumed.fat.roundToInt()} г жира при безопасном лимите " +
                    "${targets.fatCapGrams.roundToInt()} г. Каждый лишний грамм жира продлевает пребывание еды " +
                    "в желудке и ослабляет сфинктер — особенно опасно вечером.",
                action = "Остаток дня — только нежирный белок и мягкий гарнир. Завтра замените жарку на варку " +
                    "и уберите колбасные изделия.",
                severity = Severity.WARNING,
                priority = 80
            )
        }

        if (consumed.calories > 0 && consumed.fatEnergyPercent > 35.0) {
            result += Insight(
                kind = InsightKind.NUTRIENT_EXCESS,
                title = "Слишком много калорий из жира: ${consumed.fatEnergyPercent.roundToInt()}%",
                body = "Безопасный коридор при вашем диагнозе — до ${targets.fat.percentOfCalories.roundToInt()}% " +
                    "калорий из жира. Сейчас доля выше.",
                action = "Каждый грамм жира = 9 ккал. Убрав 20 г жира, вы снимете ~180 ккал без уменьшения объёма еды.",
                severity = Severity.WARNING,
                priority = 70
            )
        }

        if (consumed.caffeineMg > targets.caffeineCapMg) {
            result += Insight(
                kind = InsightKind.NUTRIENT_EXCESS,
                title = "Кофеина больше нормы",
                body = "${consumed.caffeineMg.roundToInt()} мг при лимите ${targets.caffeineCapMg.roundToInt()} мг. " +
                    "Кофеин расслабляет нижний пищеводный сфинктер и повышает секрецию кислоты.",
                action = "Замените вторую чашку кофе на цикорий, какао на молоке или травяной чай (не мятный).",
                severity = Severity.WARNING,
                priority = 65
            )
        }

        if (consumed.salt > targets.saltCapGrams) {
            result += Insight(
                kind = InsightKind.NUTRIENT_EXCESS,
                title = "Соли больше ${targets.saltCapGrams.roundToInt()} г",
                body = "Сейчас ${consumed.salt.roundToInt()} г. Соль раздражает слизистую и задерживает жидкость.",
                action = "Основные источники — колбасы, соусы, консервы, бульонные кубики, чипсы. " +
                    "Готовьте без соли, добавляйте её уже в тарелку.",
                severity = Severity.INFO,
                priority = 45
            )
        }

        if (consumed.sugar > targets.sugarCapGrams) {
            result += Insight(
                kind = InsightKind.NUTRIENT_EXCESS,
                title = "Много сахара: ${consumed.sugar.roundToInt()} г",
                body = "Ориентир — до ${targets.sugarCapGrams.roundToInt()} г. Сахар усиливает брожение, " +
                    "вздутие и заброс.",
                action = "Сладкое перенесите на десерт после основного приёма — не натощак и не перед сном.",
                severity = Severity.INFO,
                priority = 42
            )
        }

        if (consumed.protein < targets.protein.grams * 0.7) {
            result += Insight(
                kind = InsightKind.NUTRIENT_DEFICIT,
                title = "Белка не хватает: ${consumed.protein.roundToInt()} из ${targets.protein.grams.roundToInt()} г",
                body = "Белок нужен для заживления слизистой и долгой сытости. При дефиците белка сильнее тянет " +
                    "на быстрые углеводы и вечернее переедание.",
                action = "Добавьте в перекус творог, яйцо, индейку или рыбу — это не увеличит объём сильно, " +
                    "но даст сытость.",
                severity = Severity.WARNING,
                priority = 68
            )
        }

        if (consumed.fiber < targets.fiberTargetGrams * 0.6) {
            result += Insight(
                kind = InsightKind.NUTRIENT_DEFICIT,
                title = "Мало клетчатки",
                body = "${consumed.fiber.roundToInt()} г из ${targets.fiberTargetGrams.roundToInt()} г. " +
                    "При ГЭРБ и гастрите нужна именно мягкая, термически обработанная клетчатка.",
                action = "Добавьте распаренные овощи, овсянку, печёное яблоко без кожуры, пюре из тыквы.",
                severity = Severity.INFO,
                priority = 40
            )
        }

        if (consumed.calories > targets.calories * 1.15) {
            result += Insight(
                kind = InsightKind.NUTRIENT_EXCESS,
                title = "Калорий больше нормы на ${(consumed.calories - targets.calories).roundToInt()} ккал",
                body = "Лишний объём и энергия вечером — самый частый сценарий ночной изжоги.",
                action = "Следующий приём сделайте лёгким: белок + овощи на пару, без масла и хлеба.",
                severity = Severity.INFO,
                priority = 50
            )
        }

        return result
    }

    // ------------------------------------------------------------------ режим и время

    fun timingInsights(
        profile: UserProfile,
        targets: NutritionTargets,
        today: DayLog,
        history: List<DayLog>
    ): List<Insight> {
        val result = mutableListOf<Insight>()

        val lateDays = history.filter { day ->
            val last = day.lastMealMinuteOfDay ?: return@filter false
            val gap = profile.sleepHour * 60 - last
            gap in 0..180
        }
        if (lateDays.size >= 3) {
            val nightSymptoms = lateDays.count { day ->
                day.symptoms.any {
                    it.symptom == Symptom.NIGHT_COUGH || it.symptom == Symptom.HEARTBURN ||
                        it.symptom == Symptom.REGURGITATION
                }
            }
            result += Insight(
                kind = InsightKind.TIMING,
                title = "Поздние ужины повторяются: ${lateDays.size} дней",
                body = if (nightSymptoms > 0) {
                    "В ${nightSymptoms} из этих дней вы отмечали симптомы со стороны пищевода. " +
                        "Связь «поздний ужин → ночные симптомы» — одна из самых устойчивых в гастроэнтерологии."
                } else {
                    "Вы ели менее чем за 3 часа до сна ${lateDays.size} раз. Даже без симптомов сейчас " +
                        "это главный фактор риска ночного рефлюкса."
                },
                action = "Сдвиньте ужин на ${targets.lastMealHour}:00. Если голодно — стакан кефира " +
                    "или ложка творога за 2 часа до сна.",
                severity = Severity.WARNING,
                priority = 85
            )
        }

        if (today.entries.size < targets.mealCount - 1 && today.entries.isNotEmpty()) {
            result += Insight(
                kind = InsightKind.TIMING,
                title = "Мало приёмов пищи: ${today.entries.size} из ${targets.mealCount}",
                body = "Редкие приёмы означают большие порции и кислоту «натощак». Для вашего диагноза " +
                    "план — ${targets.mealCount} приёмов примерно по " +
                    "${(targets.calories / targets.mealCount).roundToInt()} ккал.",
                action = "Поставьте напоминания на перекусы: они не дают переесть вечером.",
                severity = Severity.INFO,
                priority = 55
            )
        }

        val bigPortions = today.entries.filter { it.grams > 350 }
        if (bigPortions.isNotEmpty()) {
            result += Insight(
                kind = InsightKind.PORTION,
                title = "Порция больше 350 г",
                body = bigPortions.joinToString(", ") { "${it.foodName} (${it.grams.roundToInt()} г)" } +
                    ". Растяжение желудка повышает давление и провоцирует заброс.",
                action = "Разделите приём на две части с перерывом 20–30 минут — эффект сытости сохранится.",
                severity = Severity.WARNING,
                priority = 72
            )
        }

        return result
    }

    // ------------------------------------------------------------------ личные триггеры

    /**
     * Ищет статистическую связь «продукт/фактор → симптом».
     * Сравнивает долю симптомных эпизодов после приёма продукта с фоновой частотой.
     */
    fun correlationInsights(profile: UserProfile, history: List<DayLog>): List<Insight> {
        val result = mutableListOf<Insight>()
        val allEntries = history.flatMap { it.entries }
        if (allEntries.size < 6) return result

        val symptomTimes = history.flatMap { day -> day.symptoms.filter { it.severity >= 1 }.map { it.timestamp } }
        if (symptomTimes.size < minOccurrences) return result

        fun followedBySymptom(entryTime: Long): Boolean =
            symptomTimes.any { it >= entryTime && it - entryTime <= (correlationWindowHours * 3_600_000L).toLong() }

        val episodesByTag = mutableMapOf<TriggerTag, Int>()
        val totalsByTag = mutableMapOf<TriggerTag, Int>()
        val episodesByName = mutableMapOf<String, Int>()
        val totalsByName = mutableMapOf<String, Int>()

        var symptomEntries = 0
        allEntries.forEach { entry ->
            val bad = followedBySymptom(entry.timestamp)
            if (bad) symptomEntries++
            entry.factors.forEach { tag ->
                totalsByTag[tag] = (totalsByTag[tag] ?: 0) + 1
                if (bad) episodesByTag[tag] = (episodesByTag[tag] ?: 0) + 1
            }
            totalsByName[entry.foodName] = (totalsByName[entry.foodName] ?: 0) + 1
            if (bad) episodesByName[entry.foodName] = (episodesByName[entry.foodName] ?: 0) + 1
        }

        val baseline = symptomEntries.toDouble() / allEntries.size

        episodesByTag.forEach { (tag, hits) ->
            val total = totalsByTag[tag] ?: return@forEach
            if (total < minOccurrences || hits < 2) return@forEach
            val rate = hits.toDouble() / total
            val lift = if (baseline > 0) rate / baseline else Double.MAX_VALUE
            if (lift >= 1.8 && profile.personalTriggers.contains(tag).not()) {
                result += Insight(
                    kind = InsightKind.PERSONAL_TRIGGER,
                    title = "Похоже, «${tag.title.lowercase()}» — ваш триггер",
                    body = "После приёмов с этим фактором симптомы появлялись в ${hits} случаях из $total " +
                        "(${(rate * 100).roundToInt()}%), тогда как в среднем — в ${(baseline * 100).roundToInt()}%. " +
                        tag.mechanism,
                    action = "Добавить в личные триггеры — тогда приложение будет сразу предупреждать о таких продуктах.",
                    severity = Severity.WARNING,
                    priority = 88,
                    suggestedTrigger = tag
                )
            }
        }

        episodesByName.entries
            .filter { (name, hits) -> hits >= 2 && (totalsByName[name] ?: 0) >= minOccurrences }
            .map { (name, hits) ->
                val total = totalsByName[name] ?: 1
                Triple(name, hits, hits.toDouble() / total)
            }
            .filter { (_, _, rate) -> baseline <= 0.0 || rate / baseline >= 1.8 }
            .sortedByDescending { it.third }
            .take(3)
            .forEach { (name, hits, rate) ->
                result += Insight(
                    kind = InsightKind.PERSONAL_TRIGGER,
                    title = "«$name» совпадает с симптомами",
                    body = "Симптомы в течение $correlationWindowHours ч после «$name» — $hits раз " +
                        "(${(rate * 100).roundToInt()}% приёмов с этим продуктом).",
                    action = "Попробуйте исключить его на 2 недели и сравнить состояние.",
                    severity = Severity.INFO,
                    priority = 60
                )
            }

        return result
    }

    // ------------------------------------------------------------------ прогресс и дисциплина

    fun streakInsights(history: List<DayLog>): List<Insight> {
        val result = mutableListOf<Insight>()
        val sorted = history.sortedByDescending { it.epochDay }
        if (sorted.isEmpty()) return result

        var streak = 0
        for (day in sorted) {
            if (day.symptoms.any { it.severity >= 1 }) break
            streak++
        }

        if (streak >= 3) {
            result += Insight(
                kind = InsightKind.STREAK,
                title = "Дней без симптомов подряд: $streak",
                body = "Текущий рацион работает. Держите темп: закрепление результата занимает 4–6 недель.",
                action = null,
                severity = Severity.INFO,
                priority = 30
            )
        }

        val symptomDays = sorted.take(14).count { it.symptoms.any { s -> s.severity >= 1 } }
        if (symptomDays >= 4 && sorted.size >= 7) {
            result += Insight(
                kind = InsightKind.RED_FLAG,
                title = "Симптомы в $symptomDays днях из последних ${minOf(14, sorted.size)}",
                body = "Частые обострения на фоне диеты — повод пересмотреть терапию с врачом, " +
                    "а не только рацион.",
                action = "Возьмите с собой дневник питания и симптомов на приём: это объективные данные для врача.",
                severity = Severity.WARNING,
                priority = 90
            )
        }
        return result
    }

    fun adherenceInsights(
        profile: UserProfile,
        targets: NutritionTargets,
        history: List<DayLog>
    ): List<Insight> {
        val result = mutableListOf<Insight>()
        val tracked = history.filter { it.entries.isNotEmpty() }
        if (tracked.size < 3) return result

        val avgCalories = tracked.map { it.totals.calories }.average()
        val withinRange = tracked.count {
            it.totals.calories in (targets.calories * 0.85)..(targets.calories * 1.15)
        }
        val riskyEntries = tracked.sumOf { day -> day.entries.count { it.riskLevel == RiskLevel.AVOID } }

        result += Insight(
            kind = InsightKind.ADHERENCE,
            title = "Калорийность в цели: $withinRange дней из ${tracked.size}",
            body = "Среднее за период — ${avgCalories.roundToInt()} ккал при цели " +
                "${targets.calories.roundToInt()} ккал.",
            action = if (avgCalories < targets.calories * 0.85) {
                "Вы систематически недоедаете. При ГЭРБ и гастрите это повышает кислотность и ведёт к вечернему " +
                    "перееданию — добавьте приём пищи."
            } else if (avgCalories > targets.calories * 1.15) {
                "Стабильный перебор. Начните с одного изменения: уберите калорийные напитки."
            } else {
                "Держите этот уровень 2–3 недели, затем оцените вес."
            },
            severity = Severity.INFO,
            priority = 35
        )

        if (riskyEntries > 0) {
            result += Insight(
                kind = InsightKind.ADHERENCE,
                title = "Продукты из красной зоны: $riskyEntries раз",
                body = "Это продукты, которые при вашем диагнозе почти всегда дают симптомы.",
                action = "Откройте подсказки по этим блюдам — приложение предложит безопасную замену " +
                    "с похожей калорийностью.",
                severity = Severity.WARNING,
                priority = 66
            )
        }
        return result
    }

    fun weightInsights(
        profile: UserProfile,
        weights: List<WeightRecord>,
        targets: NutritionTargets
    ): List<Insight> {
        val result = mutableListOf<Insight>()
        if (weights.size < 2) return result

        val sorted = weights.sortedBy { it.timestamp }
        val first = sorted.first()
        val last = sorted.last()
        val days = ((last.timestamp - first.timestamp) / 86_400_000.0).coerceAtLeast(1.0)
        val totalChange = last.kg - first.kg
        val perWeek = totalChange / days * 7.0
        val percentPerWeek = abs(perWeek) / profile.weightKg * 100.0

        val title = when {
            totalChange < -0.2 -> "Вес снижается: ${String.format("%.1f", abs(totalChange))} кг за ${days.roundToInt()} дн."
            totalChange > 0.2 -> "Вес растёт: +${String.format("%.1f", totalChange)} кг за ${days.roundToInt()} дн."
            else -> "Вес стабилен"
        }

        val body: String
        val severity: Severity
        when {
            profile.goal == Goal.LOSE_WEIGHT && percentPerWeek > 1.0 -> {
                body = "Темп ${String.format("%.1f", percentPerWeek)}% в неделю — быстрее безопасного. " +
                    "Быстрая потеря массы тела нередко приводит к обострению: слизистой нужны белок и микронутриенты."
                severity = Severity.WARNING
            }

            profile.goal == Goal.LOSE_WEIGHT && percentPerWeek < 0.25 && totalChange <= 0 -> {
                body = "Темп меньше 0,25% в неделю. Прежде чем урезать калории, проверьте точность записей: " +
                    "чаще всего «не уходит вес» из-за незаписанных перекусов и масла при готовке."
                severity = Severity.INFO
            }

            profile.goal == Goal.GAIN_WEIGHT && percentPerWeek > 0.5 -> {
                body = "Набор быстрее 0,5% в неделю при ГЭРБ повышает давление на сфинктер. " +
                    "Замедлите темп: калорийность снижаем на 100 ккал."
                severity = Severity.WARNING
            }

            else -> {
                body = "Целевой коридор — 0,25–1% массы тела в неделю. Оценивайте не отдельное взвешивание, " +
                    "а средний тренд за 2 недели."
                severity = Severity.INFO
            }
        }

        result += Insight(
            kind = InsightKind.WEIGHT,
            title = title,
            body = body,
            action = "Цель по калориям: ${targets.calories.roundToInt()} ккал. " +
                "При изменении тренда приложение предложит корректировку.",
            severity = severity,
            priority = 62
        )
        return result
    }

    // ------------------------------------------------------------------ правила по диагнозу

    fun diagnosisTips(profile: UserProfile, targets: NutritionTargets): List<Insight> {
        val result = mutableListOf<Insight>()
        val diagnoses = profile.diagnoses

        if (diagnoses.contains(Diagnosis.GERD) || diagnoses.contains(Diagnosis.BARRETTS) ||
            diagnoses.contains(Diagnosis.HERNIA)
        ) {
            result += Insight(
                kind = InsightKind.DIAGNOSIS_TIP,
                title = "Правило трёх часов и приподнятой кровати",
                body = "Последний приём — до ${targets.lastMealHour}:00. После еды оставайтесь вертикально 30–60 минут, " +
                    "не наклоняйтесь и не поднимайте тяжести. Спите с приподнятым на 15–20 см головным концом кровати: " +
                    "это физически уменьшает заброс.",
                action = "Проверьте, что изголовье поднято, а не просто высокая подушка.",
                severity = Severity.INFO,
                priority = 58
            )
            result += Insight(
                kind = InsightKind.DIAGNOSIS_TIP,
                title = "Объём порции важнее «разрешённости» продукта",
                body = "Даже безопасная еда объёмом больше 350 г даёт заброс. Триггеры вроде мяты, шоколада, " +
                    "кофеина, цитрусовых, томатов и газировки — на втором месте по значимости.",
                action = "Ориентир: порция размером с два кулака, жира не больше " +
                    "${(targets.fatCapGrams / targets.mealCount).roundToInt()} г за приём.",
                severity = Severity.INFO,
                priority = 52
            )
        }
        if (diagnoses.contains(Diagnosis.ULCER) || diagnoses.contains(Diagnosis.GASTRITIS_HIGH)) {
            result += Insight(
                kind = InsightKind.DIAGNOSIS_TIP,
                title = "Механически и термически щадящее питание",
                body = "В период обострения пища должна быть тёплой (не горячее 60 °C, не холоднее 15 °C), " +
                    "протёртой или мягкой, варёной или паровой. Исключают острое, жареное, копчёное, крепкий кофе и алкоголь.",
                action = "Пейте между приёмами, а не во время еды: жидкость не должна разбавлять и растягивать желудок.",
                severity = Severity.INFO,
                priority = 54
            )
        }
        if (diagnoses.contains(Diagnosis.PANCREATITIS) || diagnoses.contains(Diagnosis.GALLSTONES)) {
            result += Insight(
                kind = InsightKind.DIAGNOSIS_TIP,
                title = "Жир распределяем, а не откладываем на вечер",
                body = "Резкая жирная нагрузка — прямой путь к приступу. Лимит " +
                    "${targets.fatCapGrams.roundToInt()} г в сутки делите на ${targets.mealCount} приёмов: " +
                    "примерно ${(targets.fatCapGrams / targets.mealCount).roundToInt()} г за раз.",
                action = "Не голодайте больше 4–5 часов: застой желчи опаснее небольшого превышения калорий.",
                severity = Severity.WARNING,
                priority = 76
            )
        }
        if (diagnoses.contains(Diagnosis.IBS)) {
            result += Insight(
                kind = InsightKind.DIAGNOSIS_TIP,
                title = "Низкий FODMAP и регулярность",
                body = "Основные триггеры СРК: лук, чеснок, бобовые, молоко, яблоки, груши, сиропы, сладкие жвачки. " +
                    "Пищу вводите по одному продукту и наблюдайте 2–3 дня.",
                action = "Белок ${targets.protein.grams.roundToInt()} г и клетчатка " +
                    "${targets.fiberTargetGrams.roundToInt()} г — но из мягких источников.",
                severity = Severity.INFO,
                priority = 56
            )
        }
        return result
    }
}
